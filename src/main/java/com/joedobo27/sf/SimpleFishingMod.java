package com.joedobo27.sf;

import com.joedobo27.libs.bytecode.ByteCodeWild;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import javassist.*;
import javassist.bytecode.Descriptor;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import static org.gotti.wurmunlimited.modloader.ReflectionUtil.setPrivateField;

public class SimpleFishingMod implements WurmServerMod, Initable, ServerStartedListener, ItemTemplatesCreatedListener {

    static final Logger logger = Logger.getLogger(SimpleFishingMod.class.getName());

    @Override
    public void init() {
        ClassPool classPool = null;
        try {
            ModActions.init();

            // Hard cap MethodsFishing.additionalDamage() method so as long as tools are kept repaired they never break.
            classPool = HookManager.getInstance().getClassPool();
            CtClass ctMethodsFishing = classPool.get("com.wurmonline.server.behaviours.MethodsFishing");
            CtMethod ctAdditionalDamage = ctMethodsFishing.getDeclaredMethod("additionalDamage", new CtClass[]{
                    classPool.get("com.wurmonline.server.items.Item"), CtPrimitiveType.floatType,
                    CtPrimitiveType.booleanType});
            ctAdditionalDamage.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals(methodCall.getMethodName(), "min")) {
                        methodCall.replace(
                                "{ $_ = $proceed($$); $_ = com.joedobo27.sf.MethodsUtility.capDamage($_, 25); }");
                    }
                }
            });
        }catch (CannotCompileException | NotFoundException e) {
            logger.warning(e.getMessage());
        }
        try {
            // Void out a section of ItemBehaviour.moveBulkItemAsAction() so that the keep net can be converted into
            // a bulk container. A side affect of this is that bulk restriction on food and non-food are removed.
            CtClass ctItemBehaviour = classPool.get("com.wurmonline.server.behaviours.ItemBehaviour");
            ctItemBehaviour.getClassFile().compact();
            CtMethod ctMoveBulkItemAsAction = ctItemBehaviour.getDeclaredMethod("moveBulkItemAsAction",
                    new CtClass[]{
                            classPool.get("com.wurmonline.server.behaviours.Action"),
                            classPool.get("com.wurmonline.server.creatures.Creature"),
                            classPool.get("com.wurmonline.server.items.Item"),
                            classPool.get("com.wurmonline.server.items.Item"), CtPrimitiveType.floatType});
            ByteCodeWild find1 = new ByteCodeWild(ctItemBehaviour.getClassFile().getConstPool(),
                    ctMoveBulkItemAsAction.getMethodInfo().getCodeAttribute());
            find1.addAload("sourceTemplate", "Lcom/wurmonline/server/items/ItemTemplate;");
            find1.addInvokevirtual("com/wurmonline/server/items/ItemTemplate","isFood",
                    "()Z");
            find1.addOpcode(Opcode.IFEQ);

            find1.trimFoundBytecode();
            int insert1 = find1.getTableLineNumberStart();

            ByteCodeWild find2 = new ByteCodeWild(ctItemBehaviour.getClassFile().getConstPool(),
                    ctMoveBulkItemAsAction.getMethodInfo().getCodeAttribute());
            find2.addAload("sourceTemplate", "Lcom/wurmonline/server/items/ItemTemplate;");
            find2.addInvokevirtual("com/wurmonline/server/items/ItemTemplate","isFood",
                    "()Z");
            find2.addOpcode(Opcode.IFNE);
            find2.trimFoundBytecode();
            int insert2 = find2.getTableLineNumberStart();

            ctMoveBulkItemAsAction.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals(methodCall.getMethodName(), "isFood") &&
                            methodCall.getLineNumber() == insert1) {
                        methodCall.replace("{ $_ = false; }");
                    }
                    else if (Objects.equals(methodCall.getMethodName(), "isFood") &&
                            methodCall.getLineNumber() == insert2) {
                        methodCall.replace("{ $_ = true; }");
                    }
                }
            });
        }catch (NotFoundException | CannotCompileException | RuntimeException e) {
            logger.warning(e.getMessage());
        }
        try {
            // Void out a section of Item.moveToItem() so the keep net can be a bulk container. A side affect of this
            // is that bulk restriction on food and non-food are removed.
            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
            ctItem.getClassFile().compact();
            CtMethod ctMoveToItem = ctItem.getMethod("moveToItem", Descriptor.ofMethod(CtPrimitiveType.booleanType,
                    new CtClass[]{ classPool.get("com.wurmonline.server.creatures.Creature"),
                    CtPrimitiveType.longType, CtPrimitiveType.booleanType}));
            ByteCodeWild find1 = new ByteCodeWild(ctItem.getClassFile().getConstPool(),
                    ctMoveToItem.getMethodInfo().getCodeAttribute());
            find1.addAload("this", "Lcom/wurmonline/server/items/Item;");
            find1.addInvokevirtual("com.wurmonline.server.items.Item","isFood","()Z");
            find1.addCodeBranchingWild(Opcode.IFEQ);
            find1.addAload("target", "Lcom/wurmonline/server/items/Item;");
            find1.addInvokevirtual("com.wurmonline.server.items.Item","getTemplateId",
                    "()I");
            find1.addIconst(661);
            find1.trimFoundBytecode();
            int insert3 = find1.getTableLineNumberStart();

            ByteCodeWild find2 = new ByteCodeWild(ctItem.getClassFile().getConstPool(),
                    ctMoveToItem.getMethodInfo().getCodeAttribute());
            find2.addAload("target", "Lcom/wurmonline/server/items/Item;");
            find2.addInvokevirtual("com.wurmonline.server.items.Item","getTemplateId",
                    "()I");
            find2.addIconst(662);
            find2.addCodeBranchingWild(Opcode.IF_ICMPEQ);
            find2.addAload("target", "Lcom/wurmonline/server/items/Item;");
            find2.addInvokevirtual("com.wurmonline.server.items.Item","getTemplateId",
                    "()I");
            find2.addIconst(1317);
            find2.trimFoundBytecode();
            int insert4 = find2.getTableLineNumberStart();

            ctMoveToItem.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals(methodCall.getMethodName(), "isFood") && methodCall.getLineNumber() == insert3) {
                        methodCall.replace("{$_ = false;}");
                    }
                    else if (Objects.equals(methodCall.getMethodName(), "getTemplateId") &&
                            methodCall.getLineNumber() == insert4) {
                        methodCall.replace("{$_ = 662;}");
                    }
                }
            });
        }catch (NotFoundException | CannotCompileException | RuntimeException e) {
            logger.warning(e.getMessage());
        }
    }

    @Override
    public void onServerStarted() {
        DigWormsAction digWormsAction = DigWormsAction.getDigWormsAction();
        ModActions.registerAction(digWormsAction.getActionEntry());
        ModActions.registerAction(digWormsAction);

        DigGrubsAction digGrubsAction = DigGrubsAction.getDigGrubsAction();
        ModActions.registerAction(digGrubsAction.getActionEntry());
        ModActions.registerAction(digGrubsAction);

        TrapFliesAction trapFliesAction = TrapFliesAction.getTrapFliesAction();
        ModActions.registerAction(trapFliesAction.getActionEntry());
        ModActions.registerAction(trapFliesAction);
    }

    @Override
    public void onItemTemplatesCreated() {
        //*****************************
        // Make fishing gear items compatible with bulk container.
        ArrayList<Integer> makeItemsBulk = new ArrayList<>();
        makeItemsBulk.add(ItemList.floatBark);
        makeItemsBulk.add(ItemList.floatMoss);
        makeItemsBulk.add(ItemList.floatTwig);
        makeItemsBulk.add(ItemList.baitFly);
        makeItemsBulk.add(ItemList.baitCheese);
        makeItemsBulk.add(ItemList.baitDough);
        makeItemsBulk.add(ItemList.baitWurm);
        makeItemsBulk.add(ItemList.baitFish);
        makeItemsBulk.add(ItemList.baitGrub);
        makeItemsBulk.add(ItemList.baitWheat);
        makeItemsBulk.add(ItemList.baitCorn);
        makeItemsBulk.add(ItemList.fishingLineBasic);
        makeItemsBulk.add(ItemList.fishingLineLight);
        makeItemsBulk.add(ItemList.fishingLineMedium);
        makeItemsBulk.add(ItemList.fishingLineHeavy);
        makeItemsBulk.add(ItemList.fishingLineBraided);
        makeItemsBulk.add(ItemList.hookWood);
        makeItemsBulk.add(ItemList.hookBone);
        makeItemsBulk.add(ItemList.hookMetal);
        boolean fail1 = Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                .filter(itemTemplate -> makeItemsBulk.stream()
                        .anyMatch(value -> Objects.equals(value, itemTemplate.getTemplateId())))
                .map(MethodsUtility::setFieldTrueBulk)
                .anyMatch(aBoolean -> !aBoolean);
        if (fail1)
            SimpleFishingMod.logger.info("Make items bulk FAIL");
        else
            SimpleFishingMod.logger.info("Make items bulk SUCCESS. TemplateId's " + makeItemsBulk.toString());

        //*****************************
        // Make keep net a bulk container.
        boolean fail2 = Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                .filter(itemTemplate -> itemTemplate.getTemplateId() == ItemList.netKeep)
                .map(MethodsUtility::setFieldTrueBulkContainer)
                .anyMatch(aBoolean -> !aBoolean);
        if (fail2)
            SimpleFishingMod.logger.info("Make keep net bulk FAIL");
        else
            SimpleFishingMod.logger.info("Make keep net bulk SUCCESS");

        //*****************************
        // Null keep net containerRestrictions to remove limits on what can go in it.
        try {
            ItemTemplate keepNet = ItemTemplateFactory.getInstance().getTemplateOrNull(ItemList.netKeep);
            if (keepNet != null) {
                setPrivateField(keepNet, ReflectionUtil.getField(ItemTemplate.class, "containerRestrictions"),
                        null);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            logger.warning(e.getMessage());
        }

        //*****************************
        // Make keep net be a usesSpecifiedContainerSizes type item so it uses its big internal size.
        try {
            ItemTemplate keepNet = ItemTemplateFactory.getInstance().getTemplateOrNull(ItemList.netKeep);
            if (keepNet != null) {
                setPrivateField(keepNet,
                        ReflectionUtil.getField(ItemTemplate.class, "usesSpecifiedContainerSizes"),
                        Boolean.TRUE);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            logger.warning(e.getMessage());
        }
    }
}
