package com.example.pdforganizer.infrastructure;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Encapsula TODAS las operaciones de sistema de archivos.
 *
 * Es la unica clase que debe tocar disco directamente para copiar/crear/leer,
 * de modo que las garantias de seguridad (nunca escribir sobre el origen)
 * se verifican en un solo lugar.
 */
public final class FileSystemService {

    /** Recorre recursivamente un directorio y devuelve todos los archivos regulares. */
    public List<Path> listFilesRecursively(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(result::add);
        }
        return result;
    }

    public long sizeOf(Path file) throws IOException {
        return Files.size(file);
    }

    public void createDirectories(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    /**
     * Copia un archivo NO PDF (xml u otros) tal cual, sin ninguna
     * transformacion, preservando nombre y contenido byte a byte.
     */
    public void copyExact(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** Mueve un archivo temporal a su ubicacion final (usado tras compresion/particion). */
    public void moveToFinal(Path tempFile, Path finalTarget) throws IOException {
        Files.createDirectories(finalTarget.getParent());
        Files.move(tempFile, finalTarget, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Limpieza de temporales: un fallo aqui no debe interrumpir el flujo principal.
        }
    }

    /** Limpia recursivamente un directorio temporal completo. */
    public void deleteRecursivelyQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // Best-effort: los temporales residuales no comprometen el original ni la salida.
        }
    }

    /**
     * Valida que el destino no sea el mismo directorio que el origen, ni un
     * subdirectorio del origen (lo cual causaria recursion infinita o
     * sobrescritura de la carpeta madre original).
     */
    public boolean isDestinationSafe(Path source, Path destinationParent) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedDest = destinationParent.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedDest)) {
            return false;
        }
        return !normalizedDest.startsWith(normalizedSource);
    }

    public BasicFileAttributes readAttributes(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class);
    }
}
