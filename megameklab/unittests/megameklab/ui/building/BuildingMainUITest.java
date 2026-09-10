/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.ui.building;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JSpinner;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import megamek.common.board.CubeCoords;
import megamek.common.enums.BuildingType;
import megamek.common.equipment.EquipmentType;
import megamek.common.units.IBuilding;
import megamek.common.units.BuildingDesign;
import megameklab.testing.util.InitializeTypes;
import megameklab.ui.dialog.UiLoader;
import megameklab.ui.util.EquipmentTableModel;
import megameklab.util.BuildingUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(InitializeTypes.class)
class BuildingMainUITest {
    @Test
    void classificationControlsIncludeCastleBrianWallsAndBridgesInTheSameEditor() throws Exception {
        var reference = new AtomicReference<BuildingMainUI>();
        SwingUtilities.invokeAndWait(() -> {
            var editor = new BuildingMainUI(BuildingUtil.newBuilding(), "classifications.blk");
            reference.set(editor);
            editor.onActivated();
            var classes = (JComboBox<?>) find(editor, "Classification");
            assertEquals(9, classes.getItemCount());
            classes.setSelectedItem("Castles Brian");
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertEquals(IBuilding.CASTLE_BRIAN, editor.getEntity().getBldgClass());
            assertEquals(BuildingType.HEAVY, editor.getEntity().getBuildingType());
            assertFalse(((JCheckBox) find(editor, "Environmental sealing")).isEnabled());
            ((JCheckBox) find(editor, "Open-space construction")).doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertTrue(editor.getEntity().getDesign().isOpenSpace());
            render(editor, "castle-brian-editor");
            ((JCheckBox) find(editor, "Open-space construction")).doClick();
            ((JComboBox<?>) find(editor, "Classification")).setSelectedItem("Wall");
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            ((JCheckBox) find(editor, "Structure side 1")).doClick();
            assertEquals(3, editor.getEntity().getDesign().wallSides(CubeCoords.ZERO));
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            render(editor, "wall-editor");
            editor.undo();
            assertEquals(1, editor.getEntity().getDesign().wallSides(CubeCoords.ZERO));
            ((JComboBox<?>) find(editor, "Classification")).setSelectedItem("Bridge");
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertFalse(((JSpinner) find(editor, "Levels (ground = G)")).isEnabled());
            ((JSpinner) find(editor, "Bridge start elevation")).setValue(4);
            assertEquals(4, editor.getEntity().getDesign().bridgeDeck(CubeCoords.ZERO));
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> render(reference.get(), "bridge-editor"));
    }

    @ParameterizedTest(name = "First hex addition in direction {0} stays where clicked")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
    void addingTheFirstNeighborKeepsTheOriginalHexAndClickedHexInPlace(int direction) throws Exception {
        var building = BuildingUtil.newBuilding();
        var laser = building.addEquipment(EquipmentType.get("ISMediumLaser"), 0);
        SwingUtilities.invokeAndWait(() -> {
            var editor = new BuildingMainUI(building, "first-hex.blk");
            editor.onActivated();
            var footprint = find(editor, "Building footprint");
            assertNotNull(footprint);
            footprint.setSize(900, 660);
            var selectedColor = new Color(180, 210, 240);
            var occupiedColor = new Color(230, 230, 230);
            Rectangle original = colorBounds(paint(footprint), selectedColor);
            double radius = original.getWidth() / 2 + 1;
            CubeCoords neighbor = CubeCoords.ZERO.toOffset().translated(direction).toCube();
            Point clicked = new Point((int) Math.round(original.getCenterX() + 1.5 * radius * neighbor.q()),
                  (int) Math.round(original.getCenterY() + Math.sqrt(3) * radius * (neighbor.r() + neighbor.q() / 2)));
            footprint.dispatchEvent(new MouseEvent(footprint, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                  clicked.x, clicked.y, 1, false, MouseEvent.BUTTON1));

            assertEquals(List.of(CubeCoords.ZERO, neighbor), building.getInternalBuilding().getCoordsList());
            assertEquals(neighbor, editor.selectedHex());
            assertEquals(0, laser.getLocation(), "The original hex retains its equipment");
            var after = paint(footprint);
            Rectangle added = colorBounds(after, selectedColor);
            assertEquals(occupiedColor.getRGB(), after.getRGB((int) original.getCenterX(),
                  (int) (original.getCenterY() - original.getHeight() / 4)), "The original hex stays occupied at its old position");
            assertEquals(clicked.x, added.getCenterX(), 2, "The clicked + must become the selected hex at the same position");
            assertEquals(clicked.y, added.getCenterY(), 2, "The clicked + must become the selected hex at the same position");
            if (direction == 1) {
                render(editor, "building-first-hex-fixed");
                footprint.setSize(900, 660);
                paint(footprint);
            }
            // Measure the rendered neighbor spacing; estimating radius from fill pixels loses the border width.
            Point next = new Point((int) Math.round(2 * added.getCenterX() - original.getCenterX()),
                  (int) Math.round(2 * added.getCenterY() - original.getCenterY()));
            footprint.dispatchEvent(new MouseEvent(footprint, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                  next.x, next.y, 1, false, MouseEvent.BUTTON1));
            assertEquals(List.of(CubeCoords.ZERO, neighbor, neighbor.add(neighbor)), building.getInternalBuilding().getCoordsList());
            Rectangle nextAdded = colorBounds(paint(footprint), selectedColor);
            assertEquals(next.x, nextAdded.getCenterX(), 2, "Subsequent additions also stay where clicked");
            assertEquals(next.y, nextAdded.getCenterY(), 2, "Subsequent additions also stay where clicked");
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private BufferedImage paint(Component component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    private Rectangle colorBounds(BufferedImage image, Color color) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "The rendered hex must be visible");
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Test
    void floorNavigationControlsBothDatabaseInstallationAndTheVisibleLoadout() throws Exception {
        var building = BuildingUtil.newBuilding();
        var east = new CubeCoords(1, 0, -1);
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 0, List.of(CubeCoords.ZERO, east));
        var reference = new AtomicReference<BuildingMainUI>();
        SwingUtilities.invokeAndWait(() -> {
            var editor = new BuildingMainUI(building, "floor-test.blk");
            reference.set(editor);
            editor.onActivated();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            boolean wasDirty = editor.isDirty();
            var floor = (JComboBox<?>) find(editor, "Edit floor");
            assertEquals("2", floor.getItemAt(0));
            assertEquals("G", floor.getItemAt(2));
            ((JComboBox<?>) find(editor, "Edit hex")).setSelectedIndex(1);
            floor.setSelectedItem("2");
            assertEquals(5, editor.selectedLocation());
            assertEquals(wasDirty, editor.isDirty(), "Navigating hexes/floors must not change the unit");
            JTable database = databaseTable(editor);
            assertNotNull(database);
            int laserRow = -1;
            for (int row = 0; row < database.getRowCount(); row++) {
                if (((EquipmentTableModel) database.getModel()).getType(database.convertRowIndexToModel(row))
                      == EquipmentType.get("ISMediumLaser")) {
                    laserRow = row;
                    break;
                }
            }
            assertTrue(laserRow >= 0, "The real equipment database should contain a Medium Laser");
            database.setRowSelectionInterval(laserRow, laserRow);
            JButton add = button(editor, "<< Add");
            assertNotNull(add);
            assertTrue(add.isEnabled());
            add.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertEquals(5, editor.getEntity().getEquipment().getFirst().getLocation());
            assertEquals(1, ((JTable) find(editor, "Building equipment")).getRowCount());
            ((JComboBox<?>) find(editor, "Edit floor")).setSelectedItem("1");
            assertEquals(0, ((JTable) find(editor, "Building equipment")).getRowCount());
            ((JComboBox<?>) find(editor, "Edit floor")).setSelectedItem("2");
            assertEquals(1, ((JTable) find(editor, "Building equipment")).getRowCount());
            ((JSpinner) find(editor, "Levels (ground = G)")).setValue(1);
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertEquals("G", ((JComboBox<?>) find(editor, "Edit floor")).getSelectedItem());
            assertTrue(editor.getEntity().getEquipment().isEmpty());
            editor.undo();
            assertEquals(3, editor.getEntity().getInternalBuilding().getBuildingHeight());
            assertEquals(5, editor.getEntity().getEquipment().getFirst().getLocation());
        });
    }

    @Test
    void constructionOptionsAreUndoableNativeDataAndTheReportIsVisible() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 0,
              List.of(CubeCoords.ZERO, new CubeCoords(1, 0, -1)));
        var reference = new AtomicReference<BuildingMainUI>();
        SwingUtilities.invokeAndWait(() -> {
            var editor = new BuildingMainUI(building, "options.blk");
            reference.set(editor);
            editor.onActivated();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            ((JCheckBox) find(editor, "Environmental sealing")).doClick();
            ((JCheckBox) find(editor, "Heavy-metal superstructure")).doClick();
            ((JComboBox<?>) find(editor, "Building ceiling")).setSelectedItem("High");
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var editor = reference.get();
            assertEquals(360, editor.getEntity().getWeight());
            assertTrue(editor.getEntity().getDesign().hasEnvironmentalSealing());
            assertEquals(BuildingDesign.Ceiling.HIGH, editor.getEntity().getDesign().getCeiling());
            var report = (javax.swing.JTextArea) find(editor, "Building construction report");
            assertTrue(report.getText().contains("OPERATING CREW"));
            assertTrue(report.getText().contains("Construction checks pass"));
            var tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class,
                  (JTable) find(editor, "Building equipment"));
            tabs.setSelectedIndex(3);
            render(editor, "building-construction-services");
            editor.undo();
            assertEquals(BuildingDesign.Ceiling.STANDARD, editor.getEntity().getDesign().getCeiling());
            editor.redo();
            assertEquals(BuildingDesign.Ceiling.HIGH, editor.getEntity().getDesign().getCeiling());
        });
    }

