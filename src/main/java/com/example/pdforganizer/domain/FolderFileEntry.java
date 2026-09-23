package com.example.pdforganizer.domain;

import java.nio.file.Path;

/**
 * Un archivo listo para ser asignado a una carpeta de salida.
 *
 * groupId permite representar una unidad de distribucion compuesta. Para un
 * PDF que tiene un XML homonimo, el primer fragmento del PDF y su XML usan
 * el mismo groupId, por lo que PartitionService nunca los separa. Los demas
 * fragmentos no pertenecen al grupo y por tanto pueden distribuirse en
 * carpetas posteriores sin volver a copiar el XML.
 */
public record FolderFileEntry(Path sourcePath, String outputFileName, long sizeBytes,
                               boolean temporary, boolean priority, String groupId) {

    public FolderFileEntry(Path sourcePath, String outputFileName, long sizeBytes,
                           boolean temporary, boolean priority) {
        this(sourcePath, outputFileName, sizeBytes, temporary, priority, null);
    }

    public boolean isGrouped() {
        return groupId != null && !groupId.isBlank();
    }
}
