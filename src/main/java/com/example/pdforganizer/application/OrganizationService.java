package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.*;
import com.example.pdforganizer.infrastructure.ApplicationLogger;
import com.example.pdforganizer.infrastructure.FileSystemService;
import com.example.pdforganizer.util.FileNameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orquesta la replicacion de la jerarquia Proveedor/Poliza, delegando en
 * CompressionService, PdfSplitService y PartitionService, y finalmente
 * materializa las carpetas numeradas de salida en disco.
 *
 * DECISION DE DISENO: la regla de "las carpetas de proveedores no pueden
 * superar 3.95 MB" (seccion 5) y la regla de "las carpetas de polizas
 * tampoco" (seccion 6) se aplican asi:
 *   - Cada POLIZA se particiona de forma independiente a nivel de archivo
 *     (seccion 6): sus PDFs se comprimen/dividen y se distribuyen en una o
 *     mas carpetas de poliza, cada una <= 3.95 MB.
 *   - Ademas, si un proveedor tiene varias polizas, esas carpetas de poliza
 *     YA PARTICIONADAS se agrupan a su vez en contenedores de proveedor que
 *     tampoco superan 3.95 MB (seccion 5). Si el contenido completo del
 *     proveedor cabe en un solo contenedor, se usa el nombre normal
 *     ("Punto 4-Documental Proveedor A"); si no, se numeran igual que las
 *     polizas: "Punto 4-Documental Proveedor A 1", "... 2", "... 3", etc.
 *     Una poliza nunca se reparte entre dos contenedores de proveedor
 *     distintos: permanece intacta dentro de uno solo.
 *   - Si un proveedor NO tiene subcarpetas de poliza (archivos sueltos
 *     directamente bajo el proveedor), el PROVEEDOR se particiona
 *     directamente como unidad a nivel de archivo (mismo mecanismo que una
 *     poliza), generando "Punto 4-Documental Proveedor A 1", "... 2", etc.
 *
 * Cualquier nivel de anidamiento adicional e inesperado dentro de una
 * poliza se aplana (todos sus archivos se tratan como pertenecientes a esa
 * poliza), ya que la estructura del cliente no contempla mas de dos niveles.
 */
public final class OrganizationService {

    private final FileSystemService fileSystemService;
    private final CompressionService compressionService;
    private final PdfSplitService pdfSplitService;
    private final PartitionService partitionService;
    private final ApplicationLogger logger;

    public OrganizationService(FileSystemService fileSystemService,
                                CompressionService compressionService,
                                PdfSplitService pdfSplitService,
                                PartitionService partitionService,
                                ApplicationLogger logger) {
        this.fileSystemService = fileSystemService;
        this.compressionService = compressionService;
        this.pdfSplitService = pdfSplitService;
        this.partitionService = partitionService;
        this.logger = logger;
    }

