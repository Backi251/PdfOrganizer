package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.CompressionLevel;
import com.example.pdforganizer.domain.ProcessingError;
import com.example.pdforganizer.domain.ProcessingResult;
import com.example.pdforganizer.domain.ProcessingStatus;
import com.example.pdforganizer.infrastructure.ApplicationLogger;
import com.example.pdforganizer.infrastructure.FileSystemService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integracion end-to-end: construye una jerarquia real
 * Proveedor/Poliza con PDFs y XML, ejecuta el pipeline completo y verifica
 * que el original queda intacto y que la salida tiene la estructura esperada.
 */
class OrganizationServiceTest {

    private static final ProgressListener NOOP_LISTENER = new ProgressListener() {
        @Override public void onStatusChanged(ProcessingStatus status) { }
        @Override public void onFileStarted(String fileName, int processedCount, int totalCount) { }
        @Override public void onError(ProcessingError error) { }
        @Override public void onFinished() { }
    };

    private void createBlankPdf(Path path, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(path.toFile());
        }
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void organize_preservesOriginalFilesUntouched(@TempDir Path tempRoot) throws Exception {
        Path source = tempRoot.resolve("Documentacion");
        Path providerDir = source.resolve("Proveedor A");
        Path policyDir = providerDir.resolve("Poliza 001");
        Files.createDirectories(policyDir);

        Path pdfFile = policyDir.resolve("transferencia.pdf");
        createBlankPdf(pdfFile, 1);
        Path xmlFile = policyDir.resolve("archivo.xml");
        Files.writeString(xmlFile, "<root><dato>123</dato></root>", StandardCharsets.UTF_8);

        String originalPdfHash = sha256(pdfFile);
        String originalXmlHash = sha256(xmlFile);

        Path outputRoot = tempRoot.resolve("comprimido Documentacion");
        Path workTemp = Files.createTempDirectory("test_tmp_");

        OrganizationService service = new OrganizationService(
                new FileSystemService(), new CompressionService(), new PdfSplitService(),
                new PartitionService(), new ApplicationLogger(tempRoot.resolve("logs")));

        ProcessingResult result = new ProcessingResult();
        service.organize(source, outputRoot, workTemp, CompressionLevel.MEDIA, result, NOOP_LISTENER, 2);

        // El original NUNCA debe modificarse: mismo contenido antes y despues.
        assertEquals(originalPdfHash, sha256(pdfFile), "El PDF original no debe modificarse");
        assertEquals(originalXmlHash, sha256(xmlFile), "El XML original no debe modificarse");
        assertTrue(Files.exists(pdfFile));
        assertTrue(Files.exists(xmlFile));
    }

    @Test
    void organize_createsExpectedOutputStructure(@TempDir Path tempRoot) throws Exception {
        Path source = tempRoot.resolve("Documentacion");
        Path providerDir = source.resolve("Proveedor A");
        Path policyDir = providerDir.resolve("Poliza 001");
        Files.createDirectories(policyDir);
        createBlankPdf(policyDir.resolve("contrato.pdf"), 1);
        Files.writeString(policyDir.resolve("archivo.xml"), "<root/>", StandardCharsets.UTF_8);

        Path outputRoot = tempRoot.resolve("comprimido Documentacion");
        Path workTemp = Files.createTempDirectory("test_tmp_");

        OrganizationService service = new OrganizationService(
                new FileSystemService(), new CompressionService(), new PdfSplitService(),
                new PartitionService(), new ApplicationLogger(tempRoot.resolve("logs")));

        ProcessingResult result = new ProcessingResult();
        service.organize(source, outputRoot, workTemp, CompressionLevel.MEDIA, result, NOOP_LISTENER, 2);

        Path expectedProviderDir = outputRoot.resolve("Punto 4-Documental Proveedor A");
        Path expectedPolicyDir = expectedProviderDir.resolve("Poliza 001");
        assertTrue(Files.isDirectory(expectedPolicyDir), "Debe crearse la estructura Proveedor/Poliza con el prefijo");
        assertTrue(Files.exists(expectedPolicyDir.resolve("contrato.pdf")));
        assertTrue(Files.exists(expectedPolicyDir.resolve("archivo.xml")));

        assertEquals(1, result.getPdfsProcessed());
        assertEquals(1, result.getNonPdfFiles());
        assertEquals(1, result.getFoldersCreated());
    }

    @Test
    void organize_copiesNonPdfFilesExactly(@TempDir Path tempRoot) throws Exception {
        Path source = tempRoot.resolve("Documentacion");
        Path providerDir = source.resolve("Proveedor B");
        Path policyDir = providerDir.resolve("Poliza 002");
        Files.createDirectories(policyDir);
        String xmlContent = "<root><campo valor=\"abc\"/></root>";
        Files.writeString(policyDir.resolve("datos.xml"), xmlContent, StandardCharsets.UTF_8);

        Path outputRoot = tempRoot.resolve("comprimido Documentacion");
        Path workTemp = Files.createTempDirectory("test_tmp_");

        OrganizationService service = new OrganizationService(
                new FileSystemService(), new CompressionService(), new PdfSplitService(),
                new PartitionService(), new ApplicationLogger(tempRoot.resolve("logs")));

        ProcessingResult result = new ProcessingResult();
        service.organize(source, outputRoot, workTemp, CompressionLevel.MEDIA, result, NOOP_LISTENER, 1);

        Path copiedXml = outputRoot.resolve("Punto 4-Documental Proveedor B").resolve("Poliza 002").resolve("datos.xml");
        assertTrue(Files.exists(copiedXml));
        assertEquals(xmlContent, Files.readString(copiedXml, StandardCharsets.UTF_8),
                "El contenido del XML debe copiarse sin ninguna modificacion");
    }
}
