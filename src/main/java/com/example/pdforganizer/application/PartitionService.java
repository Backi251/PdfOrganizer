package com.example.pdforganizer.application;

import com.example.pdforganizer.config.AppConfig;
import com.example.pdforganizer.domain.FolderFileEntry;
import com.example.pdforganizer.domain.FolderInfo;
import com.example.pdforganizer.util.FileNameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Distribuye los archivos de UNA carpeta logica (una poliza, o un proveedor
 * sin subcarpetas de poliza) en una o mas carpetas de salida, respetando:
 *
 *  1. Ninguna carpeta de salida supera AppConfig.MAX_FOLDER_SIZE_BYTES.
 *  2. Los archivos prioritarios (Transferencia/Cheque/Poliza) se colocan,
 *     en la mayor cantidad posible, en la carpeta numero 1.
 *  3. Se conservan todos los archivos (nunca se descarta ninguno).
 *
 * ALGORITMO ELEGIDO: variante de First Fit Decreasing (FFD).
 * FFD ordena los elementos de mayor a menor tamano y los coloca en el primer
 * contenedor donde quepan, abriendo uno nuevo si ninguno tiene espacio. Da
 * resultados cercanos al optimo con complejidad baja (O(n log n) + O(n *
 * carpetas)), muy por debajo del costo de un bin-packing exacto (NP-dificil),
 * que no aporta beneficio real aqui.
 *
 * Se adapta FFD en dos puntos para cumplir las reglas del cliente:
 *  - La carpeta 1 se llena PRIMERO con archivos prioritarios, ordenados de
 *    forma ASCENDENTE (no descendente), porque el objetivo ahi no es
 *    minimizar el numero de carpetas sino MAXIMIZAR CUANTOS prioritarios
 *    caben en esa carpeta concreta; para maximizar la cantidad de elementos
 *    dentro de un presupuesto fijo conviene intentar primero los mas
 *    pequenos.
 *  - El espacio restante de la carpeta 1, y todas las carpetas siguientes,
 *    se llenan con FFD estandar (descendente) sobre el resto de archivos
 *    (prioritarios que no cupieron + no prioritarios).
 */
public final class PartitionService {

    public List<FolderInfo> partition(String logicalFolderName, Path outputParent,
                                       List<FolderFileEntry> entries) {
        long maxSize = AppConfig.MAX_FOLDER_SIZE_BYTES;
        List<DistributionUnit> units = buildUnits(entries);
        long totalSize = units.stream().mapToLong(DistributionUnit::sizeBytes).sum();

        if (totalSize <= maxSize) {
            FolderInfo single = new FolderInfo(logicalFolderName, outputParent.resolve(logicalFolderName));
            for (DistributionUnit unit : units) {
                unit.entries().forEach(single::addFile);
            }
            return List.of(single);
        }

        List<DistributionUnit> priority = new ArrayList<>();
        List<DistributionUnit> normal = new ArrayList<>();
        for (DistributionUnit unit : units) {
            (unit.priority() ? priority : normal).add(unit);
        }

        priority.sort(Comparator.comparingLong(DistributionUnit::sizeBytes));
        List<FolderInfo> folders = new ArrayList<>();
        FolderInfo folder1 = new FolderInfo("__pending__", outputParent);
        folders.add(folder1);

        List<DistributionUnit> overflow = new ArrayList<>();
        for (DistributionUnit unit : priority) {
            if (folder1.fits(unit.sizeBytes(), maxSize)) {
                unit.entries().forEach(folder1::addFile);
            } else {
                overflow.add(unit);
            }
        }

        overflow.addAll(normal);
        overflow.sort(Comparator.comparingLong(DistributionUnit::sizeBytes).reversed());

        for (DistributionUnit unit : overflow) {
            FolderInfo target = null;
            for (FolderInfo folder : folders) {
                if (folder.fits(unit.sizeBytes(), maxSize)) {
                    target = folder;
                    break;
                }
            }
            if (target == null) {
                target = new FolderInfo("__pending__", outputParent);
                folders.add(target);
            }
            unit.entries().forEach(target::addFile);
        }

        int total = folders.size();
        for (int i = 0; i < total; i++) {
            String name = FileNameUtils.numberedFolderName(logicalFolderName, i + 1, total);
            folders.get(i).rename(name, outputParent.resolve(name));
        }
        return folders;
    }

