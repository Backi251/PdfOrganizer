package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.FileInfo;

import java.util.List;

/** Resultado del analisis previo (fase de "vista previa" antes de procesar). */
public final class AnalysisResult {

    private final List<FileInfo> allFiles;
    private final int pdfCount;
    private final int otherCount;
    private final long totalSizeBytes;
    private final int estimatedFoldersAtLeast;

    public AnalysisResult(List<FileInfo> allFiles, int pdfCount, int otherCount,
                           long totalSizeBytes, int estimatedFoldersAtLeast) {
        this.allFiles = allFiles;
        this.pdfCount = pdfCount;
        this.otherCount = otherCount;
        this.totalSizeBytes = totalSizeBytes;
        this.estimatedFoldersAtLeast = estimatedFoldersAtLeast;
    }

    public List<FileInfo> getAllFiles() { return allFiles; }
    public int getPdfCount() { return pdfCount; }
    public int getOtherCount() { return otherCount; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public int getEstimatedFoldersAtLeast() { return estimatedFoldersAtLeast; }
    public int getTotalFileCount() { return allFiles.size(); }
}
