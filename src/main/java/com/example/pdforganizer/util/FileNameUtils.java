package com.example.pdforganizer.util;

import com.example.pdforganizer.config.AppConfig;

import java.util.Locale;

/** Utilidades de deteccion y generacion de nombres de archivo/carpeta. */
public final class FileNameUtils {

    private FileNameUtils() {
    }

    /**
     * Determina si un nombre de archivo corresponde a un PDF prioritario
     * (Transferencia / Cheque / Poliza), de forma case-insensitive y
     * tolerante a acentos (poliza / póliza).
     */
    public static boolean isPriorityFile(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        for (String keyword : AppConfig.PRIORITY_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isXml(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    public static boolean isPdf(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /** Quita la extension ".pdf" (o cualquier extension) de un nombre. */
    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }

    /** Genera el nombre de fragmento: "factura.pdf" -> "factura 2.pdf". */
    public static String fragmentName(String originalFileName, int index) {
        String base = stripExtension(originalFileName);
        String ext = getExtension(originalFileName);
        return base + " " + index + ext;
    }

    /** Genera el nombre de carpeta numerada: "Proveedor A" -> "Proveedor A 2". */
    public static String numberedFolderName(String baseName, int index, int totalParts) {
        if (totalParts <= 1) {
            return baseName;
        }
        return baseName + " " + index;
    }

    /** Nombre de carpeta de proveedor en la salida, con el prefijo del cliente. */
    public static String providerOutputName(String providerName) {
        return AppConfig.OUTPUT_FOLDER_PREFIX + providerName;
    }

    /** Nombre de la carpeta madre de salida. */
    public static String rootOutputName(String originalRootName) {
        return AppConfig.OUTPUT_ROOT_PREFIX + originalRootName;
    }

    /**
     * Comparador de "orden natural": compara cadenas tramo a tramo,
     * tratando las secuencias de digitos como numeros (no caracter a
     * caracter). Asi "Poliza 2" queda antes que "Poliza 10", en vez del
     * orden lexicografico normal (donde "10" precede a "2").
     *
     * Se usa para listar proveedores y polizas en el orden en que un
     * humano los esperaria, ya que el sistema de archivos no garantiza
     * ningun orden particular al listar un directorio.
     */
    public static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);

            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startI = i, startJ = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;

                String numA = a.substring(startI, i).replaceFirst("^0+(?=\\d)", "");
                String numB = b.substring(startJ, j).replaceFirst("^0+(?=\\d)", "");

                if (numA.length() != numB.length()) {
                    return Integer.compare(numA.length(), numB.length());
                }
                int cmp = numA.compareTo(numB);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
