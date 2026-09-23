package com.example.pdforganizer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileSizeUtilsTest {

    @Test
    void humanReadable_bytes() {
        assertEquals("512 B", FileSizeUtils.humanReadable(512));
    }

    @Test
    void humanReadable_kilobytes() {
        assertTrue(FileSizeUtils.humanReadable(2048).endsWith("KB"));
    }

    @Test
    void humanReadable_megabytes() {
        assertTrue(FileSizeUtils.humanReadable(5L * 1024 * 1024).endsWith("MB"));
    }

    @Test
    void fitsWithin_exactLimit_true() {
        assertTrue(FileSizeUtils.fitsWithin(100, 50, 150));
    }

    @Test
    void fitsWithin_overLimit_false() {
        assertFalse(FileSizeUtils.fitsWithin(100, 51, 150));
    }
}
