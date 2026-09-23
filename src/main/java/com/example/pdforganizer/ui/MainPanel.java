package com.example.pdforganizer.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Pantalla principal: seleccion de carpeta origen/destino, nivel de
 * compresion y boton ANALIZAR. Interfaz deliberadamente simple (botones
 * grandes, sin efectos visuales) para minimizar consumo en equipos antiguos.
 */
public final class MainPanel extends JPanel {

    public interface Listener {
        void onAnalyzeRequested(File sourceDir, File destinationParentDir);
    }

    private final JTextField sourceField = new JTextField();
    private final JTextField destinationField = new JTextField();
    private final SettingsPanel settingsPanel = new SettingsPanel();
    private final JButton analyzeButton = new JButton("ANALIZAR");

    private File selectedSource;
    private File selectedDestination;
    private Listener listener;

    public MainPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(buildFolderRow("Carpeta madre de origen:", sourceField, this::chooseSource));
        form.add(Box.createVerticalStrut(10));
        form.add(buildFolderRow("Carpeta donde crear la salida:", destinationField, this::chooseDestination));
        form.add(Box.createVerticalStrut(16));
        form.add(settingsPanel);

        add(form, BorderLayout.CENTER);

        analyzeButton.setFont(analyzeButton.getFont().deriveFont(Font.BOLD, 16f));
        analyzeButton.setPreferredSize(new Dimension(200, 48));
        analyzeButton.addActionListener(e -> {
            if (selectedSource == null || selectedDestination == null) {
                JOptionPane.showMessageDialog(this,
                        "Selecciona la carpeta de origen y la carpeta de destino.",
                        "Datos incompletos", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (listener != null) {
                listener.onAnalyzeRequested(selectedSource, selectedDestination);
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(analyzeButton);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildFolderRow(String label, JTextField field, Runnable onBrowse) {
        JPanel row = new JPanel(new BorderLayout(6, 6));
        row.add(new JLabel(label), BorderLayout.NORTH);
        field.setEditable(false);
        row.add(field, BorderLayout.CENTER);
        JButton browse = new JButton("Seleccionar...");
        browse.addActionListener(e -> onBrowse.run());
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private void chooseSource() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecciona la carpeta madre de origen");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedSource = chooser.getSelectedFile();
            sourceField.setText(selectedSource.getAbsolutePath());
        }
    }

    private void chooseDestination() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecciona donde crear la carpeta de salida");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDestination = chooser.getSelectedFile();
            destinationField.setText(selectedDestination.getAbsolutePath());
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }
}
