package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.block.PartBlockEntity;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Brig content (design.md §13.3): shackles, brig bars, the lockable brig door and its key. Prisoners: {@code law.brig}.
 * And the notice board (§13.2).
 */
public final class LawContent {

    /**
     * Iron door sounds. {@code canOpenByHand = false} like vanilla's iron set, so mobs (villagers, door-breaking
     * zombies, pathfinding) treat the door like an iron door; {@link BrigDoorBlock} opens it by hand itself and adds
     * the lock. Not added to vanilla's block set registry (its {@code register} is private); nothing in 1.21.1 looks
     * block sets up by name for our blocks.
     */
    public static final BlockSetType BRIG_SET = new BlockSetType(Constants.MOD_ID + ":brig",
            false, false, false, BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

    public static final RegistryEntry<Item, Item> SHACKLES = ModRegistry.item("shackles", () -> new ShacklesItem(new Item.Properties().stacksTo(16)));

    /** Locks and unlocks any brig door (design.md §13.3); never changes the door's owner. */
    public static final RegistryEntry<Item, Item> BRIG_KEY = ModRegistry.item("brig_key", () -> new BrigKeyItem(new Item.Properties().stacksTo(1)));

    /** Vanilla iron bars behavior (connects to neighbors and sturdy faces), plus brig doors. */
    public static final RegistryEntry<Block, BrigBarsBlock> BRIG_BARS = ModRegistry.blockWithItem("brig_bars",
            () -> new BrigBarsBlock(BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(5.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));

    /** Door behavior plus a lock (two halves, hinge, opens by hand and redstone unless locked). */
    public static final RegistryEntry<Block, BrigDoorBlock> BRIG_DOOR = ModRegistry.blockWithItem("brig_door",
            () -> new BrigDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                    .strength(5.0f).noOcclusion().pushReaction(PushReaction.DESTROY)));

    /** Lists the active bounties and takes new ones (design.md §13.2). */
    /** The notice board in every wood (WOOD3); the dark oak member keeps the id {@code notice_board}. */
    public static final WoodFamily<NoticeBoardBlock> NOTICE_BOARDS = WoodFamily.register("notice_board", ShipWood.DARK_OAK,
            wood -> new NoticeBoardBlock(wood, WoodFamily.properties(wood).noOcclusion()));
    /** The dark oak notice board, legacy id {@code notice_board}. */
    public static final RegistryEntry<Block, NoticeBoardBlock> NOTICE_BOARD = NOTICE_BOARDS.entry(ShipWood.DARK_OAK);

    /**
     * The 2×2 market notice board in every wood (NB2); the dark oak member keeps the id {@code market_notice_board}.
     * Pistons can't move it (a part pushed away would split the board).
     */
    public static final WoodFamily<MarketNoticeBoardBlock> MARKET_NOTICE_BOARDS = WoodFamily.register("market_notice_board", ShipWood.DARK_OAK,
            wood -> new MarketNoticeBoardBlock(wood, WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.BLOCK)));
    /** The dark oak market notice board, id {@code market_notice_board}. */
    public static final RegistryEntry<Block, MarketNoticeBoardBlock> MARKET_NOTICE_BOARD = MARKET_NOTICE_BOARDS.entry(ShipWood.DARK_OAK);
    /** The anchor's block entity: counts the postings and sets the board's load. */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<MarketNoticeBoardBlockEntity>> MARKET_NOTICE_BOARD_ENTITY =
            MARKET_NOTICE_BOARDS.blockEntity("market_notice_board", MarketNoticeBoardBlockEntity::new);
    /** The other three parts' empty block entity (FIX3, {@link PartBlockEntity}). */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<PartBlockEntity>> MARKET_NOTICE_BOARD_PART_ENTITY =
            MARKET_NOTICE_BOARDS.blockEntity("market_notice_board_part",
                    (pos, state) -> new PartBlockEntity(LawContent.MARKET_NOTICE_BOARD_PART_ENTITY.get(), pos, state));

    /** Owner of a brig door (lower half). */
    @SuppressWarnings("unchecked")
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BrigDoorBlockEntity>> BRIG_DOOR_ENTITY =
            ModRegistry.blockEntity("brig_door", BrigDoorBlockEntity::new, BRIG_DOOR);

    /** The upper half's empty block entity (FIX3, {@link PartBlockEntity}: worldgen needs one on every door state). */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<PartBlockEntity>> BRIG_DOOR_UPPER_ENTITY =
            ModRegistry.blockEntity("brig_door_upper",
                    (pos, state) -> new PartBlockEntity(LawContent.BRIG_DOOR_UPPER_ENTITY.get(), pos, state), BRIG_DOOR);

    private LawContent() {
    }

    public static void init() {
    }
}