    public void organize(Path sourceRoot, Path outputRoot, Path tempRoot,
                          CompressionLevel level, ProcessingResult result,
                          ProgressListener progress, int totalFileCountForProgress) throws IOException {

        fileSystemService.createDirectories(outputRoot);
        List<Path> providers = listSubdirectories(sourceRoot);

        int[] processedCounter = {0};

        if (providers.isEmpty()) {
            // La carpeta madre no tiene subcarpetas de proveedor: se trata
            // toda la carpeta madre como una unica unidad a particionar.
            List<FolderInfo> rootFolders = computeLeafFolders(sourceRoot, sourceRoot.getFileName().toString(),
                    tempRoot, level, result, progress, processedCounter, totalFileCountForProgress, false);
            for (FolderInfo folder : rootFolders) {
                materializeFolder(folder, outputRoot, result);
            }
            fileSystemService.deleteRecursivelyQuietly(tempRoot.resolve("tmp_" + Integer.toHexString(sourceRoot.hashCode())));
            return;
        }

        for (Path providerDir : providers) {
            String providerOutputName = FileNameUtils.providerOutputName(providerDir.getFileName().toString());

            List<Path> policyDirs = listSubdirectories(providerDir);
            List<Path> looseFiles = listRegularFilesDirect(providerDir);
            List<Path> leafTempDirsToClean = new ArrayList<>();

            if (!policyDirs.isEmpty()) {
                // Paso 1: particionar cada poliza de forma independiente (seccion 6),
                // SIN materializar todavia en disco (aun no sabemos a que contenedor
                // de proveedor pertenecera cada una).
                List<FolderInfo> allPolicyFolders = new ArrayList<>();
                for (Path policyDir : policyDirs) {
                    Path leafTempDir = tempRoot.resolve("tmp_" + Integer.toHexString(policyDir.hashCode()));
                    leafTempDirsToClean.add(leafTempDir);
                    allPolicyFolders.addAll(computeLeafFolders(policyDir, policyDir.getFileName().toString(),
                            leafTempDir, level, result, progress, processedCounter, totalFileCountForProgress, false));
                }
                if (!looseFiles.isEmpty()) {
                    // Caso mixto no contemplado en la estructura tipica: se tratan como
                    // una poliza adicional nombrada igual que el proveedor.
                    Path leafTempDir = tempRoot.resolve("tmp_" + Integer.toHexString((providerDir.toString() + "#sueltos").hashCode()));
                    leafTempDirsToClean.add(leafTempDir);
                    allPolicyFolders.addAll(computeLeafFolders(providerDir, "Archivos sueltos",
                            leafTempDir, level, result, progress, processedCounter, totalFileCountForProgress, true));
                }

                // Paso 2: agrupar las carpetas de poliza ya particionadas en
                // contenedores de proveedor que tampoco superen 3.95 MB (seccion 5).
                if (!allPolicyFolders.isEmpty()) {
                    List<List<FolderInfo>> containers = partitionService.groupFoldersIntoContainers(allPolicyFolders);
                    int totalContainers = containers.size();
                    for (int i = 0; i < totalContainers; i++) {
                        String containerName = FileNameUtils.numberedFolderName(providerOutputName, i + 1, totalContainers);
                        Path containerDir = outputRoot.resolve(containerName);
                        fileSystemService.createDirectories(containerDir);
                        for (FolderInfo policyFolder : containers.get(i)) {
                            materializeFolder(policyFolder, containerDir, result);
                        }
                    }
                }
            } else {
                // Proveedor sin subcarpetas de poliza: se particiona el proveedor
                // directamente como unidad a nivel de archivo (seccion 5).
                Path leafTempDir = tempRoot.resolve("tmp_" + Integer.toHexString(providerDir.hashCode()));
                leafTempDirsToClean.add(leafTempDir);
                List<FolderInfo> providerFolders = computeLeafFolders(providerDir, providerOutputName,
                        leafTempDir, level, result, progress, processedCounter, totalFileCountForProgress, false);
                for (FolderInfo folder : providerFolders) {
                    materializeFolder(folder, outputRoot, result);
                }
            }

            for (Path t : leafTempDirsToClean) {
                fileSystemService.deleteRecursivelyQuietly(t);
            }
        }
    }

    /**
     * Lee, comprime/divide y particiona (a nivel de archivo) los archivos de
     * UNA carpeta logica (poliza, o proveedor sin polizas). Devuelve la
     * lista de FolderInfo ya calculada, cada una <= 3.95 MB, PERO SIN
     * MATERIALIZAR TODAVIA en su ubicacion final: el nombre y ruta de cada
     * FolderInfo aqui son provisionales (relativos a tempRoot) y deben
     * corregirse con FolderInfo.rename(...) antes o durante materializeFolder,
     * lo cual materializeFolder hace automaticamente al recibir el
     * outputParent definitivo.
     */
    private List<FolderInfo> computeLeafFolders(Path sourceLeafDir, String logicalName, Path leafTempDir,
                                                 CompressionLevel level, ProcessingResult result,
                                                 ProgressListener progress, int[] processedCounter, int totalFiles,
                                                 boolean onlyDirectFiles) throws IOException {

        List<Path> files = onlyDirectFiles
                ? listRegularFilesDirect(sourceLeafDir)
                : fileSystemService.listFilesRecursively(sourceLeafDir);

        if (files.isEmpty()) {
            return List.of();
        }

        List<FolderFileEntry> entries = new ArrayList<>();
        java.util.Map<String, Path> xmlByBaseName = new java.util.HashMap<>();
        java.util.Set<Path> pairedXmlFiles = new java.util.HashSet<>();

        // Primero indexamos XML por nombre base. Un XML solo puede asociarse
        // con el PDF homonimo y se materializa una sola vez: junto al primer
        // fragmento del PDF.
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (FileNameUtils.isXml(name)) {
                xmlByBaseName.put(xmlPairKey(file), file);
            }
        }

