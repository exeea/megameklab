/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.printing;

import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import megamek.common.bays.Bay;
import megamek.common.board.Coords;
import megamek.common.board.CubeCoords;
import megamek.common.equipment.AmmoType;
import megamek.common.equipment.Mounted;
import megamek.common.equipment.PowerGeneratorType;
import megamek.common.equipment.WeaponType;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.BuildingEntity;
import megamek.common.units.IBuilding;
import megameklab.util.BuildingUtil;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGRectElement;

/** Full-page structure record sheet, using the same template/print pipeline as other entities. */
public class PrintBuilding extends PrintEntity {
    private static final int LEVELS_PER_PAGE = 6;
    private static final int INVENTORY_ROWS_PER_PAGE = 18;
    private final BuildingEntity building;
    private int currentPage;

    public PrintBuilding(BuildingEntity building, int firstPage, RecordSheetOptions options) {
        super(firstPage, options);
        this.building = building;
    }

    @Override
    public BuildingEntity getEntity() {
        return building;
    }

    @Override
    public int getPageCount() {
        return Math.max(Math.max((BuildingConstruction.mapLevels(building).size() + LEVELS_PER_PAGE - 1) / LEVELS_PER_PAGE,
              (protectionRows().size() + 35) / 36), inventoryPages().size());
    }

    @Override
    protected void processImage(int pageNum, PageFormat pageFormat) {
        currentPage = pageNum;
        super.processImage(pageNum, pageFormat);
        setTextField("pageNumber", "Page " + (pageNum + 1) + " / " + getPageCount());
        // Keep the selected sheet font, with a PDF-safe sans-serif fallback when it is not installed.
        var textElements = getSVGDocument().getElementsByTagName("text");
        for (int i = 0; i < textElements.getLength(); i++) {
            ((Element) textElements.item(i)).setAttribute("font-family", getTypeface() + ",Helvetica,sans-serif");
        }
    }

    @Override
    protected String getSVGFileName(int pageNumber) {
        return "building_default.svg";
    }

    @Override
    protected String getRecordSheetTitle() {
        return "Structure Record Sheet";
    }

    @Override
    protected boolean supportsAlternateArmorGrouping() {
        return false;
    }

    @Override
    protected boolean includeReferenceCharts() {
        return false;
    }

    @Override
    protected void writeTextFields() {
        setTextField(TITLE, getRecordSheetTitle().toUpperCase());
        setTextField(TYPE, building.getShortNameRaw(), true);
        setTextField("levels", building.getBldgClass() == IBuilding.BRIDGE ? "Decks: " + BuildingConstruction.mapLevels(building).stream()
              .map(BuildingUtil::levelLabel).collect(Collectors.joining(",")) : Integer.toString(building.getInternalBuilding().getBuildingHeight()));
        setTextField("buildingType", building.getBldgClass() == IBuilding.TENT || building.getBldgClass() == IBuilding.FENCE
              ? "—" : building.getBuildingType().toString());
        setTextField("buildingClass", IBuilding.className(building.getBldgClass()));
        setTextField(MP_WALK, "NA");
        setTextField(TECH_BASE, formatTechBase());
        setTextField(COST, formatCost());
        setTextField(BV, NumberFormat.getInstance().format(building.calculateBattleValue(true, !showPilotInfo())));
        String generators = building.getEquipment().stream().filter(m -> m.getType() instanceof PowerGeneratorType)
              .map(Mounted::getName).distinct().collect(Collectors.joining(", "));
        setTextField("powerplant", BuildingConstruction.hasNoInterior(building) || BuildingConstruction.usesHexsides(building)
              ? "NA" : generators.isBlank() ? "External supply" : generators, true);
        var crew = BuildingConstruction.crew(building);
        setTextField("buildingCrew", Integer.toString(crew.total()));
        if (showPilotInfo()) {
            setTextField("buildingGunnery", Integer.toString(building.getCrew().getGunnery()));
        }
    }

