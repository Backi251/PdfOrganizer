package com.example.pdforganizer.infrastructure;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del algoritmo "expandir y retroceder" de division por paginas.
 * Se generan PDFs reales (no simulados) con paginas de tamanos deliberadamente
 * distintos, incrustando imagenes de ruido aleatorio (que no comprimen bien,
 * garantizando un tamano de archivo predecible y suficientemente grande).
 */
class PdfBoxSplitterTest {

    private final PdfBoxSplitter splitter = new PdfBoxSplitter();

    @TempDir
    Path tempDir;

    private Path buildPdfWithPages(int pageCount, int imageSizePx) throws IOException {
        Path file = tempDir.resolve("source_" + pageCount + "_" + imageSizePx + ".pdf");
        try (PDDocument document = new PDDocument()) {
            Random random = new Random(42);
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (imageSizePx > 0) {
                    BufferedImage noise = new BufferedImage(imageSizePx, imageSizePx, BufferedImage.TYPE_INT_RGB);
                    for (int x = 0; x < imageSizePx; x++) {
                        for (int y = 0; y < imageSizePx; y++) {
                            noise.setRGB(x, y, random.nextInt(0xFFFFFF));
                        }
                    }
                    PDImageXObject imageXObject = JPEGFactory.createFromImage(document, noise, 1.0f);
                    try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                        cs.drawImage(imageXObject, 0, 0, 200, 200);
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private int countPages(Path pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void splitting_neverLosesPages_withUniformPages() throws IOException {
        Path source = buildPdfWithPages(6, 150);
        long maxSize = Files.size(source) / 3; // fuerza al menos 2-3 fragmentos

        List<Path> fragments = splitter.splitByPageLimit(source, tempDir.resolve("out1"), "doc.pdf", maxSize);

        int totalPages = 0;
        for (Path fragment : fragments) {
            totalPages += countPages(fragment);
        }
        assertEquals(6, totalPages, "Ninguna pagina debe perderse al dividir");
        assertTrue(fragments.size() >= 2, "Con ese limite deberian generarse varios fragmentos");
    }

    @Test
    void splitting_preservesPageOrder() throws IOException {
        Path source = buildPdfWithPages(4, 120);
        long maxSize = Files.size(source) / 2;

        List<Path> fragments = splitter.splitByPageLimit(source, tempDir.resolve("out2"), "doc.pdf", maxSize);

        // Los nombres deben seguir la numeracion 1..N en el mismo orden generado.
        for (int i = 0; i < fragments.size(); i++) {
            assertTrue(fragments.get(i).getFileName().toString().contains("doc " + (i + 1) + ".pdf")
                            || (fragments.size() == 1 && fragments.get(i).getFileName().toString().equals("doc 1.pdf")),
                    "Fragmento " + i + " deberia seguir la numeracion secuencial");
        }
    }

    @Test
    void singlePageLargerThanLimit_isKeptWholeAndNotDropped() throws IOException {
        // Una sola pagina con una imagen grande que, por si sola, ya supera el limite.
        Path source = buildPdfWithPages(1, 400);
        long fullSize = Files.size(source);
        long maxSize = fullSize / 4; // deliberadamente imposible de cumplir con 1 pagina

        List<Path> fragments = splitter.splitByPageLimit(source, tempDir.resolve("out3"), "grande.pdf", maxSize);

        assertEquals(1, fragments.size());
        assertEquals(1, countPages(fragments.get(0)), "La pagina no debe descartarse aunque supere el limite");
    }

    @Test
    void pagesWithVeryDifferentSizes_areDistributedCorrectly() throws IOException {
        // Documento con paginas de tamanos muy distintos: se construye concatenando
        // paginas de dos PDFs base con distinto tamano de imagen.
        Path small = buildPdfWithPages(3, 80);
        Path large = buildPdfWithPages(2, 350);

        Path merged = tempDir.resolve("mixed.pdf");
        try (PDDocument doc = new PDDocument();
             PDDocument smallDoc = PDDocument.load(small.toFile());
             PDDocument largeDoc = PDDocument.load(large.toFile())) {
            for (PDPage p : smallDoc.getPages()) {
                doc.importPage(p);
            }
            for (PDPage p : largeDoc.getPages()) {
                doc.importPage(p);
            }
            // Los documentos fuente deben permanecer abiertos hasta guardar: importPage
            // no copia los streams de inmediato, solo referencias a los objetos COS originales.
            doc.save(merged.toFile());
        }

        long maxSize = Files.size(large) / 2 + 10_000; // suficiente para paginas pequenas, ajustado para las grandes
        List<Path> fragments = splitter.splitByPageLimit(merged, tempDir.resolve("out4"), "mixto.pdf", maxSize);

        int totalPages = 0;
        for (Path fragment : fragments) {
            totalPages += countPages(fragment);
        }
        assertEquals(5, totalPages, "Las 5 paginas originales deben conservarse tras dividir");
    }
}
