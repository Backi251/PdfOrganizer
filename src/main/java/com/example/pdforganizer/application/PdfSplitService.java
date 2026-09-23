package com.example.pdforganizer.application;

import com.example.pdforganizer.config.AppConfig;
import com.example.pdforganizer.domain.PdfInfo;
import com.example.pdforganizer.infrastructure.PdfBoxSplitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Si un PDF ya comprimido sigue superando el limite, lo divide por paginas
 * completas (nunca se recorta contenido). Cada fragmento resultante hereda
 * la marca de "prioritario" del archivo original, ya que el nombre base
 * (Transferencia/Cheque/Poliza) se conserva en cada fragmento.
 */
public final class PdfSplitService {

    private final PdfBoxSplitter splitter = new PdfBoxSplitter();

    public List<PdfInfo> splitIfNeeded(Path compressedPdf, String originalFileName, boolean priority,
                                        Path tempDir) throws IOException {
        return splitIfNeeded(compressedPdf, originalFileName, priority, tempDir, 0L);
    }

    /**
     * Si existe un XML homonimo, el primer fragmento debe reservar espacio para
     * ese XML. El XML no se divide ni se repite en los fragmentos posteriores.
     */
    public List<PdfInfo> splitIfNeeded(Path compressedPdf, String originalFileName, boolean priority,
                                        Path tempDir, long xmlSize) throws IOException {
        long size = Files.size(compressedPdf);
        long pdfLimit = AppConfig.MAX_FOLDER_SIZE_BYTES - Math.max(0L, xmlSize);

        // Si el XML por si solo excede el limite, no es posible cumplir la
        // restriccion de tamano sin dividir el XML; por requisito, no se divide.
        if (xmlSize > AppConfig.MAX_FOLDER_SIZE_BYTES) {
            pdfLimit = AppConfig.MAX_FOLDER_SIZE_BYTES;
        }
        List<PdfInfo> result = new ArrayList<>();

        if (size <= pdfLimit) {
            result.add(new PdfInfo(compressedPdf, size, priority, originalFileName));
            return result;
        }

        List<Path> fragments = splitter.splitByPageLimit(
                compressedPdf, tempDir, originalFileName, pdfLimit);

        for (Path fragment : fragments) {
            result.add(new PdfInfo(fragment, Files.size(fragment), priority, fragment.getFileName().toString()));
        }
        return result;
    }
}
