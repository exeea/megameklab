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
    private boolean absoluteCoordinates;

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
        configPane.addTab("Structure", structure);
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
        int crewSize = megamek.common.compute.Compute.getFullCrewSize(getEntity());
        getEntity().getCrew().setSize(crewSize);
        getEntity().getCrew().setCurrentSize(crewSize);
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
                selectLocation(selectedHex, getEntity().getInternalBuilding().getBuildingHeight()
                      - 1 - floorSelector.getSelectedIndex());
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
        hexes.forEach(hex -> hexSelector.addItem(hexLabel(hex)));
        hexSelector.setSelectedIndex(hexes.indexOf(selectedHex));
        floorSelector.removeAllItems();
        boolean bridge = getEntity().getBldgClass() == megamek.common.units.IBuilding.BRIDGE;
        floorSelector.setEnabled(!bridge);
        if (bridge) {
            floorSelector.addItem("Deck " + getEntity().getLevelLabel(getEntity().getDesign().bridgeDeck(selectedHex)));
        } else {
            for (int floor = height - 1; floor >= 0; floor--) {
                floorSelector.addItem(getEntity().getLevelLabel(floor));
            }
            floorSelector.setSelectedItem(getEntity().getLevelLabel(selectedFloor));
        }
        selecting = false;
    }

    CubeCoords selectedHex() {
        return selectedHex == null ? CubeCoords.ZERO : selectedHex;
    }

    boolean absoluteCoordinates() {
        return absoluteCoordinates;
    }

    void setAbsoluteCoordinates(boolean absolute) {
        absoluteCoordinates = absolute;
        refreshLocationSelector();
        structure.refresh();
    }

    String hexLabel(CubeCoords hex) {
        return absoluteCoordinates ? BuildingUtil.absoluteHexLabel(hex)
              : BuildingUtil.sheetGrid(getEntity().getInternalBuilding().getOriginalCoordsList()).label(hex);
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
