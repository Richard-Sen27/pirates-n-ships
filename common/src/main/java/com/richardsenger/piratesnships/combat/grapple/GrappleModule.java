package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The {@code combat.grapple} module (G11, docs/design.md §8.3 version 1, §8.4): the grappling hook is thrown, latches
 * onto another ship's hull at a plot position and hauls the two ships together with a rope force on both bodies until
 * they lie side by side. GR1 adds launching it from a crossbow or musket in the other hand ({@link GrappleLaunch}) and
 * the mooring ring ({@link MooringRingBlock}). Config section {@code grapple}. The item is
 * {@code combat.content.CombatContent#GRAPPLING_HOOK}.
 */
public final class GrappleModule implements ModModule {

    @Override
    public String id() {
        return "combat.grapple";
    }

    @Override
    public void registerConfig() {
        GrappleConfig.init();
    }

    @Override
    public void registerContent() {
        GrappleContent.init();
        ShipForces.registerGrapple();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToServer(ReleaseHookPayload.TYPE, ReleaseHookPayload.CODEC, ReleaseHookPayload::handle);
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(GrappleService::onServerTick);
        CommonEvents.PLAYER_LOGOUT.register(GrappleService::onLogout);
        CommonEvents.SERVER_STOPPED.register(server -> GrappleService.onServerStopped());
        SableShips.onPhysicsTick(GrappleService::onPhysicsTick);
        com.richardsenger.piratesnships.ship.assembly.ShipSplits.onSplit(GrappleService::onShipSplit); // RS1
        com.richardsenger.piratesnships.ship.assembly.ShipRejoin.onRejoin(GrappleService::onShipRejoined); // RS2
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.grapple.client.GrappleClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(GrappleContent.HOOK.get().getDescriptionId(), "Grappling Hook")
                .add(GrapplingHookItem.TOOLTIP_KEY, "Throw at another ship to haul it alongside. Sneak + use with an empty hand to let go")
                .add(GrapplingHookItem.LAUNCH_TOOLTIP_KEY, "With a crossbow in the other hand: hold to draw, let go to shoot it farther. "
                        + "With an empty musket: fire it farthest for one gunpowder")
                .add(GrapplingHookItem.MUSKET_LOADED_KEY, "Unload the musket first: it still holds a ball")
                .add(GrapplingHookItem.NO_POWDER_KEY, "Firing the hook from the musket takes one gunpowder")
                .add(ShipForces.GRAPPLE_KEY, "Grappling Rope")
                .block(GrappleContent.MOORING_RING, "Mooring Ring")
                .add(MooringRingBlock.TOOLTIP_KEY, "Hooks passing close catch on it and hold fast. Use it with a hook out to tie off the rope")
                .add(GrappleService.TIED_KEY, "Rope tied to the mooring ring")
                .add(GrappleService.ALREADY_TIED_KEY, "The rope is already tied to this ring")
                .add(GrappleService.TIE_SAME_SHIP_KEY, "The hook hangs on this ship: tie the rope on your own ship")
                .add(GrappleService.TIE_TOO_FAR_KEY, "The rope does not reach this ring"));
        data.models(GrappleModule::ringModels);
        data.blockLoot(loot -> loot.dropSelf(GrappleContent.MOORING_RING.get()));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(GrappleContent.MOORING_RING.get());
            // a small iron fitting: light and thin like iron bars in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(GrappleContent.MOORING_RING.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(GrappleContent.MOORING_RING.get());
        });
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, GrappleContent.MOORING_RING.get(), 2)
                .pattern(" I ").pattern("I I").pattern(" I ")
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(out, GrappleContent.MOORING_RING.id()));
    }

    /**
     * Placeholder look of the mooring ring until its Blockbench model (design.md §4.8): vanilla's button shape in iron,
     * turned like a button for floor, wall and ceiling; the item uses the button's inventory model.
     */
    private static void ringModels(ModelContext m) {
        Block ring = GrappleContent.MOORING_RING.get();
        TextureMapping iron = new TextureMapping().put(TextureSlot.TEXTURE, ResourceLocation.withDefaultNamespace("block/iron_block"));
        ResourceLocation model = ModelTemplates.BUTTON.create(ring, iron, m.models());
        ModelTemplates.BUTTON_INVENTORY.create(ModelLocationUtils.getModelLocation(ring.asItem()), iron, m.models());
        m.blockStates().accept(MultiVariantGenerator.multiVariant(ring, Variant.variant().with(VariantProperties.MODEL, model))
                .with(faceAndFacing()));
    }

    /** Rotations of a model made for the floor facing north, like vanilla buttons and levers. */
    private static PropertyDispatch faceAndFacing() {
        VariantProperties.Rotation r0 = VariantProperties.Rotation.R0;
        VariantProperties.Rotation r90 = VariantProperties.Rotation.R90;
        VariantProperties.Rotation r180 = VariantProperties.Rotation.R180;
        VariantProperties.Rotation r270 = VariantProperties.Rotation.R270;
        return PropertyDispatch.properties(BlockStateProperties.ATTACH_FACE, BlockStateProperties.HORIZONTAL_FACING)
                .select(AttachFace.FLOOR, Direction.NORTH, rot(r0, r0))
                .select(AttachFace.FLOOR, Direction.EAST, rot(r0, r90))
                .select(AttachFace.FLOOR, Direction.SOUTH, rot(r0, r180))
                .select(AttachFace.FLOOR, Direction.WEST, rot(r0, r270))
                .select(AttachFace.WALL, Direction.NORTH, rot(r90, r0).with(VariantProperties.UV_LOCK, true))
                .select(AttachFace.WALL, Direction.EAST, rot(r90, r90).with(VariantProperties.UV_LOCK, true))
                .select(AttachFace.WALL, Direction.SOUTH, rot(r90, r180).with(VariantProperties.UV_LOCK, true))
                .select(AttachFace.WALL, Direction.WEST, rot(r90, r270).with(VariantProperties.UV_LOCK, true))
                .select(AttachFace.CEILING, Direction.NORTH, rot(r180, r180))
                .select(AttachFace.CEILING, Direction.EAST, rot(r180, r270))
                .select(AttachFace.CEILING, Direction.SOUTH, rot(r180, r0))
                .select(AttachFace.CEILING, Direction.WEST, rot(r180, r90));
    }

    private static Variant rot(VariantProperties.Rotation x, VariantProperties.Rotation y) {
        Variant v = Variant.variant();
        if (x != VariantProperties.Rotation.R0) v = v.with(VariantProperties.X_ROT, x);
        if (y != VariantProperties.Rotation.R0) v = v.with(VariantProperties.Y_ROT, y);
        return v;
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(GrappleGameTests.class, GrappleLaunchGameTests.class);
    }
}
