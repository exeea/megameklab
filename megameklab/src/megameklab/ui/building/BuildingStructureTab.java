/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.ui.building;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

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
    private final JPanel sideControls = new JPanel();
    private final JCheckBox[] sides = new JCheckBox[6];
    private final JPanel bridgeControls = new JPanel();
    private final JSpinner deckLevel = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner bridgeStart = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner bridgeEnd = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JLabel bridgeEndpoints = new JLabel();
    private final JLabel protectionScale = new JLabel();
    private final JLabel geometryHint = new JLabel();
    private boolean refreshing;

    BuildingStructureTab(BuildingMainUI editor) {
        this.editor = editor;
        basicInfo = new BasicInfoView(entity().getConstructionTechAdvancement());
        setLayout(new BorderLayout(15, 10));
        JPanel identity = new JPanel();
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        basicInfo.setBorder(BorderFactory.createTitledBorder("Basic Information"));
        identity.add(basicInfo);
        icon.setFromEntity(entity());
        icon.setRefreshedListener(editor);
        identity.add(icon);
        basicInfo.setMaximumSize(basicInfo.getPreferredSize());
        icon.setMaximumSize(icon.getPreferredSize());
        identity.add(Box.createVerticalGlue());
        add(identity, BorderLayout.WEST);

        JPanel geometry = new JPanel(new BorderLayout(5, 10));
        JPanel settings = new JPanel(new GridLayout(0, 2, 8, 6));
        settings.setBorder(BorderFactory.createTitledBorder("Superstructure"));
        addField(settings, "Building type", type);
        addField(settings, "Classification", buildingClass);
        addField(settings, "Levels (ground = G)", levels);
        cfLabel = addField(settings, "CF per hex", cf);
        armorLabel = addField(settings, "Armor points per hex", armor);
        settings.add(new JLabel("Construction limits"));
        settings.add(limits);
        settings.add(new JLabel("Protection scale"));
        settings.add(protectionScale);
        geometry.add(settings, BorderLayout.NORTH);
        geometry.add(footprint, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(new JLabel("Click an empty neighboring hex to add it; click an occupied hex to select it."));
        actions.add(geometryHint);
        JPanel buttons = new JPanel();
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
        JPanel moveControls = new JPanel();
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
        moveControls.add(new JLabel("Double-click an occupied hex to edit its equipment."));
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
    }

    private static JLabel addField(JPanel panel, String label, javax.swing.JComponent field) {
        JLabel name = new JLabel(label);
        name.setLabelFor(field);
        field.setName(label);
        panel.add(name);
        panel.add(field);
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
        cfLabel.setText(BuildingConstruction.usesHexsides(entity()) ? "CF per hexside"
              : entity().getConstructionCFScale() == 10 ? "Capital CF per hex" : "CF per hex");
        armorLabel.setText(BuildingConstruction.usesHexsides(entity()) ? "Armor points per hexside"
              : entity().getConstructionCFScale() == 10 ? "Capital armor points per hex" : "Armor points per hex");
        var rule = TestBuilding.limits(entity().getBuildingType(), entity().getBldgClass());
        limits.setText(rule == null ? "Invalid type/class combination" : "CF %d–%d; %s; %d %s"
              .formatted(rule.minimumCF(), rule.maximumCF(), rule.hexes() == Integer.MAX_VALUE ? "no length limit"
                    : "up to " + rule.hexes() + " hexes", rule.levels(), entity().getBldgClass() == IBuilding.BRIDGE ? "deck" : "levels"));
        protectionScale.setText(entity().getConstructionCFScale() == 10 ? "Capital CF and armor (1 point = 10 standard points)"
              : BuildingConstruction.usesHexsides(entity()) ? "Standard CF and armor per occupied hexside" : "Standard CF and armor per hex");
        geometryHint.setText(entity().getBldgClass() == IBuilding.BRIDGE
              ? "Bridge decks follow a steady slope. Their ends must meet the underlying map terrain."
              : BuildingConstruction.usesHexsides(entity()) ? "Select occupied hexsides below. All segments share CF, armor and height."
                    : "All hexes share the same height. Stepped buildings are separate buildings in a complex.");
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
            var grid = BuildingUtil.sheetGrid(entity().getInternalBuilding().getOriginalCoordsList());
            bridgeEndpoints.setText("Steady slope: " + grid.label(span.start()) + " → " + grid.label(span.end()));
            bridgeStart.setValue(entity().getDesign().bridgeDeck(span.start()));
            bridgeEnd.setValue(entity().getDesign().bridgeDeck(span.end()));
        } else {
            bridgeEndpoints.setText("Connect the bridge hexes to set a slope");
        }
        levels.setEnabled(entity().getBldgClass() != IBuilding.BRIDGE && entity().getBldgClass() != IBuilding.TENT
              && entity().getBldgClass() != IBuilding.GUN_EMPLACEMENT);
        type.setEnabled(entity().getBldgClass() != IBuilding.TENT && entity().getBldgClass() != IBuilding.FENCE);
        selection.setText("Editing: " + BuildingUtil.locationLabel(entity(), editor.selectedLocation()));
        remove.setEnabled(entity().getInternalBuilding().getCoordsList().size() > 1);
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

    private class Footprint extends JPanel {
        private final Map<CubeCoords, Polygon> cells = new LinkedHashMap<>();

        Footprint() {
            setName("Building footprint");
            setPreferredSize(new Dimension(560, 370));
            setBorder(BorderFactory.createTitledBorder("Footprint"));
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
            double size = Math.min((getWidth() - 30.0) / width,
                  (getHeight() - 45.0) / (Math.sqrt(3) * height));
            double centerX = getWidth() / 2.0;
            double centerY = 30 + (getHeight() - 45.0) / 2;
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
                g.setColor(hex.equals(editor.selectedHex()) ? new Color(180, 210, 240)
                      : occupied ? new Color(230, 230, 230) : Color.WHITE);
                g.fill(polygon);
                boolean wall = occupied && BuildingConstruction.usesHexsides(entity());
                g.setStroke(new BasicStroke(occupied && !wall ? 2f : 1f));
                g.setColor(occupied && !wall ? Color.BLACK : Color.GRAY);
                g.draw(polygon);
                g.setColor(occupied ? Color.BLACK : Color.GRAY);
                int displayedLevel = entity().getBldgClass() == IBuilding.BRIDGE ? entity().getDesign().bridgeDeck(hex) : editor.selectedFloor();
                String text = occupied ? labels.label(hex) + "/" + BuildingUtil.levelLabel(displayedLevel) : "+";
                g.drawString(text, (float) (x - g.getFontMetrics().stringWidth(text) / 2.0), (float) y);
                if (occupied) {
                    long count = entity().getEquipmentInHex(hex).stream().filter(m -> BuildingConstruction.equipmentPositions(entity(), m)
                          .stream().anyMatch(p -> p.hex().equals(hex) && p.level() == editor.selectedFloor())).count();
                    String equipment = count + " items";
                    g.drawString(equipment, (float) (x - g.getFontMetrics().stringWidth(equipment) / 2.0), (float) (y + 12));
                }
                cells.put(hex, polygon);
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
