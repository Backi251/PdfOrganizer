package com.example.pdforganizer.domain;

import java.util.ArrayList;
import java.util.List;

/** Resumen final de una ejecucion completa, mostrado en el panel de resultados. */
public final class ProcessingResult {

    private int filesProcessed = 0;
    private int pdfsProcessed = 0;
    private int nonPdfFiles = 0;
    private int pdfsSplit = 0;
    private int foldersCreated = 0;
    private long originalSizeBytes = 0L;
    private long finalSizeBytes = 0L;
    private final List<ProcessingError> errors = new ArrayList<>();

    public void incrementFilesProcessed() { filesProcessed++; }
    public void incrementPdfsProcessed() { pdfsProcessed++; }
    public void incrementNonPdfFiles() { nonPdfFiles++; }
    public void incrementPdfsSplit() { pdfsSplit++; }
    public void incrementFoldersCreated() { foldersCreated++; }
    public void addOriginalSize(long bytes) { originalSizeBytes += bytes; }
    public void addFinalSize(long bytes) { finalSizeBytes += bytes; }
    public void addError(ProcessingError error) { errors.add(error); }

    public int getFilesProcessed() { return filesProcessed; }
    public int getPdfsProcessed() { return pdfsProcessed; }
    public int getNonPdfFiles() { return nonPdfFiles; }
    public int getPdfsSplit() { return pdfsSplit; }
    public int getFoldersCreated() { return foldersCreated; }
    public long getOriginalSizeBytes() { return originalSizeBytes; }
    public long getFinalSizeBytes() { return finalSizeBytes; }
    public List<ProcessingError> getErrors() { return errors; }

    public long getSpaceSavedBytes() {
        return Math.max(0L, originalSizeBytes - finalSizeBytes);
    }
}
