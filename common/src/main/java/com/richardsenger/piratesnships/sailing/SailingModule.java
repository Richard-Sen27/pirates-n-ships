package com.richardsenger.piratesnships.sailing;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.helm.HelmSetup;
import com.richardsenger.piratesnships.sailing.sail.SailDecorations;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsControls;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.sailing.wind.WindSync;
import com.richardsenger.piratesnships.ship.ShipBlockChanges;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * The {@code sailing} module (docs/design.md §5): the wind field and its client sync, the pure force model
 * ({@code sailing.force}), the sail blocks and the sail winch ({@code sailing.block}), and the per-ship runtime that
 * applies sail and keel forces to Sable ships every physics substep ({@code sailing.ship}).
 */
public final class SailingModule implements ModModule {

    @Override
    public String id() {
        return "sailing";
    }

    @Override
    public void registerConfig() {
        SailingConfig.init();
        com.richardsenger.piratesnships.sailing.anchor.AnchorConfig.init(); // visible anchor (F4)
        HelmSetup.registerConfig(); // wheel steering (HELM1)
        com.richardsenger.piratesnships.sailing.effects.SeaEffectsConfig.init(); // wind streaks (WD1)
        com.richardsenger.piratesnships.sailing.sail.SailVisualsConfig.init(); // sails in the wind (VIS1b), client section
    }

    @Override
    public void registerContent() {
        SailingBlocks.init();
        ShipForces.register();
        ShipForces.registerAnchor(); // the anchor's chain (AN2a)
        com.richardsenger.piratesnships.sailing.anchor.AnchorContent.init(); // visible anchor (F4)
        HelmSetup.registerContent(); // wheel steering (HELM1)
    }

    @Override
    public void registerPayloads() {
        WindSync.registerPayloads();
        HelmSetup.registerPayloads(); // wheel steering (HELM1)
        com.richardsenger.piratesnships.sailing.ship.ShipBowSync.registerPayloads(); // the bow for the sails (VIS1c)
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(WindSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(WindSync::onLogin);
        CommonEvents.LEVEL_TICK_END.register(SailingRuntimes::onLevelTick);
        // VIS1c: each ship's bow to the clients that render it, after the runtimes so a fresh one goes out this tick
        CommonEvents.LEVEL_TICK_END.register(com.richardsenger.piratesnships.sailing.ship.ShipBowSync::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> SailingRuntimes.onServerStopped());
        CommonEvents.SERVER_STOPPED.register(server -> com.richardsenger.piratesnships.sailing.ship.ShipBowSync.onServerStopped());
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> SailingCommands.register(dispatcher));
        SableShips.onShipRemoved(SailingRuntimes::onShipRemoved);
        SableShips.onShipRemoved(com.richardsenger.piratesnships.sailing.ship.ShipBowSync::onShipRemoved);
        SableShips.onPhysicsTick(SailingRuntimes::onPhysicsTick);
        ShipBlockChanges.register(SailingRuntimes::onBlockChanged);
        // HL1b: a ship Sable moves to a new body keeps its bow (position-free); the anchor (a plot position) stays behind
        com.richardsenger.piratesnships.ship.assembly.ShipSplits.carryOnIdentityMove(SailingRuntimes.USER_DATA_KEY, tag -> {
            net.minecraft.nbt.CompoundTag kept = new net.minecraft.nbt.CompoundTag();
            if (tag.contains("bow")) {
                kept.putString("bow", tag.getString("bow"));
            }
            return kept;
        });
        // wheel steering (HELM1): the helm's steering handler starts a wheel session, or runs the click steps of
        // ShipControls.steer when helm.wheel.drag_steering is off
        HelmSetup.registerEvents();
        com.richardsenger.piratesnships.sailing.anchor.AnchorEntities.registerEvents(); // visible anchor (F4)
        com.richardsenger.piratesnships.sailing.anchor.AnchorPhysics.registerEvents(); // the anchor's chain force (AN2a)
    }

