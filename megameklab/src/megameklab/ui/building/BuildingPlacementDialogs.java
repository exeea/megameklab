/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.ui.building;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import megamek.common.bays.Bay;
import megamek.common.equipment.Mounted;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.BuildingEntity;
import megameklab.util.BuildingUtil;

/** Location editors display sheet coordinates; native cube coordinates never need to be entered by hand. */
final class BuildingPlacementDialogs {
    private BuildingPlacementDialogs() { }

    static void equipment(BuildingMainUI editor, Mounted<?> mount) {
        var entity = editor.getEntity();
        var hexes = entity.getInternalBuilding().getOriginalCoordsList();
        var grid = BuildingUtil.sheetGrid(hexes);
        var selected = BuildingConstruction.equipmentPositions(entity, mount);
        var anchor = BuildingConstruction.position(entity, mount.getLocation());
        var model = new DefaultTableModel(new String[] { "Use", "Hex", "Floor" }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                var hex = hexes.get(row);
                return column != 1 && !hex.equals(anchor.hex()) && (!BuildingConstruction.isCapital(mount.getType())
                      || anchor.hex().neighbors().contains(hex));
            }
        };
        for (var hex : hexes) {
            var position = selected.stream().filter(p -> p.hex().equals(hex)).findFirst();
            if (hex.equals(anchor.hex())) {
                position = java.util.Optional.of(anchor);
            }
            model.addRow(new Object[] { position.isPresent(), grid.label(hex),
                  entity.getLevelLabel(position.map(BuildingDesign.Position::level).orElse(editor.selectedFloor())) });
        }
        JTable table = new JTable(model);
        JComboBox<String> floors = new JComboBox<>();
        for (int floor = entity.getInternalBuilding().getBuildingHeight() - 1; floor >= 0; floor--) {
            floors.addItem(entity.getLevelLabel(floor));
        }
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(floors));
        JPanel panel = tablePanel(table, "One item, with its mass divided evenly across the selected hexes. Include its primary hex/floor.");
        if (JOptionPane.showConfirmDialog(editor, panel, "Distribute " + mount.getName(), JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            stopEditing(table);
            List<BuildingDesign.Position> positions = new ArrayList<>();
            for (int row = 0; row < model.getRowCount(); row++) {
                if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                    positions.add(new BuildingDesign.Position(hexes.get(row), floor(entity, model.getValueAt(row, 2).toString())));
                }
            }
            if (positions.equals(List.of(anchor))) {
                entity.getDesign().getEquipmentSpace().remove(mount);
            } else {
                entity.getDesign().getEquipmentSpace().put(mount, positions);
            }
            editor.scheduleRefresh();
        }
    }

    static void bay(BuildingMainUI editor, Bay bay) {
        var entity = editor.getEntity();
        var spaces = BuildingConstruction.baySpaces(entity, bay);
        var model = new DefaultTableModel(new String[] { "Hex/Floor", "Tons" }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? String.class : Double.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        for (int loc = 0; loc < entity.locations(); loc++) {
            var position = BuildingConstruction.position(entity, loc);
            model.addRow(new Object[] { BuildingUtil.locationLabel(entity, loc), spaces.stream()
                  .filter(space -> space.position().equals(position)).mapToDouble(BuildingDesign.Space::tons).sum() });
        }
        JTable table = new JTable(model);
        JPanel panel = tablePanel(table, "Allocate " + bay.getWeight() + " tons. Crew and residential quarters may span hexes and floors.");
        JPanel buttons = new JPanel();
        JButton even = new JButton("Distribute evenly");
        even.addActionListener(e -> {
            stopEditing(table);
            for (int row = 0; row < model.getRowCount(); row++) {
                model.setValueAt(bay.getWeight() / model.getRowCount(), row, 1);
            }
        });
        JButton here = new JButton("Place all at editing location");
        here.addActionListener(e -> {
            stopEditing(table);
            for (int row = 0; row < model.getRowCount(); row++) {
                model.setValueAt(row == editor.selectedLocation() ? bay.getWeight() : 0.0, row, 1);
            }
        });
        buttons.add(even);
        buttons.add(here);
        panel.add(buttons, BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(editor, panel, "Bay and quarters placement", JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            stopEditing(table);
            List<BuildingDesign.Space> result = new ArrayList<>();
            for (int loc = 0; loc < model.getRowCount(); loc++) {
                double weight = ((Number) model.getValueAt(loc, 1)).doubleValue();
                if (weight != 0) {
                    result.add(new BuildingDesign.Space(BuildingConstruction.position(entity, loc), weight));
                }
            }
            if (result.stream().anyMatch(space -> !Double.isFinite(space.tons()) || space.tons() < 0)
                  || Math.abs(result.stream().mapToDouble(BuildingDesign.Space::tons).sum() - bay.getWeight()) > .00001) {
                JOptionPane.showMessageDialog(editor, "Distribute exactly " + bay.getWeight() + " tons using non-negative values.");
                continue;
            }
            entity.getDesign().getBaySpace().put(bay, result);
            editor.scheduleRefresh();
            return;
        }
    }

    static void elevator(BuildingMainUI editor, int index) {
        var entity = editor.getEntity();
        var lifts = entity.getDesign().getElevators();
        var old = index < 0 ? null : lifts.get(index);
        var hex = old == null ? editor.selectedHex() : old.hex();
        int height = entity.getInternalBuilding().getBuildingHeight();
        var model = new DefaultTableModel(new String[] { "Stop", "Floor", "N", "NE", "SE", "S", "SW", "NW" }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? String.class : Boolean.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || (column >= 2 && entity.getInternalBuilding().getOriginalCoordsList()
                      .contains(hex.toOffset().translated(column - 2).toCube()));
            }
        };
        int internalSides = 0;
        for (int side = 0; side < 6; side++) {
            if (entity.getInternalBuilding().getOriginalCoordsList().contains(hex.toOffset().translated(side).toCube())) {
                internalSides |= 1 << side;
            }
        }
        for (int floor = height; floor >= 0; floor--) {
            int mask = old == null ? internalSides : old.exits().getOrDefault(floor, 0);
            model.addRow(new Object[] { old == null ? floor < height : old.exits().containsKey(floor),
                  BuildingUtil.roofLevelLabel(entity, floor), (mask & 1) != 0, (mask & 2) != 0,
                  (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0, (mask & 32) != 0 });
        }
        JTable table = new JTable(model);
        JPanel panel = tablePanel(table, "Select a continuous range of floors and internal access sides; the shaft must be clear.");
        JSpinner capacity = new JSpinner(new SpinnerNumberModel(old == null ? 20.0 : old.capacity(), 1.0, 1000.0, 1.0));
        JPanel top = new JPanel();
        top.add(new JLabel("Hex " + BuildingUtil.sheetGrid(entity.getInternalBuilding().getOriginalCoordsList()).label(hex) + "  Lift capacity (t):"));
        top.add(capacity);
        panel.add(top, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(editor, panel, "Industrial elevator", JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            stopEditing(table);
            var exits = new HashMap<Integer, Integer>();
            for (int row = 0; row < model.getRowCount(); row++) {
                if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                    int mask = 0;
                    for (int side = 0; side < 6; side++) {
                        if (Boolean.TRUE.equals(model.getValueAt(row, side + 2))) {
                            mask |= 1 << side;
                        }
                    }
                    exits.put(height - row, mask);
                }
            }
            var lift = new BuildingDesign.Elevator(hex, ((Number) capacity.getValue()).doubleValue(), exits);
            if (old == null) {
                lifts.add(lift);
            } else {
                lifts.set(index, lift);
            }
            editor.scheduleRefresh();
        }
    }

    private static JPanel tablePanel(JTable table, String description) {
        table.setRowHeight(24);
        table.putClientProperty("terminateEditOnFocusLost", true);
        JPanel panel = new JPanel(new BorderLayout(6, 8));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(650, 320));
        panel.add(new JLabel(description), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    static void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    static int floor(BuildingEntity entity, String text) {
        return Math.toIntExact(("Ground".equals(text) ? 0 : Long.parseLong(text)) - BuildingConstruction.baseLevel(entity));
    }
}
