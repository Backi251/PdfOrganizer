package com.example.pdforganizer.util;

/** Utilidades de calculo y formateo de tamanos de archivo. */
public final class FileSizeUtils {

    private FileSizeUtils() {
    }

    /** Convierte bytes a una cadena legible (KB, MB, GB) con 2 decimales. */
    public static String humanReadable(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    /** true si sumar addedBytes a currentBytes seguiria dentro del limite. */
    public static boolean fitsWithin(long currentBytes, long addedBytes, long limitBytes) {
        return currentBytes + addedBytes <= limitBytes;
    }
}
