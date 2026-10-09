package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * FIX3: block entities and worldgen. {@code BlockState#hasBlockEntity} is true for every state of an
 * {@link EntityBlock}; worldgen leaves a {@code DUMMY} block entity tag at each such position of a proto chunk, and the
 * level chunk later asks {@code newBlockEntity} for it, logging "Tried to load a block entity for block ... but failed"
 * on a null. So every state of every entity block of ours must create a block entity (the passive halves get an empty
 * {@code core.block.PartBlockEntity}), and every structure template of ours carries block entity data only on a block
 * that has one, of that block's type. {@code tools/check_structures.py} runs the same template check without a game.
 */
public final class StructureBlockEntityGameTests {

    /**
     * Entity blocks of ours with no block entity in some state, by registry path, with the reason. Such a block must
     * never stand in a structure template (worldgen would log the warning for it). Keep this short and explained.
     */
    static final Map<String, String> NO_BLOCK_ENTITY = Map.of(
            // extends BedBlock (so EntityBlock) for its sleeping logic, but vanilla's BedBlockEntity is valid only for
            // vanilla beds and carries only their colour
            "sea_cot", "a bed without vanilla's bed block entity");

    private StructureBlockEntityGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(StructureBlockEntityGameTests.class);
    }

    /** Every state of every entity block of ours creates a block entity whose type accepts it, except the listed ones. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void everyStateOfOurEntityBlocksCreatesABlockEntity(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        int checked = 0;
        for (RegistryEntry<Block, ? extends Block> entry : ModRegistry.blocks()) {
            Block block = entry.get();
            String path = entry.id().getPath();
            paths.add(path);
            if (!(block instanceof EntityBlock entityBlock)) continue;
            boolean listed = NO_BLOCK_ENTITY.containsKey(path);
            boolean anyMissing = false;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, state);
                checked++;
                if (be == null) {
                    anyMissing = true;
                    if (!listed) problems.add(state + " has no block entity");
                } else if (!be.getType().isValid(state)) {
                    problems.add(state + " creates a " + BlockEntityType.getKey(be.getType()) + " that does not accept it");
                }
            }
            if (listed && !anyMissing) problems.add(path + " is listed without a block entity but has one in every state");
        }
        for (String listed : NO_BLOCK_ENTITY.keySet()) {
            if (!paths.contains(listed)) problems.add(listed + " is listed but not registered");
        }
        h.assertTrue(checked > 50, "only " + checked + " entity block states checked");
        h.assertTrue(problems.isEmpty(), "entity block states worldgen cannot give a block entity: " + problems);
        h.succeed();
    }

    /**
     * Every structure template of ours (world structure pieces, ship templates, test templates): block entity data only
     * on a block that has a block entity, with that block entity's id, and no entity block whose state has none.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void everyStructureTemplateIsSafeForWorldgen(GameTestHelper h) {
        StructureTemplateManager manager = h.getLevel().getStructureManager();
        List<ResourceLocation> ids = manager.listTemplates().filter(id -> id.getNamespace().equals(Constants.MOD_ID))
                .sorted().toList();
        List<String> problems = new ArrayList<>();
        int blockEntities = 0;
        for (ResourceLocation id : ids) {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template == null) {
                problems.add(id + " does not load");
                continue;
            }
            CompoundTag saved = template.save(new CompoundTag());
            List<ListTag> palettes = new ArrayList<>();
            if (saved.contains("palettes", Tag.TAG_LIST)) {
                ListTag all = saved.getList("palettes", Tag.TAG_LIST);
                for (int i = 0; i < all.size(); i++) palettes.add(all.getList(i));
            } else {
                palettes.add(saved.getList("palette", Tag.TAG_COMPOUND));
            }
            ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
            for (ListTag palette : palettes) {
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag block = blocks.getCompound(i);
                    String problem = check(palette.getCompound(block.getInt("state")), block);
                    if (problem != null) problems.add(id + " " + NbtUtils.readBlockPos(block, "pos").map(BlockPos::toShortString)
                            .orElse(block.get("pos") + "") + ": " + problem);
                    if (block.contains("nbt")) blockEntities++;
                }
            }
        }
        h.assertTrue(ids.size() > 30, "only " + ids.size() + " templates of ours listed: " + ids);
        h.assertTrue(blockEntities > 0, "no block entity data in any template");
        h.assertTrue(problems.isEmpty(), problems.size() + " block entity problems in the structure templates: " + problems);
        h.succeed();
    }

    /** The problem with one template block ({@code pos}, {@code state}, optional {@code nbt}), or null. */
    private static String check(CompoundTag paletteEntry, CompoundTag block) {
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteEntry);
        String name = paletteEntry.getString("Name");
        if (state.isAir() && !name.endsWith("air")) return "unknown block " + name;
        CompoundTag nbt = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null;
        if (!state.hasBlockEntity()) {
            return nbt == null ? null : "block entity data " + nbt.getString("id") + " on " + state + ", which has none";
        }
        BlockEntity be = ((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
        if (be == null) return state + " is an entity block without a block entity here (worldgen would log a failed load)";
        String expected = String.valueOf(BlockEntityType.getKey(be.getType()));
        if (nbt != null && !expected.equals(nbt.getString("id"))) {
            return "block entity data " + nbt.getString("id") + " on " + state + ", whose block entity is " + expected;
        }
        return null;
    }
}
