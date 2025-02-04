package org.ironsight.wpplugin.expandLayerTool.Gui;

import org.ironsight.wpplugin.expandLayerTool.operations.LayerMapping;
import org.ironsight.wpplugin.expandLayerTool.operations.LayerMappingContainer;
import org.ironsight.wpplugin.expandLayerTool.operations.MappingMacro;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Arrays;
import java.util.UUID;

public class MacroDesigner extends JPanel {
    private MappingMacro macro;

    private JLabel name;
    private JLabel description;
    private JTable table;
    private JButton addButton, removeButton, moveUpButton, moveDownButton, changeMappingButton;
    private JScrollPane scrollPane;
    private int selectedRow;
    private boolean isUpdating;

    MacroDesigner() {
        init();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Macro Designer");

        LayerMappingContainer.addDefaultMappings(LayerMappingContainer.INSTANCE);
        MappingMacro mappingMacro = new MappingMacro("test macro",
                "it does cool things on your map",
                Arrays.stream(LayerMappingContainer.INSTANCE.queryMappingsAll())
                        .map(LayerMapping::getUid)
                        .toArray(UUID[]::new),
                UUID.randomUUID());

        MacroDesigner designer = new MacroDesigner();
        designer.setMacro(mappingMacro, false);
        frame.add(designer);

        frame.setSize(new Dimension(400, 400));
        frame.pack();
        frame.setVisible(true);
    }

    private void init() {
        this.setLayout(new BorderLayout());

        name = new JLabel("Name goes here");
        name.setFont(LayerMappingTopPanel.header1Font);
        description = new JLabel("Description goes here");
        description.setFont(LayerMappingTopPanel.header2Font);
        table = new JTable();
        table.setDefaultRenderer(Object.class, new MappingTableCellRenderer());
        scrollPane = new JScrollPane(table);
        this.add(scrollPane, BorderLayout.CENTER);

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(name);
        top.add(description);
        this.add(top, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout());
        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> onApplyButtonPressed());
        buttons.add(applyButton);

        addButton = new JButton("Add");
        addButton.addActionListener(e -> onAddMapping());
        buttons.add(addButton);

        removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> onDeleteMapping());
        buttons.add(removeButton);

        moveUpButton = new JButton("Move Up");
        moveUpButton.addActionListener(e -> onMoveUpMapping());
        buttons.add(moveUpButton);

        moveDownButton = new JButton("Move Down");
        moveDownButton.addActionListener(e -> onMoveDownMapping());
        buttons.add(moveDownButton);

        changeMappingButton = new JButton("Change Mapping");
        changeMappingButton.addActionListener(e -> onEditMapping());
        buttons.add(changeMappingButton);

        this.add(buttons, BorderLayout.SOUTH);
    }

    private void onAddMapping() {
        //insert any mapping from container at tail of list
        LayerMapping[] all = LayerMappingContainer.INSTANCE.queryMappingsAll();
        if (all.length == 0) return;
        UUID next = all[0].getUid();
        UUID[] ids = Arrays.copyOf(macro.mappingUids, macro.mappingUids.length + 1);
        ids[ids.length - 1] = next;
        selectedRow = ids.length - 1;
        MappingMacro mappingMacro = macro.withUUIDs(ids);
        setMacro(mappingMacro, true);

    }

    private void onMoveUpMapping() {
        if (selectedRow > 0 && selectedRow < table.getRowCount()) {
            UUID[] ids = macro.mappingUids.clone();
            ids[selectedRow - 1] = macro.mappingUids[selectedRow];
            ids[selectedRow] = macro.mappingUids[selectedRow - 1];
            selectedRow = selectedRow - 1;
            setMacro(macro.withUUIDs(ids), true);
            System.out.println("move mapping up to " + selectedRow);
        }
    }

    private void onMoveDownMapping() {
        if (selectedRow >= 0 && selectedRow < table.getRowCount() - 1) {
            UUID[] ids = macro.mappingUids.clone();
            ids[selectedRow + 1] = macro.mappingUids[selectedRow];
            ids[selectedRow] = macro.mappingUids[selectedRow + 1];
            selectedRow = selectedRow + 1;
            setMacro(macro.withUUIDs(ids), true);
            System.out.println("move mapping down to " + selectedRow);
        }
    }

    private void onDeleteMapping() {
        if (macro.mappingUids.length != 0 && selectedRow != -1) {
            UUID[] newIds = new UUID[macro.mappingUids.length - 1];
            int j = 0;
            for (int i = 0; i < macro.mappingUids.length; i++) {
                if (i != selectedRow) {
                    newIds[j++] = macro.mappingUids[i];
                }
            }
            selectedRow = Math.max(0, Math.min(newIds.length - 1, selectedRow));
            setMacro(macro.withUUIDs(newIds), true);
        }
    }

    private void onEditMapping() {
        new SelectLayerMappingDialog((JFrame) SwingUtilities.getWindowAncestor(this),
                LayerMappingContainer.INSTANCE.queryMappingsAll(),
                f -> {
                    UUID[] newIds = macro.mappingUids.clone();
                    newIds[selectedRow] = f.getUid();
                    setMacro(macro.withUUIDs(newIds), true);
                });
    }


    private void onApplyButtonPressed() {

    }

    private void update() {
        System.out.println("update MACRO DESIGNER");
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

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || isUpdating) return;
            selectedRow = table.getSelectedRow();
            System.out.println(" ROW SELECTED:  " + selectedRow);
        });

        final int rowCount = table.getRowCount();
        final int colCount = table.getColumnCount();
        for (int ix = 0; ix < rowCount; ix++) {
            int maxHeight = 0;
            for (int j = 0; j < colCount; j++) {
                final TableCellRenderer renderer = table.getCellRenderer(ix, j);
                maxHeight = Math.max(maxHeight, table.prepareRenderer(renderer, ix, j).getPreferredSize().height);
            }
            table.setRowHeight(ix, maxHeight);
        }
        invalidate();
        repaint();
        table.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        // Get the row index of the edited row (the row that triggered the update)
        if (selectedRow < table.getRowCount()) {
            System.out.println("scroll to row:" + selectedRow);
            Rectangle view = table.getCellRect(selectedRow, 0, true);
            scrollPane.getViewport().scrollRectToVisible(view);
        }
    }

    public void setMacro(MappingMacro macro, boolean forceUpdate) {
        isUpdating = true;
        assert macro != null;
        if (!forceUpdate && this.macro != null && this.macro.equals(macro)) return; //dont update if nothing changed
        this.macro = macro;
        update();
        isUpdating = false;
    }

}
