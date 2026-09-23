package com.example.pdforganizer.infrastructure;

import com.example.pdforganizer.util.FileNameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide un PDF por paginas completas hasta que cada fragmento respete
 * el limite de tamano, sin asumir un numero fijo de paginas por fragmento
 * (las paginas pueden pesar muy distinto entre si, sobre todo si tienen
 * imagenes de tamanos dispares).
 *
 * ALGORITMO ("expandir y retroceder"):
 * Para cada fragmento, empezamos con 1 sola pagina (nunca se puede tener
 * menos, ya que no esta permitido perder paginas) y vamos anadiendo paginas
 * de una en una, escribiendo un archivo temporal real y midiendo su tamano
 * en disco tras cada adicion. En cuanto anadir una pagina mas rompe el
 * limite, nos quedamos con la ultima version que si cabia. Si incluso una
 * sola pagina supera el limite, se conserva esa pagina sola (no se puede
 * dividir por debajo de una pagina) y se continua con el resto del documento.
 *
 * Esto garantiza: nunca se pierden paginas, se respeta el orden original,
 * y el limite de tamano se respeta siempre que sea tecnicamente posible.
 */
public final class PdfBoxSplitter {

    /**
     * @return lista de rutas de los fragmentos generados, en orden, dentro de outputDir.
     */
    public List<Path> splitByPageLimit(Path sourcePdf, Path outputDir, String originalFileName, long maxSizeBytes)
            throws IOException {

        Files.createDirectories(outputDir);
        List<Path> fragments = new ArrayList<>();

        try (PDDocument source = PDDocument.load(sourcePdf.toFile())) {
            int totalPages = source.getNumberOfPages();
            int currentPage = 0;
            int fragmentIndex = 1;

            while (currentPage < totalPages) {
                int pagesInFragment = 1;
                Path currentBest = writeFragment(source, currentPage, pagesInFragment, outputDir);

                if (Files.size(currentBest) <= maxSizeBytes) {
                    // Intentar anadir mas paginas mientras siga cabiendo.
                    while (currentPage + pagesInFragment < totalPages) {
                        int candidateCount = pagesInFragment + 1;
                        Path candidate = writeFragment(source, currentPage, candidateCount, outputDir);
                        if (Files.size(candidate) <= maxSizeBytes) {
                            Files.deleteIfExists(currentBest);
                            currentBest = candidate;
                            pagesInFragment = candidateCount;
                        } else {
                            Files.deleteIfExists(candidate);
                            break;
                        }
                    }
                }
                // Si ni siquiera 1 pagina cabe en el limite, currentBest se conserva
                // igualmente (prioridad: nunca perder paginas por encima del limite de tamano).

                Path finalName = outputDir.resolve(FileNameUtils.fragmentName(originalFileName, fragmentIndex));
                Files.move(currentBest, finalName, StandardCopyOption.REPLACE_EXISTING);
                fragments.add(finalName);

                currentPage += pagesInFragment;
                fragmentIndex++;
            }
        }
        return fragments;
    }

    /** Escribe un archivo temporal con las paginas [startPage, startPage+count) del documento fuente. */
    private Path writeFragment(PDDocument source, int startPage, int count, Path outputDir) throws IOException {
        Path temp = Files.createTempFile(outputDir, "fragment_", ".pdf.tmp");
        try (PDDocument fragmentDoc = new PDDocument()) {
            for (int i = startPage; i < startPage + count; i++) {
                fragmentDoc.importPage(source.getPage(i));
            }
            fragmentDoc.save(temp.toFile());
        }
        return temp;
    }
}
