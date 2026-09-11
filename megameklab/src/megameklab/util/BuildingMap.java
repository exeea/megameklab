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
import megamek.common.board.CubeCoords;
import megamek.common.equipment.BuildingEquipmentType;
import megamek.common.equipment.Mounted;
import megamek.common.units.BuildingConstruction;
import megamek.common.units.AbstractBuildingEntity;

/** Feature identity shared by the editing map and printed structure maps. */
public final class BuildingMap {
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
        List<Feature> result = new ArrayList<>();
        if (building.getTransportBays().stream().flatMap(bay -> BuildingConstruction.baySpaces(building, bay).stream())
              .anyMatch(space -> space.tons() > 0 && space.position().hex().equals(hex) && space.position().level() == level)) {
            result.add(Feature.BAY);
        }
        if (building.getDesign().getElevators().stream().anyMatch(lift -> lift.hex().equals(hex) && lift.reaches(level))) {
            result.add(Feature.ELEVATOR);
        }
        if (level == building.getInternalBuilding().getHeight(hex) - 1) {
            if (building.getEquipmentInHex(hex).stream().anyMatch(mount -> mount.getType() instanceof BuildingEquipmentType facility && facility.getFacility().isRoof())) {
                result.add(Feature.DECK);
            }
            if (building.getEquipmentInHex(hex).stream().anyMatch(Mounted::isSponsonTurretMounted)) {
                result.add(Feature.TURRET);
            }
        }
        if (building.getDesign().getMapDoors().stream().anyMatch(door -> door.position().hex().equals(hex)
              && level >= door.position().level() && level < door.position().level() + door.height())) {
            result.add(Feature.DOOR);
        }
        return result;
    }

    public static Feature fill(List<Feature> features) {
        return List.of(Feature.ELEVATOR, Feature.BAY, Feature.DECK, Feature.TURRET).stream().filter(features::contains).findFirst().orElse(null);
    }

    /** Center the triangle on its hexside; affine projection preserves that alignment. */
    public static double[][] doorPoints(double[] a, double[] b) {
        double dx = (a[0] + b[0]) / 2, dy = (a[1] + b[1]) / 2;
        return new double[][] { { dx * 1.3, dy * 1.3 },
            { dx * .85 - (b[0] - a[0]) * .18, dy * .85 - (b[1] - a[1]) * .18 },
            { dx * .85 + (b[0] - a[0]) * .18, dy * .85 + (b[1] - a[1]) * .18 } };
    }
}
