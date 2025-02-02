package org.ironsight.wpplugin.expandLayerTool.Gui;

import org.ironsight.wpplugin.expandLayerTool.operations.LayerMapping;
import org.ironsight.wpplugin.expandLayerTool.operations.LayerMappingContainer;
import org.ironsight.wpplugin.expandLayerTool.operations.MappingMacro;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.UUID;

public class MacroDesigner extends JPanel {
    private MappingMacro macro;

    private JLabel name, description;
    private JTable table;

    MacroDesigner() {
        init();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Macro Designer");

        LayerMappingContainer.addDefaultMappings(LayerMappingContainer.INSTANCE);
        MappingMacro mappingMacro = new MappingMacro(LayerMappingContainer.INSTANCE.queryMappingsAll());

        MacroDesigner designer = new MacroDesigner();
        designer.setMacro(mappingMacro);
        frame.add(designer);

        frame.setSize(new Dimension(400, 400));
        frame.setVisible(true);
    }

    private void init() {
        name = new JLabel("Name goes here");
        name.setFont(LayerMappingTopPanel.header1Font);
        description = new JLabel("Description goes here");
        description.setFont(LayerMappingTopPanel.header2Font);
        table = new JTable();
        table.setDefaultEditor(Object.class,
                new MappingTableCellEditor(this::onDeleteMapping,
                        this::onEditMapping,
                        this::onMoveUpMapping,
                        this::onMoveDownMapping));
        table.setDefaultRenderer(Object.class, new MappingTableCellRenderer());

        this.setLayout(new BorderLayout());
        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(name);
        top.add(description);
        this.add(top, BorderLayout.NORTH);

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onApplyButtonPressed();
            }
        });
        this.add(table, BorderLayout.CENTER);

    }

    private void onMoveUpMapping(LayerMapping mapping) {

    }

    private void onMoveDownMapping(LayerMapping mapping) {

    }

    private void onDeleteMapping(LayerMapping mapping) {

    }

    private void onEditMapping(LayerMapping mapping) {

    }

    private void onApplyButtonPressed() {

    }

    private void update() {
        assert macro.allMappingsReady(LayerMappingContainer.INSTANCE);

        name.setText(macro.getName());
        description.setText(macro.getDescription());

        DefaultTableModel model = new DefaultTableModel();
        Object[] columns = new Object[]{"Action"};
        Object[][] data = new Object[macro.mappingUids.length][];

        int i = 0;
        for (UUID id : macro.mappingUids) {
            LayerMapping m = LayerMappingContainer.INSTANCE.queryMappingById(id);
            assert m != null;

            data[i++] = new Object[]{m};
        }
        model.setDataVector(data, columns);
        table.setModel(model);

        invalidate();
        repaint();
    }

    public void setMacro(MappingMacro macro) {
        assert macro != null;
        this.macro = macro;
        update();
    }

}
