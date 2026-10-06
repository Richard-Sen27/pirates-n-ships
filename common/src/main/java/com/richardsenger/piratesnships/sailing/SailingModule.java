package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.block.SailBlock;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindSync;
import com.richardsenger.piratesnships.ship.ShipBlockChanges;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

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
    }

    @Override
    public void registerContent() {
        SailingBlocks.init();
        ShipForces.register();
    }

    @Override
    public void registerPayloads() {
        WindSync.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(WindSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(WindSync::onLogin);
        CommonEvents.LEVEL_TICK_END.register(SailingRuntimes::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> SailingRuntimes.onServerStopped());
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> SailingCommands.register(dispatcher));
        SableShips.onShipRemoved(SailingRuntimes::onShipRemoved);
        SableShips.onPhysicsTick(SailingRuntimes::onPhysicsTick);
        ShipBlockChanges.register(SailingRuntimes::onBlockChanged);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> {
            lang.block(SailingBlocks.SMALL_SQUARE_SAIL, "Small Square Sail")
                    .block(SailingBlocks.LARGE_SQUARE_SAIL, "Large Square Sail")
                    .block(SailingBlocks.FORE_AND_AFT_SAIL, "Fore-and-Aft Sail")
                    .block(SailingBlocks.SAIL_WINCH, "Sail Winch")
                    .add(ShipForces.SAILING_KEY, "Sails and Keel")
                    .add(SailWinchBlock.trimKey(SailTrim.FURLED), "furled")
                    .add(SailWinchBlock.trimKey(SailTrim.HALF), "half sail")
                    .add(SailWinchBlock.trimKey(SailTrim.FULL), "full sail")
                    .add(SailWinchBlock.KEY_NOT_ON_SHIP, "The winch must be on an assembled ship")
                    .add(SailWinchBlock.KEY_NO_SAILS, "This ship has no sails")
                    .add(SailWinchBlock.KEY_SET, "Sails set: %s (%s sails)")
                    .add(SailWinchBlock.KEY_SAIL_SET, "Sail set: %s");
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
            for (RegistryEntry<Block, SailBlock> s : SailingBlocks.sails()) sail(m, s.get());
            m.blocks().createTrivialCube(SailingBlocks.SAIL_WINCH.get());
        });
        data.blockLoot(loot -> {
            for (RegistryEntry<Block, SailBlock> s : SailingBlocks.sails()) loot.dropSelf(s.get());
            loot.dropSelf(SailingBlocks.SAIL_WINCH.get());
        });
        data.blockTags(tags -> {
            for (RegistryEntry<Block, SailBlock> s : SailingBlocks.sails()) {
                tags.tag(BlockTags.MINEABLE_WITH_AXE).add(s.get());
                // canvas on a yard: thin and light, like the flagpole and nameplate
                tags.tag(SableWeightTags.SUPER_LIGHT).add(s.get());
                tags.tag(SableWeightTags.QUARTER_VOLUME).add(s.get());
            }
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(SailingBlocks.SAIL_WINCH.get());
            tags.tag(SableWeightTags.LIGHT).add(SailingBlocks.SAIL_WINCH.get());
        });
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.SMALL_SQUARE_SAIL.get())
                    .pattern("SSS").pattern("WWW")
                    .define('S', Items.STICK).define('W', ItemTags.WOOL)
                    .unlockedBy("has_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHITE_WOOL))
                    .save(out, SailingBlocks.SMALL_SQUARE_SAIL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.LARGE_SQUARE_SAIL.get())
                    .pattern("SSS").pattern("WWW").pattern("WWW")
                    .define('S', Items.STICK).define('W', ItemTags.WOOL)
                    .unlockedBy("has_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHITE_WOOL))
                    .save(out, SailingBlocks.LARGE_SQUARE_SAIL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.FORE_AND_AFT_SAIL.get())
                    .pattern("S  ").pattern("SW ").pattern("SWW")
                    .define('S', Items.STICK).define('W', ItemTags.WOOL)
                    .unlockedBy("has_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHITE_WOOL))
                    .save(out, SailingBlocks.FORE_AND_AFT_SAIL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, SailingBlocks.SAIL_WINCH.get())
                    .pattern("TIT").pattern("PPP")
                    .define('T', Items.STRING).define('I', Items.IRON_INGOT).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, SailingBlocks.SAIL_WINCH.id());
        });
    }

    /**
     * One thin plate model per trim ({@code block/<name>_<trim>}, the open orientable trapdoor plate, as the
     * nameplate), rotated by {@code facing}; the item shows the full sail.
     */
    private static void sail(ModelContext m, SailBlock block) {
        ResourceLocation base = ModelLocationUtils.getModelLocation(block);
        PropertyDispatch.C2<Direction, SailTrim> dispatch = PropertyDispatch.properties(SailBlock.FACING, SailBlock.TRIM);
        for (SailTrim trim : SailTrim.values()) {
            ResourceLocation tex = base.withSuffix("_" + trim.getSerializedName());
            ResourceLocation model = ModelTemplates.ORIENTABLE_TRAPDOOR_OPEN.create(tex, TextureMapping.defaultTexture(tex), m.models());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                VariantProperties.Rotation r = switch (d) {
                    case EAST -> VariantProperties.Rotation.R90;
                    case SOUTH -> VariantProperties.Rotation.R180;
                    case WEST -> VariantProperties.Rotation.R270;
                    default -> VariantProperties.Rotation.R0;
                };
                dispatch.select(d, trim, Variant.variant().with(VariantProperties.MODEL, model).with(VariantProperties.Y_ROT, r));
            }
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()),
                TextureMapping.layer0(base.withSuffix("_full")), m.models());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SailingGameTests.class, SailingGameTestsShips.class);
    }
}
