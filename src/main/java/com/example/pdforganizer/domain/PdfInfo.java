package com.example.pdforganizer.domain;

import java.nio.file.Path;

/**
 * Resultado de comprimir un PDF individual. Si tras comprimir sigue
 * superando el limite, PdfSplitService lo reemplaza por varios PdfInfo
 * (uno por fragmento). A partir de este punto, PartitionService solo
 * necesita conocer tamano y prioridad, no si es fragmento u original.
 */
public final class PdfInfo {

    private final Path compressedPath;
    private final long sizeBytes;
    private final boolean priority;
    private final String originalFileName;

    public PdfInfo(Path compressedPath, long sizeBytes, boolean priority, String originalFileName) {
        this.compressedPath = compressedPath;
        this.sizeBytes = sizeBytes;
        this.priority = priority;
        this.originalFileName = originalFileName;
    }

    public Path getCompressedPath() { return compressedPath; }
    public long getSizeBytes() { return sizeBytes; }
    public boolean isPriority() { return priority; }
    public String getOriginalFileName() { return originalFileName; }
}
