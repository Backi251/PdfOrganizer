package com.example.pdforganizer.ui;

import com.example.pdforganizer.application.AnalysisResult;
import com.example.pdforganizer.application.ProcessingService;
import com.example.pdforganizer.config.AppConfig;
import com.example.pdforganizer.domain.ProcessingResult;
import com.example.pdforganizer.infrastructure.ApplicationLogger;
import com.example.pdforganizer.util.FileNameUtils;
import com.example.pdforganizer.util.FileSizeUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

/**
 * Ventana principal. Usa CardLayout para alternar entre las pantallas
 * (analisis, progreso, resultado) y SwingWorker para que el procesamiento
 * pesado nunca bloquee el Event Dispatch Thread.
 */
public final class MainWindow extends JFrame {

    private static final String CARD_MAIN = "main";
    private static final String CARD_PROGRESS = "progress";
    private static final String CARD_RESULT = "result";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final MainPanel mainPanel = new MainPanel();
    private final ProgressPanel progressPanel = new ProgressPanel();
    private final ResultPanel resultPanel = new ResultPanel();

    private final ProcessingService processingService = new ProcessingService();

    public MainWindow() {
        super("PDF Compressor & Document Organizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);

        cards.add(mainPanel, CARD_MAIN);
        cards.add(progressPanel, CARD_PROGRESS);
        cards.add(resultPanel, CARD_RESULT);
        add(cards);

        mainPanel.setListener(this::onAnalyzeRequested);
        resultPanel.setListener(() -> {
            progressPanel.reset();
            cardLayout.show(cards, CARD_MAIN);
        });

        cardLayout.show(cards, CARD_MAIN);
    }

    private void onAnalyzeRequested(File sourceDir, File destinationParentDir) {
        Path source = sourceDir.toPath();
        Path destinationParent = destinationParentDir.toPath();

        try {
            processingService.validatePaths(source, destinationParent);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ruta invalida", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<AnalysisResult, Void> analysisWorker = new SwingWorker<>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                return processingService.analyze(source);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    AnalysisResult analysis = get();
                    handleAnalysisComplete(analysis, source, destinationParent);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Error durante el analisis: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        analysisWorker.execute();
    }

    private void handleAnalysisComplete(AnalysisResult analysis, Path source, Path destinationParent) {
        boolean showPreview = mainPanel.getSettingsPanel().isPreviewEnabled();

        String outputName = FileNameUtils.rootOutputName(source.getFileName().toString());
        String message = String.format(
                "Origen:%n%s%n%nDestino:%n%s%n%nArchivos: %d%nPDF: %d%nOtros: %d%nTamano: %s%n" +
                        "Carpetas de salida estimadas (minimo): %d",
                source, destinationParent.resolve(outputName),
                analysis.getTotalFileCount(), analysis.getPdfCount(), analysis.getOtherCount(),
                FileSizeUtils.humanReadable(analysis.getTotalSizeBytes()),
                analysis.getEstimatedFoldersAtLeast());

        if (showPreview) {
            int choice = JOptionPane.showConfirmDialog(this, message, "Vista previa",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }
        startProcessing(source, destinationParent);
    }

    private void startProcessing(Path source, Path destinationParent) {
        progressPanel.reset();
        cardLayout.show(cards, CARD_PROGRESS);

        AppConfig config = new AppConfig();
        config.setCompressionLevel(mainPanel.getSettingsPanel().getSelectedLevel());

        SwingWorker<ProcessingResult, Void> worker = new SwingWorker<>() {
            private ApplicationLogger logger;

            @Override
            protected ProcessingResult doInBackground() throws Exception {
                // Carpeta de logs en el perfil del usuario, NUNCA relativa al
                // directorio de trabajo: cuando la app esta instalada (jpackage),
                // el directorio de trabajo puede ser una carpeta protegida
                // (ej. "C:\Program Files\...") donde el usuario no puede escribir.
                // ApplicationLogger ademas trae su propio respaldo automatico
                // al directorio temporal si ni siquiera esta ubicacion funcionara.
                Path logsDir = Path.of(System.getProperty("user.home"), "PDFOrganizer", "logs");
                logger = new ApplicationLogger(logsDir);
                return processingService.process(source, destinationParent, config, progressPanel, logger);
            }

            @Override
            protected void done() {
                if (logger != null) {
                    logger.close();
                }
                try {
                    ProcessingResult result = get();
                    String outputName = FileNameUtils.rootOutputName(source.getFileName().toString());
                    resultPanel.show(result, destinationParent.resolve(outputName));
                    cardLayout.show(cards, CARD_RESULT);
                } catch (Exception ex) {
                    String detail = (ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Error durante el procesamiento: " + detail,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    cardLayout.show(cards, CARD_MAIN);
                }
            }
        };
        worker.execute();
    }
}
