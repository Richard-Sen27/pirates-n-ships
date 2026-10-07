package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Datagen of the swivel gun (docs/design.md §8.2, P2): block states and placeholder models, loot, tags (what it mounts
 * on), physical weight, recipe and lang. Called from {@code CannonModule.gatherData}.
 */
public final class SwivelData {

    private SwivelData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> {
            lang.block(CannonContent.SWIVEL_GUN, "Swivel Gun");
            for (SwivelService.Outcome o : SwivelService.Outcome.values()) {
                lang.add(o.key(), switch (o) {
                    case POWDER_IN -> "Powder in. Now load the shot";
                    case SHOT_IN -> "Loaded. Hold use with an empty hand to aim, let go to fire";
                    case NEEDS_POWDER_FIRST -> "Powder first, then the shot";
                    case ALREADY_POWDERED -> "The powder is in. Load the shot";
                    case ALREADY_LOADED -> "Loaded. Hold use with an empty hand to aim, let go to fire";
                    case RELOADING -> "The barrel is still hot: %s s";
                    case NOT_ENOUGH_AMMO -> "One load takes %s × %s";
                    case AIMING -> "Aiming: let go to fire";
                    case AIMING_UNLOADED -> "Aiming, but not loaded: gunpowder first, then the shot";
                    case OCCUPIED -> "Someone else is aiming this gun";
                    case FIRED -> "Fire!";
                    case NOT_LOADED -> "Not loaded: put in gunpowder, then the shot";
                    case DISABLED -> "Swivel guns are disabled on this server";
                    case NOT_A_SWIVEL -> "That is not a swivel gun";
                });
            }
        });
        data.models(SwivelData::models);
        data.blockLoot(loot -> loot.dropSelf(CannonContent.SWIVEL_GUN.get()));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CannonContent.SWIVEL_GUN.get());
            // railings a swivel gun sits on (full blocks carry it too, see SwivelGunBlock#mountedAt); the brig bars are
            // the law module's block, referenced by id so the tag does not need that module
            tags.tag(CannonContent.SWIVEL_MOUNTS).add(Blocks.IRON_BARS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS)
                    .addOptional(Constants.id("brig_bars"));
        });
        // Sable physics: a small iron gun on a yoke, about a quarter of the big gun's half
        CannonData.physics(data, CannonContent.SWIVEL_GUN.id(), 0.5, 0.1);
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CannonContent.SWIVEL_GUN.get())
                .pattern("III").pattern(" S ")
                .define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(out, CannonContent.SWIVEL_GUN.id()));
    }

    private static void models(ModelContext m) {
        Block block = CannonContent.SWIVEL_GUN.get();
        ResourceLocation base = ModelLocationUtils.getModelLocation(block);
        ResourceLocation yokeId = base.withSuffix("_yoke");
        ResourceLocation barrelId = base.withSuffix("_barrel");
        ResourceLocation loadedId = base.withSuffix("_barrel_loaded");
        PlaceholderModel yoke = yoke();
        PlaceholderModel barrel = barrel();
        PlaceholderModel loaded = barrel.copy().texture("ball", "minecraft:block/iron_block")
                .box(7.25, 5.25, -6.5, 8.75, 6.75, -6.05, "ball");
        m.models().accept(yokeId, yoke::json);
        m.models().accept(barrelId, barrel::json);
        m.models().accept(loadedId, loaded::json);
        // the item's model (the block item's model delegates to block/swivel_gun): the whole gun, level, muzzle north
        m.models().accept(base, () -> yoke.copy().include(barrel).parent("minecraft:block/block").json());

        PropertyDispatch.C2<CannonLoad, SwivelPiece> dispatch = PropertyDispatch.properties(SwivelGunBlock.LOAD, SwivelGunBlock.PIECE);
        for (CannonLoad load : CannonLoad.values()) {
            dispatch.select(load, SwivelPiece.YOKE, Variant.variant().with(VariantProperties.MODEL, yokeId));
            dispatch.select(load, SwivelPiece.BARREL, Variant.variant()
                    .with(VariantProperties.MODEL, load == CannonLoad.LOADED ? loadedId : barrelId));
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }

    /**
     * The placeholder yoke (pixels): a pintle down the block's centre into the mount and a fork whose arms hold the
     * barrel's trunnions at y 6 ({@link SwivelRules#PIVOT_HEIGHT}), z 8. Turns with the yaw. F7g replaces it.
     */
    private static PlaceholderModel yoke() {
        return new PlaceholderModel(CannonData.IRON)
                .texture("iron", CannonData.IRON)
                .box(7, 0, 7, 9, 3.5, 9, "iron")
                .box(4.5, 3.5, 7, 11.5, 4.5, 9, "iron")
                .box(4.5, 4.5, 7.25, 5.5, 7.5, 8.75, "iron")
                .box(10.5, 4.5, 7.25, 11.5, 7.5, 8.75, "iron");
    }

    /**
     * The placeholder barrel, muzzle north (pixels): axis at x 8, y 6, from the muzzle at z −6 (14 px ahead of the
     * pivot at z 8, {@link SwivelRules#MUZZLE_LENGTH}) back to the breech at z 14 (6 px behind), trunnions through the
     * pivot and a wooden tiller to z 20. Turns with the yaw and the elevation about the pivot. F7g replaces it.
     */
    private static PlaceholderModel barrel() {
        return new PlaceholderModel(CannonData.IRON)
                .texture("iron", CannonData.IRON)
                .texture("wood", "minecraft:block/stripped_oak_log")
                .texture("bore", "minecraft:block/coal_block")
                .box(6.5, 4.5, -4.5, 9.5, 7.5, 14, "iron")
                .box(6, 4, -6, 10, 8, -4.5, "iron")
                .box(5.5, 5.5, 7.5, 10.5, 6.5, 8.5, "iron")
                .box(7.5, 5.5, 14, 8.5, 6.5, 20, "wood")
                .box(7, 5, -6.05, 9, 7, -6, "bore");
    }
}
