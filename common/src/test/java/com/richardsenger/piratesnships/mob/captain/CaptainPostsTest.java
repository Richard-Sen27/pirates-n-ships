package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.structure.StandAloneOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOS1: the captain's posts lie in the committed ST2 pieces with room to stand on a floor, and the plan prefers the
 * first captain's hut, falls back to the camp, and turns with the piece.
 */
class CaptainPostsTest {

    private static Path root;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = StandAloneOps.root();
    }

    private record Template(int[] size, Map<BlockPos, BlockState> blocks, Map<BlockPos, String> names) {
        BlockState at(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static Template template(String name) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/pirate_island/"
                + name + ".nbt"), NbtAccounter.unlimitedHeap());
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        int[] size = {sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2)};
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, String> names = new HashMap<>();
        for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            CompoundTag state = palette.getCompound(b.getInt("state"));
            names.put(pos, state.getString("Name"));
            blocks.put(pos, NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state));
        }
        return new Template(size, blocks, names);
    }

    private static void assertStandable(String piece, CaptainPosts.Post post, boolean sturdy) throws IOException {
        Template t = template(piece);
        BlockPos p = post.local();
        String where = piece + " " + p.toShortString();
        assertTrue(p.getX() >= 0 && p.getX() < t.size()[0] && p.getZ() >= 0 && p.getZ() < t.size()[2]
                && p.getY() >= 1 && p.getY() + 1 < t.size()[1], where + " lies inside the piece");
        for (BlockPos room : List.of(p, p.above())) {
            String name = t.names().getOrDefault(room, "minecraft:air");
            assertTrue(name.equals("minecraft:air") || name.equals("minecraft:structure_void"), where + ": " + room.toShortString() + " is free, found " + name);
        }
        BlockState below = t.at(p.below());
        assertFalse(below.isAir(), where + " has a floor (" + t.names().get(p.below()) + ")");
        if (sturdy) {
            assertTrue(below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP),
                    where + " stands on a sturdy block, found " + t.names().get(p.below()));
        } else {
            assertFalse(below.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(), where + " stands on a solid block");
        }
    }

    @Test
    void theHutPostIsInTheRoomOnTheFloorBoards() throws IOException {
        assertStandable(CaptainPosts.CAPTAINS_HUT, CaptainPosts.HUT_POST, true);
        Template hut = template(CaptainPosts.CAPTAINS_HUT);
        BlockPos door = new BlockPos(4, 3, 3);
        assertTrue(hut.names().get(door).contains("door"), "the door is in front of him: " + hut.names().get(door));
        assertEquals(Direction.NORTH, CaptainPosts.HUT_POST.facing(), "he faces the door");
    }

    @Test
    void theCampPostIsOnTheInlandTrail() throws IOException {
        assertStandable(CaptainPosts.CAMP_START, CaptainPosts.CAMP_POST, false);
    }

    @Test
    void theFirstHutWinsThenTheCamp() {
        PlacedPiece camp = new PlacedPiece(CaptainPosts.CAMP_START, new BlockPos(100, 64, 200), Rotation.NONE);
        PlacedPiece tent = new PlacedPiece("tent", new BlockPos(90, 64, 220), Rotation.NONE);
        PlacedPiece hut1 = new PlacedPiece(CaptainPosts.CAPTAINS_HUT, new BlockPos(120, 63, 230), Rotation.NONE);
        PlacedPiece hut2 = new PlacedPiece(CaptainPosts.CAPTAINS_HUT, new BlockPos(60, 63, 230), Rotation.NONE);
        CaptainPosts.Placement p = CaptainPosts.post(List.of(camp, tent, hut1, hut2)).orElseThrow();
        assertEquals(new BlockPos(124, 66, 235), p.pos(), "the first hut's room");
        assertEquals(Direction.NORTH, p.facing());
        assertEquals(CaptainPosts.CAPTAINS_HUT, p.piece());
        CaptainPosts.Placement fallback = CaptainPosts.post(List.of(camp, tent)).orElseThrow();
        assertEquals(new BlockPos(106, 65, 210), fallback.pos(), "no hut: the camp's trail");
        assertEquals(CaptainPosts.CAMP_START, fallback.piece());
        assertEquals(Optional.empty(), CaptainPosts.post(List.of(tent)), "neither: no post");
    }

    @Test
    void thePostTurnsWithItsHut() {
        // the hut turned clockwise: local (4, 3, 5) goes to (-5, 3, 4) from the origin, north turns east
        PlacedPiece hut = new PlacedPiece(CaptainPosts.CAPTAINS_HUT, new BlockPos(100, 64, 200), Rotation.CLOCKWISE_90);
        CaptainPosts.Placement p = CaptainPosts.post(List.of(hut)).orElseThrow();
        assertEquals(new BlockPos(95, 67, 204), p.pos());
        assertEquals(Direction.EAST, p.facing());
    }

    @Test
    void pieceNamesComeFromThePoolElement() {
        assertEquals("captains_hut", CaptainPosts.pieceName("Single[Left[pirates_n_ships:pirate_island/captains_hut]]"));
        assertEquals("camp_start", CaptainPosts.pieceName("LegacySingle[Left[pirates_n_ships:pirate_island/camp_start]]"));
        assertEquals("", CaptainPosts.pieceName("Single[Left[pirates_n_ships:navy_outpost/wall]]"));
    }
}
