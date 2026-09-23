package com.example.pdforganizer.infrastructure;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger simple basado en archivo, sin dependencias externas (evita
 * frameworks innecesarios en una app pensada para hardware antiguo).
 *
 * Crea un archivo por ejecucion en logsDirectory/AAAA-MM-DD_HH-mm-ss.log y
 * escribe de forma sincronizada porque puede ser invocado desde varios
 * workers.
 *
 * RESPALDO AUTOMATICO: si no se puede escribir en logsDirectory (caso
 * tipico: la aplicacion quedo instalada con jpackage en una carpeta
 * protegida como "C:\Program Files\...", donde un usuario normal no tiene
 * permiso de escritura), se usa automaticamente una carpeta dentro del
 * directorio temporal del sistema como respaldo. Un problema de logging
 * nunca debe impedir que la aplicacion funcione.
 */
public final class ApplicationLogger implements AutoCloseable {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final PrintWriter writer;
    private final Path logFile;

    public ApplicationLogger(Path preferredLogsDirectory) {
        Path resolvedDirectory = resolveWritableDirectory(preferredLogsDirectory);
        try {
            this.logFile = resolvedDirectory.resolve(LocalDateTime.now().format(FILE_STAMP) + ".log");
            this.writer = new PrintWriter(Files.newBufferedWriter(logFile), true);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el archivo de log", e);
        }
    }

    /**
     * Intenta usar la carpeta preferida; si no se puede crear o no admite
     * escritura (permisos insuficientes), recurre al directorio temporal
     * del sistema, que siempre es escribible por el usuario actual.
     */
    private static Path resolveWritableDirectory(Path preferredLogsDirectory) {
        try {
            Files.createDirectories(preferredLogsDirectory);
            if (Files.isWritable(preferredLogsDirectory)) {
                return preferredLogsDirectory;
            }
        } catch (IOException ignored) {
            // Se intenta el respaldo a continuacion.
        }

        Path fallback = Path.of(System.getProperty("java.io.tmpdir", "."), "PDFOrganizer", "logs");
        try {
            Files.createDirectories(fallback);
            return fallback;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo crear el archivo de log ni en \"" + preferredLogsDirectory
                            + "\" ni en el directorio temporal del sistema", e);
        }
    }

    public synchronized void info(String message) {
        writer.println(timestamp() + " [INFO] " + message);
    }

    public synchronized void warn(String message) {
        writer.println(timestamp() + " [WARN] " + message);
    }

    public synchronized void error(String message, Throwable t) {
        writer.println(timestamp() + " [ERROR] " + message
                + (t != null ? " -> " + t.getClass().getSimpleName() + ": " + t.getMessage() : ""));
    }

    private String timestamp() {
        return LocalDateTime.now().format(LINE_STAMP);
    }

    public Path getLogFile() {
        return logFile;
    }

    @Override
    public void close() {
        writer.flush();
        writer.close();
    }
}
