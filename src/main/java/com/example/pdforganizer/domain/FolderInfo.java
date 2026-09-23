package com.example.pdforganizer.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Carpeta de salida numerada (ej. "Poliza 001 2") con la lista de archivos
 * que contendra y el tamano acumulado. Usada por PartitionService mientras
 * distribuye archivos, y luego por OrganizationService para materializar
 * la copia fisica en disco.
 */
public final class FolderInfo {

    private String name;
    private Path targetPath;
    private final List<FolderFileEntry> files = new ArrayList<>();
    private long currentSizeBytes = 0L;

    public FolderInfo(String name, Path targetPath) {
        this.name = name;
        this.targetPath = targetPath;
    }

    /** Renombra la carpeta una vez que se conoce el numero final de divisiones necesarias. */
    public void rename(String newName, Path newTargetPath) {
        this.name = newName;
        this.targetPath = newTargetPath;
    }

    public void addFile(FolderFileEntry entry) {
        files.add(entry);
        currentSizeBytes += entry.sizeBytes();
    }

    public boolean fits(long sizeBytes, long maxFolderSizeBytes) {
        return currentSizeBytes + sizeBytes <= maxFolderSizeBytes;
    }

    public String getName() {
        return name;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public List<FolderFileEntry> getFiles() {
        return files;
    }

    public long getCurrentSizeBytes() {
        return currentSizeBytes;
    }
}
