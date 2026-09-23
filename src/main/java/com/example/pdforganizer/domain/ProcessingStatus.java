package com.example.pdforganizer.domain;

/** Etapas del pipeline de procesamiento, usadas para reportar progreso en la UI. */
public enum ProcessingStatus {
    IDLE,
    ANALYZING,
    COPYING_STRUCTURE,
    COMPRESSING,
    SPLITTING_PDFS,
    PARTITIONING,
    VALIDATING,
    FINISHED,
    CANCELLED,
    ERROR
}
