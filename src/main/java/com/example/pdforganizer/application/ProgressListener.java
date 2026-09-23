package com.example.pdforganizer.application;

import com.example.pdforganizer.domain.ProcessingError;
import com.example.pdforganizer.domain.ProcessingStatus;

/**
 * Callback para reportar progreso desde ProcessingService hacia la UI.
 * Los metodos pueden ser invocados desde un hilo de trabajo; la
 * implementacion en la UI (MainWindow/ProgressPanel) es responsable de
 * saltar al Event Dispatch Thread con SwingUtilities.invokeLater.
 */
public interface ProgressListener {

    void onStatusChanged(ProcessingStatus status);

    void onFileStarted(String fileName, int processedCount, int totalCount);

    void onError(ProcessingError error);

    void onFinished();
}
