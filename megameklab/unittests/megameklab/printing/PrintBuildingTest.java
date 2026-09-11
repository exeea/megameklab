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

package megameklab.printing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import megamek.common.board.CubeCoords;
import megamek.common.bays.FirstClassQuartersCargoBay;
import megamek.common.enums.BuildingType;
import megamek.common.equipment.EquipmentType;
import megamek.common.equipment.MiscType;
import megamek.common.loaders.BLKFile;
import megamek.common.loaders.BLKStructureFile;
import megamek.common.units.BuildingEntity;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.IBuilding;
import megamek.common.util.BuildingBlock;
import megameklab.testing.util.InitializeTypes;
import megameklab.util.BuildingUtil;
import megameklab.util.UnitPrintManager;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGPolygonElement;
import org.w3c.dom.svg.SVGGElement;
import org.w3c.dom.svg.SVGRectElement;

@ExtendWith(InitializeTypes.class)
class PrintBuildingTest {
    @Test
    void fitsTheStandardGridBeforeMakingWideOrTallFootprintsDenserWithinTheMapArea() throws Exception {
        var tight = java.util.stream.IntStream.range(0, 14)
              .mapToObj(i -> new CubeCoords(i / 7, i % 7, -i / 7 - i % 7)).toList();
        var wide = java.util.stream.IntStream.range(0, 13)
              .mapToObj(q -> new CubeCoords(q, -q / 2, -q + q / 2)).toList();
        var tall = java.util.stream.IntStream.range(0, 12).mapToObj(r -> new CubeCoords(0, r, -r)).toList();
        var both = new ArrayList<>(wide.subList(0, 11));
        both.addAll(tall.subList(1, 9));
        var footprints = List.of(tight, wide, tall, both);
        int[] cells = { 9 * 7, 13 * 7, 9 * 12, 11 * 9 };
        for (var paper : List.of(PaperSize.US_LETTER, PaperSize.ISO_A4)) {
            double standardHexWidth = 0;
            for (int index = 0; index < footprints.size(); index++) {
                var hexes = footprints.get(index);
                var building = BuildingUtil.newBuilding();
                BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.CASTLE_BRIAN, 2, 40, 0, hexes);
                var mount = building.addEquipment(EquipmentType.get("ISMediumLaser"), (hexes.size() - 1) * 2);
                var sheet = sheet(building, paper);
                assertTrue(sheet.createDocument(0, pageFormat(paper), true));
                assertEquals(cells[index] * 2, elements(sheet, "polygon", "building-hex").size());
                assertEquals(hexes.size() * 2, elements(sheet, "polygon", "occupied").size());
                var region = (SVGRectElement) sheet.getSVGDocument().getElementById("structureMap");
                for (var element : elements(sheet, "g", "building-map-layer")) {
                    var layer = (SVGGElement) element;
                    var translation = layer.getTransform().getBaseVal().consolidate().getMatrix();
                    var polygons = layer.getElementsByTagName("polygon");
                    for (int i = 0; i < polygons.getLength(); i++) {
                        var points = ((SVGPolygonElement) polygons.item(i)).getPoints();
                        for (int j = 0; j < points.getNumberOfItems(); j++) {
                            double x = translation.getE() + points.getItem(j).getX();
                            double y = translation.getF() + points.getItem(j).getY();
                            assertTrue(x >= region.getX().getBaseVal().getValue() - .01);
                            assertTrue(x <= region.getX().getBaseVal().getValue() + region.getWidth().getBaseVal().getValue() + .01);
                            assertTrue(y >= region.getY().getBaseVal().getValue() - .01);
                            assertTrue(y <= region.getY().getBaseVal().getValue() + region.getHeight().getBaseVal().getValue() + .01);
                        }
                    }
                }
                var points = ((SVGPolygonElement) elements(sheet, "polygon", "building-hex").getFirst()).getPoints();
                double hexWidth = points.getItem(3).getX() - points.getItem(0).getX();
                if (index == 0) {
                    standardHexWidth = hexWidth;
                    assertTrue(elements(sheet, "g", "building-inventory-entry").getFirst().getTextContent().contains("0607/G"));
                } else {
                    assertTrue(hexWidth < standardHexWidth, "A larger grid uses smaller hexes in the same map area");
                }
                assertEquals(hexes, building.getInternalBuilding().getOriginalCoordsList());
                assertEquals((hexes.size() - 1) * 2, mount.getLocation());
                if (paper == PaperSize.US_LETTER && (index == 0 || index == 3)) {
                    render(sheet, "building-fit-grid-" + index);
                }
            }
        }
    }

    @Test
    void printsWallSidesAndOnlyTheActualBridgeDeckElevations() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.MEDIUM, IBuilding.WALL, 2, 40, 32, List.of(CubeCoords.ZERO));
        building.getDesign().getWallSides().put(CubeCoords.ZERO, 3);
        var wall = sheet(building, PaperSize.US_LETTER);
        assertTrue(wall.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertEquals(2, elements(wall, "g", "building-map-layer").size());
        assertTrue(wall.getSVGDocument().getDocumentElement().getTextContent().contains("0504/N"));
        assertTrue(wall.getSVGDocument().getDocumentElement().getTextContent().contains("0504/NE"));
        int sides = 0;
        var lines = wall.getSVGDocument().getElementsByTagName("line");
        for (int i = 0; i < lines.getLength(); i++) {
            if (((Element) lines.item(i)).hasAttribute("data-building-side")) {
                sides++;
            }
        }
        assertEquals(4, sides);
        render(wall, "wall-hexside-classification");
        var hexes = java.util.stream.IntStream.rangeClosed(0, 8).mapToObj(q -> new CubeCoords(q, 0, -q)).toList();
        BuildingUtil.configure(building, BuildingType.RAIL, IBuilding.BRIDGE, 1, 650, 0, hexes);
        BuildingConstruction.setBridgeSlope(building, 5, 7);
        var bridge = sheet(building, PaperSize.US_LETTER);
        assertTrue(bridge.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertEquals(List.of("7", "6", "5"), elements(bridge, "g", "building-map-layer").stream()
              .map(layer -> layer.getAttribute("data-building-floor")).toList());
        assertEquals(9, elements(bridge, "polygon", "occupied").size());
        render(bridge, "bridge-deck-classification");
    }

    @Test
    void largeCastleBrianKeepsEveryLevelAndProtectionRowReadableAcrossPages() throws Exception {
        var building = BuildingUtil.newBuilding();
        var hexes = java.util.stream.IntStream.range(0, 70).mapToObj(index ->
              new CubeCoords(index % 10, index / 10, -(index % 10) - index / 10)).toList();
        BuildingUtil.configure(building, BuildingType.HARDENED, IBuilding.CASTLE_BRIAN, 15, 150, 100, hexes);
        var sheet = sheet(building, PaperSize.US_LETTER);
        assertEquals(3, sheet.getPageCount());
        var levels = new ArrayList<String>();
        for (int page = 0; page < sheet.getPageCount(); page++) {
            assertTrue(sheet.createDocument(page, pageFormat(PaperSize.US_LETTER), true));
            var layers = elements(sheet, "g", "building-map-layer");
            assertTrue(layers.size() <= 6);
            levels.addAll(layers.stream().map(layer -> layer.getAttribute("data-building-floor")).toList());
            render(sheet, "castle-brian-classification-" + page);
        }
        assertEquals(java.util.stream.IntStream.range(0, 15).mapToObj(index -> Integer.toString(14 - index)).toList(), levels);
    }

    private PrintBuilding sheet(megamek.common.units.AbstractBuildingEntity building, PaperSize size) {
        var options = new RecordSheetOptions();
        options.setPaperSize(size);
        options.setReferenceCharts(false);
        return new PrintBuilding(building, 0, options) {
            @Override
            String getSVGDirectoryName(boolean testDirectory) {
                // The actual mm-data assets are the source of truth for both test and release rendering.
                return "../../mm-data/data/images/recordsheets/" + size.dirName;
            }
        };
    }

    @Test
    void mobileSheetPreservesSpeedAndIndividualHexHeights() throws Exception {
        var mobile = BuildingUtil.newMobileStructure();
        var hexes = List.copyOf(mobile.getInternalBuilding().getOriginalCoordsList());
        BuildingUtil.configure(mobile, BuildingType.MEDIUM, IBuilding.FORTRESS, 3, 40, 0, hexes);
        BuildingUtil.setHexHeight(mobile, hexes.get(1), 1);
        mobile.setMaximumMP(1.25);
        var sheet = sheet(mobile, PaperSize.ISO_A4);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.ISO_A4), true));
        assertEquals(3, elements(sheet, "g", "building-map-layer").size());
        assertEquals(4, elements(sheet, "polygon", "occupied").size());
        assertTrue(sheet.getSVGDocument().getDocumentElement().getTextContent().contains("1.25"));
        render(sheet, "mobile-variable-height");
    }

    @Test
    void compressesBeforeOverflowAndKeepsAllEquipmentOnContinuationPages() throws Exception {
        var building = BuildingUtil.newBuilding();
        for (int index = 0; index < 30; index++) {
            building.addEquipment(printableItem(index), 0);
        }
        var compact = sheet(building, PaperSize.US_LETTER);
        assertEquals(1, compact.getPageCount());
        assertTrue(compact.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        render(compact, "building-dense-inventory");
        for (int index = 30; index < 110; index++) {
            building.addEquipment(printableItem(index), 0);
        }
        var overflow = sheet(building, PaperSize.US_LETTER);
        assertTrue(overflow.getPageCount() > 1);
        var seen = new ArrayList<String>();
        for (int page = 0; page < overflow.getPageCount(); page++) {
            assertTrue(overflow.createDocument(page, pageFormat(PaperSize.US_LETTER), true));
            for (var entry : elements(overflow, "g", "building-inventory-entry")) {
                assertEquals(0, entry.getElementsByTagName("line").getLength(), "No ruled inventory placeholders");
                if (Integer.parseInt(entry.getAttribute("data-location")) >= 0) {
                    seen.add(entry.getAttribute("data-equipment-id"));
                }
            }
            render(overflow, "building-inventory-overflow-" + page);
        }
        assertEquals(110, seen.size());
        assertEquals(110, new java.util.HashSet<>(seen).size());
    }

    private MiscType printableItem(int index) {
        return new MiscType() {
            {
                name = "Equipment " + index;
                setInternalName(name);
                tonnage = 1;
                criticalSlots = 1;
            }
        };
    }

    @Test
    void projectedDoorsStayCenteredAndFeatureColorsRemainAlongsideSymbols() throws Exception {
        var building = BuildingUtil.newBuilding();
        for (int side = 0; side < 6; side++) {
            building.getDesign().getDoors().add(new BuildingDesign.Door(new BuildingDesign.Position(CubeCoords.ZERO, 0), side, 1));
        }
        var sheet = sheet(building, PaperSize.US_LETTER);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        var hex = (SVGPolygonElement) elements(sheet, "polygon", "occupied").getFirst();
        var layer = elements(sheet, "g", "building-map-layer").getFirst();
        var polygons = layer.getElementsByTagName("polygon");
        int doors = 0;
        for (int index = 0; index < polygons.getLength(); index++) {
            var door = (SVGPolygonElement) polygons.item(index);
            if (!door.getAttribute("data-building-symbol").equals("door")) {
                continue;
            }
            doors++;
            int side = Integer.parseInt(door.getAttribute("data-building-facing"));
            var a = hex.getPoints().getItem((side + 1) % 6);
            var b = hex.getPoints().getItem((side + 2) % 6);
            var left = door.getPoints().getItem(1);
            var right = door.getPoints().getItem(2);
            var tip = door.getPoints().getItem(0);
            assertEquals((a.getX() + b.getX()) / 2, (tip.getX() + left.getX() + right.getX()) / 3, .01);
            assertEquals((a.getY() + b.getY()) / 2, (tip.getY() + left.getY() + right.getY()) / 3, .01);
            assertEquals(0, (right.getX() - left.getX()) * (b.getY() - a.getY())
                  - (right.getY() - left.getY()) * (b.getX() - a.getX()), .01);
        }
        assertEquals(6, doors);
        var keyPolygons = elements(sheet, "g", "building-map-key").getFirst().getElementsByTagName("polygon");
        assertEquals(1, keyPolygons.getLength(), "The Door legend has no hex swatch");
        assertEquals(3, ((SVGPolygonElement) keyPolygons.item(0)).getPoints().getNumberOfItems());
        render(sheet, "building-six-door-directions");
        building.getDesign().getDoors().clear();
        building.getDesign().getElevators().add(new BuildingDesign.Elevator(CubeCoords.ZERO, 20, Map.of(0, 4, 1, 4)));
        for (var mode : RecordSheetOptions.ColorMode.values()) {
            var colored = sheet(building, PaperSize.US_LETTER);
            colored.options.setColor(mode);
            assertTrue(colored.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
            assertTrue(elements(colored, "g", "building-map-layer").getFirst().getTextContent().contains("E0504"));
            var key = elements(colored, "g", "building-map-key").getFirst();
            assertTrue(key.getTextContent().contains("EElevator"));
            assertEquals("#efcb8d", elements(colored, "polygon", "occupied").getFirst().getAttribute("fill"));
            assertEquals("#efcb8d", ((Element) key.getElementsByTagName("polygon").item(0)).getAttribute("fill"));
            if (mode == RecordSheetOptions.ColorMode.LOGO_ONLY) {
                render(colored, "building-feature-colors");
            }
        }
    }

    private PageFormat pageFormat(PaperSize size) {
        Paper paper = new Paper();
        paper.setSize(size.pxWidth, size.pxHeight);
        paper.setImageableArea(18, 18, size.pxWidth - 36, size.pxHeight - 36);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        return format;
    }

    private List<Element> elements(PrintBuilding sheet, String tag, String className) {
        List<Element> result = new ArrayList<>();
        var nodes = sheet.getSVGDocument().getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (Element) nodes.item(i);
            if (List.of(element.getAttribute("class").split(" ")).contains(className)) {
                result.add(element);
            }
        }
        return result;
    }

    @Test
    void singleFloorShowsWholeGridButOnlyOccupiedHexIsLabeled() throws Exception {
        var building = BuildingUtil.newBuilding();
        building.setChassis("Control Tower");
        var laser = EquipmentType.get("ISMediumLaser");
        building.addEquipment(laser, 0);
        building.addEquipment(laser, 0);
        var sheet = sheet(building, PaperSize.US_LETTER);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertEquals(1, elements(sheet, "g", "building-map-layer").size());
        assertEquals(63, elements(sheet, "polygon", "building-hex").size());
        assertEquals(1, elements(sheet, "polygon", "occupied").size());
        assertEquals("0504", elements(sheet, "polygon", "occupied").getFirst().getAttribute("data-building-hex"));
        var layer = elements(sheet, "g", "building-map-layer").getFirst();
        assertEquals("0504Level: G", layer.getTextContent());
        assertTrue(layer.getAttribute("transform").contains("127.0"), "Top aligned with 24 points of spare header space");
        assertEquals(1, sheet.inventoryGroups().size());
        assertEquals(2, sheet.inventoryGroups().getFirst().size());
        assertTrue(elements(sheet, "g", "building-inventory-entry").getFirst().getTextContent().contains("0504/G"));
        render(sheet, "building-letter-single");
    }

    @Test
    void sheetsPrintGroundRelativeFloorsInDescendingOrderAndKeepNativeLocations() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 4, 80, 0, List.of(CubeCoords.ZERO));
        building.getDesign().setBaseLevel(-2);
        building.addEquipment(EquipmentType.get("ISMediumLaser"), 1);
        var sheet = sheet(building, PaperSize.ISO_A4);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.ISO_A4), true));
        var layers = elements(sheet, "g", "building-map-layer");
        assertEquals(List.of("3", "2", "1", "0"), layers.stream().map(e -> e.getAttribute("data-building-floor")).toList());
        assertEquals(List.of("1", "G", "-1", "-2"), layers.stream()
              .map(e -> e.getTextContent().substring(e.getTextContent().lastIndexOf("Level: ") + 7)).toList());
        assertTrue(elements(sheet, "g", "building-inventory-entry").stream()
              .anyMatch(row -> row.getTextContent().contains("0504/-1") && row.getAttribute("data-location").equals("1")));
        render(sheet, "building-ground-reference");
        building.getDesign().setBaseLevel(null);
        building.getDesign().setSite(BuildingDesign.Site.UNDERGROUND);
        building.getDesign().setDepth(1);
        sheet = sheet(building, PaperSize.ISO_A4);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.ISO_A4), true));
        assertTrue(elements(sheet, "g", "building-map-layer").getLast().getTextContent().contains("Level: -5"));
    }

    @Test
    void rulesAtriumExampleRetainsItsEmptyCenterAndUniformHeightThroughBlkAndPrinting() throws Exception {
        // TO:AR p. 128: a Medium Standard mall, CF 40, six hexes around an open atrium, three levels tall.
        var building = BuildingUtil.newBuilding();
        building.setChassis("Atrium Mall");
        BuildingUtil.configure(building, BuildingType.MEDIUM, IBuilding.STANDARD, 3, 40, 0,
              List.of(new CubeCoords(0, -1, 1), new CubeCoords(1, -1, 0), new CubeCoords(1, 0, -1),
                    new CubeCoords(0, 1, -1), new CubeCoords(-1, 1, 0), new CubeCoords(-1, 0, 1)));
        var loaded = (BuildingEntity) new BLKStructureFile(BLKFile.getBlock(building)).getEntity();
        assertEquals(6, loaded.getInternalBuilding().getCoordsList().size());
        assertEquals(18, loaded.locations());
        assertEquals(720, loaded.getWeight(), "Six hexes at 120 tons per hex, without adding an atrium hex");
        assertTrue(loaded.getEquipment().isEmpty());
        for (var hex : loaded.getInternalBuilding().getCoordsList()) {
            assertEquals(3, loaded.getInternalBuilding().getHeight(hex));
        }
        var sheet = sheet(loaded, PaperSize.US_LETTER);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        var layers = elements(sheet, "g", "building-map-layer");
        assertEquals(List.of("2", "1", "0"), layers.stream()
              .map(layer -> layer.getAttribute("data-building-floor")).toList());
        for (var layer : layers) {
            var polygons = layer.getElementsByTagName("polygon");
            assertEquals(63, polygons.getLength());
            List<String> occupied = new ArrayList<>();
            for (int i = 0; i < polygons.getLength(); i++) {
                String label = ((Element) polygons.item(i)).getAttribute("data-building-hex");
                if (!label.isEmpty()) {
                    occupied.add(label);
                }
            }
            assertEquals(List.of("0403", "0404", "0503", "0505", "0603", "0604"), occupied);
            assertFalse(layer.getTextContent().contains("0504"), "The central atrium is empty on every floor");
        }
        render(sheet, "building-atrium-rules-example");
    }

    @Test
    void layersAndInventoryUseExactLevelsAndContinueOnNextPage() throws Exception {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 8, 80, 32,
              List.of(CubeCoords.ZERO, new CubeCoords(1, 0, -1), new CubeCoords(0, 1, -1)));
        for (int loc = 0; loc < building.locations(); loc++) {
            building.addEquipment(EquipmentType.get("ISMediumLaser"), loc);
        }
        var sheet = sheet(building, PaperSize.ISO_A4);
        assertEquals(2, sheet.getPageCount());
        assertEquals(24, sheet.inventoryGroups().size());
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.ISO_A4), true));
        assertEquals(6, elements(sheet, "g", "building-map-layer").size());
        assertEquals(List.of("7", "6", "5", "4", "3", "2"), elements(sheet, "g", "building-map-layer").stream()
              .map(e -> e.getAttribute("data-building-floor")).toList());
        assertEquals(24, elements(sheet, "g", "building-inventory-entry").stream()
              .filter(row -> Integer.parseInt(row.getAttribute("data-location")) >= 0).count());
        render(sheet, "building-a4-six-layers");
        assertTrue(sheet.createDocument(1, pageFormat(PaperSize.ISO_A4), true));
        assertEquals(List.of("1", "0"), elements(sheet, "g", "building-map-layer").stream()
              .map(e -> e.getAttribute("data-building-floor")).toList());
        assertEquals(0, elements(sheet, "g", "building-inventory-entry").size());
        render(sheet, "building-a4-continuation");
    }

    @Test
    void printQueueAccountsForAllBuildingPages() {
        var building = BuildingUtil.newBuilding();
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 8, 80, 0, List.of(CubeCoords.ZERO));
        var sheets = UnitPrintManager.createSheets(List.of(building, BuildingUtil.newBuilding()), true, new RecordSheetOptions());
        assertEquals(2, sheets.size());
        assertInstanceOf(PrintBuilding.class, sheets.getFirst());
        assertEquals(2, sheets.getFirst().getPageCount());
        assertEquals(1, sheets.getLast().getPageCount());
    }

    @Test
    void capitalWeaponsPrintTheirUpwardArcWithoutAnInventedWallFacing() throws Exception {
        var building = BuildingUtil.newBuilding();
        building.addEquipment(EquipmentType.get("Naval Autocannon (NAC/10)"), 0).setFacing(-1);
        var sheet = sheet(building, PaperSize.US_LETTER);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertTrue(elements(sheet, "g", "building-inventory-entry").stream()
              .anyMatch(row -> row.getTextContent().contains("Upward (capital)")));
    }

    @Test
    void ammoQuantitiesQuartersAndPdfUseTheNativePrintPipeline() throws Exception {
        var building = BuildingUtil.newBuilding();
        building.addTransporter(new FirstClassQuartersCargoBay(2));
        var ammo = EquipmentType.get("IS Ammo AC/5");
        building.addEquipment(ammo, 0).setOriginalShots(17);
        building.addEquipment(ammo, 0).setOriginalShots(10);
        var sheet = sheet(building, PaperSize.US_LETTER);
        assertTrue(sheet.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        var rows = elements(sheet, "g", "building-inventory-entry");
        assertEquals(3, rows.size());
        assertTrue(rows.getFirst().getTextContent().contains("(27)"));
        assertTrue(rows.get(1).getTextContent().contains("Quarters"));
        // The name may wrap around a separate location cell in SVG document order.
        assertTrue(rows.get(1).getTextContent().contains("(20"));
        assertTrue(rows.get(1).getTextContent().contains("t)"));
        Path output = Path.of("build", "building-review", "building-letter.pdf");
        Files.createDirectories(output.getParent());
        Level fontLogLevel = LogManager.getLogger("org.apache.fop").getLevel();
        Configurator.setLevel("org.apache.fop", Level.WARN);
        try {
            try (var pdf = sheet.exportPDF(0, pageFormat(PaperSize.US_LETTER))) {
                assertNotNull(pdf);
                Files.copy(pdf, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Configurator.setLevel("org.apache.fop", fontLogLevel);
        }
        assertTrue(Files.size(output) > 1000);
    }

    @Test
    void defaultBuildingAmmoPrintsStartingLoadAndCurrentRoundsSeparately() throws Exception {
        var building = BuildingUtil.newBuilding();
        building.addEquipment(EquipmentType.get("IS Ammo AC/5"), 0);
        String[] nativeLines = java.util.Arrays.stream(BLKFile.getBlock(building).getAllDataAsString())
              .map(line -> line.replace(":Shots20#", "")).toArray(String[]::new);
        var loaded = (BuildingEntity) new BLKStructureFile(new BuildingBlock(nativeLines)).getEntity();
        loaded.getAmmo().getFirst().setShotsLeft(7);

        var clean = sheet(loaded, PaperSize.US_LETTER);
        clean.options.setDamage(false);
        assertTrue(clean.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertTrue(elements(clean, "g", "building-inventory-entry").getFirst().getTextContent().contains("(20)"));

        var current = sheet(loaded, PaperSize.US_LETTER);
        current.options.setDamage(true);
        assertTrue(current.createDocument(0, pageFormat(PaperSize.US_LETTER), true));
        assertTrue(elements(current, "g", "building-inventory-entry").getFirst().getTextContent().contains("(7)"));
    }

    @Test
    void constructionServicesAndWeaponPlacementRemainReadableAcrossInventoryPages() throws Exception {
        var building = BuildingUtil.newBuilding();
        var east = new CubeCoords(1, 0, -1);
        var west = new CubeCoords(-1, 0, 1);
        building.setChassis("Underground Control Center");
        BuildingUtil.configure(building, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 32,
              List.of(CubeCoords.ZERO, east, west));
        var design = building.getDesign();
        design.setEnvironmentalSealing(true);
        design.setHeavyMetal(true);
        design.setCeiling(BuildingDesign.Ceiling.LOW);
        design.setSite(BuildingDesign.Site.UNDERGROUND);
        design.setRoofClearance(true);
        design.setDepth(2);
        var generator = building.addEquipment(EquipmentType.get("FUSION PowerGenerator"), 4);
        generator.setSize(10);
        design.getEquipmentSpace().put(generator,
              List.of(new BuildingDesign.Position(east, 1), new BuildingDesign.Position(west, 1)));
        for (int i = 0; i < 3; i++) {
            var laser = building.addEquipment(EquipmentType.get("ISMediumLaser"), 3);
            laser.setFacing(2);
            if (i < 2) {
                design.getAutomatedWeapons().add(laser);
            }
        }
        var quarters = new FirstClassQuartersCargoBay(2);
        building.addTransporter(quarters);
        design.getBaySpace().put(quarters, List.of(new BuildingDesign.Space(new BuildingDesign.Position(west, 0), 20)));
        design.getDoors().add(new BuildingDesign.Door(new BuildingDesign.Position(east, 0), 2, 2));
        design.getElevators().add(new BuildingDesign.Elevator(CubeCoords.ZERO, 20, Map.of(0, 36, 1, 36, 2, 36, 3, 36)));
        assertTrue(BuildingUtil.constructionIssues(building).isEmpty(), BuildingUtil.constructionIssues(building).toString());
        var sheet = sheet(building, PaperSize.ISO_A4);
        assertEquals(1, sheet.getPageCount(), "Fit the complete service inventory before adding another page");
        assertEquals(3, sheet.inventoryGroups().get(1).size(), "Same equipment/hex/floor retains one quantity group");
        var text = new StringBuilder();
        for (int page = 0; page < sheet.getPageCount(); page++) {
            assertTrue(sheet.createDocument(page, pageFormat(PaperSize.ISO_A4), true));
            var rows = elements(sheet, "g", "building-inventory-entry");
            assertTrue(rows.size() > 18, "A full inventory is no longer limited to 18 rows");
            assertFalse(elements(sheet, "g", "building-map-key").isEmpty());
            rows.forEach(row -> text.append(row.getTextContent()).append('\n'));
            render(sheet, "building-construction-details-" + (page + 1));
        }
        assertTrue(text.toString().contains("2 × SE fixed; auto, Gunnery 5"));
        assertTrue(text.toString().contains("Mass share: 5.00 t"));
        assertTrue(text.toString().contains("Environmental sealing"));
        assertTrue(text.toString().contains("Door SE; 2 levels high"));
        assertTrue(text.toString().contains("Lift access: SE, NW"));
        assertTrue(text.toString().contains("/Roof"));
        assertTrue(text.toString().contains("Current elevator level:"));
        assertFalse(text.toString().contains("Unallocated"));
    }

    /** Keep review images in build/ rather than committing snapshots of generated artwork. */
    private void render(PrintBuilding sheet, String name) throws Exception {
        Path directory = Path.of("build", "building-review");
        Files.createDirectories(directory);
        PNGTranscoder renderer = new PNGTranscoder();
        renderer.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 1224f);
        renderer.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
        try (OutputStream output = Files.newOutputStream(directory.resolve(name + ".png"))) {
            renderer.transcode(new TranscoderInput(sheet.getSVGDocument()), new TranscoderOutput(output));
        }
    }
}
