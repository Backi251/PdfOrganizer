package com.example.pdforganizer.ui;

import com.example.pdforganizer.domain.ProcessingResult;
import com.example.pdforganizer.util.FileSizeUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/** Muestra el resumen final de la ejecucion, con acceso a la carpeta de salida y a los errores. */
public final class ResultPanel extends JPanel {

    public interface Listener {
        void onNewOperationRequested();
    }

    private final JTextArea summaryArea = new JTextArea(12, 50);
    private Path outputRoot;
    private ProcessingResult lastResult;
    private Listener listener;

    public ResultPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        add(new JScrollPane(summaryArea), BorderLayout.CENTER);

        JButton openFolderButton = new JButton("Abrir carpeta de destino");
        openFolderButton.addActionListener(e -> openOutputFolder());

        JButton viewErrorsButton = new JButton("Ver errores");
        viewErrorsButton.addActionListener(e -> showErrorsDialog());

        JButton newOperationButton = new JButton("Nueva operacion");
        newOperationButton.addActionListener(e -> {
            if (listener != null) listener.onNewOperationRequested();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttons.add(openFolderButton);
        buttons.add(viewErrorsButton);
        buttons.add(newOperationButton);
        add(buttons, BorderLayout.SOUTH);
    }

    public void show(ProcessingResult result, Path outputRoot) {
        this.lastResult = result;
        this.outputRoot = outputRoot;

        StringBuilder sb = new StringBuilder();
        sb.append("Proceso terminado.\n\n");
        sb.append("Archivos procesados:   ").append(result.getFilesProcessed()).append('\n');
        sb.append("PDFs procesados:       ").append(result.getPdfsProcessed()).append('\n');
        sb.append("Archivos no PDF:       ").append(result.getNonPdfFiles()).append('\n');
        sb.append("PDFs divididos:        ").append(result.getPdfsSplit()).append('\n');
        sb.append("Carpetas creadas:      ").append(result.getFoldersCreated()).append('\n');
        sb.append("Errores:               ").append(result.getErrors().size()).append("\n\n");
        sb.append("Tamano original:       ").append(FileSizeUtils.humanReadable(result.getOriginalSizeBytes())).append('\n');
        sb.append("Tamano final:          ").append(FileSizeUtils.humanReadable(result.getFinalSizeBytes())).append('\n');
        sb.append("Espacio ahorrado:      ").append(FileSizeUtils.humanReadable(result.getSpaceSavedBytes())).append('\n');
        summaryArea.setText(sb.toString());
    }

    private void openOutputFolder() {
        if (outputRoot == null) return;
        try {
            Desktop.getDesktop().open(outputRoot.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo abrir la carpeta automaticamente: " + e.getMessage()
                            + "\nRuta: " + outputRoot,
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showErrorsDialog() {
        if (lastResult == null || lastResult.getErrors().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No se registraron errores.", "Errores",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea area = new JTextArea(15, 60);
        area.setEditable(false);
        StringBuilder sb = new StringBuilder();
        lastResult.getErrors().forEach(err -> sb.append(err.toString()).append('\n'));
        area.setText(sb.toString());
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Errores registrados",
                JOptionPane.WARNING_MESSAGE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
