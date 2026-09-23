package com.example.pdforganizer.infrastructure;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * Recomprime imagenes rasterizadas a JPEG con calidad y escala configurables.
 *
 * Se convierte siempre a TYPE_INT_RGB antes de codificar: JPEG no admite
 * canal alpha, y algunas imagenes extraidas de PDFs vienen en espacios de
 * color (CMYK, escala de grises con alpha, indexadas) que el encoder JPEG
 * de ImageIO no acepta directamente. Aplanar sobre fondo blanco evita
 * excepciones "Unsupported image type" en documentos reales.
 */
public final class ImageCompressor {

    private ImageCompressor() {
    }

    public static byte[] compressToJpeg(BufferedImage source, float quality, float scaleFactor) {
        try {
            BufferedImage scaled = scaleFactor < 0.999f ? scale(source, scaleFactor) : source;
            BufferedImage rgb = toOpaqueRgb(scaled);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No hay un ImageWriter JPEG disponible en esta JVM");
            }
            ImageWriter writer = writers.next();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(clamp(quality));
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Fallo al recomprimir imagen a JPEG", e);
        }
    }

    private static float clamp(float quality) {
        return Math.max(0.01f, Math.min(1.0f, quality));
    }

    private static BufferedImage scale(BufferedImage source, float factor) {
        int newWidth = Math.max(1, Math.round(source.getWidth() * factor));
        int newHeight = Math.max(1, Math.round(source.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static BufferedImage toOpaqueRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}