        // Determinamos desde el inicio qué XML tienen PDF homonimo. Esos XML
        // nunca se agregan como archivos independientes; se agregan una sola
        // vez dentro del grupo del primer fragmento del PDF.
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (FileNameUtils.isPdf(name)) {
                Path xml = xmlByBaseName.get(xmlPairKey(file));
                if (xml != null) {
                    pairedXmlFiles.add(xml);
                }
            }
        }

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            processedCounter[0]++;
            progress.onFileStarted(fileName, processedCounter[0], totalFiles);

            long originalSize;
            try {
                originalSize = fileSystemService.sizeOf(file);
            } catch (IOException e) {
                result.addError(new ProcessingError(fileName, "leer tamano", e.getMessage(), null));
                continue;
            }
            result.addOriginalSize(originalSize);

            try {
                if (FileNameUtils.isPdf(fileName)) {
                    Path matchingXml = xmlByBaseName.get(xmlPairKey(file));
                    long xmlSize = matchingXml == null ? 0L : fileSystemService.sizeOf(matchingXml);
                    handlePdf(file, fileName, matchingXml, xmlSize, leafTempDir, level, entries, result);
                    if (matchingXml != null) {
                        pairedXmlFiles.add(matchingXml);
                    }
                } else if (FileNameUtils.isXml(fileName) && pairedXmlFiles.contains(file)) {
                    // Ya fue agregado junto al primer fragmento del PDF homonimo.
                    continue;
                } else {
                    entries.add(new FolderFileEntry(file, fileName, originalSize, false, false));
                    result.incrementNonPdfFiles();
                }
                result.incrementFilesProcessed();
            } catch (Exception e) {
                logger.error("Error procesando " + fileName, e);
                result.addError(new ProcessingError(fileName, "procesar", e.getMessage(),
                        e.getClass().getSimpleName()));
                try {
                    if (FileNameUtils.isPdf(fileName)) {
                        Path matchingXml = xmlByBaseName.get(xmlPairKey(file));
                        long xmlSize = matchingXml == null ? 0L : fileSystemService.sizeOf(matchingXml);
                        addPdfWithOptionalXml(file, fileName, matchingXml, xmlSize,
                                FileNameUtils.isPriorityFile(fileName), entries, result);
                    } else if (!FileNameUtils.isXml(fileName) || !pairedXmlFiles.contains(file)) {
                        entries.add(new FolderFileEntry(file, fileName, originalSize, false, false));
                    }
                } catch (Exception ignored) {
                    // El error ya quedo registrado.
                }
            }
        }

        if (entries.isEmpty()) {
            return List.of();
        }

        // outputParent aqui es un valor provisional: partition() lo usa solo para
        // construir un targetPath temporal, que se reemplaza en materializeFolder()
        // una vez que se conoce el contenedor de proveedor definitivo.
        return partitionService.partition(logicalName, leafTempDir, entries);
    }

    /** Crea la carpeta final dentro de outputParent y copia/mueve sus archivos. */
    private void materializeFolder(FolderInfo folder, Path outputParent, ProcessingResult result) throws IOException {
        Path finalTargetPath = outputParent.resolve(folder.getName());
        folder.rename(folder.getName(), finalTargetPath);

        fileSystemService.createDirectories(finalTargetPath);
        result.incrementFoldersCreated();
        for (FolderFileEntry entry : folder.getFiles()) {
            Path target = finalTargetPath.resolve(entry.outputFileName());
            if (entry.temporary()) {
                fileSystemService.moveToFinal(entry.sourcePath(), target);
            } else {
                fileSystemService.copyExact(entry.sourcePath(), target);
            }
            result.addFinalSize(entry.sizeBytes());
        }
    }

    private void handlePdf(Path file, String fileName, Path matchingXml, long xmlSize,
                            Path leafTempDir, CompressionLevel level,
                            List<FolderFileEntry> entries, ProcessingResult result) throws IOException {
        boolean priority = FileNameUtils.isPriorityFile(fileName);
        Path compressed;
        try {
            compressed = compressionService.compress(file, leafTempDir, level);
        } catch (IOException e) {
            logger.warn("No se pudo comprimir " + fileName + ", se usara el original: " + e.getMessage());
            addPdfWithOptionalXml(file, fileName, matchingXml, xmlSize, priority, entries, result);
            result.incrementPdfsProcessed();
            return;
        }

        List<PdfInfo> pieces = pdfSplitService.splitIfNeeded(compressed, fileName, priority, leafTempDir, xmlSize);
        if (pieces.size() > 1) {
            result.incrementPdfsSplit();
            fileSystemService.deleteQuietly(compressed);
        }

        String groupId = matchingXml == null ? null : java.util.UUID.randomUUID().toString();
        for (int i = 0; i < pieces.size(); i++) {
            PdfInfo piece = pieces.get(i);
            boolean firstPiece = i == 0;
            String outputName = piece.getCompressedPath().equals(compressed) ? fileName : piece.getOriginalFileName();
            entries.add(new FolderFileEntry(piece.getCompressedPath(), outputName, piece.getSizeBytes(),
                    true, piece.isPriority(), firstPiece ? groupId : null));

            if (firstPiece && matchingXml != null) {
                entries.add(new FolderFileEntry(matchingXml, matchingXml.getFileName().toString(), xmlSize, false, false, groupId));
                result.incrementNonPdfFiles();
                result.incrementFilesProcessed();
            }
        }
        result.incrementPdfsProcessed();
    }

    private void addPdfWithOptionalXml(Path pdf, String fileName, Path matchingXml, long xmlSize,
                                       boolean priority, List<FolderFileEntry> entries,
                                       ProcessingResult result) {
        String groupId = matchingXml == null ? null : java.util.UUID.randomUUID().toString();
        entries.add(new FolderFileEntry(pdf, fileName, safeSize(pdf), false, priority, groupId));
        if (matchingXml != null) {
            entries.add(new FolderFileEntry(matchingXml, matchingXml.getFileName().toString(), xmlSize, false, false, groupId));
            result.incrementNonPdfFiles();
        }
    }

    private long safeSize(Path file) {
        try {
            return fileSystemService.sizeOf(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String xmlPairKey(Path file) {
        return file.getParent().toAbsolutePath().normalize() + "|"
                + FileNameUtils.stripExtension(file.getFileName().toString()).toLowerCase(java.util.Locale.ROOT);
    }

    private List<Path> listSubdirectories(Path parent) throws IOException {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (var stream = Files.list(parent)) {
            List<Path> dirs = stream.filter(Files::isDirectory).collect(Collectors.toList());
            // Orden natural (Poliza 2 antes que Poliza 10): el sistema de archivos
            // no garantiza ningun orden, y el agrupamiento por proveedor
            // (PartitionService.groupFoldersIntoContainers) preserva el orden que
            // reciba, asi que aqui es donde se define el orden "logico" esperado.
            dirs.sort((a, b) -> FileNameUtils.naturalCompare(
                    a.getFileName().toString(), b.getFileName().toString()));
            return dirs;
        }
    }

    private List<Path> listRegularFilesDirect(Path parent) throws IOException {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (var stream = Files.list(parent)) {
            return stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }
}
