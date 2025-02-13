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
package megameklab.ui.util;

import java.awt.dnd.DropTargetDragEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import megamek.common.CriticalSlot;
import megamek.common.Entity;
import megamek.common.MekFileParser;
import megamek.common.MiscType;
import megamek.common.Mounted;
import megamek.common.Tank;
import megamek.common.annotations.Nullable;
import megamek.common.loaders.EntityLoadingException;
import megamek.logging.MMLogger;
import megameklab.ui.EntitySource;
import megameklab.util.UnitUtil;

public class DropTargetCriticalList<E> extends JList<E> implements MouseListener {
    private static final MMLogger logger = MMLogger.create(DropTargetCriticalList.class);

    private EntitySource eSource;
    private RefreshListener refresh;
    private boolean buildView;

    public DropTargetCriticalList(Vector<E> vector, EntitySource eSource, RefreshListener refresh,
            boolean buildView) {
        super(vector);
        this.eSource = eSource;
        this.refresh = refresh;
        this.buildView = buildView;
        setCellRenderer(new CritListCellRenderer(eSource.getEntity(), buildView));
        addMouseListener(this);
        setTransferHandler(new CriticalTransferHandler(eSource, refresh));
    }

    public void dragEnter(DropTargetDragEvent dtde) {

    }

    private void changeMountStatus(Mounted<?> eq, int location, boolean rear) {
        changeMountStatus(eq, location, -1, rear);
    }

