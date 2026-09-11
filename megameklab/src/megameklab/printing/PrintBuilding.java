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

import java.awt.geom.Rectangle2D;
import java.awt.font.FontRenderContext;
import java.awt.print.PageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import megamek.common.bays.Bay;
import megamek.common.board.Coords;
import megamek.common.board.CubeCoords;
import megamek.common.equipment.AmmoType;
import megamek.common.equipment.Mounted;
import megamek.common.equipment.PowerGeneratorType;
import megamek.common.equipment.WeaponType;
import megameklab.util.BuildingMap;
import megameklab.util.BuildingMap.Feature;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.AbstractBuildingEntity;
import megamek.common.units.MobileStructure;
import megamek.common.units.IBuilding;
import megameklab.util.BuildingUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGRectElement;

/** Full-page structure record sheet, using the same template/print pipeline as other entities. */
public class PrintBuilding extends PrintEntity {
    private static final int LEVELS_PER_PAGE = 6;
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);
    private List<InventoryPage> plannedInventory;
    private final AbstractBuildingEntity building;
    private int currentPage;

    public PrintBuilding(AbstractBuildingEntity building, int firstPage, RecordSheetOptions options) {
        super(firstPage, options);
        this.building = building;
    }

    @Override
    public AbstractBuildingEntity getEntity() {
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
        NodeList textElements = getSVGDocument().getElementsByTagName("text");
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
        if (building instanceof MobileStructure) {
            return "Mobile Structure Record Sheet";
        }
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
              .map(building::getLevelLabel).collect(Collectors.joining(",")) : Integer.toString(building.getInternalBuilding().getBuildingHeight()));
        setTextField(MP_WALK, building instanceof MobileStructure mobile
              ? NumberFormat.getNumberInstance().format(mobile.getMaximumMP()) : "0");
        setTextField("movementType", building instanceof MobileStructure mobile ? mobile.getMovementModeAsString() : "Static");
        if (!building.isClan() && !building.isMixedTech()) {
            hideElement("techClanCheck");
        }
        if (building.isClan() && !building.isMixedTech()) {
            hideElement("techISCheck");
        }
        setTextField(COST, formatCost());
        setTextField(BV, NumberFormat.getInstance().format(building.calculateBattleValue(true, !showPilotInfo())));
        String generators = building.getEquipment().stream().filter(m -> m.getType() instanceof PowerGeneratorType)
              .map(Mounted::getName).distinct().collect(Collectors.joining(", "));
        if (building instanceof MobileStructure mobile) {
            generators = mobile.getPowerSystem().getEngineName();
        }
        setTextField("powerplant", BuildingConstruction.hasNoInterior(building) || BuildingConstruction.usesHexsides(building)
              ? "NA" : generators.isBlank() ? "External supply" : generators, true);
        setTextField("buildingCrew", Integer.toString(building.getNCrew()));
        if (showPilotInfo()) {
            setTextField("buildingGunnery", Integer.toString(building.getCrew().getGunnery()));
        }
    }

    @Override
    protected void drawArmor() {
        Rectangle2D box = getRectBBox((SVGRectElement) getSVGDocument().getElementById("buildingProtection"));
        Element canvas = (Element) getSVGDocument().getElementById("buildingProtection").getParentNode();
        List<Protection> allProtection = protectionRows();
        List<Protection> protection = allProtection.stream().skip(currentPage * 36L).limit(36).toList();
        int rows = Math.max(1, (protection.size() + 1) / 2);
        double step = Math.min(12, (box.getHeight() - 16) / rows);
        float font = (float) Math.min(6.6, step * .65);
        double columnWidth = box.getWidth() / 2;
        for (int column = 0; column < 2; column++) {
            double x = box.getX() + column * columnWidth;
            text(canvas, x + 13, box.getY() + 7, 26, BuildingConstruction.usesHexsides(building) ? "Hex/Side" : "Hex", 6.7f, "middle", "bold");
            text(canvas, x + columnWidth * .52, box.getY() + 7, 25, building.getConstructionCFScale() == 10 ? "CF*" : "CF", 6.7f, "middle", "bold");
            text(canvas, x + columnWidth * .84, box.getY() + 7, 27, "Armor", 6.7f, "middle", "bold");
            for (int row = 0; row < rows; row++) {
                double y = box.getY() + 18 + row * step;
                if (y > box.getMaxY() - 2) {
                    break;
                }
                int index = column * rows + row;
                if (row >= rows || index >= protection.size()) {
                    continue;
                }
                Protection entry = protection.get(index);
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
        List<CubeCoords> hexes = building.getInternalBuilding().getOriginalCoordsList();
        BuildingUtil.SheetGrid grid = BuildingUtil.sheetGrid(hexes);
        List<Protection> result = new ArrayList<>();
        for (CubeCoords hex : hexes) {
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
        BuildingUtil.SheetGrid grid = BuildingUtil.sheetGrid(hexes);
        Map<Coords, CubeCoords> occupied = new LinkedHashMap<>();
        hexes.forEach(hex -> occupied.put(grid.position(hex), hex));
        double width = 30 * (grid.columns() - 1) + 40 + 6 * (grid.rows() - 1);
        double height = 12 * (grid.rows() + .5);
          List<Integer> mapLevels = BuildingConstruction.mapLevels(building).stream().skip((long) currentPage * LEVELS_PER_PAGE)
              .limit(LEVELS_PER_PAGE).toList();
        int levels = mapLevels.size();
        if (levels <= 0) {
            return;
        }
        List<BuildingDesign.Door> mapDoors = building.getDesign().getMapDoors();
        Map<BuildingDesign.Position, List<Feature>> features = new LinkedHashMap<>();
        LinkedHashSet<Feature> symbols = new LinkedHashSet<>();
        for (CubeCoords hex : hexes) {
            for (int level : mapLevels) {
            List<Feature> values = BuildingMap.features(building, hex, level, mapDoors);
                features.put(new BuildingDesign.Position(hex, level), values);
                symbols.addAll(values);
            }
        }
        int keyColumns = Math.max(1, (int) (box.getWidth() / 110));
        double keyHeight = symbols.isEmpty() ? 0 : Math.ceil((double) symbols.size() / keyColumns) * 16 + 12;
        double scale = Math.min((box.getWidth() - 8) / width, (box.getHeight() - keyHeight - levels * 18) / (levels * height));
        double layerHeight = height * scale + 18;
        double headerGap = Math.min(24, Math.max(0, box.getHeight() - keyHeight - levels * layerHeight));
        double[][] corners = { { -20, 0 }, { -7, -6 }, { 13, -6 }, { 20, 0 }, { 7, 6 }, { -13, 6 } };
        for (int layerIndex = 0; layerIndex < levels; layerIndex++) {
            int level = mapLevels.get(layerIndex);
            Element layer = element((Element) region.getParentNode(), "g", "class", "building-map-layer",
                  "data-building-floor", Integer.toString(level), "transform", "translate(%s %s)".formatted(
                        box.getX() + (box.getWidth() - width * scale) / 2,
                        box.getY() + headerGap + layerIndex * layerHeight));
            Element background = element(layer, "g");
            Element footprint = element(layer, "g");
            Element annotations = element(layer, "g");
            for (int column = 0; column < grid.columns(); column++) {
                for (int row = 0; row < grid.rows(); row++) {
                    CubeCoords hex = occupied.get(new Coords(column, row));
                    boolean present = hex != null && BuildingConstruction.occupiesMapLevel(building, hex, level)
                          && BuildingConstruction.segmentsInHex(building, hex) > 0;
                    boolean wall = BuildingConstruction.usesHexsides(building);
                    List<Feature> cellFeatures = present ? features.get(new BuildingDesign.Position(hex, level)) : List.of();
                    Feature fill = BuildingMap.fill(cellFeatures);
                    double staggeredRow = row + (column & 1) * .5;
                    double x = (column * 30 - staggeredRow * 6 + 20 + 6 * (grid.rows() - 1)) * scale;
                    double y = (staggeredRow * 12 + 6) * scale;
                    StringBuilder points = new StringBuilder();
                    for (double[] corner : corners) {
                        points.append(x + corner[0] * scale).append(',').append(y + corner[1] * scale).append(' ');
                    }
                    Element polygon = element(present ? footprint : background, "polygon", "points", points.toString(),
                          "fill", fill == null ? "none" : fill.color,
                          "stroke", present && !wall ? "#000" : "#bbb", "stroke-width", present && !wall ? "1.5" : ".35",
                          "stroke-linejoin", "round", "class", present ? "building-hex occupied" : "building-hex");
                    if (present) {
                        if (wall) {
                            for (int side = 0; side < 6; side++) {
                                if ((building.getDesign().wallSides(hex) & (1 << side)) != 0) {
                                    double[] a = corners[(side + 1) % 6];
                                    double[] b = corners[(side + 2) % 6];
                                    element(annotations, "line", "x1", Double.toString(x + a[0] * scale), "y1", Double.toString(y + a[1] * scale),
                                          "x2", Double.toString(x + b[0] * scale), "y2", Double.toString(y + b[1] * scale),
                                          "stroke", "#000", "stroke-width", "1.8", "data-building-side", Integer.toString(side),
                                          "data-building-hex", grid.label(hex));
                                }
                            }
                        }
                        polygon.setAttribute("data-building-hex", grid.label(hex));
                        polygon.setAttribute("data-building-features", cellFeatures.stream().map(symbol -> symbol.name().toLowerCase()).collect(Collectors.joining(" ")));
                        List<Feature> glyphs = cellFeatures.stream().filter(symbol -> symbol != Feature.DOOR).toList();
                        for (BuildingDesign.Door door : mapDoors) {
                            if (door.facing() < 0 || door.facing() > 5 || !door.position().hex().equals(hex) || level < door.position().level()
                                  || level >= door.position().level() + door.height()) {
                                continue;
                            }
                            double[] a = corners[(door.facing() + 1) % 6];
                            double[] b = corners[(door.facing() + 2) % 6];
                            double[][] arrow = BuildingMap.doorPoints(a, b);
                            Element marker = mapPolygon(annotations, arrow, x, y, scale, "#fff");
                            marker.setAttribute("data-building-symbol", "door");
                            marker.setAttribute("data-building-facing", Integer.toString(door.facing()));
                        }
                        if (glyphs.isEmpty()) {
                            text(annotations, x, y + 2.3 * scale, 30 * scale, grid.label(hex), (float) (6.5 * scale), "middle", "normal");
                        } else {
                            // Center the complete text run, rather than positioning the symbol and coordinate separately.
                            Element label = element(annotations, "text", "x", Double.toString(x), "y", Double.toString(y + 2.3 * scale),
                                  "font-size", Double.toString(6.5 * scale), "text-anchor", "middle");
                            for (int index = 0; index < glyphs.size(); index++) {
                                Element glyph = element(label, "tspan", "data-building-symbol", glyphs.get(index).name().toLowerCase(),
                                      "font-size", Double.toString(5.5 * scale), "font-weight", "bold", "dx", Double.toString(index == 0 ? 0 : scale));
                                glyph.setTextContent(glyphs.get(index).glyph);
                            }
                            element(label, "tspan", "dx", Double.toString(1.5 * scale)).setTextContent(grid.label(hex));
                        }
                    }
                }
            }
            text(layer, width * scale, height * scale + 10, width * scale,
                  "Level: " + building.getLevelLabel(level, true), 7, "end", "bold");
        }
        if (!symbols.isEmpty()) {
            Element key = element((Element) region.getParentNode(), "g", "class", "building-map-key", "transform",
                  "translate(%s %s)".formatted(box.getX() + 8, box.getY() + headerGap + levels * layerHeight + 6));
            int index = 0;
            for (Feature symbol : symbols) {
                double x = index % keyColumns * ((box.getWidth() - 16) / keyColumns), y = index / keyColumns * 16;
                mapKeySymbol(key, symbol, x + 6, y + 4);
                text(key, x + 17, y + 6, box.getWidth() / keyColumns - 22, symbol.label, 6.5f, "start", "normal");
                index++;
            }
        }
    }

    private void mapKeySymbol(Element parent, Feature symbol, double x, double y) {
        if (symbol != Feature.DOOR) {
            mapPolygon(parent, new double[][] { { -6, 0 }, { -3, -4 }, { 3, -4 }, { 6, 0 }, { 3, 4 }, { -3, 4 } }, x, y, 1, symbol.color);
        }
        Element glyph = element(parent, "g", "data-building-symbol", symbol.name().toLowerCase());
        if (symbol == Feature.DOOR) {
            mapPolygon(glyph, new double[][] { { 0, -4 }, { 3.5, 3 }, { -3.5, 3 } }, x, y, 1, "#fff");
        } else {
            text(glyph, x, y + 2, 7, symbol.glyph, 6, "middle", "bold");
        }
    }

    private Element mapPolygon(Element parent, double[][] points, double x, double y, double scale, String fill) {
        StringBuilder polygon = new StringBuilder();
        for (double[] point : points) {
            polygon.append(x + point[0] * scale).append(',').append(y + point[1] * scale).append(' ');
        }
        return element(parent, "polygon", "points", polygon.toString(), "fill", fill, "stroke", "#000", "stroke-width", ".8");
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

    private List<InventoryGroup> inventoryEntries() {
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
            if (!placements.isEmpty()) {
                entries.add(note("mount-" + first.getType().getInternalName(), placements.entrySet().stream()
                      .map(placement -> placement.getValue() + " × " + placement.getKey()).collect(Collectors.joining("; ")), ""));
            }
            if (first.getType().isVariableSize()) {
                group.stream().collect(Collectors.groupingBy(Mounted::getSize, LinkedHashMap::new, Collectors.counting()))
                      .forEach((size, count) -> entries.add(note("equipment-size", "Size " + size + " (×" + count + ")",
                            BuildingUtil.locationLabel(building, first.getLocation()))));
            }
            for (Mounted<?> mount : group) {
                List<BuildingDesign.Position> spaces = building.getDesign().getEquipmentSpace().get(mount);
                if (spaces != null && !spaces.isEmpty()) {
                    for (BuildingDesign.Position position : spaces) {
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
                for (BuildingDesign.Space space : BuildingConstruction.baySpaces(building, bay)) {
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
        return entries;
    }

    private InventoryGroup note(String id, String description, String location) {
        return new InventoryGroup(id, -1, List.<String[]>of(new String[] { "", (location.isBlank() || location.equals("All") ? "" : location + ": ")
              + description, "", "", "", "", "", "" }), false);
    }

    private String mountDescription(Mounted<?> mount) {
        String result = BuildingConstruction.isCapital(mount.getType()) ? "Upward (capital)"
              : mount.isSponsonTurretMounted() ? "Roof turret (T)" : mount.getFacing() >= 0 && mount.getFacing() < 6
              ? BuildingUtil.facingLabel(mount.getFacing()) + (mount.isPintleTurretMounted() ? " (P)" : " fixed") : "";
        return result + (building.getDesign().getAutomatedWeapons().contains(mount) ? "; auto, Gunnery 5" : "");
    }

    private void appendDesign(List<InventoryGroup> entries) {
        BuildingDesign design = building.getDesign();
        entries.add(note("classification", (building.getBldgClass() == IBuilding.TENT || building.getBldgClass() == IBuilding.FENCE
              ? "" : building.getBuildingType() + " / ") + (building.getBldgClass() == IBuilding.STANDARD
                    ? "Standard" : IBuilding.className(building.getBldgClass())), ""));
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
            entries.add(note("open-space", "Open-space: 600 t total; lowest floor equipment", "All"));
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
        for (BuildingDesign.Door door : design.getMapDoors()) {
            String side = BuildingUtil.facingLabel(door.facing());
            entries.add(note("door", "Door " + side + "; " + door.height() + " levels high",
                  BuildingUtil.locationLabel(building, BuildingConstruction.location(building, door.position()))));
        }
        for (BuildingDesign.Elevator lift : design.getElevators()) {
            String hex = BuildingUtil.sheetGrid(building.getInternalBuilding().getOriginalCoordsList()).label(lift.hex());
            entries.add(note("elevator", "Elevator: " + lift.capacity() + " t", hex));
            lift.exits().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(exit -> {
                List<String> sides = new ArrayList<>();
                for (int side = 0; side < 6; side++) {
                    if ((exit.getValue() & (1 << side)) != 0) {
                        sides.add(BuildingUtil.facingLabel(side));
                    }
                }
                String level = BuildingUtil.roofLevelLabel(building, exit.getKey());
                entries.add(note("elevator-stop", "Lift access: " + String.join(", ", sides), hex + "/" + level));
            });
            entries.add(note("elevator-current", "Current elevator level: ______", hex));
        }
    }

    private record InventoryPage(List<InventoryGroup> groups, float font, float step) { }

    private List<InventoryPage> inventoryPages() {
        if (plannedInventory != null) {
            return plannedInventory;
        }
        Document template = getSVGDocument() == null ? loadTemplate(getFirstPage(), new PageFormat(), false) : getSVGDocument();
        Rectangle2D box = getRectBBox((SVGRectElement) template.getElementById(INVENTORY));
        double available = box.getHeight() - 14;
        int capacity = (int) Math.floor(available / (InventoryWriter.MIN_FONT_SIZE * InventoryWriter.MIN_LINE_HEIGHT_TO_FONT_SIZE));
        List<InventoryPage> pages = new ArrayList<>();
        List<InventoryGroup> page = new ArrayList<>();
        int rows = 0;
        for (InventoryGroup entry : inventoryEntries()) {
            InventoryGroup wrapped = wrapInventory(List.of(entry), InventoryWriter.MIN_FONT_SIZE, box.getWidth()).getFirst();
            int size = wrapped.rows().size();
            if (rows + size > capacity && !page.isEmpty()) {
                pages.add(fitInventory(page, box));
                page = new ArrayList<>();
                rows = 0;
            }
            if (size > capacity) {
                for (int start = 0; start < size; start += capacity) {
                    pages.add(fitInventory(List.of(new InventoryGroup(entry.id(), entry.location(),
                          wrapped.rows().subList(start, Math.min(size, start + capacity)), entry.destroyed())), box));
                }
            } else {
                page.add(entry);
                rows += size;
            }
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(fitInventory(page, box));
        }
        plannedInventory = List.copyOf(pages);
        return plannedInventory;
    }

    private InventoryPage fitInventory(List<InventoryGroup> groups, Rectangle2D box) {
        float[] metrics = InventoryWriter.scaleText(box.getHeight() - 14,
              font -> wrapInventory(groups, font, box.getWidth()).stream().mapToInt(group -> group.rows().size()).sum(),
              ignored -> 0.0, font -> getNormalFont(font).getLineMetrics("M", TEXT_CONTEXT).getHeight());
        return new InventoryPage(wrapInventory(groups, metrics[0], box.getWidth()), metrics[0], metrics[1]);
    }

    private List<InventoryGroup> wrapInventory(List<InventoryGroup> groups, float font, double width) {
        List<InventoryGroup> result = new ArrayList<>();
        for (InventoryGroup group : groups) {
            List<String[]> rows = new ArrayList<>();
            for (String[] values : group.rows()) {
                boolean note = values[0].isEmpty() && values[2].isEmpty() && values[3].isEmpty();
                double nameWidth = width * (note ? .90 : .34);
                String[] row = values.clone();
                String line = "";
                for (String word : values[1].split("\\s+")) {
                    String next = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && getNormalFont(font).getStringBounds(next, TEXT_CONTEXT).getWidth() > nameWidth) {
                        row[1] = line;
                        rows.add(row);
                        row = new String[] { "", "", "", "", "", "", "", "" };
                        line = word;
                    } else {
                        line = next;
                    }
                }
                row[1] = line;
                rows.add(row);
            }
            result.add(new InventoryGroup(group.id(), group.location(), rows, group.destroyed()));
        }
        return result;
    }

    @Override
    protected void writeEquipment(SVGRectElement rect) {
        Rectangle2D box = getRectBBox(rect);
        Element canvas = (Element) rect.getParentNode();
        List<InventoryPage> pages = inventoryPages();
        InventoryPage page = currentPage < pages.size() ? pages.get(currentPage) : new InventoryPage(List.of(), 6.76f, 9);
        double step = page.step();
        float font = page.font();
        double[] x = { .025, .065, .485, .625, .725, .805, .885, .97 };
        double[] widths = { .04, .34, .145, .11, .07, .07, .07, .07 };
        String[] headers = { "Qty", "Type", "Hex/Loc", "Dmg", "Min", "Sht", "Med", "Lng" };
        for (int i = 0; i < headers.length; i++) {
            text(canvas, box.getX() + x[i] * box.getWidth(), box.getY() + 6, widths[i] * box.getWidth(),
                  headers[i], 5.8f, i == 1 ? "start" : "middle", "bold");
        }
        double y = box.getY() + 12 + font;
        for (InventoryGroup inventoryGroup : page.groups()) {
            Element rowGroup = element(canvas, "g", "class", "building-inventory-entry",
                  "data-equipment-id", inventoryGroup.id(), "data-location", Integer.toString(inventoryGroup.location()));
            for (String[] values : inventoryGroup.rows()) {
                for (int column = 0; column < values.length; column++) {
                    boolean note = column == 1 && values[0].isEmpty() && values[2].isEmpty() && values[3].isEmpty();
                    text(rowGroup, box.getX() + x[column] * box.getWidth(), y, (note ? .90 : widths[column]) * box.getWidth(),
                          values[column], font, column == 1 ? "start" : "middle", "normal");
                }
                if (options.showDamage() && inventoryGroup.destroyed()) {
                    addLineThrough(rowGroup, box.getX(), y - font * .3, box.getWidth());
                }
                y += step;
            }
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
