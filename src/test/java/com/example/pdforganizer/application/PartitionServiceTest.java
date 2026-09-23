package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.FolderFileEntry;
import com.example.pdforganizer.domain.FolderInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartitionServiceTest {

    private static final long MAX = (long) (3.95 * 1024 * 1024); // debe coincidir con AppConfig
    private final PartitionService service = new PartitionService();
    private final Path outputParent = Path.of("/tmp/salida");

    private FolderFileEntry entry(String name, long size, boolean priority) {
        return new FolderFileEntry(Path.of("/tmp/origen/" + name), name, size, false, priority);
    }

    @Test
    void folderUnderLimit_isNotSplit() {
        // Caso 1: carpeta de 3.94 MB -> cabe en una sola carpeta, sin numerar.
        long size = (long) (3.94 * 1024 * 1024);
        List<FolderFileEntry> entries = List.of(entry("a.pdf", size, false));

        List<FolderInfo> folders = service.partition("Poliza 001", outputParent, entries);

        assertEquals(1, folders.size());
        assertEquals("Poliza 001", folders.get(0).getName());
    }

    @Test
    void folderExactlyNearLimit_isNotSplit() {
        // Caso 2: carpeta cercana pero por debajo del limite de 3.95 MB.
        long size = MAX - 1024; // 1 KB por debajo del limite
        List<FolderFileEntry> entries = List.of(entry("a.pdf", size, false));

        List<FolderInfo> folders = service.partition("Poliza 002", outputParent, entries);

        assertEquals(1, folders.size());
        assertTrue(folders.get(0).getCurrentSizeBytes() <= MAX);
    }

    @Test
    void folderOverLimit_isSplitIntoNumberedFolders() {
        // Caso 3: carpeta que supera 3.95 MB -> debe dividirse en varias numeradas.
        long half = MAX; // dos archivos de tamano MAX cada uno fuerzan al menos 2 carpetas
        List<FolderFileEntry> entries = List.of(
                entry("a.pdf", half, false),
                entry("b.pdf", half, false));

        List<FolderInfo> folders = service.partition("Poliza 003", outputParent, entries);

        assertTrue(folders.size() >= 2);
        assertEquals("Poliza 003 1", folders.get(0).getName());
        assertEquals("Poliza 003 2", folders.get(1).getName());
        for (FolderInfo f : folders) {
            assertTrue(f.getCurrentSizeBytes() <= MAX, "Ninguna carpeta debe superar el limite");
        }
    }

    @Test
    void noFolderEverExceedsTheLimit_evenWithManyFiles() {
        List<FolderFileEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(entry("file" + i + ".pdf", 500_000L, false)); // ~500 KB cada uno
        }
        List<FolderInfo> folders = service.partition("Proveedor X", outputParent, entries);

        long totalFilesDistributed = folders.stream().mapToLong(f -> f.getFiles().size()).sum();
        assertEquals(50, totalFilesDistributed, "No se debe perder ningun archivo");
        for (FolderInfo f : folders) {
            assertTrue(f.getCurrentSizeBytes() <= MAX);
        }
    }

    @Test
    void priorityFilesGoToFolderNumberOne_whenTheyFit() {
        // Archivos prioritarios pequenos + varios no prioritarios grandes que fuerzan division.
        List<FolderFileEntry> entries = List.of(
                entry("Transferencia.pdf", 100_000L, true),
                entry("Cheque.pdf", 100_000L, true),
                entry("contrato1.pdf", MAX, false),
                entry("contrato2.pdf", MAX, false));

        List<FolderInfo> folders = service.partition("Poliza 004", outputParent, entries);

        FolderInfo folder1 = folders.get(0);
        List<String> namesInFolder1 = folder1.getFiles().stream()
                .map(FolderFileEntry::outputFileName)
                .toList();
        assertTrue(namesInFolder1.contains("Transferencia.pdf"));
        assertTrue(namesInFolder1.contains("Cheque.pdf"));
    }

    @Test
    void priorityFilesLargerThanLimit_stillRespectTheLimit() {
        // Si los prioritarios juntos no caben, se debe respetar el limite de 3.95MB
        // por encima de la regla de prioridad (nunca se viola el limite de tamano).
        List<FolderFileEntry> entries = List.of(
                entry("Transferencia1.pdf", MAX, true),
                entry("Transferencia2.pdf", MAX, true),
                entry("Cheque1.pdf", MAX, true));

        List<FolderInfo> folders = service.partition("Poliza 005", outputParent, entries);

        for (FolderInfo f : folders) {
            assertTrue(f.getCurrentSizeBytes() <= MAX, "El limite nunca debe violarse, ni por prioridad");
        }
        long totalFiles = folders.stream().mapToLong(f -> f.getFiles().size()).sum();
        assertEquals(3, totalFiles);
    }

    @Test
    void xmlAndOtherFiles_areIncludedButNeverPrioritized() {
        List<FolderFileEntry> entries = List.of(
                entry("archivo.xml", 200_000L, false),
                entry("Poliza.pdf", 200_000L, true));

        List<FolderInfo> folders = service.partition("Poliza 006", outputParent, entries);

        assertEquals(1, folders.size());
        assertEquals(2, folders.get(0).getFiles().size());
    }

    @Test
    void folderWithoutPdfs_isHandledNormally() {
        List<FolderFileEntry> entries = List.of(
                entry("a.xml", 50_000L, false),
                entry("b.xml", 50_000L, false));

        List<FolderInfo> folders = service.partition("Poliza 007", outputParent, entries);

        assertEquals(1, folders.size());
        assertEquals(2, folders.get(0).getFiles().size());
    }

    @Test
    void multipleDivisions_preserveAllFilesAcrossManyFolders() {
        List<FolderFileEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(entry("doc" + i + ".pdf", (long) (MAX * 0.6), false));
        }
        List<FolderInfo> folders = service.partition("Proveedor Y", outputParent, entries);

        assertTrue(folders.size() >= 6, "10 archivos de 60% del limite requieren varias carpetas");
        long totalFiles = folders.stream().mapToLong(f -> f.getFiles().size()).sum();
        assertEquals(10, totalFiles);
    }
}
