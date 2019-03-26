package com.joedobo27.sf;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
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

public class TrapFliesAction implements ModAction, ActionPerformer, BehaviourProvider {

    private final int actionId;
    private final ActionEntry actionEntry;

    private TrapFliesAction(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final TrapFliesAction _performer;

        static {
            int getFlyId = ModActions.getNextActionId();
            _performer = new TrapFliesAction(getFlyId,
                    ActionEntry.createEntry((short) getFlyId, "Trap flies", "getting flies", new int[]{}));
        }
    }

    @Override
    public List<ActionEntry> getBehavioursFor(@NotNull Creature performer, @NotNull Item source, @NotNull Item target) {
        if (source.getTemplateId() != ItemList.honey || (target.getTemplateId() != ItemList.trashBin &&
                target.getTemplateId() != ItemList.corpse)) {
            return BehaviourProvider.super.getBehavioursFor(performer, source, target);
        }
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short actionId, float counter) {
        if (source.getTemplateId() != ItemList.honey || (target.getTemplateId() != ItemList.trashBin &&
                target.getTemplateId() != ItemList.corpse)) 
            return propagate(action);
        
        try {
            // Where to put the flies? first is tackle box, second is inventory, third is on ground.
            int time;
            if (counter == 1.0f) {
                action.setData(0);
                @Nullable Item tackleBox = performer.getBestTackleBox();
                Item flyBin = getBoxCompartment(tackleBox, ItemList.baitFly);
                boolean fullFlyBin = tackleBox == null || flyBin == null || flyBin.getItemCount() > 90 || flyBin.getFreeVolume() <
                        ItemTemplateFactory.getInstance().getTemplate(ItemList.baitFly).getVolume() * 10;
                if (fullFlyBin && performer.getInventory().getItemCount() > 90) {
                    performer.getCommunicator().sendNormalServerMessage(
                            "Your containers are full and you can't hold the flies.");
                    return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
                }
                if (fullFlyBin && performer.getInventory().getItemCount() < 91) {
                    setInInventory(action, true);
                }
                if (!fullFlyBin) {
                    setInTackleBox(action, true);
                }

                time = Actions.getQuickActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.DIGGING),
                        source, 0.0);
                performer.getCurrentAction().setTimeLeft(time);
                performer.getCommunicator().sendNormalServerMessage("You start to trap flies.");
            }
            if (counter * 10.0f >= action.getTimeLeft()) {
                double quality;
                if (isInInventory(action)){
                    double power = performer.getSkills().getSkillOrLearn(SkillList.GROUP_NATURE)
                            .skillCheck(1, 0, false, 10);
                    double knowledge = performer.getSkills().getSkillOrLearn(SkillList.GROUP_NATURE)
                            .getKnowledge(source, 0);
                    quality = knowledge + (100 - knowledge) * ((float)power / 500.0f);
                    IntStream.range(0, 10)
                            .boxed()
                            .map(value -> ItemFactory.createItemOptional(ItemList.baitFly, (float) quality,
                                    performer.getName()))
                            .filter(Optional::isPresent)
                            .forEach(item -> performer.getInventory().insertItem(item.get(), true,
                                    false));
                    performer.getCommunicator().sendNormalServerMessage(
                            "You trap some flies and put them in your in you inventory.");
                }
                else if (isInTackleBox(action)) {
                    @Nullable Item tackleBox = performer.getBestTackleBox();
                    Item wormBin = getBoxCompartment(tackleBox, ItemList.baitFly);
                    if (wormBin == null) {
                        SimpleFishingMod.logger.warning("TackleBox fly bin is null error.");
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
                            .map(value -> ItemFactory.createItemOptional(ItemList.baitFly, (float) quality,
                                    performer.getName()))
                            .filter(Optional::isPresent)
                            .forEach(item -> wormBin.insertItem(item.get(), true, false));
                    performer.getCommunicator().sendNormalServerMessage(
                            "You trap some flies and put them in your best tackle box.");
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

    static TrapFliesAction getTrapFliesAction() {
        return TrapFliesAction.SingletonHelper._performer;
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
