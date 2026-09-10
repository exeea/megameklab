/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.ui.building;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import megamek.common.board.CubeCoords;
import megamek.common.equipment.Mounted;
import megamek.common.interfaces.ITechManager;
import megamek.common.units.BuildingEntity;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.Entity;
import megameklab.ui.MegaMekLabMainUI;
import megameklab.ui.generalUnit.FluffTab;
import megameklab.ui.generalUnit.RecordSheetPreviewPanel;
import megameklab.ui.util.TabScrollPane;
import megameklab.util.BuildingUtil;

/** Construction editor for advanced buildings. */
public class BuildingMainUI extends MegaMekLabMainUI {
    private BuildingStructureTab structure;
    private BuildingEquipmentTab equipment;
    private BuildingTransportTab transport;
    private BuildingSystemsTab systems;
    private FluffTab fluff;
    private RecordSheetPreviewPanel preview;
    private JLabel status;
    private boolean refreshing;
    private JComboBox<String> hexSelector;
    private JComboBox<String> floorSelector;
    private JButton editLocationEquipment;
    private JLabel locationHint;
    private CubeCoords selectedHex;
    private int selectedFloor;
    private boolean selecting;

    public BuildingMainUI() {
        createNewUnit(Entity.ETYPE_BUILDING_ENTITY);
    }

    public BuildingMainUI(Entity entity, String filename) {
        setEntity(entity, filename);
    }

    @Override
    public BuildingEntity getEntity() {
        return (BuildingEntity) super.getEntity();
    }

    @Override
    protected FluffTab getFluffTab() {
        return fluff;
    }

