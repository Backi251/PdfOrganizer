package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.CompressionLevel;
import com.example.pdforganizer.infrastructure.PdfBoxCompressor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Servicio de aplicacion que envuelve PdfBoxCompressor: decide donde
 * escribir el temporal y expone una API simple al resto del pipeline.
 */
public final class CompressionService {

    private final PdfBoxCompressor compressor = new PdfBoxCompressor();

    /**
     * Comprime un PDF hacia un archivo temporal dentro de tempDir.
     * El llamador es responsable de mover o eliminar el temporal despues.
     */
    public Path compress(Path sourcePdf, Path tempDir, CompressionLevel level) throws IOException {
        Files.createDirectories(tempDir);
        Path tempOutput = Files.createTempFile(tempDir, "compressed_", ".pdf");
        compressor.compress(sourcePdf, tempOutput, level);
        return tempOutput;
    }
}
