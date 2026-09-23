package com.example.pdforganizer.ui;

import com.example.pdforganizer.application.ProgressListener;
import com.example.pdforganizer.domain.ProcessingError;
import com.example.pdforganizer.domain.ProcessingStatus;

import javax.swing.*;
import java.awt.*;

/**
 * Muestra el progreso del procesamiento. Implementa ProgressListener pero
 * SIEMPRE reenvia al Event Dispatch Thread con SwingUtilities.invokeLater,
 * ya que ProcessingService corre en un hilo de fondo (ver MainWindow).
 */
public final class ProgressPanel extends JPanel implements ProgressListener {

    private final JLabel stageLabel = new JLabel("Preparando...");
    private final JLabel fileLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea errorArea = new JTextArea(6, 40);

    public ProgressPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel(new GridLayout(0, 1, 4, 4));
        stageLabel.setFont(stageLabel.getFont().deriveFont(Font.BOLD, 15f));
        top.add(stageLabel);
        top.add(fileLabel);
        top.add(progressBar);
        progressBar.setStringPainted(true);
        add(top, BorderLayout.NORTH);

        errorArea.setEditable(false);
        errorArea.setLineWrap(true);
        JPanel errorPanel = new JPanel(new BorderLayout());
        errorPanel.setBorder(BorderFactory.createTitledBorder("Errores durante el proceso"));
        errorPanel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
        add(errorPanel, BorderLayout.CENTER);
    }

    public void reset() {
        stageLabel.setText("Preparando...");
        fileLabel.setText(" ");
        progressBar.setValue(0);
        errorArea.setText("");
    }

    @Override
    public void onStatusChanged(ProcessingStatus status) {
        SwingUtilities.invokeLater(() -> stageLabel.setText("Etapa: " + describeStatus(status)));
    }

    @Override
    public void onFileStarted(String fileName, int processedCount, int totalCount) {
        SwingUtilities.invokeLater(() -> {
            int percent = totalCount > 0 ? (int) Math.round((processedCount * 100.0) / totalCount) : 0;
            fileLabel.setText("Archivo actual: " + fileName + "  (" + processedCount + " / " + totalCount + ")");
            progressBar.setValue(Math.min(100, percent));
            progressBar.setString(percent + "%");
        });
    }

    @Override
    public void onError(ProcessingError error) {
        SwingUtilities.invokeLater(() -> errorArea.append(error.toString() + "\n"));
    }

    @Override
    public void onFinished() {
        SwingUtilities.invokeLater(() -> stageLabel.setText("Proceso finalizado."));
    }

    private String describeStatus(ProcessingStatus status) {
        return switch (status) {
            case IDLE -> "En espera";
            case ANALYZING -> "Analizando archivos";
            case COPYING_STRUCTURE -> "Copiando estructura";
            case COMPRESSING -> "Comprimiendo y organizando documentos";
            case SPLITTING_PDFS -> "Dividiendo PDFs grandes";
            case PARTITIONING -> "Distribuyendo en carpetas";
            case VALIDATING -> "Validando resultados";
            case FINISHED -> "Finalizado";
            case CANCELLED -> "Cancelado";
            case ERROR -> "Error";
        };
    }
}
