package com.example.pdforganizer.config;

import com.example.pdforganizer.domain.CompressionLevel;

/**
 * Configuracion centralizada de la aplicacion.
 *
 * Todos los valores "magicos" del negocio (limite de carpeta, palabras
 * prioritarias, numero de workers, etc.) viven aqui para poder ajustarlos
 * sin tocar la logica de PartitionService, CompressionService, etc.
 *
 * La instancia es mutable a proposito: la pantalla de ajustes (SettingsPanel)
 * modifica una instancia compartida antes de lanzar el procesamiento.
 */
public final class AppConfig {

    /**
     * Limite estricto por carpeta de salida.
     *
     * Se usa 3.95 MB (no 4 MB) como margen de seguridad: los sistemas de
     * destino del cliente rechazan carpetas de 4 MB exactos por overhead
     * de metadatos/compresion de zip, asi que dejamos ~50 KB de colchon.
     *
     * Definido explicitamente en bytes para evitar errores de redondeo:
     * 3.95 * 1024 * 1024 = 4,142,182.4 -> se trunca a long con (long) cast,
     * lo cual es seguro porque siempre queremos redondear HACIA ABAJO
     * (un limite mas estricto, nunca mas permisivo).
     */
    public static final long MAX_FOLDER_SIZE_BYTES = (long) (3.95 * 1024 * 1024);

    /** Prefijo que antecede al nombre de cada proveedor en la salida. */
    public static final String OUTPUT_FOLDER_PREFIX = "Punto 4-Documental ";

    /** Prefijo de la carpeta madre de salida. */
    public static final String OUTPUT_ROOT_PREFIX = "comprimido ";

    /**
     * Palabras clave (case-insensitive) que marcan un PDF como prioritario
     * para la carpeta numero 1 de cada division.
     */
    public static final String[] PRIORITY_KEYWORDS = { "transferencia", "cheque", "poliza", "póliza" };

    private CompressionLevel compressionLevel = CompressionLevel.MEDIA;
    private int workerThreads = 1; // secuencial por defecto (ver seccion 18 del spec)
    private boolean showPreview = true;

    public CompressionLevel getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(CompressionLevel compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = Math.max(1, workerThreads);
    }

    public boolean isShowPreview() {
        return showPreview;
    }

    public void setShowPreview(boolean showPreview) {
        this.showPreview = showPreview;
    }
}
