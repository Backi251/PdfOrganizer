package com.example.pdforganizer.domain;

/**
 * Niveles de compresion de imagenes dentro de los PDF.
 * "jpegQuality" (0.0 - 1.0) se usa al re-comprimir imagenes JPEG,
 * y "scaleFactor" reduce la resolucion antes de comprimir para ahorrar
 * mas espacio en el nivel ALTA.
 */
public enum CompressionLevel {

    BAJA(0.85f, 1.0f),
    MEDIA(0.60f, 0.85f),
    ALTA(0.35f, 0.65f);

    private final float jpegQuality;
    private final float scaleFactor;

    CompressionLevel(float jpegQuality, float scaleFactor) {
        this.jpegQuality = jpegQuality;
        this.scaleFactor = scaleFactor;
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }
}
