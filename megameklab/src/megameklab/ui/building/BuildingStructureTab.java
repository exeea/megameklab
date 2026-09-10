/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 *
 * MegaMekLab is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MegaMekLab is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MegaMek was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */

package megameklab.ui.building;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import megamek.client.ui.WrapLayout;
import megamek.common.SimpleTechLevel;
import megamek.common.board.CubeCoords;
import megamek.common.enums.BuildingType;
import megamek.common.enums.Faction;
import megamek.common.equipment.EquipmentType;
import megamek.common.interfaces.ITechManager;
import megamek.common.units.BuildingEntity;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.IBuilding;
import megamek.common.units.UnitRole;
import megamek.common.verifier.TestBuilding;
import megameklab.ui.generalUnit.BasicInfoView;
import megameklab.ui.generalUnit.IconView;
import megameklab.ui.listeners.BuildListener;
import megameklab.ui.util.TabScrollPane;
import megameklab.ui.util.WidthControlComponent;
import megameklab.util.BuildingMap;
import megameklab.util.BuildingUtil;
import megameklab.util.UnitUtil;

class BuildingStructureTab extends JPanel implements BuildListener {
    private final BuildingMainUI editor;
    private final BasicInfoView basicInfo;
    private final IconView icon = new IconView();
    private final JComboBox<BuildingType> type = new JComboBox<>(new BuildingType[] {
          BuildingType.LIGHT, BuildingType.MEDIUM, BuildingType.HEAVY, BuildingType.HARDENED, BuildingType.RAIL });
    private final JComboBox<String> buildingClass = new JComboBox<>(new String[] {
          "Standard", "Hangar", "Fortress", "Gun Emplacement", "Castles Brian", "Tent", "Wall", "Fence", "Bridge" });
    private final JSpinner levels = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JSpinner cf = new JSpinner(new SpinnerNumberModel(40, 1, 1000, 1));
    private final JSpinner armor = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    private final JLabel cfLabel;
    private final JLabel armorLabel;
    private final JLabel selection = new JLabel();
    private final JLabel limits = new JLabel();
    private final JButton remove = new JButton("Remove selected hex");
    private final Footprint footprint = new Footprint();
    private final JPanel legend = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 4));
    private final Map<BuildingMap.Feature, JLabel> legendEntries = new EnumMap<>(BuildingMap.Feature.class);
    private final JCheckBox absoluteCoordinates = new JCheckBox("Absolute coordinates");
    private final JPanel sideControls = new JPanel(new WrapLayout(FlowLayout.LEFT));
    private final JCheckBox[] sides = new JCheckBox[6];
    private final JPanel bridgeControls = new JPanel(new WrapLayout(FlowLayout.LEFT));
    private final JSpinner deckLevel = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner bridgeStart = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner bridgeEnd = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JLabel bridgeEndpoints = new JLabel();
    private final JLabel protectionScale = new JLabel();
    private final JTextArea geometryHint = new JTextArea(3, 0);
    private boolean refreshing;

    BuildingStructureTab(BuildingMainUI editor) {
        this.editor = editor;
        basicInfo = new BasicInfoView(entity().getConstructionTechAdvancement());
        setLayout(new BorderLayout(15, 10));
        JPanel identity = new JPanel(new GridBagLayout());
        basicInfo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Basic Information"),
              BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        icon.setFromEntity(entity());
        icon.setRefreshedListener(editor);
        icon.setBorder(BorderFactory.createCompoundBorder(icon.getBorder(), BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        JPanel settings = new JPanel(new BorderLayout(0, 10));
        settings.setName("Building superstructure");
        settings.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Superstructure"),
              BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        JPanel fields = new JPanel(new GridBagLayout());
        addField(fields, "Building type", type);
        addField(fields, "Classification", buildingClass);
        addField(fields, "Levels (ground = G)", levels).setText("Levels:");
        levels.setToolTipText("Number of levels, starting at ground (G / level 0).");
        cfLabel = addField(fields, "CF per hex", cf);
        armorLabel = addField(fields, "Armor points per hex", armor);
        var fieldWidth = new GridBagConstraints();
        fieldWidth.gridx = 1;
        fieldWidth.gridy = fields.getComponentCount() / 2;
        fields.add(new WidthControlComponent(), fieldWidth);
        settings.add(fields, BorderLayout.CENTER);
        JPanel notes = new JPanel(new GridLayout(0, 1, 0, 6));
        notes.add(limits);
        notes.add(protectionScale);
        settings.add(notes, BorderLayout.SOUTH);
        var row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 8, 0);
        for (var panel : List.of(basicInfo, icon, settings)) {
            identity.add(panel, row);
            row.gridy++;
        }
        row.weighty = 1;
        identity.add(Box.createVerticalGlue(), row);
        var properties = new TabScrollPane(identity);
        properties.setName("Building properties");
        add(properties, BorderLayout.WEST);

        JPanel geometry = new JPanel(new BorderLayout(5, 10));
        JPanel map = new JPanel(new BorderLayout(0, 6));
        map.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Footprint"),
              BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        legend.setName("Building footprint legend");
        int markerHeight = legend.getFontMetrics(legend.getFont()).getHeight();
        for (var feature : BuildingMap.Feature.values()) {
            var entry = new JLabel(feature.label, new FeatureIcon(feature, markerHeight), SwingConstants.LEADING);
            entry.setIconTextGap(6);
            legend.add(entry);
            legendEntries.put(feature, entry);
        }
        absoluteCoordinates.setName("Absolute coordinates");
        absoluteCoordinates.setToolTipText("Show authored coordinates (q,r). Sheets always use sheet coordinates.");
        absoluteCoordinates.addActionListener(event -> editor.setAbsoluteCoordinates(absoluteCoordinates.isSelected()));
        JPanel mapHeader = new JPanel(new BorderLayout(8, 4));
        mapHeader.add(legend, BorderLayout.CENTER);
        mapHeader.add(absoluteCoordinates, BorderLayout.EAST);
        map.add(mapHeader, BorderLayout.NORTH);
        map.add(footprint, BorderLayout.CENTER);
        geometry.add(map, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        geometryHint.setEditable(false);
        geometryHint.setFocusable(false);
        geometryHint.setOpaque(false);
        geometryHint.setFont(selection.getFont());
        geometryHint.setLineWrap(true);
        geometryHint.setWrapStyleWord(true);
        actions.add(geometryHint);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT));
        remove.addActionListener(e -> {
            if (entity().getInternalBuilding().getCoordsList().size() > 1) {
                List<CubeCoords> hexes = new ArrayList<>(entity().getInternalBuilding().getCoordsList());
                hexes.remove(editor.selectedHex());
                configure(hexes);
            }
        });
        JButton rotate = new JButton("Rotate clockwise");
        rotate.addActionListener(e -> {
            transform(c -> new CubeCoords(-(int) c.r(), -(int) c.s(), -(int) c.q()), facing -> (facing + 1) % 6);
        });
        buttons.add(selection);
        buttons.add(remove);
        buttons.add(rotate);
        JButton mirror = new JButton("Mirror");
        mirror.addActionListener(e -> {
            transform(c -> new CubeCoords(-(int) c.q(), -(int) c.s(), -(int) c.r()),
                  facing -> (6 - facing) % 6);
        });
        buttons.add(mirror);
        actions.add(buttons);
        JPanel moveControls = new JPanel(new WrapLayout(FlowLayout.LEFT));
        JComboBox<String> direction = new JComboBox<>(BuildingEquipmentTab.FACINGS);
        JButton move = new JButton("Move selected hex");
        move.addActionListener(e -> {
            var old = editor.selectedHex();
            var target = old.toOffset().translated(direction.getSelectedIndex()).toCube();
            if (!entity().getInternalBuilding().getCoordsList().contains(target)) {
                transform(hex -> hex.equals(old) ? target : hex, facing -> facing);
            }
        });
        moveControls.add(direction);
        moveControls.add(move);
        actions.add(moveControls);
        sideControls.add(new JLabel("Wall/fence sides at selected hex:"));
        for (int side = 0; side < 6; side++) {
            int facing = side;
            sides[side] = new JCheckBox(BuildingEquipmentTab.FACINGS[side]);
            sides[side].setName("Structure side " + side);
            sides[side].addActionListener(e -> {
                if (!refreshing) {
                    var design = entity().getDesign();
                    var hex = editor.selectedHex();
                    int mask = design.wallSides(hex) ^ (1 << facing);
                    design.getWallSides().put(hex, mask);
                    // The same physical side is shared by two hexes; keep one owner.
                    var neighbor = hex.toOffset().translated(facing).toCube();
                    if ((mask & (1 << facing)) != 0 && entity().getInternalBuilding().getCoordsList().contains(neighbor)) {
                        design.getWallSides().put(neighbor, design.wallSides(neighbor) & ~(1 << ((facing + 3) % 6)));
                    }
                    editor.scheduleRefresh();
                }
            });
            sideControls.add(sides[side]);
        }
        actions.add(sideControls);
        JLabel deckLabel = new JLabel("Deck elevation at selected hex (0 = G)");
        deckLabel.setLabelFor(deckLevel);
        deckLevel.setName("Bridge deck elevation");
        deckLevel.setEnabled(false);
        bridgeControls.add(deckLabel);
        bridgeControls.add(deckLevel);
        bridgeStart.setName("Bridge start elevation");
        bridgeEnd.setName("Bridge end elevation");
        bridgeControls.add(bridgeEndpoints);
        bridgeControls.add(bridgeStart);
        bridgeControls.add(bridgeEnd);
        javax.swing.event.ChangeListener slopeChanged = e -> {
            if (!refreshing) {
                var span = BuildingConstruction.bridgeSpan(entity().getInternalBuilding().getOriginalCoordsList());
                if (span == null) {
                    return;
                }
                // Keep endpoints editable for an invalid requested rise; the verifier reports it.
                span.distances().keySet().forEach(hex -> entity().getDesign().getBridgeDecks().put(hex,
                      span.level(hex, (int) bridgeStart.getValue(), (int) bridgeEnd.getValue())));
                editor.scheduleRefresh();
            }
        };
        bridgeStart.addChangeListener(slopeChanged);
        bridgeEnd.addChangeListener(slopeChanged);
        actions.add(bridgeControls);
        geometry.add(actions, BorderLayout.SOUTH);
        add(geometry, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        basicInfo.addListener(this);
        type.addActionListener(e -> applySettings());
        buildingClass.addActionListener(e -> applySettings());
        levels.addChangeListener(e -> applySettings());
        cf.addChangeListener(e -> applySettings());
        armor.addChangeListener(e -> applySettings());
        refresh();
        properties.setPreferredSize(new Dimension(identity.getPreferredSize().width
              + properties.getVerticalScrollBar().getPreferredSize().width, 0));
    }

    private static JLabel addField(JPanel panel, String label, javax.swing.JComponent field) {
        JLabel name = new JLabel(label + ":", SwingConstants.RIGHT);
        name.setLabelFor(field);
        field.setName(label);
        var cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = panel.getComponentCount() / 2;
        cell.anchor = GridBagConstraints.EAST;
        cell.insets = new Insets(2, 0, 2, 8);
        panel.add(name, cell);
        cell.gridx = 1;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(2, 0, 2, 0);
        panel.add(field, cell);
        return name;
    }

    private BuildingEntity entity() {
        return editor.getEntity();
    }

    ITechManager getTechManager() {
        return basicInfo;
    }

    void refresh() {
        refreshing = true;
        basicInfo.removeListener(this);
        basicInfo.setFromEntity(entity());
        basicInfo.addListener(this);
        icon.refresh();
        type.setModel(new DefaultComboBoxModel<>(java.util.Arrays.stream(BuildingType.values())
              .filter(value -> TestBuilding.limits(value, entity().getBldgClass()) != null || value == entity().getBuildingType())
              .toArray(BuildingType[]::new)));
        type.setSelectedItem(entity().getBuildingType());
        buildingClass.setSelectedIndex(entity().getBldgClass() >= 0 && entity().getBldgClass() < buildingClass.getItemCount()
              ? entity().getBldgClass() : -1);
        levels.setValue(entity().getInternalBuilding().getBuildingHeight());
        cf.setValue(entity().getInternalBuilding().getCurrentCF(CubeCoords.ZERO));
        armor.setValue(entity().getInternalBuilding().getArmor(CubeCoords.ZERO));
        cfLabel.setText(BuildingConstruction.usesHexsides(entity()) ? "CF per hexside:" : "CF per hex:");
        armorLabel.setText(BuildingConstruction.usesHexsides(entity()) ? "Armor per hexside:" : "Armor per hex:");
        var rule = TestBuilding.limits(entity().getBuildingType(), entity().getBldgClass());
        limits.setText("<html><b>Construction limits</b><br>" + (rule == null ? "Invalid type/class combination" : "CF %d–%d; %s; %d %s"
              .formatted(rule.minimumCF(), rule.maximumCF(), rule.hexes() == Integer.MAX_VALUE ? "no length limit"
                    : "up to " + rule.hexes() + " hexes", rule.levels(), entity().getBldgClass() == IBuilding.BRIDGE ? "deck" : "levels")) + "</html>");
        protectionScale.setText(entity().getConstructionCFScale() == 10 ? "<html>Capital CF and armor<br>1 point = 10 standard points</html>"
              : BuildingConstruction.usesHexsides(entity()) ? "Standard CF and armor per occupied hexside" : "Standard CF and armor per hex");
        geometryHint.setText("Click + to add a hex; click a hex to select it; double-click to edit its equipment.\n"
              + (entity().getBldgClass() == IBuilding.BRIDGE
              ? "Bridge decks follow a steady slope. Their ends must meet the underlying map terrain."
              : BuildingConstruction.usesHexsides(entity()) ? "Select occupied hexsides below. All segments share CF, armor and height."
                    : "All hexes share the same height. Stepped buildings are separate buildings in a complex."));
        sideControls.setVisible(BuildingConstruction.usesHexsides(entity()));
        for (int side = 0; side < 6; side++) {
            sides[side].setSelected((entity().getDesign().wallSides(editor.selectedHex()) & (1 << side)) != 0);
        }
        bridgeControls.setVisible(entity().getBldgClass() == IBuilding.BRIDGE);
        deckLevel.setValue(entity().getDesign().bridgeDeck(editor.selectedHex()));
        var span = entity().getBldgClass() == IBuilding.BRIDGE
              ? BuildingConstruction.bridgeSpan(entity().getInternalBuilding().getOriginalCoordsList()) : null;
        bridgeStart.setEnabled(span != null);
        bridgeEnd.setEnabled(span != null && span.length() > 0);
        if (span != null) {
            bridgeEndpoints.setText("Steady slope: " + editor.hexLabel(span.start()) + " → " + editor.hexLabel(span.end()));
            bridgeStart.setValue(entity().getDesign().bridgeDeck(span.start()));
            bridgeEnd.setValue(entity().getDesign().bridgeDeck(span.end()));
        } else {
            bridgeEndpoints.setText("Connect the bridge hexes to set a slope");
        }
        levels.setEnabled(entity().getBldgClass() != IBuilding.BRIDGE && entity().getBldgClass() != IBuilding.TENT
              && entity().getBldgClass() != IBuilding.GUN_EMPLACEMENT);
        type.setEnabled(entity().getBldgClass() != IBuilding.TENT && entity().getBldgClass() != IBuilding.FENCE);
        absoluteCoordinates.setSelected(editor.absoluteCoordinates());
        selection.setText("Editing: " + editor.hexLabel(editor.selectedHex()) + "/"
              + BuildingUtil.levelLabel(displayedLevel(editor.selectedHex())));
        remove.setEnabled(entity().getInternalBuilding().getCoordsList().size() > 1);
        var features = EnumSet.noneOf(BuildingMap.Feature.class);
        entity().getInternalBuilding().getCoordsList().forEach(hex -> features.addAll(BuildingMap.features(entity(), hex, displayedLevel(hex))));
        legendEntries.forEach((feature, entry) -> entry.setVisible(features.contains(feature)));
        legend.setVisible(!features.isEmpty());
        legend.revalidate();
        footprint.repaint();
        refreshing = false;
    }

    private void applySettings() {
        if (!refreshing && type.getSelectedItem() != null && buildingClass.getSelectedIndex() >= 0) {
            if (buildingClass.getSelectedIndex() != entity().getBldgClass()) {
                refreshing = true;
                var chosen = (BuildingType) type.getSelectedItem();
                if (TestBuilding.limits(chosen, buildingClass.getSelectedIndex()) == null) {
                    chosen = java.util.Arrays.stream(BuildingType.values())
                          .filter(value -> TestBuilding.limits(value, buildingClass.getSelectedIndex()) != null).findFirst().orElseThrow();
                    type.setSelectedItem(chosen);
                }
                var rule = TestBuilding.limits(chosen, buildingClass.getSelectedIndex());
                cf.setValue(Math.clamp((int) cf.getValue(), rule.minimumCF(), rule.maximumCF()));
                levels.setValue(Math.min((int) levels.getValue(), rule.levels()));
                refreshing = false;
            }
            configure(List.copyOf(entity().getInternalBuilding().getCoordsList()));
        }
    }

    private void configure(List<CubeCoords> hexes) {
        // Materialize implicit north sides before transformations so their orientation follows the building.
        if (BuildingConstruction.usesHexsides(entity())) {
            entity().getInternalBuilding().getOriginalCoordsList().forEach(hex ->
                  entity().getDesign().getWallSides().putIfAbsent(hex, 1));
        }
        BuildingUtil.configure(entity(), (BuildingType) type.getSelectedItem(), buildingClass.getSelectedIndex(),
              (int) levels.getValue(), (int) cf.getValue(), (int) armor.getValue(), hexes);
        editor.scheduleRefresh();
    }

    private void transform(java.util.function.UnaryOperator<CubeCoords> transform, java.util.function.IntUnaryOperator facing) {
        var selected = transform.apply(editor.selectedHex());
        var hexes = entity().getInternalBuilding().getOriginalCoordsList().stream().map(transform).toList();
        var origin = hexes.contains(CubeCoords.ZERO) ? CubeCoords.ZERO : hexes.getFirst();
        BuildingUtil.transform(entity(), transform, facing);
        editor.selectLocation(selected.subtract(origin), editor.selectedFloor());
        editor.scheduleRefresh();
    }

    private int displayedLevel(CubeCoords hex) {
        return entity().getBldgClass() == IBuilding.BRIDGE ? entity().getDesign().bridgeDeck(hex) : editor.selectedFloor();
    }

    private record FeatureIcon(BuildingMap.Feature feature, int getIconHeight) implements Icon {
        @Override
        public int getIconWidth() {
            return getIconHeight * 4 / 3;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getIconWidth() - 2, h = getIconHeight - 2;
            Polygon marker = feature == BuildingMap.Feature.DOOR
                  ? new Polygon(new int[] { w / 2, w - 1, 1 }, new int[] { 1, h, h }, 3)
                  : new Polygon(new int[] { 1, w / 4, w * 3 / 4, w, w * 3 / 4, w / 4 },
                        new int[] { h / 2, 1, 1, h / 2, h, h }, 6);
            g.setColor(Color.decode(feature.color));
            g.fill(marker);
            g.setColor(Color.BLACK);
            g.draw(marker);
            g.setFont(component.getFont().deriveFont((float) h - 2));
            var metrics = g.getFontMetrics();
            g.drawString(feature.glyph, (w - metrics.stringWidth(feature.glyph)) / 2,
                  (h - metrics.getHeight()) / 2 + metrics.getAscent());
            g.dispose();
        }
    }

    private class Footprint extends JPanel {
        private final Map<CubeCoords, Polygon> cells = new LinkedHashMap<>();

        Footprint() {
            setName("Building footprint");
            setPreferredSize(new Dimension(560, 370));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    for (var cell : cells.entrySet()) {
                        if (cell.getValue().contains(event.getPoint())) {
                            CubeCoords selected = cell.getKey();
                            List<CubeCoords> hexes = new ArrayList<>(entity().getInternalBuilding().getCoordsList());
                            if (!hexes.contains(selected)) {
                                hexes.add(selected);
                                configure(hexes);
                                editor.selectLocation(selected, editor.selectedFloor());
                            } else {
                                editor.selectLocation(selected, editor.selectedFloor());
                                if (event.getClickCount() == 2) {
                                    editor.showEquipment();
                                }
                            }
                            return;
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            List<CubeCoords> hexes = entity().getInternalBuilding().getCoordsList();
            var visible = new LinkedHashSet<>(hexes);
            hexes.forEach(hex -> visible.addAll(hex.neighbors()));
            var labels = BuildingUtil.sheetGrid(hexes);
            // Keep the construction origin fixed; recentering the sheet grid here makes additions jump.
            // Reserve three rings so the first two additions and their neighbors do not change the scale.
            double width = 11;
            double height = 7;
            for (CubeCoords hex : visible) {
                width = Math.max(width, 3 * Math.abs(hex.q()) + 2);
                height = Math.max(height, 2 * Math.abs(hex.r() + hex.q() / 2) + 1);
            }
            double size = Math.max(1, Math.min((getWidth() - 30.0) / width,
                  (getHeight() - 30.0) / (Math.sqrt(3) * height)));
            double centerX = getWidth() / 2.0;
            double centerY = getHeight() / 2.0;
            cells.clear();
            for (CubeCoords hex : visible) {
                double x = centerX + hex.q() * 1.5 * size;
                double y = centerY + size * Math.sqrt(3) * (hex.r() + hex.q() / 2);
                Polygon polygon = new Polygon();
                for (int i = 0; i < 6; i++) {
                    polygon.addPoint((int) (x + size * Math.cos(i * Math.PI / 3)),
                          (int) (y + size * Math.sin(i * Math.PI / 3)));
                }
                boolean occupied = hexes.contains(hex);
                int displayedLevel = displayedLevel(hex);
                var features = occupied ? BuildingMap.features(entity(), hex, displayedLevel) : List.<BuildingMap.Feature>of();
                var fill = BuildingMap.fill(features);
                g.setColor(fill != null ? Color.decode(fill.color) : hex.equals(editor.selectedHex()) ? new Color(180, 210, 240)
                      : occupied ? new Color(230, 230, 230) : Color.WHITE);
                g.fill(polygon);
                boolean wall = occupied && BuildingConstruction.usesHexsides(entity());
                g.setStroke(new BasicStroke(occupied && !wall ? 2f : 1f));
                g.setColor(occupied && !wall ? Color.BLACK : Color.GRAY);
                if (fill != null && hex.equals(editor.selectedHex())) {
                    g.setStroke(new BasicStroke(3f));
                    g.setColor(new Color(65, 125, 190));
                }
                g.draw(polygon);
                g.setColor(occupied ? Color.BLACK : Color.GRAY);
                String symbols = features.stream().filter(feature -> !feature.glyph.isBlank()).map(feature -> feature.glyph)
                      .collect(java.util.stream.Collectors.joining(" "));
                String coordinate = editor.absoluteCoordinates() ? BuildingUtil.absoluteHexLabel(hex) : labels.label(hex);
                String text = occupied ? (symbols.isEmpty() ? "" : symbols + " ") + coordinate + "/" + BuildingUtil.levelLabel(displayedLevel) : "+";
                var font = g.getFont();
                int textWidth = g.getFontMetrics().stringWidth(text);
                if (occupied && textWidth > size * 1.6) {
                    g.setFont(font.deriveFont((float) (font.getSize2D() * size * 1.6 / textWidth)));
                }
                g.drawString(text, (float) (x - g.getFontMetrics().stringWidth(text) / 2.0), (float) y);
                g.setFont(font);
                if (occupied) {
                    long count = entity().getEquipmentInHex(hex).stream().filter(m -> BuildingConstruction.equipmentPositions(entity(), m)
                          .stream().anyMatch(p -> p.hex().equals(hex) && p.level() == editor.selectedFloor())).count();
                    String equipment = count + " items";
                    g.drawString(equipment, (float) (x - g.getFontMetrics().stringWidth(equipment) / 2.0), (float) (y + 12));
                }
                cells.put(hex, polygon);
            }
            // Door markers follow all hex fills so neighboring cells cannot erase an edge symbol.
            for (var door : entity().getDesign().getDoors()) {
                if (editor.selectedFloor() < door.position().level() || editor.selectedFloor() >= door.position().level() + door.height()) {
                    continue;
                }
                var polygon = cells.get(door.position().hex());
                if (polygon == null || door.facing() < 0 || door.facing() > 5) {
                    continue;
                }
                double x = centerX + door.position().hex().q() * 1.5 * size;
                double y = centerY + size * Math.sqrt(3) * (door.position().hex().r() + door.position().hex().q() / 2);
                int a = (door.facing() + 4) % 6, b = (a + 1) % 6;
                var triangle = new Polygon();
                for (var point : BuildingMap.doorPoints(new double[] { polygon.xpoints[a] - x, polygon.ypoints[a] - y },
                      new double[] { polygon.xpoints[b] - x, polygon.ypoints[b] - y })) {
                    triangle.addPoint((int) Math.round(x + point[0]), (int) Math.round(y + point[1]));
                }
                g.setStroke(new BasicStroke(1.5f));
                g.setColor(Color.WHITE);
                g.fill(triangle);
                g.setColor(Color.BLACK);
                g.draw(triangle);
            }
            // Draw occupied sides last, so adjacent hex fills cannot cover them.
            if (BuildingConstruction.usesHexsides(entity())) {
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(4f));
                for (CubeCoords hex : hexes) {
                    Polygon polygon = cells.get(hex);
                    for (int side = 0; side < 6; side++) {
                        if ((entity().getDesign().wallSides(hex) & (1 << side)) != 0) {
                            int a = (side + 4) % 6;
                            int b = (a + 1) % 6;
                            g.drawLine(polygon.xpoints[a], polygon.ypoints[a], polygon.xpoints[b], polygon.ypoints[b]);
                        }
                    }
                }
            }
            g.dispose();
        }
    }

    @Override
    public void chassisChanged(String chassis) {
        entity().setChassis(chassis);
        editor.scheduleRefresh();
    }

    @Override
    public void modelChanged(String model) {
        entity().setModel(model);
        editor.scheduleRefresh();
    }

    @Override
    public void yearChanged(int year) {
        entity().setYear(year);
        updateTechLevel();
    }

    @Override
    public void buildYearChanged(int year) {
        entity().setOriginalBuildYear(year);
        editor.scheduleRefresh();
    }

    @Override
    public void updateTechLevel() {
        entity().setTechLevel(basicInfo.getTechLevel().getCompoundTechLevel(basicInfo.useClanTechBase()));
        editor.scheduleRefresh();
    }

    @Override
    public void sourceChanged(String source) {
        entity().setSource(source);
        editor.scheduleRefresh();
    }

    @Override
    public void publishedChanged(String published) {
        entity().setPublished(published);
        editor.scheduleRefresh();
    }

    @Override
    public void factionChanged(Faction faction) {
        entity().setTechFaction(faction);
        editor.scheduleRefresh();
    }

    @Override
    public void mulIdChanged(int mulId) {
        entity().setMulId(mulId);
        editor.scheduleRefresh();
    }

    @Override
    public void techBaseChanged(boolean clan, boolean mixed) {
        entity().setMixedTech(mixed);
        updateTechLevel();
    }

    @Override
    public void techLevelChanged(SimpleTechLevel techLevel) {
        updateTechLevel();
    }

    @Override
    public void roleChanged(UnitRole role) {
        entity().setUnitRole(role);
        editor.scheduleRefresh();
    }

    @Override
    public void manualBVChanged(int manualBV) {
        UnitUtil.setManualBV(manualBV, entity());
        editor.scheduleRefresh();
    }

    @Override
    public void refreshSummary() {
        editor.scheduleRefresh();
    }

    @Override
    public void walkChanged(int walkMP) {
    }

    @Override
    public void jumpChanged(int jumpMP, EquipmentType jumpJet) {
    }

    @Override
    public void jumpTypeChanged(EquipmentType jumpJet) {
    }
}
