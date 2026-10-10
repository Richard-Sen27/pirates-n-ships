package com.richardsenger.piratesnships.world.structure;

import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a region of the world as a Sponge schematic, version 2 (STR2's region dump for the renders of generated
 * structures; {@code tools/render_generated_structure.py} reads it). Blocks only (block entities and entities are left
 * out, the renders do not need them); index order y, z, x as the format asks, palette ids as varints.
 *
 * <p>The output is a function of the blocks alone (STR5): palette ids are handed out in scan order, so equal regions get
 * equal palettes inserted in equal order, and {@code Offset} is the region's corner relative to a caller-given origin
 * (the GameTest's), never its absolute world position. The absolute position depends on where the test framework lays
 * out the arena, which differs between NeoForge and Fabric and between scoped and full runs; writing it made the dumps
 * of identical placements differ by those twelve bytes.
 */
public final class RegionSchematic {

    private RegionSchematic() {
    }

    /** The schematic compound of {@code box} in {@code level}; {@code Offset} is {@code box}'s corner minus {@code origin}. */
    public static CompoundTag of(BlockGetter level, BoundingBox box, BlockPos origin) {
        int width = box.getXSpan();
        int height = box.getYSpan();
        int length = box.getZSpan();
        if (width > Short.MAX_VALUE || height > Short.MAX_VALUE || length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("region too large: " + box);
        }
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState state = level.getBlockState(pos.set(box.minX() + x, box.minY() + y, box.minZ() + z));
                    int id = palette.computeIfAbsent(BlockStateParser.serialize(state), k -> palette.size());
                    writeVarint(data, id);
                }
            }
        }
        CompoundTag paletteTag = new CompoundTag();
        palette.forEach(paletteTag::putInt);
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        root.put("Offset", new IntArrayTag(new int[] {box.minX() - origin.getX(), box.minY() - origin.getY(),
                box.minZ() - origin.getZ()}));
        root.putInt("PaletteMax", palette.size());
        root.put("Palette", paletteTag);
        root.put("BlockData", new ByteArrayTag(data.toByteArray()));
        return root;
    }

    /** Writes {@link #of} to {@code file} (gzipped NBT), creating its folder. */
    public static void write(BlockGetter level, BoundingBox box, BlockPos origin, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        NbtIo.writeCompressed(of(level, box, origin), file);
    }

    static void writeVarint(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
