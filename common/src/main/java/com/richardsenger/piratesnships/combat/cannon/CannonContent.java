package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registered content of the cannons (docs/design.md §8.2): the cannon block with its item and block entity, the swivel
 * gun with its item and block entity, the cannonball entity (which also flies chain shot and grapeshot pellets), the
 * chain shot and grapeshot items (CAN3) and the tags that decide what a ball may destroy, what a swivel gun mounts on and
 * which items are cannon shot. The cannonball item itself is {@code combat.content.CombatContent#CANNONBALL}.
 */
public final class CannonContent {

    public static final RegistryEntry<Block, CannonBlock> CANNON = ModRegistry.blockWithItem("cannon",
            () -> new CannonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<CannonBlockEntity>> CANNON_ENTITY =
            ModRegistry.blockEntity("cannon", CannonBlockEntity::new, CANNON);

    public static final RegistryEntry<EntityType<?>, EntityType<CannonballEntity>> CANNONBALL = ModRegistry.entity("cannonball",
            () -> EntityType.Builder.<CannonballEntity>of(CannonballEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).noSummon().clientTrackingRange(8).updateInterval(5));

    /** CAN3: two balls on a chain, loaded like a cannonball; tears sails and cuts rigging. */
    public static final RegistryEntry<Item, Item> CHAIN_SHOT = ModRegistry.item("chain_shot",
            () -> new Item(new Item.Properties().stacksTo(16)));
    /** CAN3: a canvas bag of small balls, loaded like a cannonball; a cone of pellets that hits the people on a deck. */
    public static final RegistryEntry<Item, Item> GRAPESHOT = ModRegistry.item("grapeshot",
            () -> new Item(new Item.Properties().stacksTo(16)));
    /** Every shot a cannon loads after the powder: cannonball, chain shot, grapeshot (CAN3, for datapacks and lockers). */
    public static final TagKey<Item> CANNON_SHOT = TagKey.create(Registries.ITEM, Constants.id("cannon_shot"));

    /** The swivel gun (P2): a small gun that sits on a railing and turns freely. */
    public static final RegistryEntry<Block, SwivelGunBlock> SWIVEL_GUN = ModRegistry.blockWithItem("swivel_gun",
            () -> new SwivelGunBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<SwivelGunBlockEntity>> SWIVEL_GUN_ENTITY =
            ModRegistry.blockEntity("swivel_gun", SwivelGunBlockEntity::new, SWIVEL_GUN);

    /** Blocks a swivel gun mounts on besides full blocks: fences, walls, iron bars, brig bars. */
    public static final TagKey<Block> SWIVEL_MOUNTS = TagKey.create(Registries.BLOCK, Constants.id("swivel_mounts"));

    /** Blocks a ball may destroy where they are not part of a ship: wood and wool. Ship blocks are always breakable. */
    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, Constants.id("cannon_breakable"));
    /** Blocks a ball never destroys, on a ship or not (obsidian, wither-immune blocks). */
    public static final TagKey<Block> PROOF = TagKey.create(Registries.BLOCK, Constants.id("cannon_proof"));

    private CannonContent() {
    }

    public static void init() {
        // class load registers the entries
    }
}