    private void changeMountStatus(Mounted<?> eq, int location, int secondaryLocation, boolean rear) {
        UnitUtil.changeMountStatus(getUnit(), eq, location, secondaryLocation, rear);

        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    @Override
    public void mouseClicked(MouseEvent evt) {

    }

    @Override
    public void mouseEntered(MouseEvent evt) {

    }

    @Override
    public void mouseExited(MouseEvent evt) {

    }

    @Override
    public void mousePressed(MouseEvent evt) {
        if (buildView) {
            if (evt.getButton() == MouseEvent.BUTTON2) {
                setSelectedIndex(locationToIndex(evt.getPoint()));
                removeCrit();
            } else if (evt.getButton() == MouseEvent.BUTTON3) {
                setSelectedIndex(locationToIndex(evt.getPoint()));

                if ((evt.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    removeCrit();
                    return;
                }

                int location = getCritLocation();
                JPopupMenu popup = new JPopupMenu();

                CriticalSlot cs = getCrit();

                Mounted<?> mount = getMounted();
                if (mount != null) {
                    if ((evt.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                        changeWeaponFacing(!mount.isRearMounted());
                        return;
                    }

                    if ((evt.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                        changeOmniMounting(!mount.isOmniPodMounted());
                        return;
                    }

                    popup.setAutoscrolls(true);
                    JMenuItem info;
                    if (!UnitUtil.isFixedLocationSpreadEquipment(mount.getType())) {
                        info = new JMenuItem("Remove " + mount.getName());
                        info.addActionListener(ev -> removeCrit());
                        popup.add(info);
                    }

                    if (!UnitUtil.isArmorOrStructure(mount.getType())) {
                        info = new JMenuItem("Delete " + mount.getName());
                        info.addActionListener(ev -> removeMount());
                        popup.add(info);
                    }

                    if (getUnit().hasWorkingMisc(MiscType.F_SPONSON_TURRET)
                            && ((mount.getLocation() == Tank.LOC_LEFT) || (mount.getLocation() == Tank.LOC_RIGHT))) {
                        if (!mount.isSponsonTurretMounted()) {
                            info = new JMenuItem("Mount " + mount.getName() + " in Sponson Turret");
                            info.addActionListener(evt2 -> changeSponsonTurretMount(true));
                            popup.add(info);
                        } else {
                            info = new JMenuItem("Remove " + mount.getName() + " from Sponson Turret");
                            info.addActionListener(evt2 -> changeSponsonTurretMount(false));
                            popup.add(info);
                        }
                    }

                    if (getUnit().countWorkingMisc(MiscType.F_PINTLE_TURRET,
                            mount.getLocation()) > 0) {
                        if (!mount.isPintleTurretMounted()) {
                            info = new JMenuItem("Mount " + mount.getName() + " in Pintle Turret");
                            info.addActionListener(evt2 -> changePintleTurretMount(true));
                            popup.add(info);
                        } else {
                            info = new JMenuItem("Remove " + mount.getName() + " from Pintle Turret");
                            info.addActionListener(evt2 -> changePintleTurretMount(false));
                            popup.add(info);
                        }
                    }

                    if (getUnit().isOmni() && !mount.getType().isOmniFixedOnly()) {
                        if (mount.isOmniPodMounted()) {
                            info = new JMenuItem("Change to fixed mount");
                            info.addActionListener(evt2 -> changeOmniMounting(false));
                            popup.add(info);
                        } else if (UnitUtil.canPodMount(getUnit(), mount)) {
                            info = new JMenuItem("Change to pod mount");
                            info.addActionListener(evt2 -> changeOmniMounting(true));
                            popup.add(info);
                        }
                    }
                }

                if (popup.getComponentCount() > 0) {
                    popup.show(this, evt.getX(), evt.getY());
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent evt) {

    }

    private @Nullable Mounted<?> getMounted() {
        CriticalSlot crit = getCrit();
        try {
            if ((crit != null) && (crit.getType() == CriticalSlot.TYPE_EQUIPMENT)) {
                return crit.getMount();
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return null;
    }

    private CriticalSlot getCrit() {
        int slot = getSelectedIndex();
        int location = getCritLocation();
        CriticalSlot crit = null;
        if ((slot >= 0) && (slot < getUnit().getNumberOfCriticals(location))) {
            crit = getUnit().getCritical(location, slot);
        }
        return crit;
    }

    private void removeCrit() {
        CriticalSlot crit = getCrit();
        Mounted<?> mounted = getMounted();

        if (mounted == null) {
            return;
        }

        // Cannot remove a mast mount
        if (mounted.getType().hasFlag(MiscType.F_MAST_MOUNT)) {
            return;
        }

        UnitUtil.removeCriticals(getUnit(), mounted);

        if ((crit != null) && (crit.getType() == CriticalSlot.TYPE_EQUIPMENT)) {
            changeMountStatus(mounted, Entity.LOC_NONE, false);
        }

        UnitUtil.compactCriticals(getUnit());
    }

    private void changeWeaponFacing(boolean rear) {
        Mounted<?> mount = getMounted();
        int location = getCritLocation();
        changeMountStatus(mount, location, rear);
    }

    private void changeSponsonTurretMount(boolean turret) {
        getMounted().setSponsonTurretMounted(turret);
        if (getMounted().getLinkedBy() != null) {
            getMounted().getLinkedBy().setSponsonTurretMounted(turret);
        }

        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    private void changePintleTurretMount(boolean turret) {
        getMounted().setPintleTurretMounted(turret);
        if (getMounted().getLinkedBy() != null) {
            getMounted().getLinkedBy().setPintleTurretMounted(turret);
        }

        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    private void changeOmniMounting(boolean pod) {
        Mounted<?> mount = getMounted();
        if (!pod || UnitUtil.canPodMount(getUnit(), mount)) {
            mount.setOmniPodMounted(pod);
        }

        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    private int getCritLocation() {
        return Integer.parseInt(getName());
    }

    private void removeMount() {
        Mounted<?> mounted = getMounted();

        if (mounted == null) {
            return;
        }

        UnitUtil.removeMounted(getUnit(), mounted);

        UnitUtil.compactCriticals(getUnit());
        // Check linkings after you remove everything.
        try {
            MekFileParser.postLoadInit(getUnit());
        } catch (EntityLoadingException ignored) {
            // do nothing.
        } catch (Exception ex) {
            logger.error("", ex);
        }
        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    public Entity getUnit() {
        return eSource.getEntity();
    }
}
