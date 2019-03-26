package com.joedobo27.sf;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.joedobo27.sf.MethodsUtility.getBoxCompartment;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.NO_SERVER_PROPAGATION;

public class DigGrubsAction implements ModAction, ActionPerformer, BehaviourProvider {
        private final int actionId;
        private final ActionEntry actionEntry;

        private DigGrubsAction(int actionId, ActionEntry actionEntry) {
            this.actionId = actionId;
            this.actionEntry = actionEntry;
        }

private static class SingletonHelper {
    private static final DigGrubsAction _performer;
    static {
        int getGrubsId = ModActions.getNextActionId();
        _performer = new DigGrubsAction(getGrubsId,
                ActionEntry.createEntry((short) getGrubsId, "Get grubs", "getting grubs", new int[]{}));
    }
}

    @Override
    public List<ActionEntry> getBehavioursFor(@NotNull Creature performer, @NotNull Item source, int tileX, int tileY,
                                              boolean onSurface, int encodedTile) {
        if (toolNoDigGrubs(source.getTemplateId()) || Tiles.decodeType(encodedTile) != Tiles.Tile.TILE_GRASS.id) {
            return BehaviourProvider.super.getBehavioursFor(performer, source, tileX, tileX, onSurface, encodedTile);
        }
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(@NotNull Action action, @NotNull Creature performer, @NotNull Item source, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (toolNoDigGrubs(source.getTemplateId()) || Tiles.decodeType(encodedTile) != Tiles.Tile.TILE_GRASS.id)
            return propagate(action);
        try {
            // Where to put the worms? first is tackle box, second is inventory, third is on ground.

            VolaTile dropTile = Zones.getTileOrNull(tileX, tileY, onSurface);
            int time;
            if (counter == 1.0f) {
                action.setData(0);
                @Nullable Item tackleBox = performer.getBestTackleBox();
                Item grubBin = getBoxCompartment(tackleBox, ItemList.baitGrub);
                boolean fullGrubBin = tackleBox == null || grubBin == null || grubBin.getItemCount() > 90 || grubBin.getFreeVolume() <
                        ItemTemplateFactory.getInstance().getTemplate(ItemList.baitGrub).getVolume() * 10;
                if (fullGrubBin && performer.getInventory().getItemCount() > 90 &&
                        dropTile.getNumberOfItems(0) > 90) {
                    performer.getCommunicator().sendNormalServerMessage(
                            "Your containers are full and the area is too crowded to get grubs.");
                    return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
                }
                if (fullGrubBin && performer.getInventory().getItemCount() > 90 &&
                        dropTile.getNumberOfItems(0) < 91) {
                    setOnGround(action, true);
                }
                if (fullGrubBin && performer.getInventory().getItemCount() < 91) {
                    setInInventory(action, true);
                }
                if (!fullGrubBin) {
                    setInTackleBox(action, true);
                }

                time = Actions.getQuickActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.DIGGING),
                        source, 0.0);
                performer.getCurrentAction().setTimeLeft(time);
                performer.getCommunicator().sendNormalServerMessage("You start to dig for grubs");
            }
            if (counter * 10.0f >= action.getTimeLeft()) {
                double quality;
                if (isOnGround(action)) {
                    quality = Server.rand.nextInt(30) + 20;
                    IntStream.range(0, 10)
                            .boxed()
                            .map(value -> ItemFactory.createItemOptional(ItemList.baitGrub, (float) quality,
                                    performer.getName()))
                            .filter(Optional::isPresent)
                            .forEach(item -> dropTile.addItem(item.get(), false, false));
                    performer.getCommunicator().sendNormalServerMessage(
                            "You find some grubs and leave them on the ground.");
                }
                else if (isInInventory(action)){
                    double power = performer.getSkills().getSkillOrLearn(SkillList.DIGGING)
                            .skillCheck(1, 0, false, 10);
                    double knowledge = performer.getSkills().getSkillOrLearn(SkillList.DIGGING)
                            .getKnowledge(source, 0);
                    quality = knowledge + (100 - knowledge) * ((float)power / 500.0f);
                    IntStream.range(0, 10)
                            .boxed()
                            .map(value -> ItemFactory.createItemOptional(ItemList.baitGrub, (float) quality,
                                    performer.getName()))
                            .filter(Optional::isPresent)
                            .forEach(item -> performer.getInventory().insertItem(item.get(), true,
                                    false));
                    performer.getCommunicator().sendNormalServerMessage(
                            "You find some grubs and put them in you inventory.");
                }
                else if (isInTackleBox(action)) {
                    @Nullable Item tackleBox = performer.getBestTackleBox();
                    Item wormBin = getBoxCompartment(tackleBox, ItemList.baitGrub);
                    if (wormBin == null) {
                        SimpleFishingMod.logger.warning("TackleBox grub bin is null error.");
                        performer.getCommunicator().sendNormalServerMessage(
                                "Something went wrong.");
                        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
                    }
                    double power = performer.getSkills().getSkillOrLearn(SkillList.DIGGING)
                            .skillCheck(1, source, 0, false, 10);
                    double knowledge = performer.getSkills().getSkillOrLearn(SkillList.DIGGING)
                            .getKnowledge(source, 0);
                    quality = knowledge + (100 - knowledge) * ((float)power / 500.0f);
                    IntStream.range(0, 10)
                            .boxed()
                            .map(value -> ItemFactory.createItemOptional(ItemList.baitGrub, (float) quality,
                                    performer.getName()))
                            .filter(Optional::isPresent)
                            .forEach(item -> wormBin.insertItem(item.get(), true, false));
                    performer.getCommunicator().sendNormalServerMessage(
                            "You find some grubs and put them in your best tackle box.");
                }
                return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
            }
        } catch (NoSuchTemplateException | NoSuchActionException e) {
            SimpleFishingMod.logger.warning(e.getMessage());
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

    ActionEntry getActionEntry() {
        return this.actionEntry;
    }

    @Override
    public short getActionId() {
        return (short)this.actionId;
    }

    static DigGrubsAction getDigGrubsAction() {
        return DigGrubsAction.SingletonHelper._performer;
    }

    private boolean toolNoDigGrubs(int itemTemplateId) {
        switch (itemTemplateId) {
            case ItemList.hatchet: // 7
                return false;
            case ItemList.shovel: // 25
                return false;
            case ItemList.pickAxe: // 20
                return false;
            case ItemList.crudeShovel: //690
                return false;
            case ItemList.crudeKnife: // 685
                return false;
            case ItemList.crudeAxe: // 1011
                return false;
            default:
                return true;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void setOnGround(Action action, boolean state) {
        if (state)
            action.setData(action.getData() | 0b1L); // true
        else
            action.setData(action.getData() &
                    0b1111111111111111111111111111111111111111111111111111111111111110L ); // false
    }

    private boolean isOnGround(Action action) {
        return ((action.getData() & 0b1L) == 1L);
    }

    @SuppressWarnings("SameParameterValue")
    private void setInInventory(Action action, boolean state) {
        if (state)
            action.setData(action.getData() | 0b10L); // true
        else
            action.setData(action.getData() &
                    0b1111111111111111111111111111111111111111111111111111111111111101L ); // false
    }

    private boolean isInInventory(Action action) {
        return ((action.getData() & 0b10L) >> 1 == 1L);
    }

    @SuppressWarnings("SameParameterValue")
    private void setInTackleBox(Action action, boolean state) {
        if (state)
            action.setData(action.getData() | 0b100L); // true
        else
            action.setData(action.getData() &
                    0b1111111111111111111111111111111111111111111111111111111111111011L ); // false
    }

    private boolean isInTackleBox(Action action) {
        return ((action.getData() & 0b100L) >> 2 == 1L);
    }
}
