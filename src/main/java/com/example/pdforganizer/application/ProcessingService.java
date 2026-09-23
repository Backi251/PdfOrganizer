package com.example.pdforganizer.application;

import com.example.pdforganizer.config.AppConfig;
import com.example.pdforganizer.domain.*;
import com.example.pdforganizer.infrastructure.ApplicationLogger;
import com.example.pdforganizer.infrastructure.FileSystemService;
import com.example.pdforganizer.util.FileNameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Punto de entrada de la logica de negocio: coordina analisis y
 * procesamiento completo, y es la unica clase que la UI necesita invocar.
 *
 * Flujo (seccion 11 del requerimiento):
 * ORIGINAL -> ANALISIS -> COPIA DE ESTRUCTURA -> TEMPORAL -> COMPRESION
 * -> VALIDACION -> DISTRIBUCION -> SALIDA FINAL
 *
 * La "copia de estructura" y "distribucion" ocurren dentro de
 * OrganizationService; ProcessingService se ocupa de la validacion previa
 * de seguridad, el ciclo de vida de los directorios temporales y el logging
 * global de la ejecucion.
 */
public final class ProcessingService {

    private final FileSystemService fileSystemService = new FileSystemService();

    public void validatePaths(Path source, Path destinationParent) {
        if (source == null || destinationParent == null) {
            throw new IllegalArgumentException("Debe seleccionar carpeta de origen y carpeta de destino.");
        }
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("La carpeta de origen no existe o no es un directorio.");
        }
        if (!fileSystemService.isDestinationSafe(source, destinationParent)) {
            throw new IllegalArgumentException(
                    "La carpeta de destino no puede ser igual ni estar dentro de la carpeta de origen.");
        }
    }

    /** Analiza la carpeta origen sin modificar nada; usado para la vista previa. */
    public AnalysisResult analyze(Path source) throws IOException {
        List<Path> allFiles = fileSystemService.listFilesRecursively(source);
        List<FileInfo> infos = new ArrayList<>();
        int pdfCount = 0;
        int otherCount = 0;
        long totalSize = 0L;
        int estimatedFoldersAtLeast = 0;

        for (Path path : allFiles) {
            long size = fileSystemService.sizeOf(path);
            String name = path.getFileName().toString();
            boolean pdf = FileNameUtils.isPdf(name);
            boolean priority = FileNameUtils.isPriorityFile(name);
            infos.add(new FileInfo(path, size, pdf, priority));
            totalSize += size;
            if (pdf) {
                pdfCount++;
            } else {
                otherCount++;
            }
        }
        // Estimacion conservadora: al menos 1 carpeta por cada MAX_FOLDER_SIZE de contenido.
        if (totalSize > 0) {
            estimatedFoldersAtLeast = (int) Math.ceil(totalSize / (double) AppConfig.MAX_FOLDER_SIZE_BYTES);
        }
        return new AnalysisResult(infos, pdfCount, otherCount, totalSize, Math.max(1, estimatedFoldersAtLeast));
    }

    /**
     * Ejecuta el procesamiento completo. Debe invocarse fuera del Event
     * Dispatch Thread (ver ui.ProgressPanel / MainWindow, que usan
     * SwingWorker para esto).
     */
    public ProcessingResult process(Path source, Path destinationParent, AppConfig config,
                                     ProgressListener progress, ApplicationLogger logger) throws IOException {

        validatePaths(source, destinationParent);

        String rootOutputName = FileNameUtils.rootOutputName(source.getFileName().toString());
        Path outputRoot = destinationParent.resolve(rootOutputName);
        Path tempRoot = Files.createTempDirectory("pdforganizer_tmp_");

        logger.info("Inicio de procesamiento");
        logger.info("Origen: " + source);
        logger.info("Destino: " + outputRoot);
        logger.info("Nivel de compresion: " + config.getCompressionLevel());

        ProcessingResult result = new ProcessingResult();
        progress.onStatusChanged(ProcessingStatus.ANALYZING);
        AnalysisResult analysis = analyze(source);
        int totalFiles = analysis.getTotalFileCount();

        try {
            progress.onStatusChanged(ProcessingStatus.COMPRESSING);
            FileSystemService fs = fileSystemService;
            OrganizationService organizationService = new OrganizationService(
                    fs,
                    new CompressionService(),
                    new PdfSplitService(),
                    new PartitionService(),
                    logger);

            organizationService.organize(source, outputRoot, tempRoot,
                    config.getCompressionLevel(), result, progress, totalFiles);

            progress.onStatusChanged(ProcessingStatus.VALIDATING);
            for (ProcessingError error : result.getErrors()) {
                logger.warn("Error registrado: " + error);
                progress.onError(error);
            }

            progress.onStatusChanged(ProcessingStatus.FINISHED);
            logger.info("Procesamiento finalizado. Archivos: " + result.getFilesProcessed()
                    + ", PDFs: " + result.getPdfsProcessed()
                    + ", divididos: " + result.getPdfsSplit()
                    + ", carpetas: " + result.getFoldersCreated()
                    + ", errores: " + result.getErrors().size());
            progress.onFinished();
            return result;

        } finally {
            fileSystemService.deleteRecursivelyQuietly(tempRoot);
        }
    }
}
