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

package megameklab.util;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

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

    public static String levelLabel(BuildingEntity entity, int level) {
        return levelLabel(BuildingConstruction.baseLevel(entity) + level);
    }

    public static String roofLevelLabel(BuildingEntity entity, int level) {
        return level == entity.getInternalBuilding().getBuildingHeight()
              ? "Roof (" + levelLabel(entity, level) + ")" : levelLabel(entity, level);
    }

    public static String absoluteHexLabel(CubeCoords hex) {
        return "%d,%d".formatted((int) hex.q(), (int) hex.r());
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
        int centeredQ = Math.floorDiv(columns - 1 - minQ - maxQ, 2);
        // Try the nearest translations of both column parities before making the grid denser.
        // The centered placement wins ties; all floors and location labels use this same translation.
        return IntStream.of(centeredQ, centeredQ + 1, centeredQ - 1)
              .filter(shiftQ -> minQ + shiftQ >= 0 && maxQ + shiftQ < columns)
              .mapToObj(shiftQ -> {
                  int minRow = hexes.stream().mapToInt(c -> (int) c.r() + Math.floorDiv((int) c.q() + shiftQ, 2))
                        .min().orElse(0);
                  int maxRow = hexes.stream().mapToInt(c -> (int) c.r() + Math.floorDiv((int) c.q() + shiftQ, 2))
                        .max().orElse(0);
                  int rows = Math.max(7, maxRow - minRow + 1);
                  return new SheetGrid(columns, rows, shiftQ, Math.floorDiv(rows - 1 - minRow - maxRow, 2));
              }).min(Comparator.comparingInt(SheetGrid::rows)).orElseThrow();
    }

    public static String locationLabel(BuildingEntity entity, int location) {
        if (location < 0 || location >= entity.locations()) {
            return "Unallocated";
        }
        int height = entity.getInternalBuilding().getBuildingHeight();
        List<CubeCoords> hexes = entity.getInternalBuilding().getOriginalCoordsList();
        CubeCoords hex = hexes.get(location / height);
        int level = entity.getBldgClass() == IBuilding.BRIDGE ? entity.getDesign().bridgeDeck(hex) : location % height;
        return sheetGrid(hexes).label(hex) + "/" + levelLabel(entity, level);
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