    @Override
    protected void drawArmor() {
        Rectangle2D box = getRectBBox((SVGRectElement) getSVGDocument().getElementById("buildingProtection"));
        Element canvas = (Element) getSVGDocument().getElementById("buildingProtection").getParentNode();
        var allProtection = protectionRows();
        var protection = allProtection.stream().skip(currentPage * 36L).limit(36).toList();
        int rows = Math.max(1, (protection.size() + 1) / 2);
        double step = Math.min(12, (box.getHeight() - 16) / rows);
        float font = (float) Math.min(6.6, step * .65);
        double columnWidth = box.getWidth() / 2;
        for (int column = 0; column < 2; column++) {
            double x = box.getX() + column * columnWidth;
            text(canvas, x + 13, box.getY() + 7, 26, BuildingConstruction.usesHexsides(building) ? "Hex/Side" : "Hex", 6.7f, "middle", "bold");
            text(canvas, x + columnWidth * .52, box.getY() + 7, 25, building.getConstructionCFScale() == 10 ? "CF*" : "CF", 6.7f, "middle", "bold");
            text(canvas, x + columnWidth * .84, box.getY() + 7, 27, "Armor", 6.7f, "middle", "bold");
            for (int row = 0; row < Math.max(rows, 18); row++) {
                double y = box.getY() + 18 + row * step;
                if (y > box.getMaxY() - 2) {
                    break;
                }
                line(canvas, x + 1, y + 3, x + columnWidth - 6, y + 3, "#aaa", .3);
                int index = column * rows + row;
                if (row >= rows || index >= protection.size()) {
                    continue;
                }
                var entry = protection.get(index);
                int loc = building.getInternalBuilding().getOriginalCoordsList().indexOf(entry.hex())
                      * building.getInternalBuilding().getBuildingHeight();
                text(canvas, x + 13, y, 26, entry.label(), font, "middle", "normal");
                text(canvas, x + columnWidth * .52, y, 25,
                      Integer.toString(options.showDamage() ? building.getInternal(loc) : building.getOInternal(loc)),
                      font, "middle", "normal");
                text(canvas, x + columnWidth * .84, y, 27,
                      Integer.toString(options.showDamage() ? Math.max(0, building.getArmor(loc)) : building.getOArmor(loc)),
                      font, "middle", "normal");
            }
        }
    }

    private record Protection(CubeCoords hex, String label) { }

    private List<Protection> protectionRows() {
        var hexes = building.getInternalBuilding().getOriginalCoordsList();
        var grid = BuildingUtil.sheetGrid(hexes);
        List<Protection> result = new ArrayList<>();
        for (var hex : hexes) {
            if (BuildingConstruction.usesHexsides(building)) {
                for (int side = 0; side < 6; side++) {
                    if ((building.getDesign().wallSides(hex) & (1 << side)) != 0) {
                        result.add(new Protection(hex, grid.label(hex) + "/" + BuildingUtil.facingLabel(side)));
                    }
                }
            } else {
                result.add(new Protection(hex, grid.label(hex)));
            }
        }
        return result;
    }

    @Override
    protected void drawStructure() {
        Element region = getSVGDocument().getElementById("structureMap");
        Rectangle2D box = getRectBBox((SVGRectElement) region);
        List<CubeCoords> hexes = building.getInternalBuilding().getOriginalCoordsList();
        var grid = BuildingUtil.sheetGrid(hexes);
        Map<Coords, CubeCoords> occupied = new LinkedHashMap<>();
        hexes.forEach(hex -> occupied.put(grid.position(hex), hex));
        double width = 30 * (grid.columns() - 1) + 40 + 6 * (grid.rows() - 1);
        double height = 12 * (grid.rows() + .5);
        var mapLevels = BuildingConstruction.mapLevels(building).stream().skip((long) currentPage * LEVELS_PER_PAGE)
              .limit(LEVELS_PER_PAGE).toList();
        int levels = mapLevels.size();
        if (levels <= 0) {
            return;
        }
        double scale = Math.min((box.getWidth() - 8) / width, box.getHeight() / (levels * (height + 14)));
        double layerHeight = (height + 14) * scale;
        double headerGap = Math.min(24, Math.max(0, box.getHeight() - levels * layerHeight));
        double[][] corners = { { -20, 0 }, { -7, -6 }, { 13, -6 }, { 20, 0 }, { 7, 6 }, { -13, 6 } };
        for (int layerIndex = 0; layerIndex < levels; layerIndex++) {
            int level = mapLevels.get(layerIndex);
            Element layer = element((Element) region.getParentNode(), "g", "class", "building-map-layer",
                  "data-building-floor", Integer.toString(level), "transform", "translate(%s %s)".formatted(
                        box.getX() + (box.getWidth() - width * scale) / 2,
                        box.getY() + headerGap + layerIndex * layerHeight));
            Element background = element(layer, "g");
            Element footprint = element(layer, "g");
            for (int column = 0; column < grid.columns(); column++) {
                for (int row = 0; row < grid.rows(); row++) {
                    CubeCoords hex = occupied.get(new Coords(column, row));
                    boolean present = hex != null && BuildingConstruction.occupiesMapLevel(building, hex, level)
                          && BuildingConstruction.segmentsInHex(building, hex) > 0;
                    boolean wall = BuildingConstruction.usesHexsides(building);
                    double staggeredRow = row + (column & 1) * .5;
                    double x = (column * 30 - staggeredRow * 6 + 20 + 6 * (grid.rows() - 1)) * scale;
                    double y = (staggeredRow * 12 + 6) * scale;
                    StringBuilder points = new StringBuilder();
                    for (double[] corner : corners) {
                        points.append(x + corner[0] * scale).append(',').append(y + corner[1] * scale).append(' ');
                    }
                    Element polygon = element(present ? footprint : background, "polygon", "points", points.toString(),
                          "fill", "none", "stroke", present && !wall ? "#000" : "#bbb", "stroke-width", present && !wall ? "1.5" : ".35",
                          "stroke-linejoin", "round", "class", present ? "building-hex occupied" : "building-hex");
                    if (present) {
                        if (wall) {
                            for (int side = 0; side < 6; side++) {
                                if ((building.getDesign().wallSides(hex) & (1 << side)) != 0) {
                                    var a = corners[(side + 1) % 6];
                                    var b = corners[(side + 2) % 6];
                                    element(footprint, "line", "x1", Double.toString(x + a[0] * scale), "y1", Double.toString(y + a[1] * scale),
                                          "x2", Double.toString(x + b[0] * scale), "y2", Double.toString(y + b[1] * scale),
                                          "stroke", "#000", "stroke-width", "1.8", "data-building-side", Integer.toString(side),
                                          "data-building-hex", grid.label(hex));
                                }
                            }
                        }
                        polygon.setAttribute("data-building-hex", grid.label(hex));
                        text(footprint, x, y + 2.3 * scale, 30 * scale, grid.label(hex), (float) (6.5 * scale),
                              "middle", "normal");
                    }
                }
            }
            text(layer, width * scale, (height + 10) * scale, width * scale,
                  "Level: " + BuildingUtil.levelLabel(level), (float) (7 * scale), "end", "bold");
        }
    }

