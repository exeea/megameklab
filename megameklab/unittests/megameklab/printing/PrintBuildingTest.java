/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import megamek.common.loaders.BLKFile;
import megamek.common.loaders.BLKStructureFile;
import megamek.common.units.BuildingEntity;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.IBuilding;
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

@ExtendWith(InitializeTypes.class)
class PrintBuildingTest {
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

    private PrintBuilding sheet(BuildingEntity building, PaperSize size) {
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
        assertEquals(18, elements(sheet, "g", "building-inventory-entry").size());
        render(sheet, "building-a4-six-layers");
        assertTrue(sheet.createDocument(1, pageFormat(PaperSize.ISO_A4), true));
        assertEquals(List.of("1", "0"), elements(sheet, "g", "building-map-layer").stream()
              .map(e -> e.getAttribute("data-building-floor")).toList());
        assertEquals(6, elements(sheet, "g", "building-inventory-entry").size());
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
        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().getTextContent().contains("(27)"));
        assertTrue(rows.getLast().getTextContent().contains("Quarters (20 t)"));
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
        assertEquals(2, sheet.getPageCount());
        assertEquals(3, sheet.inventoryGroups().get(1).size(), "Same equipment/hex/floor retains one quantity group");
        var text = new StringBuilder();
        for (int page = 0; page < sheet.getPageCount(); page++) {
            assertTrue(sheet.createDocument(page, pageFormat(PaperSize.ISO_A4), true));
            var rows = elements(sheet, "g", "building-inventory-entry");
            assertTrue(rows.size() <= 18);
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
