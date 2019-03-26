package com.joedobo27.sf;

import com.wurmonline.server.Items;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodsUtility {

    static boolean setFieldTrueBulk(ItemTemplate itemTemplate){
        try {
            Field bulk = ReflectionUtil.getField(ItemTemplate.class, "bulk");
            ReflectionUtil.setPrivateField(itemTemplate, bulk, Boolean.TRUE);
        }catch (IllegalAccessException | NoSuchFieldException e){
            SimpleFishingMod.logger.fine(e.getMessage());
            return false;
        }
        return true;
    }

    static boolean setFieldTrueBulkContainer(ItemTemplate itemTemplate) {
        try {
            Field bulkContainer = ReflectionUtil.getField(ItemTemplate.class, "bulkContainer");
            ReflectionUtil.setPrivateField(itemTemplate, bulkContainer, Boolean.TRUE);
        }catch (IllegalAccessException | NoSuchFieldException e){
            SimpleFishingMod.logger.fine(e.getMessage());
            return false;
        }
        return true;
    }

    @Nullable
    static Item getBoxCompartment(@Nullable Item tackleBox, int itemTemplateId) {
        if (tackleBox == null)
            return null;
        return tackleBox.getItems().stream()
                .filter(item -> item.getTemplate().getContainerRestrictions().stream()
                        .anyMatch(containerRestriction -> containerRestriction.contains(itemTemplateId)))
                .findFirst()
                .orElse(null);
    }

    public static float capDamage(float damage, int cap) {
        return Math.min(damage, cap);
    }

    public static Item getNearKeepNet(Creature performer) {
        Player player = (Player) performer;
        Set<Item> items = null;
        List<Item> keepNets = items.stream()
                .filter(item -> item.getTemplateId() == ItemList.netKeep)
                .filter(Item::isPlanted)
                .filter(item -> item.isOwner(player))
                .sorted(Comparator.comparing(Item::getFreeVolume).reversed())
                .collect(Collectors.toList());
        return keepNets.isEmpty() ? performer.getInventory() : keepNets.get(0);
    }



}