    /**
     * Agrupa entradas que deben permanecer inseparables. En particular, el
     * primer PDF de una serie dividida y su XML homonimo forman una sola unidad.
     */
    private List<DistributionUnit> buildUnits(List<FolderFileEntry> entries) {
        List<DistributionUnit> units = new ArrayList<>();
        java.util.Map<String, List<FolderFileEntry>> grouped = new java.util.LinkedHashMap<>();

        for (FolderFileEntry entry : entries) {
            if (entry.isGrouped()) {
                grouped.computeIfAbsent(entry.groupId(), ignored -> new ArrayList<>()).add(entry);
            } else {
                units.add(new DistributionUnit(List.of(entry)));
            }
        }

        for (List<FolderFileEntry> group : grouped.values()) {
            units.add(new DistributionUnit(group));
        }
        return units;
    }

    private record DistributionUnit(List<FolderFileEntry> entries) {
        long sizeBytes() {
            return entries.stream().mapToLong(FolderFileEntry::sizeBytes).sum();
        }

        boolean priority() {
            return entries.stream().anyMatch(FolderFileEntry::priority);
        }
    }

    /**
     * Agrupa carpetas YA PARTICIONADAS (por ejemplo, todas las carpetas de
     * poliza de un mismo proveedor, cada una ya garantizada <= maxSize por
     * un llamado previo a partition()) en contenedores de proveedor que
     * tampoco superen el limite.
     *
     * A diferencia de partition(), aqui las "unidades" a empaquetar son
     * carpetas completas (no se rompen ni se reparten sus archivos
     * individuales entre contenedores distintos): una poliza siempre
     * permanece intacta dentro de un unico contenedor de proveedor.
     *
     * IMPORTANTE: a diferencia de partition() (que usa FFD ordenando por
     * tamano para minimizar el numero de carpetas), aqui se preserva el
     * ORDEN DE ENTRADA de las polizas (se asume que el llamador ya las
     * entrega en orden natural: Poliza 001, Poliza 002, Poliza 003...).
     * Se usa "Next Fit": se van llenando los contenedores EN ESE ORDEN,
     * y solo se abre un contenedor nuevo cuando la poliza actual ya no
     * cabe en el ultimo contenedor abierto. Esto es intencional: el
     * cliente espera que "Proveedor A 1" contenga las primeras polizas en
     * orden, "Proveedor A 2" las siguientes, etc. — no una mezcla
     * reordenada por tamano (que es mas eficiente en espacio pero
     * confunde al usuario al buscar una poliza especifica).
     */
    public List<List<FolderInfo>> groupFoldersIntoContainers(List<FolderInfo> folders) {
        long maxSize = AppConfig.MAX_FOLDER_SIZE_BYTES;

        List<List<FolderInfo>> containers = new ArrayList<>();
        List<Long> containerSizes = new ArrayList<>();

        for (FolderInfo folder : folders) {
            int lastIndex = containers.size() - 1;
            boolean fitsInCurrent = lastIndex >= 0
                    && containerSizes.get(lastIndex) + folder.getCurrentSizeBytes() <= maxSize;

            if (!fitsInCurrent) {
                containers.add(new ArrayList<>());
                containerSizes.add(0L);
                lastIndex = containers.size() - 1;
            }
            containers.get(lastIndex).add(folder);
            containerSizes.set(lastIndex, containerSizes.get(lastIndex) + folder.getCurrentSizeBytes());
        }
        return containers;
    }
}
