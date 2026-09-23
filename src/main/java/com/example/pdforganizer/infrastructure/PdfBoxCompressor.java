package com.example.pdforganizer.infrastructure;

import com.example.pdforganizer.domain.CompressionLevel;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Comprime un PDF recomprimiendo sus imagenes rasterizadas.
 *
 * Decision de diseno: PDFBox no ofrece un metodo "compress()" generico, asi
 * que la estrategia es recorrer cada pagina, localizar los XObject de tipo
 * imagen dentro de sus recursos, decodificarlos a BufferedImage, volver a
 * codificarlos como JPEG con la calidad/escala del nivel elegido, y
 * reemplazar la entrada en el diccionario de recursos por el nuevo
 * PDImageXObject. El resto del PDF (texto, vectores, estructura) no se
 * toca, por lo que el documento sigue siendo valido y conserva todas
 * las paginas.
 *
 * Se procesa un archivo a la vez y se cierra el documento en cuanto se
 * guarda, para no retener PDFs abiertos en memoria mas de lo necesario
 * (requisito de bajo consumo de RAM en hardware antiguo).
 */
public final class PdfBoxCompressor {

    /**
     * Comprime el PDF de entrada y escribe el resultado en outputPath.
     * Si una imagen individual no puede recomprimirse (formato no soportado,
     * imagen corrupta), se deja tal cual y se continua con las demas: un
     * problema puntual de una imagen no debe invalidar todo el documento.
     */
    public void compress(Path input, Path outputPath, CompressionLevel level) throws IOException {
        try (PDDocument document = PDDocument.load(input.toFile())) {
            for (PDPage page : document.getPages()) {
                compressImagesInResources(document, page.getResources(), level);
            }
            document.save(outputPath.toFile());
        }
    }

    private void compressImagesInResources(PDDocument document, PDResources resources, CompressionLevel level)
            throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject imageXObject) {
                try {
                    BufferedImage original = imageXObject.getImage();
                    byte[] jpegBytes = ImageCompressor.compressToJpeg(
                            original, level.getJpegQuality(), level.getScaleFactor());
                    PDImageXObject recompressed = JPEGFactory.createFromByteArray(document, jpegBytes);
                    resources.put(name, recompressed);
                } catch (IOException | RuntimeException e) {
                    // Se conserva la imagen original si no puede recomprimirse;
                    // el documento debe seguir siendo valido pase lo que pase.
                }
            }
            // Los XObject de tipo Form (PDFormXObject) pueden contener imagenes
            // anidadas en sus propios recursos; no se recorren por simplicidad,
            // ya que en la practica la inmensa mayoria del peso esta en imagenes
            // directas de pagina.
        }
    }
}
