package org.ironsight.wpplugin.expandLayerTool.Gui;

import org.ironsight.wpplugin.expandLayerTool.operations.Gradient;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.function.Consumer;

public class GradientEditor extends JPanel {
    private final Consumer<Gradient> submit;
    private final Consumer<Gradient> update;
    JLabel[] warnings = new JLabel[]{new JLabel("WARNING:"), new JLabel("some entries are invalid"), new JLabel(),
            new JLabel()};
    private Gradient gradient;
    private boolean invalid;
    private boolean blockEventhandling;
    private JTextField[] positionTextFields;
    private JTextField[] valueTextFields;
    private JSlider[] valueSliders;
    private JSlider[] positionSliders;

    public GradientEditor(Gradient gradient, Consumer<Gradient> update, Consumer<Gradient> submit) {
        this.gradient = gradient;
        this.update = update;
        this.submit = submit;
        setup();
    }

    private void setup() {
        removeAll();
        valueTextFields = new JTextField[gradient.positions.length];
        valueSliders = new JSlider[gradient.positions.length];
        positionTextFields = new JTextField[gradient.positions.length];
        positionSliders = new JSlider[gradient.positions.length];

        JPanel oneP = new JPanel(new GridLayout(0, 1)), twoP = new JPanel(new GridLayout(0, 1)), threeP =
                new JPanel(new GridLayout(0, 1)), fourP = new JPanel(new GridLayout(0, 1));


        Consumer<JComponent[]> addRow;
        {
            // Panel for editing points
            GridBagLayout grid = new GridBagLayout();
            grid.columnWeights = new double[]{1, 0.3, 1};

            JPanel baseGrid = new JPanel(new GridLayout(1, 4));
            baseGrid.add(oneP);
            baseGrid.add(twoP);
            baseGrid.add(threeP);
            baseGrid.add(fourP);

            for (Component comp : baseGrid.getComponents()) {
                ((JComponent) comp).setAlignmentX(Component.TOP_ALIGNMENT); // Center align horizontally
            }

            addRow = components -> {
                oneP.add(components[0]);
                twoP.add(components[1]);
                threeP.add(components[2]);
                fourP.add(components[3]);
            };
            this.add(baseGrid);
        }
        oneP.add(new JLabel("one"));
        twoP.add(new JLabel("two"));
        threeP.add(new JLabel("three"));
        fourP.add(new JLabel("four"));

        addRow.accept(warnings);
        addRow.accept(new JLabel[]{new JLabel("Up to width %"), new JLabel(""), new JLabel("apply with chance %"),
                new JLabel("")});

        DocumentListener pointChangeListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onPointInputChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onPointInputChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // This is fired for attribute changes, which is uncommon for plain text fields.
            }
        };
        DocumentListener valueChangeListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onValueInputChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onValueInputChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // This is fired for attribute changes, which is uncommon for plain text fields.
            }
        };
        ChangeListener sliderChangeListener = e -> onSliderInputChange();

        for (int i = 0; i < gradient.positions.length; i++) {
            {
                positionTextFields[i] = new JTextField(String.valueOf(Math.round(gradient.positions[i] * 100)));
                JTextField field = positionTextFields[i];
                field.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        field.selectAll();
                    }
                });
                field.getDocument().addDocumentListener(pointChangeListener);
            }

            {
                valueTextFields[i] = new JTextField(String.valueOf(Math.round(gradient.values[i] * 100)));
                JTextField field = valueTextFields[i];
                field.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        field.selectAll();
                    }
                });
                field.getDocument().addDocumentListener(valueChangeListener);
            }

            {
                valueSliders[i] = new JSlider(0, 100, Math.round(gradient.values[i] * 100));
                valueSliders[i].addChangeListener(sliderChangeListener);
            }

            {
                positionSliders[i] = new JSlider(0, 100, Math.round(gradient.values[i] * 100));
                positionSliders[i].addChangeListener(sliderChangeListener);
            }
        }

        Arrays.stream(positionSliders).forEach(oneP::add);
        Arrays.stream(positionTextFields).forEach(twoP::add);
        Arrays.stream(valueSliders).forEach(threeP::add);
        Arrays.stream(valueTextFields).forEach(fourP::add);

        {
            JButton increaseRowsButton = new JButton("+");
            increaseRowsButton.addActionListener(e -> {
                float[] updatedPoints = new float[gradient.positions.length + 1];
                float[] updatedValues = new float[gradient.positions.length + 1];
                for (int i = 0; i < gradient.positions.length; i++) {
                    updatedPoints[i] = gradient.positions[i];
                    updatedValues[i] = gradient.values[i];
                }
                gradient = new Gradient(updatedPoints, updatedValues);
                setup();
            });

            JButton decreaseRowsButton = new JButton("-");
            decreaseRowsButton.addActionListener(e -> {
                float[] updatedPoints = new float[gradient.positions.length - 1];
                float[] updatedValues = new float[gradient.positions.length - 1];
                for (int i = 0; i < updatedPoints.length; i++) {
                    updatedPoints[i] = gradient.positions[i];
                    updatedValues[i] = gradient.values[i];
                }
                gradient = new Gradient(updatedPoints, updatedValues);
                setup();
            });
            addRow.accept(new JComponent[]{increaseRowsButton, new JLabel(), decreaseRowsButton, new JLabel()});
        }
        {
            JButton submitButton = new JButton("Submit");
            JButton cancelButton = new JButton("Cancel");
            submitButton.addActionListener((ActionEvent e) -> {
                submit.accept(this.gradient);
            });
            addRow.accept(new JComponent[]{submitButton, new JLabel(), cancelButton, new JLabel()});
        }

        revalidate();
        repaint();
    }


    private void updateGuiFromGradient(Gradient gradient) {
        blockEventhandling = true;
        invalid = false;
        try {
            //validate points are monotone rising
            for (int i = 1; i < gradient.positions.length; i++) {
                if (!(gradient.positions[i - 1] < gradient.positions[i])) {
                    invalid = true;
                    break;
                }
            }
        } catch (NumberFormatException ignored) {
            invalid = true;
        } catch (Exception ex) {
            invalid = true;
        } finally {
            for (int i = 0; i < gradient.positions.length; i++) {
                int valueInt = Math.round(100 * gradient.values[i]);
                valueSliders[i].setValue(valueInt);
                valueTextFields[i].setText(Integer.toString(valueInt));

                int pointInt = Math.round(100 * gradient.positions[i]);
                positionSliders[i].setValue(pointInt);
                positionTextFields[i].setText(Integer.toString(pointInt));
            }

            if (!invalid) {
                update.accept(gradient);
            }
            Arrays.stream(warnings).forEach(w -> w.setVisible(invalid));
        }
        blockEventhandling = false;
    }

    private void onSliderInputChange() {
        if (blockEventhandling) return;
        for (int i = 0; i < gradient.positions.length; i++) {
            gradient.values[i] = valueSliders[i].getValue() / 100f;
            gradient.positions[i] = positionSliders[i].getValue() / 100f;
        }
        //trigger input update
        SwingUtilities.invokeLater(() -> updateGuiFromGradient(gradient));
    }

    private void onValueInputChange() {
        if (blockEventhandling) return;
        for (int i = 0; i < gradient.positions.length; i++) {
            try {
                float value = Float.parseFloat(valueTextFields[i].getText()) / 100f;
                if (value < 0) value = 0;
                if (value > 1) value = 1;
                gradient.values[i] = value;

            } catch (NumberFormatException ignored) {
            }
        }
        //trigger input update
        SwingUtilities.invokeLater(() -> updateGuiFromGradient(gradient));

    }

    private void onPointInputChange() {
        if (blockEventhandling) return;
        for (int i = 0; i < gradient.positions.length; i++) {
            try {
                float value = Float.parseFloat(positionTextFields[i].getText()) / 100f;
                if (value < 0) value = 0;
                if (value > 1) value = 1;
                gradient.positions[i] = value;
            } catch (NumberFormatException ignored) {
            }
        }
        //trigger input update
        SwingUtilities.invokeLater(() -> updateGuiFromGradient(gradient));
    }


}
