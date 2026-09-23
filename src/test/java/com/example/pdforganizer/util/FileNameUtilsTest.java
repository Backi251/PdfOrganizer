package com.example.pdforganizer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileNameUtilsTest {

    @Test
    void isPriorityFile_transferencia() {
        assertTrue(FileNameUtils.isPriorityFile("Transferencia.pdf"));
        assertTrue(FileNameUtils.isPriorityFile("transferencia_01.pdf"));
    }

    @Test
    void isPriorityFile_cheque() {
        assertTrue(FileNameUtils.isPriorityFile("CHEQUE.pdf"));
        assertTrue(FileNameUtils.isPriorityFile("Cheque_2026.pdf"));
    }

    @Test
    void isPriorityFile_poliza() {
        assertTrue(FileNameUtils.isPriorityFile("Póliza.pdf"));
        assertTrue(FileNameUtils.isPriorityFile("POLIZA_123.pdf"));
    }

    @Test
    void isPriorityFile_notPriority() {
        assertFalse(FileNameUtils.isPriorityFile("contrato.pdf"));
    }

    @Test
    void isPdf_detection() {
        assertTrue(FileNameUtils.isPdf("documento.PDF"));
        assertFalse(FileNameUtils.isPdf("archivo.xml"));
    }

    @Test
    void fragmentName_generatesExpectedSuffix() {
        assertEquals("factura 2.pdf", FileNameUtils.fragmentName("factura.pdf", 2));
    }

    @Test
    void numberedFolderName_singlePart_noSuffix() {
        assertEquals("Poliza 001", FileNameUtils.numberedFolderName("Poliza 001", 1, 1));
    }

    @Test
    void numberedFolderName_multiplePart_hasSuffix() {
        assertEquals("Poliza 001 2", FileNameUtils.numberedFolderName("Poliza 001", 2, 3));
    }
}