    private JTable databaseTable(Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof JTable table && table.getColumnCount() > 0 && "Name".equals(table.getColumnName(0))) {
                return table;
            }
            if (child instanceof Container container) {
                var result = databaseTable(container);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private JButton button(Container parent, String text) {
        for (var child : parent.getComponents()) {
            if (child instanceof JButton button && button.getText() != null && text.equals(button.getText().trim())) {
                return button;
            }
            if (child instanceof Container container) {
                var result = button(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Test
    void equipmentAndQuartersControlsEditNativeBuildingFields() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 0,
              List.of(CubeCoords.ZERO, new CubeCoords(1, 0, -1)));
        var laser = building.addEquipment(EquipmentType.get("ISMediumLaser"), 0);
        var reference = new AtomicReference<BuildingMainUI>();
        SwingUtilities.invokeAndWait(() -> {
            var editor = new BuildingMainUI(building, "");
            reference.set(editor);
            editor.onActivated();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var table = (JTable) find(reference.get(), "Building equipment");
            table.getModel().setValueAt(BuildingUtil.locationLabel(building, 3), 0, 1);
            table.getModel().setValueAt("NE", 0, 2);
            table.getModel().setValueAt("Roof turret", 0, 3);
            var quarters = (JSpinner) find(reference.get(), "First class quarters");
            quarters.setValue(2);
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(3, laser.getLocation());
            assertEquals(1, laser.getFacing());
            assertTrue(laser.isSponsonTurretMounted());
            assertEquals(20, building.getTransportBays().getFirst().getWeight());
            assertTrue(reference.get().isDirty());
            building.getDesign().getBaySpace().put(building.getTransportBays().getFirst(), List.of(
                  new BuildingDesign.Space(new BuildingDesign.Position(new CubeCoords(1, 0, -1), 1), 20)));
            ((JSpinner) find(reference.get(), "First class quarters")).setValue(3);
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var bay = building.getTransportBays().getFirst();
            assertEquals(30, bay.getWeight());
            assertEquals(List.of(new BuildingDesign.Space(new BuildingDesign.Position(new CubeCoords(1, 0, -1), 1), 30)),
                  building.getDesign().getBaySpace().get(bay), "Resizing quarters preserves their authored placement");
            var table = (JTable) find(reference.get(), "Building equipment");
            var tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, table);
            tabs.setSelectedIndex(1);
            render(reference.get(), "building-equipment");
            tabs.setSelectedIndex(2);
            render(reference.get(), "building-transport");
        });
    }

    @Test
    void openedBuildingSupportsEditingUndoRedoAndKeepsItsFilename() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 32, List.of(CubeCoords.ZERO));
        building.addEquipment(EquipmentType.get("ISMediumLaser"), 1);
        var reference = new AtomicReference<BuildingMainUI>();
        SwingUtilities.invokeAndWait(() -> {
            var editor = assertInstanceOf(BuildingMainUI.class, UiLoader.getUIWithoutLinkedAsset(building, "tower.blk"));
            reference.set(editor);
            editor.onActivated();
        });
        SwingUtilities.invokeAndWait(() -> { });
        var editor = reference.get();
        SwingUtilities.invokeAndWait(() -> {
            JSpinner levels = (JSpinner) find(editor, "Levels (ground = G)");
            assertNotNull(levels);
            levels.setValue(1);
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1, editor.getEntity().locations());
            assertTrue(editor.getEntity().getEquipment().isEmpty());
            assertTrue(editor.isDirty());
            assertTrue(editor.hasUndo());
            editor.undo();
            assertEquals(2, editor.getEntity().locations());
            assertEquals(1, editor.getEntity().getEquipment().size());
            assertEquals("tower.blk", editor.getFileName());
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            editor.redo();
            assertEquals(1, editor.getEntity().locations());
            assertTrue(editor.getEntity().getEquipment().isEmpty());
            render(editor, "building-editor");
        });
    }

    private void render(BuildingMainUI editor, String name) {
        editor.addNotify();
        editor.setSize(1300, 900);
        layout(editor);
        BufferedImage image = new BufferedImage(1300, 900, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        editor.printAll(graphics);
        graphics.dispose();
        try {
            Path output = Path.of("build", "building-review", name + ".png");
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        } catch (Exception exception) {
            fail(exception);
        } finally {
            editor.removeNotify();
        }
    }

    private Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                Component result = find(container, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) {
                layout(container);
            }
        }
    }
}
