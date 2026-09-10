/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.util;

import java.util.List;

import megamek.common.CriticalSlot;
import megamek.common.TechConstants;
import megamek.common.board.Coords;
import megamek.common.board.CubeCoords;
import megamek.common.enums.BuildingType;
import megamek.common.equipment.Engine;
import megamek.common.equipment.EquipmentType;
import megamek.common.equipment.Mounted;
import megamek.common.equipment.PowerGeneratorType;
import megamek.common.units.BuildingEntity;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.ConstructionUtil;
import megamek.common.units.Entity;
import megamek.common.units.IBuilding;

/** Building construction operations and the shared editor/record-sheet coordinate system. */
public final class BuildingUtil {
    public static final List<String> FACINGS = List.of("N", "NE", "SE", "S", "SW", "NW");
    private BuildingUtil() {
    }

    public static BuildingEntity newBuilding() {
        BuildingEntity entity = new BuildingEntity(BuildingType.MEDIUM, IBuilding.STANDARD);
        entity.setEngine(new Engine(0, Engine.NONE, 0));
        entity.setChassis("New");
        entity.setModel("Building");
        entity.setYear(3145);
        entity.setTechLevel(TechConstants.T_IS_ADVANCED);
        entity.configureConstruction(BuildingType.MEDIUM, IBuilding.STANDARD, 1, 40, 0, List.of(CubeCoords.ZERO));
        entity.setArmorType(EquipmentType.T_ARMOR_STANDARD);
        entity.setArmorTechLevel(entity.getTechLevel());
        return entity;
    }

    public static String levelLabel(int level) {
        return level == 0 ? "G" : Integer.toString(level);
    }

    public static String facingLabel(int facing) {
        return facing < 0 || facing >= FACINGS.size() ? "?" : FACINGS.get(facing);
    }

    public static int exteriorFacing(BuildingEntity entity, CubeCoords hex) {
        for (int side = 0; side < 6; side++) {
            if (!entity.getInternalBuilding().getOriginalCoordsList().contains(hex.toOffset().translated(side).toCube())) {
                return side;
            }
        }
        return 0;
    }

    /** A cube translation, rather than an offset translation, keeps odd/even column adjacency intact. */
    public record SheetGrid(int columns, int rows, int shiftQ, int shiftRow) {
        public Coords position(CubeCoords hex) {
            int column = (int) hex.q() + shiftQ;
            return new Coords(column, (int) hex.r() + Math.floorDiv(column, 2) + shiftRow);
        }

        public String label(CubeCoords hex) {
            Coords position = position(hex);
            return "%02d%02d".formatted(position.getX() + 1, position.getY() + 1);
        }
    }

    public static SheetGrid sheetGrid(List<CubeCoords> hexes) {
        int minQ = hexes.stream().mapToInt(c -> (int) c.q()).min().orElse(0);
        int maxQ = hexes.stream().mapToInt(c -> (int) c.q()).max().orElse(0);
        int columns = Math.max(9, maxQ - minQ + 1);
        int shiftQ = Math.floorDiv(columns - 1 - minQ - maxQ, 2);
        int minRow = hexes.stream().mapToInt(c -> (int) c.r() + Math.floorDiv((int) c.q() + shiftQ, 2))
              .min().orElse(0);
        int maxRow = hexes.stream().mapToInt(c -> (int) c.r() + Math.floorDiv((int) c.q() + shiftQ, 2))
              .max().orElse(0);
        int rows = Math.max(7, maxRow - minRow + 1);
        return new SheetGrid(columns, rows, shiftQ, Math.floorDiv(rows - 1 - minRow - maxRow, 2));
    }

    public static String locationLabel(BuildingEntity entity, int location) {
        if (location < 0 || location >= entity.locations()) {
            return "Unallocated";
        }
        int height = entity.getInternalBuilding().getBuildingHeight();
        List<CubeCoords> hexes = entity.getInternalBuilding().getOriginalCoordsList();
        CubeCoords hex = hexes.get(location / height);
        int level = entity.getBldgClass() == IBuilding.BRIDGE ? entity.getDesign().bridgeDeck(hex) : location % height;
        return sheetGrid(hexes).label(hex) + "/" + levelLabel(level);
    }

    public static void assignEquipment(BuildingEntity entity, Mounted<?> mount, int location) {
        if (location != mount.getLocation()) {
            entity.getDesign().getEquipmentSpace().remove(mount);
        }
        ConstructionUtil.removeCriticalSlots(entity, mount);
        ConstructionUtil.changeMountStatus(entity, mount, location, Entity.LOC_NONE, false);
        if (location != Entity.LOC_NONE) {
            entity.addCritical(location, new CriticalSlot(mount));
        }
    }

    public static void configure(BuildingEntity entity, BuildingType type, int buildingClass, int levels, int cf,
          int armor, List<CubeCoords> hexes) {
        entity.configureConstruction(type, buildingClass, levels, cf, armor, hexes);
        entity.getEquipment().stream().filter(m -> m.getLocation() == Entity.LOC_NONE && !m.isOneShotAmmo()).toList()
              .forEach(m -> ConstructionUtil.removeMounted(entity, m));
        entity.getDesign().removeDeletedComponents(entity);
    }

    /** Rotate the footprint and weapon facings together, keeping the equipment on the same physical floor. */
    public static void rotate(BuildingEntity entity) {
        transform(entity, c -> new CubeCoords(-(int) c.r(), -(int) c.s(), -(int) c.q()), facing -> (facing + 1) % 6);
    }

    public static void transform(BuildingEntity entity, java.util.function.UnaryOperator<CubeCoords> transform,
          java.util.function.IntUnaryOperator facingTransform) {
        var building = entity.getInternalBuilding();
        entity.configureConstruction(entity.getBuildingType(), entity.getBldgClass(), building.getBuildingHeight(),
              entity.getOInternal(0), entity.getOArmor(0), building.getCoordsList().stream().map(transform).toList(),
              transform, facingTransform);
    }

    public static double equipmentWeight(BuildingEntity entity) {
        return UnitUtil.getEntityVerifier(entity).calculateWeight();
    }

    public static String powerDescription(BuildingEntity entity) {
        if (BuildingConstruction.hasNoInterior(entity) || BuildingConstruction.usesHexsides(entity)) {
            return "NA";
        }
        if (BuildingConstruction.isLiquidStorageOnly(entity)) {
            return "Not required (liquid storage)";
        }
        if (entity.getEquipment().stream().noneMatch(m -> m.getType() instanceof PowerGeneratorType)) {
            return "External supply";
        }
        return entity.hasPower() ? "Available" : "Insufficient";
    }

    public static List<String> constructionIssues(BuildingEntity entity) {
        StringBuffer issues = new StringBuffer();
        UnitUtil.getEntityVerifier(entity).correctEntity(issues, entity.getTechLevel());
        return issues.toString().lines().filter(line -> !line.isBlank()).toList();
    }
}
