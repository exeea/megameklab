/*
 * MegaMekLab - Copyright (C) 2008
 *
 * Original author - jtighe (torren@users.sourceforge.net)
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */

package megameklab.com.ui.Mek.tabs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import megamek.client.ui.GBC;
import megamek.common.BipedMech;
import megamek.common.CriticalSlot;
import megamek.common.Engine;
import megamek.common.Entity;
import megamek.common.EquipmentType;
import megamek.common.LandAirMech;
import megamek.common.LocationFullException;
import megamek.common.Mech;
import megamek.common.MiscType;
import megamek.common.Mounted;
import megamek.common.QuadMech;
import megamek.common.TechConstants;
import megamek.common.TripodMech;
import megamek.common.verifier.EntityVerifier;
import megamek.common.verifier.TestMech;
import megameklab.com.ui.Mek.views.ArmorView;
import megameklab.com.ui.Mek.views.SummaryView;
import megameklab.com.util.ITab;
import megameklab.com.util.RefreshListener;
import megameklab.com.util.UnitUtil;

public class StructureTab extends ITab implements ActionListener, KeyListener,
        ChangeListener, ItemListener {

    /**
     *
     */
    private static final long serialVersionUID = -6756011847500605874L;

    private static final String ENGINESTANDARD = "Standard";
    private static final String ENGINEXL = "XL";
    private static final String ENGINELIGHT = "Light";
    private static final String ENGINECOMPACT = "Compact";
    private static final String ENGINEFISSION = "Fission";
    private static final String ENGINEFUELCELL = "Fuel Cell";
    private static final String ENGINEXXL = "XXL";
    private static final String ENGINEICE = "I.C.E";

    String[] isEngineTypes = { ENGINESTANDARD, ENGINEXL, ENGINELIGHT,
            ENGINECOMPACT, ENGINEFISSION, ENGINEFUELCELL, ENGINEXXL };
    String[] isIndustrialEngineTypes = { ENGINESTANDARD, ENGINEICE,
            ENGINEFUELCELL, ENGINEFISSION };
    String[] clanEngineTypes = { ENGINESTANDARD, ENGINEXL, ENGINEFUELCELL,
            ENGINEXXL };
    String[] clanIndustrialEngineTypes = { ENGINESTANDARD, ENGINEICE,
            ENGINEFUELCELL, ENGINEFISSION };
    String[] isSuperHeavyEngineTypes = { ENGINESTANDARD, ENGINEXL, ENGINELIGHT,
            ENGINECOMPACT, ENGINEXXL };
    private int clanEngineFlag = 0;

    JPanel panInfo;
    JPanel panChassis;
    JPanel panArmor;
    JPanel panMovement;
    JPanel panHeat;
    SummaryView panSummary;

    private ArmorView armor;

    JComboBox<String> engineType = new JComboBox<String>(isEngineTypes);
    String[] enhancements = { "None", "MASC", "TSM" };
    JComboBox<String> enhancement = new JComboBox<String>(enhancements);
    JSpinner walkMP;
    JTextField runMP;
    JSpinner jumpMP;
    JComboBox<String> gyroType = new JComboBox<String>(Mech.GYRO_SHORT_STRING);
    JSpinner weightClass;
    JComboBox<String> cockpitType = new JComboBox<String>(
            Mech.COCKPIT_SHORT_STRING);
    String[] clanHeatSinkTypes = { "Single", "Double", "Laser" };
    String[] isHeatSinkTypes = { "Single", "Double", "Compact" };
    JComboBox<String> heatSinkType = new JComboBox<String>(isHeatSinkTypes);
    JSpinner heatSinkNumber;
    JSpinner baseChassisHeatSinks;
    String[] techTypes = { "Inner Sphere", "Clan", "Mixed Inner Sphere",
            "Mixed Clan" };
    JComboBox<String> techType = new JComboBox<String>(techTypes);
    String[] isTechLevels = { "Introductory", "Standard", "Advanced",
            "Experimental", "Unoffical" };
    String[] clanTechLevels = { "Standard", "Advanced", "Experimental",
            "Unoffical" };
    String[] motiveTypes = { "Biped", "Quad", "Tripod" };
    JComboBox<String> motiveType = new JComboBox<String>(motiveTypes);
    JComboBox<String> techLevel = new JComboBox<String>(isTechLevels);
    String[] jjTypes = { "Standard", "Improved", "Improved Prototype",
            "Mechanical Boosters" };
    JComboBox<String> jjType = new JComboBox<String>(jjTypes);
    JTextField era = new JTextField(3);
    JTextField source = new JTextField(3);
    RefreshListener refresh = null;
    JCheckBox omniCB = new JCheckBox("Omni");
    JCheckBox lamCB = new JCheckBox("LAM");
    JCheckBox fullHeadEjectCB = new JCheckBox("Full Head Ejection");
    JComboBox<String> structureCombo = new JComboBox<String>(
            EquipmentType.structureNames);
    JPanel masterPanel;
    JTextField manualBV = new JTextField(3);
    private JTextField chassis = new JTextField(5);
    private JTextField model = new JTextField(5);
    private JLabel lblFreeSinks = new JLabel("");

    private JComboBox<String> armorCombo = new JComboBox<String>();
    private JButton maximizeArmorButton = new JButton("Maximize Armor");
    private JSpinner armorTonnage;

    public StructureTab(Mech unit) {
        this.unit = unit;
        armor = new ArmorView(getMech());
        setLayout(new BorderLayout());
        setUpPanels();
        this.add(masterPanel, BorderLayout.CENTER);
        populateChoices(false);
        refresh();
    }

    private void setUpPanels() {
        masterPanel = new JPanel(new GridBagLayout());
        panInfo = new JPanel(new GridBagLayout());
        panChassis = new JPanel(new GridBagLayout());
        panArmor = new JPanel(new GridBagLayout());
        panMovement = new JPanel(new GridBagLayout());
        panHeat = new JPanel(new GridBagLayout());
        panSummary = new SummaryView(getMech());

        GridBagConstraints gbc;

        Dimension spinnerSize = new Dimension(55, 25);

        walkMP = new JSpinner(new SpinnerNumberModel(1, 1, 25, 1));
        ((JSpinner.DefaultEditor) walkMP.getEditor()).setSize(spinnerSize);
        ((JSpinner.DefaultEditor) walkMP.getEditor())
                .setMaximumSize(spinnerSize);
        ((JSpinner.DefaultEditor) walkMP.getEditor())
                .setPreferredSize(spinnerSize);
        ((JSpinner.DefaultEditor) walkMP.getEditor())
                .setMinimumSize(spinnerSize);
        ((JSpinner.DefaultEditor) walkMP.getEditor()).getTextField()
                .setEditable(false);
        runMP = new JTextField();
        runMP.setEditable(false);
        setFieldSize(runMP, spinnerSize);
        runMP.setHorizontalAlignment(SwingConstants.RIGHT);

        jumpMP = new JSpinner(new SpinnerNumberModel(0, 0, 25, 1));
        ((JSpinner.DefaultEditor) jumpMP.getEditor()).setSize(spinnerSize);
        ((JSpinner.DefaultEditor) jumpMP.getEditor())
                .setMaximumSize(spinnerSize);
        ((JSpinner.DefaultEditor) jumpMP.getEditor())
                .setPreferredSize(spinnerSize);
        ((JSpinner.DefaultEditor) jumpMP.getEditor())
                .setMinimumSize(spinnerSize);
        ((JSpinner.DefaultEditor) jumpMP.getEditor()).getTextField()
                .setEditable(false);

        weightClass = new JSpinner(new SpinnerNumberModel(20, 10, 100, 5));
        ((JSpinner.DefaultEditor) weightClass.getEditor()).getTextField()
                .setEditable(false);

        heatSinkNumber = new JSpinner(new SpinnerNumberModel(0, 0, 50, 1));
        spinnerSize = new Dimension(40, 25);
        ((JSpinner.DefaultEditor) heatSinkNumber.getEditor())
                .setSize(spinnerSize);
        ((JSpinner.DefaultEditor) heatSinkNumber.getEditor())
                .setMaximumSize(spinnerSize);
        ((JSpinner.DefaultEditor) heatSinkNumber.getEditor())
                .setPreferredSize(spinnerSize);
        ((JSpinner.DefaultEditor) heatSinkNumber.getEditor())
                .setMinimumSize(spinnerSize);
        ((JSpinner.DefaultEditor) heatSinkNumber.getEditor()).getTextField()
                .setEditable(false);

        baseChassisHeatSinks = new JSpinner(new SpinnerNumberModel(0, 0, 50, 1));
        ((JSpinner.DefaultEditor) baseChassisHeatSinks.getEditor())
                .setSize(spinnerSize);
        ((JSpinner.DefaultEditor) baseChassisHeatSinks.getEditor())
                .setMaximumSize(spinnerSize);
        ((JSpinner.DefaultEditor) baseChassisHeatSinks.getEditor())
                .setPreferredSize(spinnerSize);
        ((JSpinner.DefaultEditor) baseChassisHeatSinks.getEditor())
                .setMinimumSize(spinnerSize);
        ((JSpinner.DefaultEditor) baseChassisHeatSinks.getEditor())
                .getTextField().setEditable(false);

        armorTonnage = new JSpinner(new SpinnerNumberModel(
                unit.getArmorWeight(), 0.0,
                UnitUtil.getMaximumArmorTonnage(unit), 0.5));
        spinnerSize = new Dimension(45, 25);
        ((JSpinner.DefaultEditor) armorTonnage.getEditor())
                .setSize(spinnerSize);
        ((JSpinner.DefaultEditor) armorTonnage.getEditor())
                .setMaximumSize(spinnerSize);
        ((JSpinner.DefaultEditor) armorTonnage.getEditor())
                .setPreferredSize(spinnerSize);
        ((JSpinner.DefaultEditor) armorTonnage.getEditor())
                .setMinimumSize(spinnerSize);
        ((JSpinner.DefaultEditor) armorTonnage.getEditor()).getTextField()
                .setEditable(false);

        // lblFreeSinks.setFont(new Font(lblFreeSinks.getName(), Font.PLAIN,
        // 10));

        chassis.setText(unit.getChassis());
        model.setText(unit.getModel());

        Dimension labelSize = new Dimension(110, 25);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 1, 2);
        panInfo.add(createLabel("Chassis:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panInfo.add(chassis, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panInfo.add(createLabel("Model:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panInfo.add(model, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panInfo.add(createLabel("Year:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panInfo.add(era, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panInfo.add(createLabel("Source/Era:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        panInfo.add(source, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panInfo.add(createLabel("Tech:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 4;
        panInfo.add(techType, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        panInfo.add(createLabel("Tech Level:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 5;
        panInfo.add(techLevel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        panInfo.add(createLabel("Manual BV:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 6;
        panInfo.add(manualBV, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panChassis.add(createLabel("Tonnage:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panChassis.add(weightClass, gbc);
        gbc.gridx = 2;
        gbc.gridy = 0;
        panChassis.add(omniCB, gbc);
        gbc.gridx = 3;
        gbc.gridy = 0;
        panChassis.add(lamCB, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panChassis.add(createLabel("Motive Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        panChassis.add(motiveType, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panChassis.add(createLabel("Structure Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        panChassis.add(structureCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        panChassis.add(createLabel("Engine Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        panChassis.add(engineType, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        panChassis.add(createLabel("Gyro Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        panChassis.add(gyroType, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        panChassis.add(createLabel("Cockpit Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 3;
        panChassis.add(cockpitType, gbc);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        panChassis.add(createLabel("Enhancements:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 3;
        panChassis.add(enhancement, gbc);
        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.gridwidth = 3;
        panChassis.add(fullHeadEjectCB, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        panArmor.add(createLabel("Armor Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panArmor.add(armorCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panArmor.add(createLabel("Armor Tonnage:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panArmor.add(armorTonnage, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        panArmor.add(maximizeArmorButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        panMovement.add(createLabel("Walking MP:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        panMovement.add(walkMP, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        panMovement.add(createLabel("Running MP:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panMovement.add(runMP, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        panMovement.add(createLabel("Jumping MP:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panMovement.add(jumpMP, gbc);
        gbc.gridx = 0;
        gbc.gridy = 3;
        panMovement.add(createLabel("Jump Jet Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panMovement.add(jjType, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panHeat.add(createLabel("Sink Type:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panHeat.add(heatSinkType, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panHeat.add(createLabel("Number:", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        panHeat.add(heatSinkNumber, gbc);
        gbc.gridx = 2;
        gbc.gridy = 1;
        panHeat.add(lblFreeSinks, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        panHeat.add(createLabel("Base (Omni):", labelSize), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panHeat.add(baseChassisHeatSinks, gbc);

        Dimension comboSize = new Dimension(180, 25);

        setFieldSize(era, comboSize);
        setFieldSize(manualBV, comboSize);
        setFieldSize(source, comboSize);
        setFieldSize(techType, comboSize);
        setFieldSize(armorCombo, comboSize);
        setFieldSize(techLevel, comboSize);
        setFieldSize(heatSinkType, comboSize);
        setFieldSize(engineType, comboSize);
        setFieldSize(enhancement, comboSize);
        setFieldSize(gyroType, comboSize);
        setFieldSize(cockpitType, comboSize);
        setFieldSize(structureCombo, comboSize);
        setFieldSize(jjType, comboSize);
        setFieldSize(model, comboSize);
        setFieldSize(chassis, comboSize);

        JPanel leftPanel = new JPanel();
        JPanel rightPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

        leftPanel.add(panInfo);
        leftPanel.add(panChassis);
        leftPanel.add(panHeat);
        leftPanel.add(Box.createGlue());
        rightPanel.add(panArmor);
        rightPanel.add(panMovement);
        rightPanel.add(panSummary);
        leftPanel.add(Box.createVerticalGlue());

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        masterPanel.add(leftPanel, gbc);
        gbc.gridx = 1;
        masterPanel.add(rightPanel, gbc);
        gbc.gridx = 2;
        masterPanel.add(armor, gbc);

        panInfo.setBorder(BorderFactory.createTitledBorder("Basic Information"));
        panChassis.setBorder(BorderFactory.createTitledBorder("Chassis"));
        panMovement.setBorder(BorderFactory.createTitledBorder("Movement"));
        panHeat.setBorder(BorderFactory.createTitledBorder("Heat Sinks"));
        panArmor.setBorder(BorderFactory.createTitledBorder("Armor"));
        panSummary.setBorder(BorderFactory.createTitledBorder("Summary"));
        armor.setBorder(BorderFactory.createTitledBorder("Armor Allocation"));

    }

    public void refresh() {
        removeAllListeners();
        if (getMech().isPrimitive()) {
            getMech().setOmni(false);
            omniCB.setEnabled(false);
        } else {
            omniCB.setEnabled(true);
        }
        omniCB.setSelected(getMech().isOmni());
        if (unit instanceof TripodMech) {
            motiveType.setSelectedIndex(2);
        }
        if (unit instanceof QuadMech) {
            motiveType.setSelectedIndex(1);
        } else {
            motiveType.setSelectedItem(0);
        }
        lamCB.setSelected(unit instanceof LandAirMech);
        fullHeadEjectCB.setSelected(getMech().hasFullHeadEject());
        era.setText(Integer.toString(getMech().getYear()));
        source.setText(getMech().getSource());
        manualBV.setText(Integer.toString(Math.max(0, getMech().getManualBV())));
        weightClass.setValue((int) (getMech().getWeight()));

        int totalSinks = getMech().heatSinks(false);
        int freeSinks = getMech().getEngine().getWeightFreeEngineHeatSinks();
        ((SpinnerNumberModel) heatSinkNumber.getModel()).setMinimum(freeSinks);
        heatSinkNumber.setValue(totalSinks);

        ((SpinnerNumberModel) baseChassisHeatSinks.getModel())
                .setMaximum(getMech().getEngine().integralHeatSinkCapacity(
                        getMech().hasCompactHeatSinks()));

        baseChassisHeatSinks.setValue(Math.max(0, getMech().getEngine()
                .getBaseChassisHeatSinks(getMech().hasCompactHeatSinks())));

        if (getMech().isOmni()) {
            baseChassisHeatSinks.setEnabled(true);
            getMech().getEngine().setBaseChassisHeatSinks(
                    Math.max(0, (Integer) baseChassisHeatSinks.getValue()));
        } else {
            baseChassisHeatSinks.setEnabled(false);
            getMech().getEngine().setBaseChassisHeatSinks(-1);
        }

        lblFreeSinks.setText("Engine Free: "
                + UnitUtil.getBaseChassisHeatSinks(getMech(), getMech()
                        .hasCompactHeatSinks()));

        if (getMech().isClan()) {
            techLevel.removeAllItems();
            for (String item : clanTechLevels) {
                techLevel.addItem(item);
            }
        } else {
            techLevel.removeAllItems();
            for (String item : isTechLevels) {
                techLevel.addItem(item);
            }
        }
        engineType.setSelectedIndex(convertEngineType(getMech().getEngine()
                .getEngineType()));

        setEnhancementCombo();
        setStructureCombo();
        if (getMech().hasPatchworkArmor()) {
            setArmorCombo(EquipmentType.T_ARMOR_PATCHWORK);
        } else {
            setArmorCombo(getMech().getArmorType(0));
        }

        cockpitType.setSelectedItem(Mech.COCKPIT_SHORT_STRING[getMech()
                .getCockpitType()]);
        gyroType.setSelectedIndex(getMech().getGyroType());

        if (getMech().isMixedTech()) {
            if (getMech().isClan()) {

                techType.setSelectedIndex(3);
                if (getMech().getTechLevel() >= TechConstants.T_CLAN_UNOFFICIAL) {
                    techLevel.setSelectedIndex(3);
                } else if (getMech().getTechLevel() >= TechConstants.T_CLAN_EXPERIMENTAL) {
                    techLevel.setSelectedIndex(2);
                } else {
                    techLevel.setSelectedIndex(1);
                }
            } else {
                techType.setSelectedIndex(2);

                if (getMech().getTechLevel() >= TechConstants.T_IS_UNOFFICIAL) {
                    techLevel.setSelectedIndex(4);
                } else if (getMech().getTechLevel() >= TechConstants.T_IS_EXPERIMENTAL) {
                    techLevel.setSelectedIndex(3);
                } else {
                    techLevel.setSelectedIndex(2);
                }
            }
        } else if (getMech().isClan()) {

            techType.setSelectedIndex(1);
            if (getMech().getTechLevel() >= TechConstants.T_CLAN_UNOFFICIAL) {
                techLevel.setSelectedIndex(3);
            } else if (getMech().getTechLevel() >= TechConstants.T_CLAN_EXPERIMENTAL) {
                techLevel.setSelectedIndex(2);
            } else if (getMech().getTechLevel() >= TechConstants.T_CLAN_ADVANCED) {
                techLevel.setSelectedIndex(1);
            } else {
                techLevel.setSelectedIndex(0);
            }
        } else {
            techType.setSelectedIndex(0);

            if (getMech().getTechLevel() >= TechConstants.T_IS_UNOFFICIAL) {
                techLevel.setSelectedIndex(4);
            } else if (getMech().getTechLevel() >= TechConstants.T_IS_EXPERIMENTAL) {
                techLevel.setSelectedIndex(3);
            } else if (getMech().getTechLevel() >= TechConstants.T_IS_ADVANCED) {
                techLevel.setSelectedIndex(2);
            } else if (getMech().getTechLevel() >= TechConstants.T_IS_TW_NON_BOX) {
                techLevel.setSelectedIndex(1);
            } else {
                techLevel.setSelectedIndex(0);
            }

        }

        setHeatSinkCombo();

        walkMP.setValue(getMech().getOriginalWalkMP());
        jumpMP.setValue(getMech().getOriginalJumpMP());
        if ((getJumpJetType() == Mech.JUMP_IMPROVED)
                || (getJumpJetType() == Mech.JUMP_PROTOTYPE)) {
            ((SpinnerNumberModel) jumpMP.getModel()).setMaximum(getMech()
                    .getRunMP());
        } else if (getJumpJetType() == Mech.JUMP_BOOSTER) {
            ((SpinnerNumberModel) jumpMP.getModel()).setMaximum(20);
        } else {
            ((SpinnerNumberModel) jumpMP.getModel()).setMaximum(getMech()
                    .getOriginalWalkMP());
        }
        runMP.setText(getMech().getRunMPasString());

        setJumpJetCombo();

        ((SpinnerNumberModel) armorTonnage.getModel()).setMaximum(UnitUtil
                .getMaximumArmorTonnage(unit));
        ((SpinnerNumberModel) armorTonnage.getModel()).setValue(Math.min(
                UnitUtil.getMaximumArmorTonnage(unit),
                unit.getLabArmorTonnage()));
        if (unit.hasPatchworkArmor()) {
            TestMech testMech = new TestMech(
                    getMech(),
                    new EntityVerifier(new File(
                            "data/mechfiles/UnitVerifierOptions.xml")).mechOption,
                    null);
            armorTonnage.setEnabled(false);
            armorTonnage.getModel()
                    .setValue(testMech.getWeightAllocatedArmor());
            unit.setArmorTonnage(testMech.getWeightAllocatedArmor());
            maximizeArmorButton.setEnabled(false);
        } else {
            armorTonnage.setEnabled(true);
            maximizeArmorButton.setEnabled(true);
        }
        armor.updateUnit(unit);
        armor.refresh();
        panSummary.updateUnit(unit);
        panSummary.refresh();
        addAllListeners();

    }

    public JLabel createLabel(String text, Dimension maxSize) {

        JLabel label = new JLabel(text, SwingConstants.RIGHT);

        setFieldSize(label, maxSize);
        return label;
    }

    public void setFieldSize(JComponent box, Dimension maxSize) {
        box.setPreferredSize(maxSize);
        box.setMaximumSize(maxSize);
        box.setMinimumSize(maxSize);
    }

    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            removeAllListeners();
            @SuppressWarnings("unchecked")
            JComboBox<String> combo = (JComboBox<String>) e.getSource();

            // we need to do cockpit also here, because cockpitType
            // determines
            // if a mech is primitive and thus needs a larger engine
            if (combo.equals(engineType) || combo.equals(cockpitType)) {
                if (combo.equals(cockpitType)) {
                    getMech().setCockpitType(
                            Mech.getCockpitTypeForString(combo
                                    .getSelectedItem().toString()));
                    getMech().clearCockpitCrits();
                    switch (getMech().getCockpitType()) {
                        case Mech.COCKPIT_COMMAND_CONSOLE:
                            getMech().addCommandConsole();
                            break;
                        case Mech.COCKPIT_DUAL:
                            getMech().addDualCockpit();
                            break;
                        case Mech.COCKPIT_SMALL:
                            getMech().addSmallCockpit();
                            break;
                        case Mech.COCKPIT_INTERFACE:
                            getMech().addInterfaceCockpit();
                            break;
                        case Mech.COCKPIT_TORSO_MOUNTED:
                            removeSystemCrits(Mech.SYSTEM_ENGINE);
                            getMech().addEngineCrits();
                            getMech().addTorsoMountedCockpit();
                            break;
                        case Mech.COCKPIT_INDUSTRIAL:
                            getMech().addIndustrialCockpit();
                            getMech().setArmorType(
                                    EquipmentType.T_ARMOR_INDUSTRIAL);
                            break;
                        case Mech.COCKPIT_PRIMITIVE:
                            getMech().addPrimitiveCockpit();
                            getMech().setArmorType(
                                    EquipmentType.T_ARMOR_PRIMITIVE);
                            break;
                        case Mech.COCKPIT_PRIMITIVE_INDUSTRIAL:
                            getMech().addIndustrialPrimitiveCockpit();
                            getMech().setArmorType(
                                    EquipmentType.T_ARMOR_COMMERCIAL);
                            break;
                        default:
                            getMech().addCockpit();
                    }
                    armor.resetArmorPoints();
                    populateChoices(true);
                }
                int rating = ((Integer) walkMP.getValue())
                        * ((Integer) weightClass.getValue());
                if (getMech().isPrimitive()) {
                    double dRating = ((Integer) walkMP.getValue())
                            * ((Integer) weightClass.getValue());
                    dRating *= 1.2;
                    if ((dRating % 5) != 0) {
                        dRating = (dRating - (dRating % 5)) + 5;
                    }
                    rating = (int) dRating;
                }
                if ((rating > 400) && (getMech().getGyroType() == Mech.GYRO_XL)) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "That speed would require a large engine, which doesn't fit",
                                    "Bad Engine", JOptionPane.ERROR_MESSAGE);
                }
                if (rating > 500) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "That speed would create an engine with a rating over 500.",
                                    "Bad Engine Rating",
                                    JOptionPane.ERROR_MESSAGE);
                } else {
                    setNewEngine(rating, convertEngineType(engineType
                            .getSelectedItem().toString()));
                }
            } else if (combo.equals(armorCombo)) {
                if (!unit.hasPatchworkArmor()) {
                    UnitUtil.removeISorArmorMounts(unit, false);
                }
                createArmorMountsAndSetArmorType();
                if (!unit.hasPatchworkArmor()) {
                    armor.resetArmorPoints();
                }
            } else if (combo.equals(structureCombo)) {
                UnitUtil.removeISorArmorMounts(getMech(), true);
                createISMounts();
                populateChoices(true);
            } else if (combo.equals(gyroType)) {
                if (getMech().getEngine().hasFlag(Engine.LARGE_ENGINE)
                        && (combo.getSelectedIndex() == Mech.GYRO_XL)) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "A XL gyro does not fit with a large engine installed",
                                    "Bad Gyro", JOptionPane.ERROR_MESSAGE);
                } else {
                    getMech().setGyroType(combo.getSelectedIndex());
                    getMech().clearGyroCrits();

                    switch (getMech().getGyroType()) {
                        case Mech.GYRO_COMPACT:
                            getMech().addCompactGyro();
                            getMech().clearEngineCrits();
                            getMech().addEngineCrits();
                            break;
                        case Mech.GYRO_HEAVY_DUTY:
                            getMech().addHeavyDutyGyro();
                            break;
                        case Mech.GYRO_XL:
                            getMech().addXLGyro();
                            break;
                        case Mech.GYRO_NONE:
                            UnitUtil.compactCriticals(unit, Mech.LOC_CT);
                            break;
                        default:
                            getMech().addGyro();
                    }
                }
            } else if (combo.equals(heatSinkType)) {
                UnitUtil.updateHeatSinks(getMech(), (Integer) heatSinkNumber
                        .getValue(), heatSinkType.getSelectedItem().toString());
            } else if (combo.equals(jjType)) {
                if (getJumpJetType() == Mech.JUMP_STANDARD) {
                    // check to make sure we are not over the number of jets
                    // we are allowed
                    if (((Integer) jumpMP.getValue()) > getMech()
                            .getOriginalWalkMP()) {
                        jumpMP.setValue(getMech().getOriginalWalkMP());
                    }
                } else if ((getJumpJetType() == Mech.JUMP_IMPROVED)
                        || (getJumpJetType() == Mech.JUMP_PROTOTYPE)) {
                    if (((Integer) jumpMP.getValue()) > getMech().getRunMP()) {
                        jumpMP.setValue(getMech().getRunMP());
                    }
                }
                UnitUtil.updateJumpJets(getMech(), (Integer) jumpMP.getValue(),
                        getJumpJetType());
            } else if (combo.equals(enhancement)) {
                UnitUtil.updateEnhancements(getMech(), hasMASC(), hasTSM());
            } else if (combo.equals(techLevel)) {
                int unitTechLevel = techLevel.getSelectedIndex();

                if (getMech().isClan()) {
                    switch (unitTechLevel) {
                        case 0:
                            getMech().setTechLevel(TechConstants.T_CLAN_TW);
                            techType.setSelectedIndex(1);
                            break;
                        case 1:
                            getMech().setTechLevel(
                                    TechConstants.T_CLAN_ADVANCED);
                            break;
                        case 2:
                            getMech().setTechLevel(
                                    TechConstants.T_CLAN_EXPERIMENTAL);
                            break;
                        case 3:
                            getMech().setTechLevel(
                                    TechConstants.T_CLAN_UNOFFICIAL);
                            break;
                        default:
                            getMech().setTechLevel(TechConstants.T_CLAN_TW);
                            break;
                    }

                } else {
                    switch (unitTechLevel) {
                        case 0:
                            getMech()
                                    .setTechLevel(TechConstants.T_INTRO_BOXSET);
                            techType.setSelectedIndex(0);
                            break;
                        case 1:
                            getMech().setTechLevel(
                                    TechConstants.T_IS_TW_NON_BOX);
                            techType.setSelectedIndex(0);
                            break;
                        case 2:
                            getMech().setTechLevel(TechConstants.T_IS_ADVANCED);
                            break;
                        case 3:
                            getMech().setTechLevel(
                                    TechConstants.T_IS_EXPERIMENTAL);
                            break;
                        default:
                            getMech().setTechLevel(
                                    TechConstants.T_IS_UNOFFICIAL);
                            break;
                    }
                }
                populateChoices(true);
                armor.resetArmorPoints();
                UnitUtil.checkEquipmentByTechLevel(unit);
            } else if (combo.equals(techType)) {
                if ((techType.getSelectedIndex() == 1)
                        && (!getMech().isClan() || getMech().isMixedTech())) {
                    techLevel.removeAllItems();
                    for (String item : clanTechLevels) {
                        techLevel.addItem(item);
                    }
                    getMech().setMixedTech(false);
                    if (!getMech().isClan()) {
                        int level = TechConstants
                                .getOppositeTechLevel(getMech().getTechLevel());
                        getMech().setTechLevel(level);
                        getMech().setStructureTechLevel(level);
                    }
                } else if ((techType.getSelectedIndex() == 0)
                        && (getMech().isClan() || getMech().isMixedTech())) {
                    techLevel.removeAllItems();
                    for (String item : isTechLevels) {
                        techLevel.addItem(item);
                    }
                    getMech().setMixedTech(false);
                    if (getMech().isClan()) {
                        int level = TechConstants
                                .getOppositeTechLevel(getMech().getTechLevel());
                        getMech().setTechLevel(level);
                        getMech().setStructureTechLevel(level);
                    }
                } else if ((techType.getSelectedIndex() == 2)
                        && (!getMech().isMixedTech() || getMech().isClan())) {
                    techLevel.removeAllItems();
                    for (String item : isTechLevels) {
                        techLevel.addItem(item);
                    }
                    if (unit.getYear() < 3090) {
                        // before 3090, mixed tech is experimental
                        if ((unit.getTechLevel() != TechConstants.T_IS_UNOFFICIAL)) {
                            unit.setTechLevel(TechConstants.T_IS_EXPERIMENTAL);
                        }
                    } else if (unit.getYear() < 3145) {
                        // between 3090 and 3145, mixed tech is advanced
                        if ((unit.getTechLevel() != TechConstants.T_IS_UNOFFICIAL)
                                && (unit.getTechLevel() != TechConstants.T_IS_EXPERIMENTAL)) {
                            unit.setTechLevel(TechConstants.T_IS_ADVANCED);
                        }
                    } else {
                        // from 3145 on, mixed tech is tourney legal
                        if ((unit.getTechLevel() != TechConstants.T_IS_UNOFFICIAL)
                                && (unit.getTechLevel() != TechConstants.T_IS_EXPERIMENTAL)
                                && (unit.getTechLevel() != TechConstants.T_IS_TW_NON_BOX)) {
                            unit.setTechLevel(TechConstants.T_IS_TW_NON_BOX);
                        }
                    }
                    unit.setMixedTech(true);

                } else if ((techType.getSelectedIndex() == 3)
                        && (!unit.isMixedTech() || !unit.isClan())) {
                    techLevel.removeAllItems();
                    for (String item : clanTechLevels) {
                        techLevel.addItem(item);
                    }
                    if (unit.getYear() < 3090) {
                        // before 3090, mixed tech is experimental
                        if ((unit.getTechLevel() != TechConstants.T_CLAN_UNOFFICIAL)) {
                            unit.setTechLevel(TechConstants.T_CLAN_EXPERIMENTAL);
                        }
                    } else if (unit.getYear() < 3145) {
                        // between 3090 and 3145, mixed tech is advanced
                        if ((unit.getTechLevel() != TechConstants.T_CLAN_UNOFFICIAL)
                                && (unit.getTechLevel() != TechConstants.T_CLAN_EXPERIMENTAL)) {
                            unit.setTechLevel(TechConstants.T_CLAN_ADVANCED);
                        }
                    } else {
                        // from 3145 on, mixed tech is tourney legal
                        if ((unit.getTechLevel() != TechConstants.T_CLAN_UNOFFICIAL)
                                && (unit.getTechLevel() != TechConstants.T_CLAN_EXPERIMENTAL)
                                && (unit.getTechLevel() != TechConstants.T_CLAN_TW)) {
                            unit.setTechLevel(TechConstants.T_CLAN_TW);
                        }
                    }
                    getMech().setMixedTech(true);
                }
                if (!unit.hasPatchworkArmor()) {
                    UnitUtil.removeISorArmorMounts(unit, false);
                }
                createArmorMountsAndSetArmorType();
                populateChoices(true);
                armor.resetArmorPoints();
                UnitUtil.checkEquipmentByTechLevel(unit);
            }
            addAllListeners();
            refresh.refreshAll();
        }
    }

    public void actionPerformed(ActionEvent e) {
        removeAllListeners();
        if (e.getSource() instanceof JCheckBox) {
            JCheckBox check = (JCheckBox) e.getSource();
            if (check.equals(omniCB)) {
                getMech().setOmni(omniCB.isSelected());
                if (getMech().isOmni()) {
                    baseChassisHeatSinks.setEnabled(true);
                    getMech().getEngine().setBaseChassisHeatSinks(
                            10 + (Integer) baseChassisHeatSinks.getValue());
                    UnitUtil.removeOmniArmActuators(getMech());
                } else {
                    baseChassisHeatSinks.setEnabled(false);
                    getMech().getEngine().setBaseChassisHeatSinks(-1);
                }
                UnitUtil.updateAutoSinks(getMech(),
                        (String) heatSinkType.getSelectedItem());

            } else if (check.equals(fullHeadEjectCB)) {
                getMech().setFullHeadEject(fullHeadEjectCB.isSelected());
            }
        } else if (e.getSource() instanceof JButton) {
            if (e.getSource().equals(maximizeArmorButton)) {
                maximizeArmor();
            }
        }
        addAllListeners();
        refresh.refreshAll();
    }

    public void removeSystemCrits(int systemType) {

        for (int loc = 0; loc < getMech().locations(); loc++) {
            for (int slot = 0; slot < getMech().getNumberOfCriticals(loc); slot++) {
                CriticalSlot cs = getMech().getCritical(loc, slot);

                if ((cs == null) || (cs.getType() != CriticalSlot.TYPE_SYSTEM)) {
                    continue;
                }

                if (cs.getIndex() == systemType) {
                    getMech().setCritical(loc, slot, null);
                }
            }
        }
    }

    public void removeAllListeners() {
        maximizeArmorButton.removeActionListener(this);
        armorCombo.removeItemListener(this);
        gyroType.removeItemListener(this);
        engineType.removeItemListener(this);
        weightClass.removeChangeListener(this);
        cockpitType.removeItemListener(this);
        heatSinkNumber.removeChangeListener(this);
        heatSinkType.removeItemListener(this);
        walkMP.removeChangeListener(this);
        techLevel.removeItemListener(this);
        techType.removeItemListener(this);
        era.removeKeyListener(this);
        source.removeKeyListener(this);
        manualBV.removeKeyListener(this);
        omniCB.removeActionListener(this);
        motiveType.removeItemListener(this);
        lamCB.removeActionListener(this);
        fullHeadEjectCB.removeActionListener(this);
        structureCombo.removeItemListener(this);
        baseChassisHeatSinks.removeChangeListener(this);
        chassis.removeKeyListener(this);
        model.removeKeyListener(this);
        jumpMP.removeChangeListener(this);
        jjType.removeItemListener(this);
        enhancement.removeItemListener(this);
        armorTonnage.removeChangeListener(this);
    }

    public void addAllListeners() {
        maximizeArmorButton.addActionListener(this);
        armorCombo.addItemListener(this);
        gyroType.addItemListener(this);
        engineType.addItemListener(this);
        weightClass.addChangeListener(this);
        cockpitType.addItemListener(this);
        heatSinkNumber.addChangeListener(this);
        heatSinkType.addItemListener(this);
        walkMP.addChangeListener(this);
        techLevel.addItemListener(this);
        techType.addItemListener(this);
        era.addKeyListener(this);
        source.addKeyListener(this);
        manualBV.addKeyListener(this);
        omniCB.addActionListener(this);
        motiveType.addItemListener(this);
        lamCB.addActionListener(this);
        fullHeadEjectCB.addActionListener(this);
        structureCombo.addItemListener(this);
        baseChassisHeatSinks.addChangeListener(this);
        chassis.addKeyListener(this);
        model.addKeyListener(this);
        jumpMP.addChangeListener(this);
        jjType.addItemListener(this);
        enhancement.addItemListener(this);
        armorTonnage.addChangeListener(this);

    }

    public void keyPressed(KeyEvent e) {

    }

    public void keyReleased(KeyEvent e) {
        removeAllListeners();
        if (e.getSource().equals(era)) {
            try {
                int year = Integer.parseInt(era.getText());
                if (year < 1950) {
                    addAllListeners();
                    return;
                }
                getMech().setYear(Integer.parseInt(era.getText()));
            } catch (Exception ex) {
                getMech().setYear(3145);
            }
            refresh.refreshEquipment();
            populateChoices(true);
            armor.resetArmorPoints();
            UnitUtil.checkEquipmentByTechLevel(unit);
        } else if (e.getSource().equals(source)) {
            getMech().setSource(source.getText());
        } else if (e.getSource().equals(manualBV)) {
            if (!manualBV.getText().equals("-")) {
                int bv = Integer.parseInt(manualBV.getText());
                if (bv == 0) {
                    getMech().setUseManualBV(false);
                    getMech().setManualBV(0);
                } else {
                    getMech().setUseManualBV(true);
                    getMech().setManualBV(bv);
                }
            }
        } else if (e.getSource().equals(chassis)) {
            unit.setChassis(chassis.getText().trim());
        } else if (e.getSource().equals(model)) {
            unit.setModel(model.getText().trim());
        }
        addAllListeners();
        refresh.refreshPreview();
        refresh.refreshHeader();
    }

    public void keyTyped(KeyEvent e) {
    }

    public void addRefreshedListener(RefreshListener l) {
        refresh = l;
        armor.addRefreshedListener(l);
    }

    public boolean isQuad() {
        return motiveType.getSelectedIndex() == 1;
    }

    public boolean isTripod() {
        return motiveType.getSelectedIndex() == 2;
    }

    public boolean isLAM() {
        return lamCB.isSelected();
    }

    private void createISMounts() {
        int isCount = 0;
        int structType = structureCombo.getSelectedIndex();
        String structName = EquipmentType.getStructureTypeName(structType,
                getMech().isClan());

        if (getMech().isMixedTech()) {
            structName = structureCombo.getSelectedItem().toString();
            boolean clanStruct = getMech().isClan() ? structName
                    .indexOf(" (IS)") == -1
                    : structName.indexOf(" (Clan)") > -1;

            structName = structureCombo.getSelectedItem().toString()
                    .replace(" (IS)", "").replace(" (Clan)", "");

            if (clanStruct) {
                structName = String.format("Clan %1$s", structName);
            } else {
                structName = String.format("IS %1$s", structName);
            }
        }

        getMech().setStructureType(structName);
        isCount = EquipmentType.get(structName).getCriticals(getMech());
        if (isCount < 1) {
            return;
        }
        for (; isCount > 0; isCount--) {
            try {
                getMech().addEquipment(
                        new Mounted(getMech(), EquipmentType.get(structName)),
                        Entity.LOC_NONE, false);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private int convertEngineType(int engineType) {

        if (getMech().isMixedTech()) {
            // Clan Chassis with Clan Engine
            if (getMech().isClan()
                    && getMech().getEngine().hasFlag(Engine.CLAN_ENGINE)) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.XL_ENGINE:
                        return 2;
                    case Engine.FUEL_CELL:
                        return 7;
                    case Engine.XXL_ENGINE:
                        return 9;
                }
            }// Clan Chassis with IS Engine
            else if (getMech().isClan()
                    && !getMech().getEngine().hasFlag(Engine.CLAN_ENGINE)) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 1;
                    case Engine.XL_ENGINE:
                        return 3;
                    case Engine.LIGHT_ENGINE:
                        return 4;
                    case Engine.COMPACT_ENGINE:
                        return 5;
                    case Engine.FISSION:
                        return 6;
                    case Engine.FUEL_CELL:
                        return 8;
                    case Engine.XXL_ENGINE:
                        return 10;
                }
            }// IS Chassis with Clan Engine
            else if (!getMech().isClan()
                    && getMech().getEngine().hasFlag(Engine.CLAN_ENGINE)) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 1;
                    case Engine.XL_ENGINE:
                        return 3;
                    case Engine.FUEL_CELL:
                        return 8;
                    case Engine.XXL_ENGINE:
                        return 10;
                }
            }// IS Chassis with IS Engine
            else {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.XL_ENGINE:
                        return 2;
                    case Engine.LIGHT_ENGINE:
                        return 4;
                    case Engine.COMPACT_ENGINE:
                        return 5;
                    case Engine.FISSION:
                        return 6;
                    case Engine.FUEL_CELL:
                        return 7;
                    case Engine.XXL_ENGINE:
                        return 9;
                }

            }
        } else if (getMech().getEngine().hasFlag(Engine.CLAN_ENGINE)) {
            if (getMech().isIndustrial() || getMech().isPrimitive()) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.COMBUSTION_ENGINE:
                        return 1;
                    case Engine.FUEL_CELL:
                        return 2;
                    case Engine.FISSION:
                        return 3;
                }
            } else {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.XL_ENGINE:
                        return 1;
                    case Engine.FUEL_CELL:
                        return 2;
                    case Engine.XXL_ENGINE:
                        return 3;
                }
            }
        } else {
            if (getMech().isIndustrial() || getMech().isPrimitive()) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.COMBUSTION_ENGINE:
                        return 1;
                    case Engine.FUEL_CELL:
                        return 2;
                    case Engine.FISSION:
                        return 3;
                }
            } else if (getMech().isSuperHeavy()) {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.XL_ENGINE:
                        return 1;
                    case Engine.LIGHT_ENGINE:
                        return 2;
                    case Engine.COMPACT_ENGINE:
                        return 3;
                    case Engine.XXL_ENGINE:
                        return 4;
                }
            } else {
                switch (engineType) {
                    case Engine.NORMAL_ENGINE:
                        return 0;
                    case Engine.XL_ENGINE:
                        return 1;
                    case Engine.LIGHT_ENGINE:
                        return 2;
                    case Engine.COMPACT_ENGINE:
                        return 3;
                    case Engine.FISSION:
                        return 4;
                    case Engine.FUEL_CELL:
                        return 5;
                    case Engine.XXL_ENGINE:
                        return 6;
                }
            }
        }

        return 0;
    }

    private int convertEngineType(String engineType) {

        if (getMech().isMixedTech()) {
            if (engineType.startsWith("(")) {
                if (engineType.startsWith("(Clan")) {
                    clanEngineFlag = Engine.CLAN_ENGINE;
                } else {
                    clanEngineFlag = 0;
                }

                engineType = engineType.substring(
                        engineType.lastIndexOf(")") + 1).trim();
            } else {
                if (getMech().isClan()) {
                    clanEngineFlag = Engine.CLAN_ENGINE;
                } else {
                    clanEngineFlag = 0;
                }
            }
        }

        if (engineType.equals(ENGINESTANDARD)) {
            return Engine.NORMAL_ENGINE;
        }
        if (engineType.equals(ENGINEXL)) {
            return Engine.XL_ENGINE;
        }
        if (engineType.equals(ENGINEXXL)) {
            return Engine.XXL_ENGINE;
        }
        if (engineType.equals(ENGINEICE)) {
            return Engine.COMBUSTION_ENGINE;
        }
        if (engineType.equals(ENGINECOMPACT)) {
            return Engine.COMPACT_ENGINE;
        }
        if (engineType.equals(ENGINEFISSION)) {
            return Engine.FISSION;
        }
        if (engineType.equals(ENGINEFUELCELL)) {
            return Engine.FUEL_CELL;
        }
        if (engineType.equals(ENGINELIGHT)) {
            return Engine.LIGHT_ENGINE;
        }

        return Engine.NORMAL_ENGINE;
    }

    /**
     * resets all the various combo boxes with appropriate options based on the
     * tech base and tech level of the unit. This should NEVER be run when
     * listeners are turned on. If the updateUnit boolean is set to true, then
     * this method will check that the values of the current unit are available
     * as choices after populating the choices and if not it will reset them to
     * default values on the unit itself.
     *
     * @param updateUnit
     */
    private void populateChoices(boolean updateUnit) {

        boolean isClan = getMech().isClan();
        boolean isMixed = getMech().isMixedTech();
        boolean isExperimental = (getMech().getTechLevel() == TechConstants.T_IS_EXPERIMENTAL)
                || (getMech().getTechLevel() == TechConstants.T_CLAN_EXPERIMENTAL)
                || (getMech().getTechLevel() == TechConstants.T_IS_UNOFFICIAL)
                || (getMech().getTechLevel() == TechConstants.T_CLAN_UNOFFICIAL);
        boolean isAdvanced = (getMech().getTechLevel() == TechConstants.T_IS_ADVANCED)
                || (getMech().getTechLevel() == TechConstants.T_CLAN_ADVANCED)
                || (getMech().getTechLevel() == TechConstants.T_IS_EXPERIMENTAL)
                || (getMech().getTechLevel() == TechConstants.T_CLAN_EXPERIMENTAL)
                || (getMech().getTechLevel() == TechConstants.T_IS_UNOFFICIAL)
                || (getMech().getTechLevel() == TechConstants.T_CLAN_UNOFFICIAL);

        // advanced means we allow ultra-light mechs
        if (!isAdvanced) {
            ((SpinnerNumberModel) weightClass.getModel()).setMinimum(20);
        } else {
            ((SpinnerNumberModel) weightClass.getModel()).setMinimum(10);
        }
        // advanced IS means we allow super-heavy mechs
        if (!isClan && isAdvanced) {
            ((SpinnerNumberModel) weightClass.getModel()).setMaximum(200);
        } else {
            ((SpinnerNumberModel) weightClass.getModel()).setMaximum(100);
        }

        /* ARMOR */
        armorCombo.removeAllItems();
        for (int index = 0; index < (EquipmentType.armorNames.length); index++) {
            EquipmentType et;
            if (!isMixed) {
                et = EquipmentType.get(EquipmentType.getArmorTypeName(index,
                        isClan));
                if (((index == EquipmentType.T_ARMOR_PATCHWORK) && isExperimental)
                        || ((et != null)
                                && et.hasFlag(MiscType.F_MECH_EQUIPMENT) && (TechConstants
                                    .isLegal(getMech().getTechLevel(), et
                                            .getTechLevel(getMech().getYear()),
                                            isMixed)))) {
                    armorCombo.addItem(EquipmentType.armorNames[index]);
                }
            } else {
                et = EquipmentType.get(EquipmentType.getArmorTypeName(index,
                        true));
                if (et != null) {
                    armorCombo.addItem(EquipmentType.getArmorTypeName(index,
                            true));
                }
                et = EquipmentType.get(EquipmentType.getArmorTypeName(index,
                        false));
                if (et != null) {
                    armorCombo.addItem(EquipmentType.getArmorTypeName(index,
                            false));
                }
                if (index == EquipmentType.T_ARMOR_PATCHWORK) {
                    armorCombo.addItem(EquipmentType.getArmorTypeName(index));
                }
            }
        }

        /* ENGINE */
        int engineCount = 1;
        String[] engineList = new String[0];

        engineType.removeAllItems();

        if (isMixed) {
            if (isClan) {
                engineCount = clanEngineTypes.length + isEngineTypes.length;
                engineList = new String[engineCount];
                int clanPos = 0;
                int enginePos = 0;
                for (String isEngine : isEngineTypes) {
                    if (clanEngineTypes[clanPos].equals(isEngine)) {
                        engineList[enginePos] = clanEngineTypes[clanPos];
                        clanPos++;
                        enginePos++;
                    }
                    engineList[enginePos] = String
                            .format("(IS) %1$s", isEngine);
                    enginePos++;
                }
            } else {
                engineCount = clanEngineTypes.length + isEngineTypes.length;
                engineList = new String[engineCount];
                int clanPos = 0;
                int enginePos = 0;
                for (String isEngine : isEngineTypes) {
                    engineList[enginePos] = isEngine;
                    enginePos++;
                    if (clanEngineTypes[clanPos].equals(isEngine)) {
                        engineList[enginePos] = String.format("(Clan) %1$s",
                                clanEngineTypes[clanPos]);
                        clanPos++;
                        enginePos++;
                    }
                }
            }
        } else {
            if (isClan) {
                clanEngineFlag = Engine.CLAN_ENGINE;
                if (getMech().isIndustrial() || getMech().isPrimitive()) {
                    engineList = clanIndustrialEngineTypes;
                    engineCount = clanIndustrialEngineTypes.length;
                } else {
                    engineList = clanEngineTypes;
                    switch (getMech().getTechLevel()) {
                        case TechConstants.T_INTRO_BOXSET:
                            engineCount = 1;
                            break;
                        case TechConstants.T_CLAN_TW:
                        case TechConstants.T_IS_TW_NON_BOX:
                            engineCount = 2;
                            break;
                        case TechConstants.T_CLAN_ADVANCED:
                        case TechConstants.T_IS_ADVANCED:
                            engineCount = 3;
                            break;
                        case TechConstants.T_CLAN_EXPERIMENTAL:
                        case TechConstants.T_IS_EXPERIMENTAL:
                            engineCount = 4;
                            break;
                        case TechConstants.T_CLAN_UNOFFICIAL:
                        case TechConstants.T_IS_UNOFFICIAL:
                            engineCount = 4;
                            break;
                    }
                }
            } else {
                clanEngineFlag = 0;
                if (getMech().isIndustrial() || getMech().isPrimitive()) {
                    engineList = isIndustrialEngineTypes;
                    engineCount = isIndustrialEngineTypes.length;
                    if (getMech().isSuperHeavy()) {
                        engineCount = 1;
                    }
                } else {
                    if (getMech().isSuperHeavy()) {
                        engineList = isSuperHeavyEngineTypes;
                        engineCount = isSuperHeavyEngineTypes.length;
                        switch (getMech().getTechLevel()) {
                            case TechConstants.T_IS_ADVANCED:
                                engineCount = isSuperHeavyEngineTypes.length - 1;
                                break;
                            case TechConstants.T_IS_EXPERIMENTAL:
                            case TechConstants.T_IS_UNOFFICIAL:
                                engineCount = isSuperHeavyEngineTypes.length;
                                break;
                        }
                    } else {
                        engineList = isEngineTypes;
                        switch (getMech().getTechLevel()) {
                            case TechConstants.T_INTRO_BOXSET:
                                engineCount = 1;
                                break;
                            case TechConstants.T_CLAN_TW:
                            case TechConstants.T_IS_TW_NON_BOX:
                                engineCount = 4;
                                break;
                            case TechConstants.T_CLAN_ADVANCED:
                            case TechConstants.T_IS_ADVANCED:
                                engineCount = 6;
                                break;
                            case TechConstants.T_CLAN_EXPERIMENTAL:
                            case TechConstants.T_IS_EXPERIMENTAL:
                                engineCount = 7;
                                break;
                            case TechConstants.T_CLAN_UNOFFICIAL:
                            case TechConstants.T_IS_UNOFFICIAL:
                                engineCount = 7;
                                break;
                        }
                    }

                }
            }
        }
        for (int index = 0; index < engineCount; index++) {
            engineType.addItem(engineList[index]);
        }

        /* COCKPIT */
        cockpitType.removeAllItems();

        if (getMech().isSuperHeavy()) {
            if (getMech() instanceof TripodMech) {
                cockpitType
                        .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SUPERHEAVY_TRIPOD]);
            } else {
                cockpitType
                        .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SUPERHEAVY]);
                if (isExperimental
                        || (unit.getTechLevel() == TechConstants.T_CLAN_ADVANCED)
                        || (unit.getTechLevel() == TechConstants.T_IS_ADVANCED)) {
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                }
            }
        } else {
            switch (getMech().getTechLevel()) {
                case TechConstants.T_INTRO_BOXSET:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    break;
                case TechConstants.T_CLAN_TW:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    break;
                case TechConstants.T_IS_TW_NON_BOX:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    break;
                case TechConstants.T_CLAN_ADVANCED:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    break;
                case TechConstants.T_IS_ADVANCED:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    break;
                case TechConstants.T_CLAN_EXPERIMENTAL:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_TORSO_MOUNTED]);
                    break;
                case TechConstants.T_IS_EXPERIMENTAL:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_TORSO_MOUNTED]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INTERFACE]);
                    break;
                case TechConstants.T_CLAN_UNOFFICIAL:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_TORSO_MOUNTED]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_DUAL]);

                    break;
                case TechConstants.T_IS_UNOFFICIAL:
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_STANDARD]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_SMALL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_COMMAND_CONSOLE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_PRIMITIVE_INDUSTRIAL]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_TORSO_MOUNTED]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_INTERFACE]);
                    cockpitType
                            .addItem(Mech.COCKPIT_SHORT_STRING[Mech.COCKPIT_DUAL]);
                    break;
            }
        }

        /* INTERNAL STRUCTURE */
        structureCombo.removeAllItems();
        for (String structure : getStructureTypes()) {
            structureCombo.addItem(structure);
        }

        /* HEAT SINKS */
        int heatSinkCount = 0;
        String[] heatSinkList;
        heatSinkType.removeAllItems();

        if (isMixed) {
            heatSinkCount = (clanHeatSinkTypes.length + isHeatSinkTypes.length) - 1;
            heatSinkList = new String[heatSinkCount];
            int clanPos = 1;
            int heatSinkPos = 0;
            if (isClan) {
                clanPos = 0;
                for (String isHeatSink : isHeatSinkTypes) {
                    heatSinkList[heatSinkPos] = clanHeatSinkTypes[clanPos];
                    heatSinkPos++;
                    clanPos++;
                    if (isHeatSink.equals("Single")) {
                        continue;
                    }
                    heatSinkList[heatSinkPos] = String.format("(IS) %1$s",
                            isHeatSink);
                    heatSinkPos++;
                }
            } else {
                for (String isHeatSink : isHeatSinkTypes) {
                    heatSinkList[heatSinkPos] = isHeatSink;
                    heatSinkPos++;
                    if (clanPos < clanHeatSinkTypes.length) {
                        heatSinkList[heatSinkPos] = String.format(
                                "(Clan) %1$s", clanHeatSinkTypes[clanPos]);
                    }
                    clanPos++;
                    heatSinkPos++;
                }
            }
        } else {
            if (isClan) {

                heatSinkCount = clanHeatSinkTypes.length;
                heatSinkList = clanHeatSinkTypes;

                switch (getMech().getTechLevel()) {
                    case TechConstants.T_CLAN_TW:
                        heatSinkCount = 2;
                        break;
                    case TechConstants.T_CLAN_ADVANCED:
                    case TechConstants.T_CLAN_EXPERIMENTAL:
                    case TechConstants.T_CLAN_UNOFFICIAL:
                        heatSinkCount = 3;
                        break;
                }
            } else {
                heatSinkList = isHeatSinkTypes;
                switch (getMech().getTechLevel()) {
                    case TechConstants.T_INTRO_BOXSET:
                        heatSinkCount = 1;
                        break;
                    case TechConstants.T_IS_TW_NON_BOX:
                    case TechConstants.T_IS_ADVANCED:
                        heatSinkCount = 2;
                        break;
                    case TechConstants.T_IS_EXPERIMENTAL:
                    case TechConstants.T_IS_UNOFFICIAL:
                        heatSinkCount = 3;
                        break;
                }
            }
        }

        for (int index = 0; index < heatSinkCount; index++) {
            heatSinkType.addItem(heatSinkList[index]);
        }

        /* JUMP JETS */
        int jjCount = 0;
        jjType.removeAllItems();

        switch (getMech().getTechLevel()) {
            case TechConstants.T_INTRO_BOXSET:
                jjCount = 1;
                break;
            case TechConstants.T_IS_EXPERIMENTAL:
                jjCount = jjTypes.length;
                break;
            default:
                jjCount = 2;
        }

        for (int index = 0; index < jjCount; index++) {
            jjType.addItem(jjTypes[index]);
        }

        /* GYRO */
        String[] gyroList = new String[0];
        gyroType.removeAllItems();

        if (isMixed) {
            if (isClan) {
                int gyroPos = 0;
                gyroList = new String[Mech.GYRO_SHORT_STRING.length];
                for (String gyro : Mech.GYRO_SHORT_STRING) {
                    if (gyroPos == 0) {
                        gyroList[gyroPos] = gyro;
                    } else {
                        gyroList[gyroPos] = String.format("(IS) %1$s", gyro);
                    }
                    gyroPos++;
                }
            } else {
                gyroList = Mech.GYRO_SHORT_STRING.clone();
            }
        } else {
            if (isClan) {
                gyroList = new String[1];
                gyroList[0] = Mech.GYRO_SHORT_STRING[0];
            } else {
                gyroList = Mech.GYRO_SHORT_STRING.clone();
            }
        }
        if (getMech().getTechLevel() <= TechConstants.T_INTRO_BOXSET) {
            gyroList = new String[1];
            gyroList[0] = Mech.GYRO_SHORT_STRING[0];
        }
        for (String gyro : gyroList) {
            if (gyro.equals(Mech.GYRO_SHORT_STRING[Mech.GYRO_NONE])
                    && !(getMech().getCockpitType() == Mech.COCKPIT_INTERFACE)) {
                continue;
            }
            gyroType.addItem(gyro);
        }

        /* ENHANCEMENTS */
        enhancement.removeAllItems();
        enhancement.addItem("None");
        if (getMech().getTechLevel() > TechConstants.T_INTRO_BOXSET) {
            enhancement.addItem("MASC");
            if (!TechConstants.isClan(getMech().getTechLevel())) {
                if (getMech().isIndustrial()) {
                    enhancement.addItem("Industrial TSM");
                } else {
                    enhancement.addItem("TSM");
                }
            }
        }

        /* UNIT UPDATING */
        if (updateUnit) {
            // if we're ultra-light, and not at least advanced any more, revert
            // to 20 tons
            if (!isAdvanced && (unit.getWeight() < 20)) {
                unit.setWeight(20);
            }
            // if we're clan or not at least advanced, and super heavy, revert
            // to 100 tons
            if ((isClan || !isAdvanced) && (unit.getWeight() > 100)) {
                getMech().setWeight(100);
                getMech().clearCockpitCrits();
                getMech().addCockpit();
                UnitUtil.resetCriticalsAndMounts(getMech());
            }
            setArmorCombo(getMech().getArmorType(0));
            if (armorCombo.getSelectedIndex() == -1) {
                armorCombo.setSelectedIndex(0);
                UnitUtil.removeISorArmorMounts(unit, false);
                createArmorMountsAndSetArmorType();
                armor.resetArmorPoints();
            }
            int selEngine = convertEngineType(getMech().getEngine()
                    .getEngineType());
            if (engineType.getItemCount() <= selEngine) {
                selEngine = -1;
            }
            engineType.setSelectedIndex(selEngine);
            if (engineType.getSelectedIndex() == -1) {
                engineType.setSelectedIndex(0);
                setNewEngine(getMech().getEngine().getRating(),
                        convertEngineType(engineType.getSelectedItem()
                                .toString()));
            }
            setStructureCombo();
            if (structureCombo.getSelectedIndex() == -1) {
                structureCombo.setSelectedIndex(0);
                UnitUtil.removeISorArmorMounts(getMech(), true);
                createISMounts();
            }
            cockpitType.setSelectedIndex(-1);
            String selItem = Mech.COCKPIT_SHORT_STRING[getMech()
                    .getCockpitType()];
            for (int i = 0; i < cockpitType.getItemCount(); i++) {
                if (cockpitType.getItemAt(i).equals(selItem)) {
                    cockpitType.setSelectedIndex(i);
                    break;
                }
            }
            if (cockpitType.getSelectedIndex() == -1) {
                cockpitType.setSelectedIndex(0);
                getMech().clearCockpitCrits();
                getMech().addCockpit();
            }
            setHeatSinkCombo();
            if (heatSinkType.getSelectedIndex() == -1) {
                heatSinkType.setSelectedIndex(0);
                UnitUtil.updateHeatSinks(getMech(),
                        (Integer) heatSinkNumber.getValue(), "Single");
            }
            setJumpJetCombo();
            if (jjType.getSelectedIndex() == -1) {
                jjType.setSelectedIndex(0);
                int jump = Math.min((Integer) jumpMP.getValue(), getMech()
                        .getWalkMP(true, false, true));
                UnitUtil.updateJumpJets(getMech(), jump, Mech.JUMP_STANDARD);
            }
            int selGyro = getMech().getGyroType();
            if (gyroType.getItemCount() <= selGyro) {
                selGyro = -1;
            }
            gyroType.setSelectedIndex(selGyro);
            if (gyroType.getSelectedIndex() == -1) {
                gyroType.setSelectedIndex(0);
                getMech().clearGyroCrits();
                getMech().addGyro();
            }
            setEnhancementCombo();
            if (enhancement.getSelectedIndex() == -1) {
                enhancement.setSelectedIndex(0);
                UnitUtil.updateEnhancements(getMech(), false, false);
            }
        }

    }

    private void setStructureCombo() {

        if (getMech().isMixedTech()) {
            String structName;
            if (getMech().isClan()) {
                structName = EquipmentType.structureNames[getMech()
                        .getStructureType()];
                if ((getMech().getStructureType() != EquipmentType.T_STRUCTURE_STANDARD)
                        && (getMech().getStructureType() != EquipmentType.T_STRUCTURE_INDUSTRIAL)) {
                    structName = structName + " (IS)";
                }
                if (getMech()
                        .hasWorkingMisc(
                                "Clan "
                                        + EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_STEEL])) {
                    structName = EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_STEEL];
                } else if (getMech()
                        .hasWorkingMisc(
                                "Clan "
                                        + EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_COMPOSITE])) {
                    structName = EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_COMPOSITE];
                }
            } else {
                structName = EquipmentType.structureNames[getMech()
                        .getStructureType()];
                if (getMech()
                        .hasWorkingMisc(
                                "Clan "
                                        + EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_STEEL])) {
                    structName = EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_STEEL]
                            + " (Clan)";
                } else if (getMech()
                        .hasWorkingMisc(
                                "Clan "
                                        + EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_COMPOSITE])) {
                    structName = EquipmentType.structureNames[EquipmentType.T_STRUCTURE_ENDO_COMPOSITE]
                            + " (Clan)";
                }
            }
            structureCombo.setSelectedIndex(-1);
            for (int pos = 0; pos < structureCombo.getItemCount(); pos++) {
                if (structureCombo.getItemAt(pos).equals(structName)) {
                    structureCombo.setSelectedIndex(pos);
                    break;
                }
            }
        } else {
            int selIndex = getMech().getStructureType();
            if (structureCombo.getItemCount() <= selIndex) {
                selIndex = -1;
            }
            structureCombo.setSelectedIndex(selIndex);
        }
    }

    private void setJumpJetCombo() {
        int selIndex = Math.max(0, getMech().getJumpType() - 1);
        if (jjType.getItemCount() <= selIndex) {
            selIndex = -1;
        }
        jjType.setSelectedIndex(selIndex);
    }

    private void setEnhancementCombo() {
        String selEnhance = "None";
        if ((getMech().hasMASC() && !getMech().hasWorkingMisc(MiscType.F_MASC,
                MiscType.S_SUPERCHARGER))
                || getMech().hasMASCAndSuperCharger()) {
            selEnhance = "MASC";
        } else if (getMech().hasIndustrialTSM()) {
            selEnhance = "Industrial TSM";
        } else if (getMech().hasTSM()) {
            selEnhance = "TSM";
        }
        enhancement.setSelectedIndex(-1);
        for (int i = 0; i < enhancement.getItemCount(); i++) {
            if (enhancement.getItemAt(i).equals(selEnhance)) {
                enhancement.setSelectedIndex(i);
                break;
            }
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof JSpinner) {
            JSpinner spinner = (JSpinner) e.getSource();
            removeAllListeners();
            if (spinner.equals(weightClass)) {
                int rating = ((Integer) walkMP.getValue())
                        * ((Integer) weightClass.getValue());
                if (getMech().isPrimitive()) {
                    double dRating = ((Integer) walkMP.getValue())
                            * ((Integer) weightClass.getValue());
                    dRating *= 1.2;
                    if ((dRating % 5) != 0) {
                        dRating = (dRating - (dRating % 5)) + 5;
                    }
                    rating = (int) dRating;
                }
                if (rating > 500) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "That speed would create an engine with a rating over 500.",
                                    "Bad Engine Rating",
                                    JOptionPane.ERROR_MESSAGE);
                } else {
                    if ((getMech().isSuperHeavy() && ((Integer)weightClass.getValue() <= 100)) || (!getMech().isSuperHeavy() && ((Integer)weightClass.getValue() > 100))) {
                        // if we switch from being superheavy to not being superheavy,
                        // remove crits
                        for (Mounted mount : unit.getEquipment()) {
                            if (!UnitUtil.isFixedLocationSpreadEquipment(mount.getType())) {
                                UnitUtil.removeCriticals(getMech(), mount);
                                UnitUtil.changeMountStatus(unit, mount, Entity.LOC_NONE, Entity.LOC_NONE, false);
                            }
                        }
                    }
                    getMech().setWeight((Integer) weightClass.getValue());
                    getMech().autoSetInternal();
                    engineType.setSelectedIndex(engineType.getSelectedIndex());
                }
                populateChoices(true);
            } else if (spinner.equals(walkMP)) {
                int rating = ((Integer) walkMP.getValue())
                        * ((Integer) weightClass.getValue());
                if (getMech().isPrimitive()) {
                    double dRating = ((Integer) walkMP.getValue())
                            * ((Integer) weightClass.getValue());
                    dRating *= 1.2;
                    if ((dRating % 5) != 0) {
                        dRating = (dRating - (dRating % 5)) + 5;
                    }
                    rating = (int) dRating;
                }
                if ((rating > 400) && (getMech().getGyroType() == Mech.GYRO_XL)) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "That speed would require a large engine, which doesn't fit",
                                    "Bad Engine", JOptionPane.ERROR_MESSAGE);
                }
                if (rating > 500) {
                    JOptionPane
                            .showMessageDialog(
                                    this,
                                    "That speed would create an engine with a rating over 500.",
                                    "Bad Engine Rating",
                                    JOptionPane.ERROR_MESSAGE);
                } else {
                    System.out.println("Clearning engine crits.");
                    getMech().clearEngineCrits();
                    System.out.println("Setting new engine rating.");
                    getMech().setEngine(
                            new Engine(rating, convertEngineType(engineType
                                    .getSelectedItem().toString()),
                                    clanEngineFlag));
                    getMech().addEngineCrits();
                    System.out.println("Adding engine crits.");
                    int autoSinks = getMech().getEngine()
                            .getWeightFreeEngineHeatSinks();
                    System.out.println("Updating # engine heat sinks to "
                            + autoSinks);
                    UnitUtil.updateAutoSinks(getMech(),
                            (String) heatSinkType.getSelectedItem());
                }
            } else if (spinner.equals(jumpMP)) {
                UnitUtil.updateJumpJets(getMech(), (Integer) jumpMP.getValue(),
                        getJumpJetType());
            } else if (spinner.equals(armorTonnage)) {
                setArmorTonnage();
            } else if (spinner.equals(heatSinkNumber)) {
                UnitUtil.updateHeatSinks(getMech(), (Integer) heatSinkNumber
                        .getValue(), heatSinkType.getSelectedItem().toString());
            } else if (spinner.equals(baseChassisHeatSinks)) {
                getMech().getEngine().setBaseChassisHeatSinks(
                        Math.max(0, (Integer) baseChassisHeatSinks.getValue()));
                UnitUtil.updateAutoSinks(getMech(),
                        (String) heatSinkType.getSelectedItem());
            }
            addAllListeners();

            refresh.refreshAll();
        }
    }

    private void maximizeArmor() {
        double maxArmor = UnitUtil.getMaximumArmorTonnage(unit);
        armorTonnage.setValue(maxArmor);
        unit.setArmorTonnage(maxArmor);
        armor.resetArmorPoints();
    }

    private void createArmorMountsAndSetArmorType() {

        if (getArmorType(armorCombo) == EquipmentType
                .getArmorTypeName(EquipmentType.T_ARMOR_PATCHWORK)) {
            JComboBox<String> headArmor = new JComboBox<String>();
            headArmor.setName("head");
            JComboBox<String> laArmor = new JComboBox<String>();
            laArmor.setName("la");
            JComboBox<String> ltArmor = new JComboBox<String>();
            ltArmor.setName("lt");
            JComboBox<String> ctArmor = new JComboBox<String>();
            ctArmor.setName("ct");
            JComboBox<String> rtArmor = new JComboBox<String>();
            rtArmor.setName("rt");
            JComboBox<String> raArmor = new JComboBox<String>();
            raArmor.setName("ra");
            JComboBox<String> llArmor = new JComboBox<String>();
            llArmor.setName("ll");
            JComboBox<String> rlArmor = new JComboBox<String>();
            rlArmor.setName("rl");
            boolean isMixed = getMech().isMixedTech();
            boolean isClan = getMech().isClan();
            for (int index = 0; index < (EquipmentType.armorNames.length); index++) {
                EquipmentType et;
                if (!isMixed) {
                    et = EquipmentType.get(EquipmentType.getArmorTypeName(
                            index, isClan));
                    if ((et != null)
                            && et.hasFlag(MiscType.F_MECH_EQUIPMENT)
                            && (TechConstants.isLegal(getMech().getTechLevel(),
                                    et.getTechLevel(getMech().getYear()),
                                    isMixed))) {
                        headArmor.addItem(EquipmentType.armorNames[index]);
                        laArmor.addItem(EquipmentType.armorNames[index]);
                        ltArmor.addItem(EquipmentType.armorNames[index]);
                        ctArmor.addItem(EquipmentType.armorNames[index]);
                        rtArmor.addItem(EquipmentType.armorNames[index]);
                        raArmor.addItem(EquipmentType.armorNames[index]);
                        llArmor.addItem(EquipmentType.armorNames[index]);
                        rlArmor.addItem(EquipmentType.armorNames[index]);
                    }
                } else {
                    et = EquipmentType.get(EquipmentType.getArmorTypeName(
                            index, true));
                    if (et != null) {
                        headArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        laArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        ltArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        ctArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        rtArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        raArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        llArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                        rlArmor.addItem(EquipmentType.getArmorTypeName(index,
                                true));
                    }
                    et = EquipmentType.get(EquipmentType.getArmorTypeName(
                            index, false));
                    if (et != null) {
                        headArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        laArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        ltArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        ctArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        rtArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        raArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        llArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                        rlArmor.addItem(EquipmentType.getArmorTypeName(index,
                                false));
                    }
                }
            }
            setArmorType(headArmor, unit.getArmorType(Mech.LOC_HEAD), false);
            setArmorType(laArmor, unit.getArmorType(Mech.LOC_LARM), false);
            setArmorType(ltArmor, unit.getArmorType(Mech.LOC_LT), false);
            setArmorType(ctArmor, unit.getArmorType(Mech.LOC_CT), false);
            setArmorType(rtArmor, unit.getArmorType(Mech.LOC_RT), false);
            setArmorType(raArmor, unit.getArmorType(Mech.LOC_RARM), false);
            setArmorType(llArmor, unit.getArmorType(Mech.LOC_LLEG), false);
            setArmorType(rlArmor, unit.getArmorType(Mech.LOC_RLEG), false);
            JLabel headLabel = new JLabel("Head:");
            JLabel laLabel = new JLabel(unit instanceof BipedMech ? "Left Arm:"
                    : "Front Left Leg");
            JLabel ltLabel = new JLabel("Left Torso:");
            JLabel ctLabel = new JLabel("Center Torso:");
            JLabel rtLabel = new JLabel("Right Torso:");
            JLabel raLabel = new JLabel(
                    unit instanceof BipedMech ? "Right Arm:"
                            : "Front Right Leg");
            JLabel llLabel = new JLabel(unit instanceof BipedMech ? "Left Leg:"
                    : "Rear Left Leg");
            JLabel rlLabel = new JLabel(
                    unit instanceof BipedMech ? "Right Leg:" : "Rear Right Leg");
            JPanel panel = new JPanel(new GridBagLayout());
            panel.add(headLabel, GBC.std());
            panel.add(headArmor, GBC.eol());
            panel.add(laLabel, GBC.std());
            panel.add(laArmor, GBC.eol());
            panel.add(ltLabel, GBC.std());
            panel.add(ltArmor, GBC.eol());
            panel.add(ctLabel, GBC.std());
            panel.add(ctArmor, GBC.eol());
            panel.add(rtLabel, GBC.std());
            panel.add(rtArmor, GBC.eol());
            panel.add(raLabel, GBC.std());
            panel.add(raArmor, GBC.eol());
            panel.add(llLabel, GBC.std());
            panel.add(llArmor, GBC.eol());
            panel.add(rlLabel, GBC.std());
            panel.add(rlArmor, GBC.eol());
            JOptionPane.showMessageDialog(this, panel,
                    "Please choose the armor types",
                    JOptionPane.QUESTION_MESSAGE);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(headArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_HEAD);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(laArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_LARM);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(ltArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_LT);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(ctArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_CT);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(rtArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_RT);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(raArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_RARM);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(llArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_LLEG);
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(rlArmor))
                    .getTechLevel(getMech().getYear()), Mech.LOC_RLEG);
            unit.setArmorType(getArmorType(headArmor), Mech.LOC_HEAD);
            unit.setArmorType(getArmorType(laArmor), Mech.LOC_LARM);
            unit.setArmorType(getArmorType(ltArmor), Mech.LOC_LT);
            unit.setArmorType(getArmorType(ctArmor), Mech.LOC_CT);
            unit.setArmorType(getArmorType(rtArmor), Mech.LOC_RT);
            unit.setArmorType(getArmorType(raArmor), Mech.LOC_RARM);
            unit.setArmorType(getArmorType(llArmor), Mech.LOC_LLEG);
            unit.setArmorType(getArmorType(rlArmor), Mech.LOC_RLEG);
            for (int i = 0; i < unit.locations(); i++) {
                int armorCount = 0;
                switch (unit.getArmorType(i)) {
                    case EquipmentType.T_ARMOR_STANDARD:
                    case EquipmentType.T_ARMOR_HARDENED:
                    case EquipmentType.T_ARMOR_INDUSTRIAL:
                    case EquipmentType.T_ARMOR_COMMERCIAL:
                    case EquipmentType.T_ARMOR_HEAVY_INDUSTRIAL:
                        armorCount = 0;
                        break;
                    case EquipmentType.T_ARMOR_STEALTH:
                    case EquipmentType.T_ARMOR_FERRO_LAMELLOR:
                        armorCount = 2;
                        break;
                    case EquipmentType.T_ARMOR_HEAVY_FERRO:
                        armorCount = 3;
                        break;
                    case EquipmentType.T_ARMOR_FERRO_FIBROUS:
                    case EquipmentType.T_ARMOR_REFLECTIVE:
                    case EquipmentType.T_ARMOR_REACTIVE:
                        if (TechConstants.isClan(unit.getArmorTechLevel(i))) {
                            armorCount = 1;
                        } else {
                            armorCount = 2;
                        }
                        break;
                    default:
                        break;
                }
                if (armorCount < 1) {
                    continue;
                }

                for (; armorCount > 0; armorCount--) {
                    try {
                        getMech().addEquipment(
                                new Mounted(unit,
                                        EquipmentType.get(EquipmentType
                                                .getArmorTypeName(
                                                        unit.getArmorType(i),
                                                        unit.isClan()))), i,
                                false);
                    } catch (LocationFullException ex) {
                        JOptionPane
                                .showMessageDialog(
                                        null,
                                        EquipmentType.getArmorTypeName(unit
                                                .getArmorType(i))
                                                + " does not fit in location "
                                                + unit.getLocationName(i)
                                                + ". Resetting to Standard Armor in this location.",
                                        "Error",
                                        JOptionPane.INFORMATION_MESSAGE);
                        unit.setArmorType(EquipmentType.T_ARMOR_STANDARD, i);
                    }
                }
            }
            if (!unit.hasPatchworkArmor()) {
                setArmorType(armorCombo, EquipmentType.T_ARMOR_STANDARD, false);
            }
        } else {
            unit.setArmorTechLevel(EquipmentType.get(getArmorType(armorCombo))
                    .getTechLevel(getMech().getYear()));
            unit.setArmorType(getArmorType(armorCombo));
            int armorCount = 0;

            armorCount = EquipmentType.get(getArmorType(armorCombo))
                    .getCriticals(unit);

            if (armorCount < 1) {
                return;
            }
            // auto-place stealth crits
            if (unit.getArmorType(0) == EquipmentType.T_ARMOR_STEALTH) {
                Mounted mount = UnitUtil.createSpreadMounts(
                        getMech(),
                        EquipmentType.get(EquipmentType.getArmorTypeName(
                                unit.getArmorType(0), false)));
                if (mount == null) {
                    JOptionPane.showMessageDialog(null,
                            "Stealth Armor does not fit in location.",
                            "Resetting to Standard Armor",
                            JOptionPane.INFORMATION_MESSAGE);
                    setArmorCombo(EquipmentType.T_ARMOR_STANDARD);
                    unit.setArmorTechLevel(EquipmentType.get(
                            getArmorType(armorCombo)).getTechLevel(
                            getMech().getYear()));
                    unit.setArmorType(getArmorType(armorCombo));
                }
            } else {
                for (; armorCount > 0; armorCount--) {
                    try {
                        getMech()
                                .addEquipment(
                                        new Mounted(unit, EquipmentType
                                                .get(getArmorType(armorCombo))),
                                        Entity.LOC_NONE, false);
                    } catch (Exception ex) {
                    }
                }
            }
        }
    }

    private void setArmorCombo(int type) {
        armorCombo.setSelectedIndex(-1);

        for (int index = 0; index < armorCombo.getItemCount(); index++) {
            if (getMech().isMixedTech()) {
                if (EquipmentType.getArmorTypeName(type,
                        TechConstants.isClan(getMech().getArmorTechLevel(0)))
                        .equals(armorCombo.getItemAt(index))) {
                    armorCombo.setSelectedIndex(index);
                }
            } else {
                if (EquipmentType.getArmorTypeName(type).equals(
                        armorCombo.getItemAt(index))) {
                    armorCombo.setSelectedIndex(index);
                }
            }
        }
    }

    private int getJumpJetType() {
        return jjType.getSelectedIndex() + 1;
    }

    private String getArmorType(JComboBox<String> combo) {
        String armorType = combo.getSelectedItem().toString();
        if (armorType.equals(EquipmentType
                .getArmorTypeName(EquipmentType.T_ARMOR_PATCHWORK))) {
            return armorType;
        }
        if (!getMech().isMixedTech()) {
            String prefix = getMech().isClan() ? "Clan " : "IS ";
            for (int pos = 0; pos < EquipmentType.armorNames.length; pos++) {
                if (armorType.equals(EquipmentType.armorNames[pos])) {
                    return prefix + armorType;
                }
            }
        } else {
            for (int pos = 0; pos < EquipmentType.armorNames.length; pos++) {
                if (armorType.equals(EquipmentType.getArmorTypeName(pos, true))) {
                    return armorType;
                }
                if (armorType
                        .equals(EquipmentType.getArmorTypeName(pos, false))) {
                    return armorType;
                }
            }
        }

        return EquipmentType.getArmorTypeName(EquipmentType.T_ARMOR_STANDARD,
                getMech().isClan());
    }

    private void setArmorTonnage() {
        unit.setArmorTonnage(((Double) armorTonnage.getValue()));
        armor.resetArmorPoints();
    }

    private boolean hasTSM() {
        return ((String) enhancement.getSelectedItem()).contains("TSM");
    }

    private boolean hasMASC() {
        return ((String) enhancement.getSelectedItem()).contains("MASC");
    }

    public void setAsCustomization() {
        chassis.setEditable(false);
        chassis.setEnabled(false);
        weightClass.setEnabled(false);
        era.setEditable(false);
        era.setEnabled(false);
        motiveType.setEnabled(false);
    }

    private void setNewEngine(int rating, int type) {
        getMech().clearEngineCrits();
        getMech().setEngine(new Engine(rating, type, clanEngineFlag));
        getMech().addEngineCrits();
        UnitUtil.updateAutoSinks(getMech(),
                (String) heatSinkType.getSelectedItem());
    }

    private void setHeatSinkCombo() {
        int selIndex = -1;
        if (getMech().isMixedTech()) {
            if (getMech().isClan()) {
                if (UnitUtil.hasLaserHeatSinks(getMech())) {
                    selIndex = 3;
                } else if (getMech().hasCompactHeatSinks()) {
                    selIndex = 4;
                } else if (getMech().hasDoubleHeatSinks()) {

                    if (UnitUtil.hasClanDoubleHeatSinks(getMech())) {
                        selIndex = 1;
                    } else {
                        selIndex = 2;
                    }

                } else {
                    selIndex = 0;
                }
            } else {
                if (UnitUtil.hasLaserHeatSinks(getMech())) {
                    selIndex = 3;
                } else if (getMech().hasCompactHeatSinks()) {
                    selIndex = 4;
                } else if (getMech().hasDoubleHeatSinks()) {
                    if (UnitUtil.hasClanDoubleHeatSinks(getMech())) {
                        selIndex = 1;
                    } else {
                        selIndex = 2;
                    }
                } else {
                    selIndex = 0;
                }
            }
        } else {
            if (UnitUtil.hasLaserHeatSinks(getMech())) {
                selIndex = 2;
            } else if (getMech().hasDoubleHeatSinks()) {
                if (getMech().hasCompactHeatSinks()) {
                    selIndex = 2;
                } else {
                    selIndex = 1;
                }
            } else if (getMech().hasCompactHeatSinks()) {
                selIndex = 2;
            } else {
                selIndex = 0;
            }
        }
        if (heatSinkType.getItemCount() <= selIndex) {
            selIndex = -1;
        }
        heatSinkType.setSelectedIndex(selIndex);
    }

    private void setArmorType(JComboBox<String> combo, int type,
            boolean removeListeners) {
        if (removeListeners) {
            removeAllListeners();
        }
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (EquipmentType.getArmorTypeName(type).equals(
                    combo.getItemAt(index))) {
                armorCombo.setSelectedIndex(index);
            }
        }
        if (removeListeners) {
            addAllListeners();
        }
    }

    public void setArmorType(int type) {
        setArmorType(armorCombo, type, true);
    }

    /**
     * Get a list of the internal structure types legal for this unit
     *
     * @return a <code>List<String></code> of the legal IS types, for use in the
     *         IS combobox
     */
    private List<String> getStructureTypes() {
        List<String> structures = new ArrayList<String>();
        for (int i = 0; i < EquipmentType.structureNames.length; i++) {
            // superheavies are restricted to standard, industrial, endo and
            // endo-composite
            if (getMech().isSuperHeavy()) {
                if ((i != EquipmentType.T_STRUCTURE_STANDARD)
                        && (i != EquipmentType.T_STRUCTURE_INDUSTRIAL)
                        && (i != EquipmentType.T_STRUCTURE_ENDO_STEEL)
                        && (i != EquipmentType.T_STRUCTURE_ENDO_COMPOSITE)) {
                    continue;
                }
            }
            EquipmentType et = EquipmentType.get(EquipmentType
                    .getStructureTypeName(i, unit.isClan()));
            if ((et != null)
                    && TechConstants
                            .isLegal(unit.getTechLevel(),
                                    et.getTechLevel(unit.getYear()),
                                    unit.isMixedTech())) {
                structures.add(et.getName());
            }
            if (unit.isMixedTech()) {
                et = EquipmentType.get(EquipmentType.getStructureTypeName(i,
                        !unit.isClan()));
                if ((et != null)
                        && TechConstants.isLegal(unit.getTechLevel(),
                                et.getTechLevel(unit.getYear()),
                                unit.isMixedTech())) {
                    structures.add(et.getName()
                            + (unit.isClan() ? " (IS)" : " (Clan)"));
                }
            }
        }
        return structures;
    }
}