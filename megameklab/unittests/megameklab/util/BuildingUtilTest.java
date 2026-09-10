/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package megameklab.util;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(InitializeTypes.class)
class BuildingUtilTest {
    private static final CubeCoords EAST = new CubeCoords(1, 0, -1);

    @Test
    void centersSingleHexAndPreservesAdjacencyAcrossColumnParity() {
        assertEquals("0504", BuildingUtil.sheetGrid(List.of(CubeCoords.ZERO)).label(CubeCoords.ZERO));
        List<CubeCoords> hexes = List.of(CubeCoords.ZERO, EAST, new CubeCoords(-1, 0, 1));
        var grid = BuildingUtil.sheetGrid(hexes);
        for (CubeCoords a : hexes) {
            for (CubeCoords b : hexes) {
                assertEquals(a.toOffset().distance(b.toOffset()), grid.position(a).distance(grid.position(b)));
            }
        }
        assertEquals("0504/G", BuildingUtil.locationLabel(BuildingUtil.newBuilding(), 0));
    }

    @Test
    void resizingRemapsSurvivingEquipmentAndRemovesDeletedFloorsAndHexes() throws Exception {
        var entity = BuildingUtil.newBuilding();
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 32,
              List.of(CubeCoords.ZERO, EAST));
        var ground = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 0);
        var upper = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 2);
        var eastern = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 4);
        assertEquals(100, entity.getNumberOfCriticalSlots(4));
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 32,
              List.of(CubeCoords.ZERO, EAST));
        assertEquals(4, entity.locations());
        assertFalse(entity.getEquipment().contains(upper));
        assertFalse(entity.getWeaponList().contains(upper));
        assertEquals(3, eastern.getLocation());
        assertEquals(0, ground.getLocation());
        assertEquals(eastern, entity.getCritical(3, 0).getMount());
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 32, List.of(CubeCoords.ZERO));
        assertEquals(2, entity.locations());
        assertEquals(List.of(ground), entity.getEquipment());
        assertEquals(32, entity.getArmor(0));
        assertEquals(80, entity.getOInternal(1));
    }

    @Test
    void nativeBlkRoundTripRetainsGeometryArmorFacingTurretSizeAndAmmo() throws Exception {
        var entity = BuildingUtil.newBuilding();
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 32,
              List.of(CubeCoords.ZERO, EAST));
        var weapon = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 4);
        weapon.setFacing(5);
        weapon.setSponsonTurretMounted(true);
        var generator = entity.addEquipment(EquipmentType.get("FUSION PowerGenerator"), 0);
        generator.setSize(12.5);
        var ammo = entity.addEquipment(EquipmentType.get("IS Ammo AC/5"), 2);
        ammo.setOriginalShots(17);
        ammo.setShotsLeft(17);
        entity.addTransporter(new FirstClassQuartersCargoBay(2));
        var loaded = (BuildingEntity) new BLKStructureFile(BLKFile.getBlock(entity)).getEntity();
        assertEquals(6, loaded.locations());
        assertEquals(List.of(CubeCoords.ZERO, EAST), loaded.getInternalBuilding().getCoordsList());
        var loadedWeapon = loaded.getWeaponList().getFirst();
        assertEquals(4, loadedWeapon.getLocation());
        assertEquals(5, loadedWeapon.getFacing());
        assertTrue(loadedWeapon.isSponsonTurretMounted());
        assertEquals(12.5, loaded.getMisc().getFirst().getSize());
        assertEquals(17, loaded.getAmmo().getFirst().getBaseShotsLeft());
        assertEquals(ammo.getTonnage(), loaded.getAmmo().getFirst().getTonnage());
        assertEquals(20, loaded.getTransportBays().getFirst().getWeight());
        assertEquals(80, loaded.getOInternal(5));
        assertEquals(32, loaded.getArmor(5));
        assertFalse(UnitUtil.saveUnitToString(loaded, false).contains("Unallocated Equipment"));
        loaded.getAmmo().getFirst().setShotsLeft(0);
        var empty = (BuildingEntity) new BLKStructureFile(BLKFile.getBlock(loaded)).getEntity();
        assertEquals(0, empty.getAmmo().getFirst().getBaseShotsLeft());
    }

    @Test
    void rotationKeepsEquipmentAndFacingWithItsPhysicalHex() throws Exception {
        var entity = BuildingUtil.newBuilding();
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 0,
              List.of(CubeCoords.ZERO, EAST));
        var weapon = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 3);
        weapon.setFacing(5);
        for (int i = 0; i < 6; i++) {
            BuildingUtil.rotate(entity);
            assertTrue(entity.getEquipment().contains(weapon));
            assertEquals(3, weapon.getLocation());
        }
        assertEquals(List.of(CubeCoords.ZERO, EAST), entity.getInternalBuilding().getCoordsList());
        assertEquals(5, weapon.getFacing());
        assertEquals(weapon, entity.getCritical(3, 0).getMount());
    }

    @Test
    void removingTheOriginRebasesSurvivorsWithoutLosingTheirEquipment() throws Exception {
        var entity = BuildingUtil.newBuilding();
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 0, List.of(CubeCoords.ZERO, EAST));
        var removed = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 0);
        var retained = entity.addEquipment(EquipmentType.get("ISMediumLaser"), 3);
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 0, List.of(EAST));
        assertEquals(List.of(CubeCoords.ZERO), entity.getInternalBuilding().getCoordsList());
        assertFalse(entity.getEquipment().contains(removed));
        assertEquals(List.of(retained), entity.getEquipment());
        assertEquals(1, retained.getLocation());
        assertEquals("0504/1", BuildingUtil.locationLabel(entity, retained.getLocation()));
    }

    @Test
    void geometryEditsKeepServicesAndSharedMassWithTheirPhysicalLocations() throws Exception {
        var entity = BuildingUtil.newBuilding();
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 3, 80, 0, List.of(CubeCoords.ZERO, EAST));
        var generator = entity.addEquipment(EquipmentType.get("FUSION PowerGenerator"), 4);
        generator.setSize(6);
        var quarters = new FirstClassQuartersCargoBay(2);
        entity.addTransporter(quarters);
        var design = entity.getDesign();
        design.getEquipmentSpace().put(generator, List.of(new BuildingDesign.Position(EAST, 1),
              new BuildingDesign.Position(CubeCoords.ZERO, 1)));
        design.getBaySpace().put(quarters, List.of(new BuildingDesign.Space(new BuildingDesign.Position(EAST, 0), 20)));
        design.getDoors().add(new BuildingDesign.Door(new BuildingDesign.Position(EAST, 0), 2, 2));
        design.getElevators().add(new BuildingDesign.Elevator(EAST, 20, Map.of(0, 32, 1, 32, 2, 32, 3, 32)));
        for (int i = 0; i < 6; i++) {
            BuildingUtil.rotate(entity);
        }
        assertEquals(new BuildingDesign.Door(new BuildingDesign.Position(EAST, 0), 2, 2), design.getDoors().getFirst());
        assertEquals(Map.of(0, 32, 1, 32, 2, 32, 3, 32), design.getElevators().getFirst().exits());
        BuildingUtil.configure(entity, BuildingType.HEAVY, IBuilding.FORTRESS, 2, 80, 0, List.of(EAST));
        assertEquals(List.of(new BuildingDesign.Position(CubeCoords.ZERO, 1)), design.getEquipmentSpace().get(generator));
        assertEquals(1, generator.getLocation());
        assertEquals(20, BuildingConstruction.bayWeightInHex(entity, CubeCoords.ZERO));
        assertEquals(CubeCoords.ZERO, design.getDoors().getFirst().position().hex());
        assertEquals(CubeCoords.ZERO, design.getElevators().getFirst().hex());
        assertEquals(Map.of(0, 32, 1, 32, 2, 32), design.getElevators().getFirst().exits(), "The old roof follows the new roof");
    }
}