    private record EquipmentKey(String internalName, int location) {
    }

    /** Quantity groups deliberately use the equipment's internal id and exact hex/level. */
    List<List<Mounted<?>>> inventoryGroups() {
        Map<EquipmentKey, List<Mounted<?>>> groups = new LinkedHashMap<>();
        for (Mounted<?> mount : building.getEquipment()) {
            if (!mount.isOneShotAmmo() && !mount.isWeaponGroup()) {
                groups.computeIfAbsent(new EquipmentKey(mount.getType().getInternalName(), mount.getLocation()),
                      key -> new ArrayList<>()).add(mount);
            }
        }
        return List.copyOf(groups.values());
    }

    private record InventoryGroup(String id, int location, List<String[]> rows, boolean destroyed) {
    }

    private List<List<InventoryGroup>> inventoryPages() {
        List<InventoryGroup> entries = new ArrayList<>();
        for (List<Mounted<?>> group : inventoryGroups()) {
            Mounted<?> first = group.getFirst();
            StandardInventoryEntry entry = new StandardInventoryEntry(first);
            List<String[]> rows = new ArrayList<>();
            for (int row = 0; row < entry.nRows(); row++) {
                String name = entry.getNameField(row);
                if (row == 0 && first.getType().isVariableSize()) {
                    name = first.getType().getName();
                }
                if (row == 0 && first.getType() instanceof AmmoType) {
                    int shots = group.stream().mapToInt(m -> options.showDamage() ? m.getBaseShotsLeft() : m.getOriginalShots()).sum();
                    name = first.getType().getShortName() + " (" + shots + ")";
                }
                rows.add(new String[] { row == 0 ? Integer.toString(group.size()) : "", name,
                      row == 0 ? BuildingUtil.locationLabel(building, first.getLocation()) : "", entry.getDamageField(row),
                      entry.getMinField(row), entry.getShortField(row), entry.getMediumField(row), entry.getLongField(row) });
            }
            entries.add(new InventoryGroup(first.getType().getInternalName(), first.getLocation(), rows,
                  group.stream().allMatch(m -> m.isDestroyed() || m.isMissing())));
            Map<String, Long> placements = group.stream().filter(m -> m.getType() instanceof WeaponType)
                  .map(this::mountDescription).filter(s -> !s.isBlank())
                  .collect(Collectors.groupingBy(s -> s, LinkedHashMap::new, Collectors.counting()));
            placements.forEach((description, count) -> entries.add(note("mount-" + first.getType().getInternalName(),
                  count + " × " + description, BuildingUtil.locationLabel(building, first.getLocation()))));
            if (first.getType().isVariableSize()) {
                group.stream().collect(Collectors.groupingBy(Mounted::getSize, LinkedHashMap::new, Collectors.counting()))
                      .forEach((size, count) -> entries.add(note("equipment-size", "Size " + size + " (×" + count + ")",
                            BuildingUtil.locationLabel(building, first.getLocation()))));
            }
            for (var mount : group) {
                var spaces = building.getDesign().getEquipmentSpace().get(mount);
                if (spaces != null && !spaces.isEmpty()) {
                    for (var position : spaces) {
                        entries.add(note("space-" + building.getEquipmentNum(mount), "Mass share: %.2f t".formatted(mount.getTonnage() / spaces.size()),
                              BuildingUtil.locationLabel(building, BuildingConstruction.location(building, position))));
                    }
                }
                if (building.getDesign().getPcmtSources().containsKey(mount)) {
                    entries.add(note("pcmt-source", "PCMT source: " + building.getDesign().getPcmtSources().get(mount) + " t", ""));
                }
            }
        }
        for (Bay bay : building.getTransportBays()) {
            String name = "%s (%s t)".formatted(bay.getTransporterType(), NumberFormat.getInstance().format(bay.getWeight()));
            entries.add(new InventoryGroup("bay-" + bay.getBayNumber(), -1,
                  List.<String[]>of(new String[] { "1", name, "—", "", "", "", "", "" }), false));
            if (building.getDesign().getBaySpace().containsKey(bay)) {
                for (var space : BuildingConstruction.baySpaces(building, bay)) {
                    entries.add(note("bay-space", "Space: %.2f t".formatted(space.tons()),
                          BuildingUtil.locationLabel(building, BuildingConstruction.location(building, space.position()))));
                }
            }
        }
        if (building.getTroopCarryingSpace() > 0) {
            String name = "Infantry compartment (%s t)".formatted(NumberFormat.getInstance().format(building.getTroopCarryingSpace()));
            entries.add(new InventoryGroup("infantry-compartment", -1,
                  List.<String[]>of(new String[] { "1", name, "—", "", "", "", "", "" }), false));
        }
        appendDesign(entries);
        List<List<InventoryGroup>> pages = new ArrayList<>();
        List<InventoryGroup> page = new ArrayList<>();
        pages.add(page);
        int rows = 0;
        for (InventoryGroup entry : entries) {
            if (rows + entry.rows().size() > INVENTORY_ROWS_PER_PAGE && !page.isEmpty()) {
                page = new ArrayList<>();
                pages.add(page);
                rows = 0;
            }
            page.add(entry);
            rows += entry.rows().size();
        }
        return pages;
    }

