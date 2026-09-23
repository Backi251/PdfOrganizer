package com.example.pdforganizer.ui;

import com.example.pdforganizer.domain.CompressionLevel;

import javax.swing.*;
import java.awt.*;

/** Panel con el nivel de compresion y la opcion de vista previa. Sin animaciones ni estilos costosos. */
public final class SettingsPanel extends JPanel {

    private final JRadioButton lowButton = new JRadioButton("Baja (mayor calidad, menor reduccion)");
    private final JRadioButton mediumButton = new JRadioButton("Media (equilibrio)", true);
    private final JRadioButton highButton = new JRadioButton("Alta (mayor reduccion, menor calidad)");
    private final JCheckBox previewCheckBox = new JCheckBox("Mostrar vista previa antes de procesar", true);

    public SettingsPanel() {
        setLayout(new GridLayout(0, 1, 4, 4));
        setBorder(BorderFactory.createTitledBorder("Nivel de compresion"));

        ButtonGroup group = new ButtonGroup();
        group.add(lowButton);
        group.add(mediumButton);
        group.add(highButton);

        add(lowButton);
        add(mediumButton);
        add(highButton);
        add(previewCheckBox);
    }

    public CompressionLevel getSelectedLevel() {
        if (lowButton.isSelected()) return CompressionLevel.BAJA;
        if (highButton.isSelected()) return CompressionLevel.ALTA;
        return CompressionLevel.MEDIA;
    }

    public boolean isPreviewEnabled() {
        return previewCheckBox.isSelected();
    }
}
