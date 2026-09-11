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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 */
package megameklab.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import megamek.client.ui.clientGUI.calculationReport.DummyCalculationReport;
import megamek.common.battleArmor.BattleArmor;
import megamek.common.equipment.Engine;
import megamek.common.equipment.EquipmentType;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekFileParser;
import megamek.common.units.ConvInfantry;
import megamek.common.units.Entity;
import megamek.common.units.EntityMovementMode;
import megamek.common.units.SupportTank;
import megamek.common.weapons.infantry.InfantryWeapon;
import megameklab.testing.util.InitializeTypes;
import megameklab.util.UnitUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(InitializeTypes.class)
class SVGMassPrinterUnitDataTest {
    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -1, 0, 1, 1234 })
    void onlyPositiveMulIdsAreExported(int mulId) throws Exception {
        ConvInfantry infantry = infantry();
        infantry.setMulId(mulId);
        SVGMassPrinter.UnitData data = metadata(infantry, "Infantry");
        var json = new ObjectMapper().valueToTree(data);
        assertFalse(json.has("id"));
        assertTrue(json.has("mul1id"));
        if (mulId > 0) {
            assertEquals(mulId, data.mul1id);
            assertEquals(mulId, json.get("mul1id").intValue());
        } else {
            assertNull(data.mul1id);
            assertTrue(json.get("mul1id").isNull());
        }
        assertEquals(mulId, infantry.getMulId());
    }

    @Test
    void manualBattleValueOverridesOnlyTheExportedScalar() {
        ConvInfantry infantry = infantry();
        int calculated = infantry.getBvCalculator().calculateBV(true, true, new DummyCalculationReport());
        infantry.setManualBV(calculated + 123);
        infantry.setUseManualBV(true);
        SVGMassPrinter.UnitData data = metadata(infantry, "Infantry");
        assertEquals(infantry.calculateBattleValue(true, true), data.bv);
        assertEquals(infantry.getManualBV(), new ObjectMapper().valueToTree(data).get("bv").intValue());
        assertNotEquals(calculated, data.bv);
        assertEquals(infantry.getBvCalculator().getOffensiveSpeedFactorMultiplier(), data.offSpeedFactor);
        infantry.setUseManualBV(false);
        assertEquals(calculated, metadata(infantry, "Infantry").bv);
    }

    @ParameterizedTest
    @CsvSource({ "37.5, 1, 37.5", "100, 6, 500" })
    void supportPowerRatingUsesTonnageAndCruiseMp(double tons, int walkMp, double expected) {
        SupportTank vehicle = new SupportTank();
        vehicle.setChassis("Support power rating test");
        vehicle.setModel("");
        vehicle.setWeight(tons);
        vehicle.setMovementMode(EntityMovementMode.TRACKED);
        vehicle.setOriginalWalkMP(walkMp);
        vehicle.setEngine(new Engine(20, Engine.COMBUSTION_ENGINE, Engine.TANK_ENGINE | Engine.SUPPORT_VEE_ENGINE));
        assertEquals(expected, metadata(vehicle, "Tank").engineRating);
        assertEquals(20, vehicle.getEngine().getRating());
    }

    @Test
    void loadedBattleArmorCalculationsKeepTheirStartingAmmunition() throws Exception {
        Entity entity = new MekFileParser(new File("testresources/Longinus metadata lifecycle.blk")).getEntity();
        SVGMassPrinter.UnitData data = new SVGMassPrinter.UnitData(entity);
        assertTrue(((BattleArmor) entity).isBurdened());
        assertEquals(0, data.jump);
        assertEquals(203, data.bv);
        UnitUtil.updateLoadedUnit(entity);
        data.readMetadata(summary("BattleArmor"), entity, new RecordSheetOptions());
        assertEquals(3, entity.getJumpMP());
        assertEquals(231, entity.calculateBattleValue(true, true));
        assertEquals(0, data.jump);
        assertEquals(0, data.jump2);
        assertEquals(203, data.bv);
        assertTrue(data.comp.stream().noneMatch(entry -> entry.p == Entity.LOC_NONE && "X".equals(entry.t)));
    }

    @Test
    void loadedSmallCraftCalculationsExcludeConstructionOnlyEcmAndCrewChanges() throws Exception {
        Entity entity = new MekFileParser(new File("testresources/Dragonstar metadata lifecycle.blk")).getEntity();
        SVGMassPrinter.UnitData data = new SVGMassPrinter.UnitData(entity);
        double loadedWeight = data.loadoutTons;
        assertEquals(9_192_200, data.cost);
        UnitUtil.updateLoadedUnit(entity);
        data.readMetadata(summary("Small Craft"), entity, new RecordSheetOptions());
        // Editor crew reconciliation can change this transient cost as well as adding ECM.
        assertTrue(entity.getCost(false) > data.cost);
        assertEquals(9_192_200, data.cost);
        assertEquals(loadedWeight, data.loadoutTons);
        assertTrue(data.comp.stream().anyMatch(entry -> "IS BA ECM".equals(entry.id)));
    }

    private SVGMassPrinter.UnitData metadata(Entity entity, String unitType) {
        return new SVGMassPrinter.UnitData(summary(unitType), entity, new RecordSheetOptions());
    }

    private MekSummary summary(String unitType) {
        MekSummary summary = new MekSummary();
        summary.setUnitType(unitType);
        return summary;
    }

    private ConvInfantry infantry() {
        ConvInfantry infantry = new ConvInfantry();
        infantry.setChassis("Metadata export test");
        infantry.setModel("");
        infantry.setSquadSize(5);
        infantry.setSquadCount(4);
        infantry.setPrimaryWeapon((InfantryWeapon) EquipmentType.get("InfantryAssaultRifle"));
        infantry.autoSetInternal();
        return infantry;
    }
}