    private InventoryGroup note(String id, String description, String location) {
        return new InventoryGroup(id, -1, List.<String[]>of(new String[] { "", description, location, "", "", "", "", "" }), false);
    }

    private String mountDescription(Mounted<?> mount) {
        String result = BuildingConstruction.isCapital(mount.getType()) ? "Upward (capital)"
              : mount.isSponsonTurretMounted() ? "Roof turret (T)" : mount.getFacing() >= 0 && mount.getFacing() < 6
              ? BuildingUtil.facingLabel(mount.getFacing()) + (mount.isPintleTurretMounted() ? " (P)" : " fixed") : "";
        return result + (building.getDesign().getAutomatedWeapons().contains(mount) ? "; auto, Gunnery 5" : "");
    }

    private void appendDesign(List<InventoryGroup> entries) {
        var design = building.getDesign();
        if (building.getConstructionCFScale() == 10) {
            entries.add(note("capital-protection", "CF and armor: capital points (×10 standard)", "All"));
        }
        if (building.hasEnvironmentalSealing()) {
            entries.add(note("sealing", "Environmental sealing", "All"));
        }
        if (design.hasHeavyMetal()) {
            entries.add(note("heavy-metal", "Heavy-metal superstructure", "All"));
        }
        if (design.isTunnel()) {
            entries.add(note("tunnel", "Tunnel construction", "All"));
        }
        if (design.isOpenSpace()) {
            entries.add(note("open-space", "Open-space: 600 t total; ground equipment", "All"));
        }
        if (BuildingConstruction.usesHexsides(building)) {
            entries.add(note("hexsides", "CF / armor / capacity apply per hexside", "All"));
        }
        if (building.getBldgClass() == IBuilding.BRIDGE) {
            entries.add(note("bridge", "Decks only; ends must meet map terrain", "All"));
        }
        if (design.hasRoofClearance()) {
            entries.add(note("roof-clearance", "Cave: ≥1 level roof clearance", "All"));
        }
        if (design.getCeiling() != BuildingDesign.Ceiling.STANDARD) {
            entries.add(note("ceiling", design.getCeiling() == BuildingDesign.Ceiling.HIGH ? "High ceilings" : "Low ceilings", "All"));
        }
        if (design.getSite() != BuildingDesign.Site.SURFACE) {
            entries.add(note("site", design.getSite() + "; cover " + design.getDepth() + " levels", "All"));
        }
        for (var door : design.getDoors()) {
            String side = BuildingUtil.facingLabel(door.facing());
            entries.add(note("door", "Door " + side + "; " + door.height() + " levels high",
                  BuildingUtil.locationLabel(building, BuildingConstruction.location(building, door.position()))));
        }
        for (var lift : design.getElevators()) {
            String hex = BuildingUtil.sheetGrid(building.getInternalBuilding().getOriginalCoordsList()).label(lift.hex());
            entries.add(note("elevator", "Elevator: " + lift.capacity() + " t", hex));
            lift.exits().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(exit -> {
                List<String> sides = new ArrayList<>();
                for (int side = 0; side < 6; side++) {
                    if ((exit.getValue() & (1 << side)) != 0) {
                        sides.add(BuildingUtil.facingLabel(side));
                    }
                }
                String level = exit.getKey() == building.getInternalBuilding().getBuildingHeight() ? "Roof" : BuildingUtil.levelLabel(exit.getKey());
                entries.add(note("elevator-stop", "Lift access: " + String.join(", ", sides), hex + "/" + level));
            });
            entries.add(note("elevator-current", "Current elevator level: ______", hex));
        }
    }

