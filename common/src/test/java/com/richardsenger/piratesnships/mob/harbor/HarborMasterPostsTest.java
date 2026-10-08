package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * PRT1a: each harbor master's post lies in the committed start piece with room to stand on a floor, right behind the
 * desk on the side away from the customer, facing the desk's customer side; the post turns with its piece, and the
 * first start piece in the jigsaw's order wins.
 */
class HarborMasterPostsTest {

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

    private static Template template(String piece) throws IOException {
        String group = HarborMasterPosts.GROUPS.get(piece);
        CompoundTag tag = NbtIo.readCompressed(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/" + group + "/"
                + piece + ".nbt"), NbtAccounter.unlimitedHeap());
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

    /** Free (air or nothing placed) at the post and above it, a solid floor below. */
    private static void assertStandable(String piece) throws IOException {
        Template t = template(piece);
        HarborMasterPosts.Post post = HarborMasterPosts.POSTS.get(piece);
        BlockPos p = post.local();
        String where = piece + " " + p.toShortString();
        assertTrue(p.getX() >= 0 && p.getX() < t.size()[0] && p.getZ() >= 0 && p.getZ() < t.size()[2]
                && p.getY() >= 1 && p.getY() + 1 < t.size()[1], where + " lies inside the piece");
        for (BlockPos room : List.of(p, p.above())) {
            String name = t.names().getOrDefault(room, "minecraft:air");
            assertTrue(name.equals("minecraft:air") || name.equals("minecraft:structure_void"),
                    where + ": " + room.toShortString() + " is free, found " + name);
        }
        BlockState below = t.at(p.below());
        assertTrue(below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP),
                where + " stands on a sturdy floor, found " + t.names().get(p.below()));
    }

    /**
     * The piece's harbor desk touches the post (beside it or diagonal on the same row), lies on the side the post
     * faces, and its customer side ({@code facing}) is the way he faces: he stands behind it.
     */
    private static void assertBehindTheDesk(String piece) throws IOException {
        Template t = template(piece);
        HarborMasterPosts.Post post = HarborMasterPosts.POSTS.get(piece);
        BlockPos desk = t.names().entrySet().stream().filter(e -> e.getValue().equals("pirates_n_ships:harbor_desk"))
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new AssertionError(piece + " has a harbor desk"));
        BlockPos d = desk.subtract(post.local());
        assertEquals(0, d.getY(), piece + ": the desk stands on his row");
        assertTrue(Math.abs(d.getX()) <= 1 && Math.abs(d.getZ()) <= 1, piece + ": the desk " + desk.toShortString() + " is next to him");
        Direction f = post.facing();
        assertEquals(1, d.getX() * f.getStepX() + d.getZ() * f.getStepZ(), piece + ": the desk is in front of him");
        assertEquals(f, t.at(desk).getValue(BlockStateProperties.HORIZONTAL_FACING), piece + ": the desk's customer side faces his way");
    }

    @Test
    void theDockHeadPostIsBehindTheVillageDesk() throws IOException {
        assertStandable(HarborMasterPosts.DOCK_HEAD);
        assertBehindTheDesk(HarborMasterPosts.DOCK_HEAD);
        assertEquals(new HarborMasterPosts.Post(new BlockPos(7, 1, 7), Direction.NORTH), HarborMasterPosts.DOCK_HEAD_POST);
    }

    @Test
    void theFortGatePostIsBesideTheStoolBehindTheDesk() throws IOException {
        assertStandable(HarborMasterPosts.FORT_GATE);
        assertBehindTheDesk(HarborMasterPosts.FORT_GATE);
        assertEquals(Direction.EAST, HarborMasterPosts.FORT_GATE_POST.facing());
        // the plan's (2, 1, 8) is his stool: a stair, not a place to stand
        assertTrue(template(HarborMasterPosts.FORT_GATE).names().get(new BlockPos(2, 1, 8)).contains("stairs"), "the stool");
    }

    @Test
    void theCampPostIsBehindTheFencesCounter() throws IOException {
        assertStandable(HarborMasterPosts.CAMP_START);
        assertBehindTheDesk(HarborMasterPosts.CAMP_START);
        assertEquals(new HarborMasterPosts.Post(new BlockPos(10, 1, 2), Direction.WEST), HarborMasterPosts.CAMP_POST);
    }

    @Test
    void thePostsKeepClearOfTheGarrison() {
        for (GarrisonPosts.Post g : GarrisonPosts.POSTS.get(GarrisonPosts.FORT_GATE)) {
            assertFalse(g.local().equals(HarborMasterPosts.FORT_GATE_POST.local()), "no garrison post at " + g.local().toShortString());
        }
    }

    @Test
    void thePostTurnsWithItsPiece() {
        // the dock head turned clockwise: local (7, 1, 7) goes to (-7, 1, 7) from the origin, north turns east
        PlacedPiece dock = new PlacedPiece(HarborMasterPosts.DOCK_HEAD, new BlockPos(100, 64, 200), Rotation.CLOCKWISE_90);
        HarborMasterPosts.Placement p = HarborMasterPosts.post(List.of(dock)).orElseThrow();
        assertEquals(new BlockPos(93, 65, 207), p.pos());
        assertEquals(Direction.EAST, p.facing());
        assertEquals(HarborMasterPosts.DOCK_HEAD, p.piece());
        // the fort gate half-turned: (2, 1, 9) to (-2, 1, -9), east to west
        PlacedPiece gate = new PlacedPiece(HarborMasterPosts.FORT_GATE, new BlockPos(0, 70, 0), Rotation.CLOCKWISE_180);
        HarborMasterPosts.Placement g = HarborMasterPosts.post(List.of(gate)).orElseThrow();
        assertEquals(new BlockPos(-2, 71, -9), g.pos());
        assertEquals(Direction.WEST, g.facing());
    }

    @Test
    void theStartPieceHoldsThePostOtherPiecesNone() {
        PlacedPiece street = new PlacedPiece("street", new BlockPos(0, 64, 0), Rotation.NONE);
        PlacedPiece camp = new PlacedPiece(HarborMasterPosts.CAMP_START, new BlockPos(10, 64, 20), Rotation.NONE);
        assertEquals(Optional.empty(), HarborMasterPosts.post(List.of(street)), "no start piece: no post");
        HarborMasterPosts.Placement p = HarborMasterPosts.post(List.of(street, camp)).orElseThrow();
        assertEquals(new BlockPos(20, 65, 22), p.pos());
        assertEquals(Direction.WEST, p.facing());
    }

    @Test
    void pieceNamesComeFromThePoolElement() {
        assertEquals("dock_head", HarborMasterPosts.pieceName("Single[Left[pirates_n_ships:village/dock_head]]"));
        assertEquals("fort_gate", HarborMasterPosts.pieceName("Single[Left[pirates_n_ships:navy_outpost/fort_gate]]"));
        assertEquals("camp_start", HarborMasterPosts.pieceName("LegacySingle[Left[pirates_n_ships:pirate_island/camp_start]]"));
        assertEquals("", HarborMasterPosts.pieceName("Single[Left[minecraft:village/plains/houses/plains_small_house_1]]"));
    }
}
