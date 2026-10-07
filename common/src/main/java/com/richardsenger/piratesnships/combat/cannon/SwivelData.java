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
 * Datagen of the swivel gun (docs/design.md §8.2, P2): block states (the models are hand-made, F7g), loot, tags (what it mounts
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

    /**
     * The swivel gun's pieces are hand-made (F7g, art/models/swivel_gun*.bbmodel), all with the muzzle to the north:
     * {@code swivel_gun_yoke} (pintle from y 0 and the fork up to the trunnions; {@code SwivelGunRenderer} turns it by
     * the yaw about the block's vertical centre line), {@code swivel_gun_barrel} and {@code swivel_gun_barrel_loaded}
     * (axis at x 8, y 6, muzzle at z −6, breech at z 14, tiller to z 20; turned by the yaw and raised by the elevation
     * about the pivot (8, 6, 8), {@link SwivelRules#PIVOT_HEIGHT}), and {@code swivel_gun}, the whole gun, which is the
     * item's model. Only the block state is generated here.
     */
    private static void models(ModelContext m) {
        Block block = CannonContent.SWIVEL_GUN.get();
        ResourceLocation base = ModelLocationUtils.getModelLocation(block);
        ResourceLocation yokeId = base.withSuffix("_yoke");
        ResourceLocation barrelId = base.withSuffix("_barrel");
        ResourceLocation loadedId = base.withSuffix("_barrel_loaded");

        PropertyDispatch.C2<CannonLoad, SwivelPiece> dispatch = PropertyDispatch.properties(SwivelGunBlock.LOAD, SwivelGunBlock.PIECE);
        for (CannonLoad load : CannonLoad.values()) {
            dispatch.select(load, SwivelPiece.YOKE, Variant.variant().with(VariantProperties.MODEL, yokeId));
            dispatch.select(load, SwivelPiece.BARREL, Variant.variant()
                    .with(VariantProperties.MODEL, load == CannonLoad.LOADED ? loadedId : barrelId));
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }
}