    @Override
    protected void writeEquipment(SVGRectElement rect) {
        Rectangle2D box = getRectBBox(rect);
        Element canvas = (Element) rect.getParentNode();
        List<List<InventoryGroup>> pages = inventoryPages();
        List<InventoryGroup> groups = currentPage < pages.size() ? pages.get(currentPage) : List.of();
        int rows = groups.stream().mapToInt(group -> group.rows().size()).sum();
        double step = Math.min(11, (box.getHeight() - 14) / Math.max(1, rows));
        float font = (float) Math.min(6.2, step * .68);
        double[] x = { .025, .065, .485, .625, .725, .805, .885, .97 };
        double[] widths = { .04, .34, .145, .11, .07, .07, .07, .07 };
        String[] headers = { "Qty", "Type", "Hex/Loc", "Dmg", "Min", "Sht", "Med", "Lng" };
        for (int i = 0; i < headers.length; i++) {
            text(canvas, box.getX() + x[i] * box.getWidth(), box.getY() + 6, widths[i] * box.getWidth(),
                  headers[i], 5.8f, i == 1 ? "start" : "middle", "bold");
        }
        double y = box.getY() + 17;
        for (InventoryGroup inventoryGroup : groups) {
            Element rowGroup = element(canvas, "g", "class", "building-inventory-entry",
                  "data-equipment-id", inventoryGroup.id(), "data-location", Integer.toString(inventoryGroup.location()));
            for (String[] values : inventoryGroup.rows()) {
                for (int column = 0; column < values.length; column++) {
                    text(rowGroup, box.getX() + x[column] * box.getWidth(), y, widths[column] * box.getWidth(),
                          values[column], font, column == 1 ? "start" : "middle", "normal");
                }
                if (options.showDamage() && inventoryGroup.destroyed()) {
                    addLineThrough(rowGroup, box.getX(), y - font * .3, box.getWidth());
                }
                line(rowGroup, box.getX(), y + 3, box.getMaxX(), y + 3, "#aaa", .3);
                y += step;
            }
        }
        while (y <= box.getMaxY() - 3) {
            line(canvas, box.getX(), y + 3, box.getMaxX(), y + 3, "#aaa", .3);
            y += step;
        }
    }

    private void text(Element parent, double x, double y, double width, String value, float font,
          String anchor, String weight) {
        addTextElementToFit(parent, x, y, width, value, font, anchor, weight);
    }

    private void line(Element parent, double x1, double y1, double x2, double y2, String stroke, double width) {
        element(parent, "line", "x1", Double.toString(x1), "y1", Double.toString(y1), "x2", Double.toString(x2),
              "y2", Double.toString(y2), "stroke", stroke, "stroke-width", Double.toString(width));
    }

    private Element element(Element parent, String name, String... attributes) {
        Element element = getSVGDocument().createElementNS(svgNS, name);
        for (int i = 0; i < attributes.length; i += 2) {
            element.setAttribute(attributes[i], attributes[i + 1]);
        }
        parent.appendChild(element);
        return element;
    }
}