    @Override
    public void reloadTabs() {
        configPane.removeAll();
        removeAll();
        structure = new BuildingStructureTab(this);
        equipment = new BuildingEquipmentTab(this);
        transport = new BuildingTransportTab(this);
        systems = new BuildingSystemsTab(this);
        fluff = new FluffTab(this);
        fluff.setRefreshedListener(this);
        preview = new RecordSheetPreviewPanel();
        preview.setFullAsyncMode(true);
        status = new JLabel();
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        configPane.addTab("Structure", new TabScrollPane(structure));
        configPane.addTab("Equipment", equipment);
        configPane.addTab("Transport & Quarters", new TabScrollPane(transport));
        configPane.addTab("Construction & Services", systems);
        configPane.addTab("Fluff", new TabScrollPane(fluff));
        configPane.addTab("Record Sheet", preview);
        add(createLocationSelector(), BorderLayout.NORTH);
        add(configPane, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        preview.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent event) {
                preview.setEntity(getEntity());
            }
        });
        refreshAll();
        revalidate();
    }

    @Override
    public void refreshAll() {
        super.refreshAll();
        if (structure == null || refreshing) {
            return;
        }
        refreshing = true;
        getEntity().getDesign().removeDeletedComponents(getEntity());
        refreshLocationSelector();
        structure.refresh();
        equipment.refresh();
        transport.refresh();
        systems.refresh();
        fluff.refresh();
        if (preview.isShowing()) {
            preview.setEntity(getEntity());
        }
        List<String> issues = BuildingUtil.constructionIssues(getEntity());
        status.setText("Installed: %.2f / %.2f tons    |    Power: %s    |    %s".formatted(
              BuildingUtil.equipmentWeight(getEntity()), getEntity().getWeight(),
              BuildingUtil.powerDescription(getEntity()), issues.isEmpty() ? "" : issues.getFirst()));
        status.setToolTipText("<html>" + String.join("<br>", issues) + "</html>");
        refreshing = false;
        refreshHeader();
    }

    private JPanel createLocationSelector() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Editing location"));
        hexSelector = new JComboBox<>();
        hexSelector.setName("Edit hex");
        floorSelector = new JComboBox<>();
        floorSelector.setName("Edit floor");
        panel.add(new JLabel("Hex:"));
        panel.add(hexSelector);
        panel.add(new JLabel("Floor:"));
        panel.add(floorSelector);
        editLocationEquipment = new JButton("Edit equipment here");
        editLocationEquipment.addActionListener(e -> showEquipment());
        panel.add(editLocationEquipment);
        locationHint = new JLabel();
        panel.add(locationHint);
        hexSelector.addActionListener(e -> {
            if (!selecting && hexSelector.getSelectedIndex() >= 0) {
                selectLocation(getEntity().getInternalBuilding().getOriginalCoordsList()
                      .get(hexSelector.getSelectedIndex()), selectedFloor);
            }
        });
        floorSelector.addActionListener(e -> {
            if (!selecting && floorSelector.getSelectedItem() != null) {
                String floor = floorSelector.getSelectedItem().toString();
                selectLocation(selectedHex, "G".equals(floor) ? 0 : Integer.parseInt(floor));
            }
        });
        return panel;
    }

    private void refreshLocationSelector() {
        if (hexSelector == null) {
            return;
        }
        selecting = true;
        boolean hasInterior = !BuildingConstruction.hasNoInterior(getEntity());
        editLocationEquipment.setEnabled(hasInterior);
        locationHint.setText(hasInterior ? "New equipment is installed at the selected hex and floor."
              : "This structure has no interior equipment space.");
        var hexes = getEntity().getInternalBuilding().getOriginalCoordsList();
        if (!hexes.contains(selectedHex)) {
            selectedHex = hexes.getFirst();
        }
        int height = getEntity().getInternalBuilding().getBuildingHeight();
        selectedFloor = Math.clamp(selectedFloor, 0, height - 1);
        hexSelector.removeAllItems();
        var grid = BuildingUtil.sheetGrid(hexes);
        hexes.forEach(hex -> hexSelector.addItem(grid.label(hex)));
        hexSelector.setSelectedIndex(hexes.indexOf(selectedHex));
        floorSelector.removeAllItems();
        boolean bridge = getEntity().getBldgClass() == megamek.common.units.IBuilding.BRIDGE;
        floorSelector.setEnabled(!bridge);
        if (bridge) {
            floorSelector.addItem("Deck " + BuildingUtil.levelLabel(getEntity().getDesign().bridgeDeck(selectedHex)));
        } else {
            for (int floor = height - 1; floor >= 0; floor--) {
                floorSelector.addItem(BuildingUtil.levelLabel(floor));
            }
            floorSelector.setSelectedItem(BuildingUtil.levelLabel(selectedFloor));
        }
        selecting = false;
    }

    CubeCoords selectedHex() {
        return selectedHex == null ? CubeCoords.ZERO : selectedHex;
    }

    int selectedFloor() {
        return selectedFloor;
    }

    int selectedLocation() {
        return getEntity().getInternalBuilding().getOriginalCoordsList().indexOf(selectedHex())
              * getEntity().getInternalBuilding().getBuildingHeight() + selectedFloor;
    }

    void selectLocation(CubeCoords hex, int floor) {
        selectedHex = hex;
        selectedFloor = floor;
        refreshLocationSelector();
        // Navigation is not a construction change: do not schedule an undo snapshot or dirty the unit.
        structure.refresh();
        equipment.refreshPlacement();
    }

    void showEquipment() {
        configPane.setSelectedComponent(equipment);
    }

    @Override
    public void refreshBuild() {
        scheduleRefresh();
    }

    @Override
    public void refreshEquipment() {
        scheduleRefresh();
    }

    @Override
    public void refreshEquipmentTable() {
        scheduleRefresh();
    }

    @Override
    public void refreshStatus() {
        if (!refreshing) {
            scheduleRefresh();
        }
    }

    @Override
    public void refreshStructure() {
        if (!refreshing) {
            scheduleRefresh();
        }
    }

    @Override
    public void refreshPreview() {
        if (!refreshing) {
            scheduleRefresh();
        }
    }

    @Override
    public void refreshSummary() {
        scheduleRefresh();
    }

    @Override
    public JDialog getFloatingEquipmentDatabase() {
        return null;
    }

    @Override
    public List<Mounted<?>> getUnallocatedMounted() {
        return getEntity().getEquipment().stream().filter(m -> m.getLocation() == Entity.LOC_NONE).toList();
    }

    @Override
    public void createNewUnit(long entityType, boolean primitive, boolean industrial, Entity oldUnit) {
        BuildingEntity building = BuildingUtil.newBuilding();
        if (oldUnit != null) {
            copyUnitBasics(building, oldUnit);
        }
        setEntity(building, "");
        forceDirtyUntilNextSave();
    }

    @Override
    public ITechManager getTechManager() {
        return structure.getTechManager();
    }
}