    @Override
    public void gatherData(DataContributions data) {
        com.richardsenger.piratesnships.sailing.anchor.AnchorData.gather(data); // visible anchor (F4)
        com.richardsenger.piratesnships.sailing.sail.TriangularSailData.gather(data); // cleat and rope (F5b)
        HelmSetup.gatherData(data); // wheel steering (HELM1)
        data.lang(lang -> {
            lang.block(SailingBlocks.YARD, "Yard")
                    .add(YardBlock.KEY_NO_SAIL, "This yard heads no sail: hang a second yard %s to %s blocks straight below its middle, on the same mast")
                    .block(SailingBlocks.SAIL_WINCH, "Sail Winch")
                    .add(ShipForces.SAILING_KEY, "Sails and Keel")
                    .add(ShipForces.ANCHOR_KEY, "Anchor Chain")
                    .add(SailWinchBlock.trimKey(SailTrim.FURLED), "furled")
                    .add(SailWinchBlock.trimKey(SailTrim.HALF), "half sail")
                    .add(SailWinchBlock.trimKey(SailTrim.FULL), "full sail")
                    .add(SailWinchBlock.KEY_NOT_ON_SHIP, "The winch must be on an assembled ship")
                    .add(SailWinchBlock.KEY_NO_SAILS, "This ship has no sails")
                    .add(SailWinchBlock.KEY_SET, "Sails set: %s (%s sails)")
                    .add(SailWinchBlock.KEY_SAIL_SET, "Sail set: %s")
                    .block(SailingBlocks.CAPSTAN, "Capstan")
                    .add(ShipControls.KEY_RUDDER_MIDSHIPS, "Rudder midships")
                    .add(ShipControls.KEY_RUDDER, "Rudder %s of %s to %s (%s°)")
                    .add(ShipControls.KEY_PORT, "port")
                    .add(ShipControls.KEY_STARBOARD, "starboard")
                    .add(ShipControls.KEY_STEERING_OFF, "Steering is disabled on this server. Sneak-use with an empty hand to disassemble")
                    .add(ShipControls.KEY_CAPSTAN_NOT_ON_SHIP, "The capstan must be on an assembled ship")
                    .add(ShipControls.KEY_CAPSTAN_OFF, "Anchors are disabled on this server")
                    .add(ShipControls.KEY_NO_GROUND, "No ground within %s blocks below: the anchor would not hold")
                    .add(ShipControls.KEY_DROPPING, "Anchor dropping to the ground %s blocks below, lands in about %s s")
                    .add(ShipControls.KEY_RAISING, "Raising the anchor, stowed in %s s");
            // SAIL2: dyeing sails and hanging banners on them
            lang.add(SailDecorations.Outcome.DYED.key(), "Sail dyed %s")
                    .add(SailDecorations.Outcome.SAME_DYE.key(), "This sail is already %s")
                    .add(SailDecorations.Outcome.NO_SAIL.key(), "This heads no sail: dye or hang a banner on the upper yard of a square sail, or dye the head cleat of a triangular sail")
                    .add(SailDecorations.Outcome.HAS_BANNER.key(), "A banner hangs on this sail and gives it its colour: sneak-use the yard with an empty hand to take it back first")
                    .add(SailDecorations.Outcome.HUNG.key(), "Banner hung on the sail")
                    .add(SailDecorations.Outcome.TOO_SMALL.key(), "Too small for a banner: both yards must be at least %s blocks long and %s blocks apart")
                    .add(SailDecorations.Outcome.ALREADY_BANNER.key(), "A banner already hangs on this sail: sneak-use the yard with an empty hand to take it back")
                    .add(SailDecorations.Outcome.TAKEN.key(), "Banner taken down")
                    .add(SailDecorations.Outcome.STAY_DYE_ONLY.key(), "A triangular sail takes dye only; banners go on large square sails");
            String k = SailingCommands.KEY;
            lang.add(k + "wind.get", "Wind from %s° at %s blocks/s (%s)")
                    .add(k + "wind.fixed", "fixed by command")
                    .add(k + "wind.natural", "natural")
                    .add(k + "wind.set", "Wind fixed: from %s° at %s blocks/s, until /pirates wind clear")
                    .add(k + "wind.clear", "Wind override cleared, the natural wind blows again")
                    .add(k + "ship.none", "You are not on an assembled ship")
                    .add(k + "ship.idle", "No forces this tick: no sail is set and the ship is not moving in water");
        });
        data.models(m -> {
            yard(m, SailingBlocks.YARD.get());
            sailWinch(m, SailingBlocks.SAIL_WINCH.get());
            // hand-made Blockbench model (art/models/capstan.bbmodel, design.md §4.8): only the block state is generated
            m.blockStates().accept(MultiVariantGenerator.multiVariant(SailingBlocks.CAPSTAN.get(), Variant.variant()
                    .with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(SailingBlocks.CAPSTAN.get()))));
        });
        data.blockLoot(loot -> {
            loot.dropSelf(SailingBlocks.YARD.get());
            loot.dropSelf(SailingBlocks.SAIL_WINCH.get());
            loot.dropSelf(SailingBlocks.CAPSTAN.get());
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(SailingBlocks.YARD.get());
            // what may stand between the two yards of a square sail (besides air); our own mast blocks join later
            tags.tag(SailingBlocks.MASTS).addTag(BlockTags.LOGS).addTag(BlockTags.WOODEN_FENCES);
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(SailingBlocks.SAIL_WINCH.get());
            tags.tag(SableWeightTags.LIGHT).add(SailingBlocks.SAIL_WINCH.get());
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(SailingBlocks.CAPSTAN.get());
        });
        // A yard is a 6 px spar (about 0.14 of a block of wood) high up on the mast: Sable's lightest tag (0.25 kpg, the
        // fence's) still made the small test hull top-heavy with six of them, so it gets its own, physical mass and volume
        // (refs/sable/wiki/Block Physics Properties.md; priority above Sable's tag definitions).
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", SailingBlocks.YARD.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 0.1);
            properties.addProperty("sable:volume", 0.15);
            JsonObject json = new JsonObject();
            json.addProperty("selector", SailingBlocks.YARD.id().toString());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
        data.recipes(out -> {
            // three logs in a row give three yards (the cloth needs no item: it is drawn between the yards)
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.YARD.get(), 3)
                    .pattern("LLL")
                    .define('L', ItemTags.LOGS)
                    .unlockedBy("has_log", InventoryChangeTrigger.TriggerInstance.hasItems(
                            net.minecraft.advancements.critereon.ItemPredicate.Builder.item().of(ItemTags.LOGS)))
                    .save(out, SailingBlocks.YARD.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.SAIL_WINCH.get())
                    .pattern("TIT").pattern("PPP")
                    .define('T', Items.STRING).define('I', Items.IRON_INGOT).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, SailingBlocks.SAIL_WINCH.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.CAPSTAN.get())
                    .pattern("LSL").pattern("CIC").pattern("PPP")
                    .define('L', ItemTags.LOGS).define('S', Items.STICK).define('C', Items.CHAIN)
                    .define('I', Items.IRON_BLOCK).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_chain", InventoryChangeTrigger.TriggerInstance.hasItems(Items.CHAIN))
                    .save(out, SailingBlocks.CAPSTAN.id());
        });
    }

    /**
     * The yard: a hand-made Blockbench model along x (art/models/yard.bbmodel, design.md §4.8), so only the block state
     * is generated; {@code axis=z} turns it by 90°. The cloth is drawn by {@code YardClothRenderer}, not by the model.
     */
    private static void yard(ModelContext m, YardBlock block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(YardBlock.AXIS)
                .select(Direction.Axis.X, Variant.variant().with(VariantProperties.MODEL, model))
                .select(Direction.Axis.Z, Variant.variant().with(VariantProperties.MODEL, model)
                        .with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))));
    }

    /**
     * The sail winch: a hand-made Blockbench model (art/models/sail_winch.bbmodel, design.md §4.8) with its crank on the
     * east side, so only the block state is generated. {@code facing} is the crank's side: {@code east} is the unrotated
     * model, and each further quarter turn clockwise seen from above (vanilla's y rotation, as for the helm) adds 90°.
     */
    private static void sailWinch(ModelContext m, SailWinchBlock block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant().with(VariantProperties.MODEL, model))
                .with(PropertyDispatch.property(SailWinchBlock.FACING)
                        .select(Direction.EAST, Variant.variant())
                        .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                        .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                        .select(Direction.NORTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.sailing.client.SailingClient.init();
        com.richardsenger.piratesnships.sailing.anchor.client.AnchorClient.init(); // visible anchor (F4)
        HelmSetup.initClient(); // wheel steering (HELM1)
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SailingGameTests.class, SailingGameTestsShips.class, SailingGameTestsControls.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsStays.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsRigging.class,
                com.richardsenger.piratesnships.sailing.helm.HelmSteeringGameTests.class,
                com.richardsenger.piratesnships.sailing.rope.RopeLineGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.HelmHandoverGameTests.class,
                com.richardsenger.piratesnships.sailing.anchor.AnchorPhysicsGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsTurning.class,
                com.richardsenger.piratesnships.sailing.ship.ShipBowSyncGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.SailDyeGameTests.class);
    }
}
