package com.example.pdforganizer.domain;

import java.nio.file.Path;

/**
 * Representa un archivo detectado durante el analisis de la carpeta origen.
 * Es inmutable: el analisis nunca debe alterar el archivo original,
 * por lo que este objeto solo transporta metadatos.
 */
public final class FileInfo {

    private final Path sourcePath;
    private final String fileName;
    private final long sizeBytes;
    private final boolean pdf;
    private final boolean priority; // Transferencia / Cheque / Poliza

    public FileInfo(Path sourcePath, long sizeBytes, boolean pdf, boolean priority) {
        this.sourcePath = sourcePath;
        this.fileName = sourcePath.getFileName().toString();
        this.sizeBytes = sizeBytes;
        this.pdf = pdf;
        this.priority = priority;
    }

    public Path getSourcePath() { return sourcePath; }
    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
    public boolean isPdf() { return pdf; }
    public boolean isPriority() { return priority; }
}
