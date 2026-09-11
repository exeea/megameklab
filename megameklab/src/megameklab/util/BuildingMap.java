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

import java.util.ArrayList;
import java.util.List;
import megamek.common.bays.Bay;
import megamek.common.board.CubeCoords;
import megamek.common.equipment.BuildingEquipmentType;
import megamek.common.equipment.Mounted;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.BuildingDesign;
import megamek.common.units.AbstractBuildingEntity;

/** Feature identity shared by the editing map and printed structure maps. */
public final class BuildingMap {
    private static final List<Feature> FILL_PRIORITY = List.of(Feature.ELEVATOR, Feature.BAY, Feature.DECK, Feature.TURRET);

    private BuildingMap() { }

    public enum Feature {
        BAY("Bay / quarters", "#f3b0b4", "B"), ELEVATOR("Elevator", "#efcb8d", "E"), DECK("Roof facility", "#b5d1bf", "D"),
        TURRET("Roof turret", "#aca6d2", "T"), DOOR("Door", "#ffffff", "");

        public final String label;
        public final String color;
        public final String glyph;

        Feature(String label, String color, String glyph) {
            this.label = label;
            this.color = color;
            this.glyph = glyph;
        }
    }

    public static List<Feature> features(AbstractBuildingEntity building, CubeCoords hex, int level) {
        return features(building, hex, level, building.getDesign().getMapDoors());
    }

    public static List<Feature> features(AbstractBuildingEntity building, CubeCoords hex, int level,
          List<BuildingDesign.Door> mapDoors) {
        List<Feature> result = new ArrayList<>(5);
        boolean bay = false;
        for (Bay transportBay : building.getTransportBays()) {
            for (BuildingDesign.Space space : BuildingConstruction.baySpaces(building, transportBay)) {
                if (space.tons() > 0 && space.position().hex().equals(hex) && space.position().level() == level) {
                    bay = true;
                    break;
                }
            }
            if (bay) {
                break;
            }
        }
        if (bay) {
            result.add(Feature.BAY);
        }

        boolean elevator = false;
        for (BuildingDesign.Elevator lift : building.getDesign().getElevators()) {
            if (lift.hex().equals(hex) && lift.reaches(level)) {
                elevator = true;
                break;
            }
        }
        if (elevator) {
            result.add(Feature.ELEVATOR);
        }

        if (level == building.getInternalBuilding().getHeight(hex) - 1) {
            boolean deck = false;
            boolean turret = false;
            for (Mounted<?> mount : building.getEquipment()) {
                if (mount.isOneShotAmmo() || mount.isWeaponGroup()) {
                    continue;
                }
                boolean inHex = false;
                for (BuildingDesign.Position position : BuildingConstruction.equipmentPositions(building, mount)) {
                    if (position.hex().equals(hex)) {
                        inHex = true;
                        break;
                    }
                }
                if (!inHex) {
                    continue;
                }
                if (mount.getType() instanceof BuildingEquipmentType facility && facility.getFacility().isRoof()) {
                    deck = true;
                }
                if (mount.isSponsonTurretMounted()) {
                    turret = true;
                }
                if (deck && turret) {
                    break;
                }
            }
            if (deck) {
                result.add(Feature.DECK);
            }
            if (turret) {
                result.add(Feature.TURRET);
            }
        }

        for (BuildingDesign.Door door : mapDoors) {
            if (door.position().hex().equals(hex) && level >= door.position().level()
                  && level < door.position().level() + door.height()) {
                result.add(Feature.DOOR);
                break;
            }
        }
        return result;
    }

    public static Feature fill(List<Feature> features) {
        for (Feature feature : FILL_PRIORITY) {
            if (features.contains(feature)) {
                return feature;
            }
        }
        return null;
    }

    /** Center the triangle on its hexside; affine projection preserves that alignment. */
    public static double[][] doorPoints(double[] a, double[] b) {
        double dx = (a[0] + b[0]) / 2, dy = (a[1] + b[1]) / 2;
        return new double[][] { { dx * 1.3, dy * 1.3 },
            { dx * .85 - (b[0] - a[0]) * .18, dy * .85 - (b[1] - a[1]) * .18 },
            { dx * .85 + (b[0] - a[0]) * .18, dy * .85 + (b[1] - a[1]) * .18 } };
    }
}
