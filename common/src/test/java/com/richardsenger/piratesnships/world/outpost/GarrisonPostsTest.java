package com.richardsenger.piratesnships.world.outpost;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.Assignment;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.Post;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.Role;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The garrison's posts (WG3): every post of the table lies inside its committed ST3 piece on a walkable block with
 * room to stand, and the plan fills the posts in the decided order, capped, turned with each piece.
 */
class GarrisonPostsTest {

    private static Path root;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = StandAloneOps.root();
    }

    /** A committed piece: size and its blocks by local position (vanilla states; mod blocks read as air). */
    private record Template(int[] size, Map<BlockPos, BlockState> blocks, Map<BlockPos, String> names) {
        BlockState at(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static Template template(String name) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/navy_outpost/"
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

    @Test
    void everyPostStandsOnAWalkableBlockInsideItsPiece() throws IOException {
        Set<BlockPos> seen = new HashSet<>();
        for (Map.Entry<String, List<Post>> e : GarrisonPosts.POSTS.entrySet()) {
            Template t = template(e.getKey());
            seen.clear();
            for (Post post : e.getValue()) {
                BlockPos p = post.local();
                String where = e.getKey() + " " + p.toShortString();
                assertTrue(seen.add(p), where + " is listed once");
                assertTrue(p.getX() >= 0 && p.getX() < t.size()[0] && p.getZ() >= 0 && p.getZ() < t.size()[2]
                        && p.getY() >= 1 && p.getY() + 1 < t.size()[1], where + " lies inside the piece " + List.of(t.size()[0], t.size()[1], t.size()[2]));
                for (BlockPos room : List.of(p, p.above())) {
                    String name = t.names().getOrDefault(room, "minecraft:air");
                    assertTrue(name.equals("minecraft:air") || name.equals("minecraft:white_carpet"),
                            where + ": " + room.toShortString() + " is free, found " + name);
                }
                BlockState below = t.at(p.below());
                assertFalse(below.isAir(), where + " has a floor (" + t.names().get(p.below()) + ")");
                assertTrue(below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP),
                        where + " stands on a sturdy block, found " + t.names().get(p.below()));
            }
        }
    }

    @Test
    void theGateAloneHoldsTheDefaultGarrison() {
        long soldiers = GarrisonPosts.POSTS.get(GarrisonPosts.FORT_GATE).stream().filter(p -> p.role() == Role.SOLDIER).count();
        long officers = GarrisonPosts.POSTS.get(GarrisonPosts.FORT_GATE).stream().filter(p -> p.role() == Role.OFFICER).count();
        assertEquals(6, soldiers);
        assertEquals(3, officers);
        List<Assignment> plan = GarrisonPosts.plan(List.of(gate(Rotation.NONE)), 6, 1);
        assertEquals(7, plan.size());
        assertEquals(1, plan.stream().filter(a -> a.role() == Role.OFFICER).count());
        assertEquals(new BlockPos(100 + 6, 65, 200 + 7), plan.get(0).pos(), "the officer beside the office door");
        assertEquals(Direction.EAST, plan.get(0).facing());
        assertEquals(7, Set.copyOf(plan.stream().map(Assignment::pos).toList()).size(), "one mob per post");
    }

    @Test
    void soldiersGuardTheLandGateThenTheNearestWallsThenTheBuilding() {
        PlacedPiece gate = gate(Rotation.NONE);
        PlacedPiece eastWall = new PlacedPiece("wall", new BlockPos(115, 64, 200), Rotation.NONE);
        PlacedPiece westWall = new PlacedPiece("wall", new BlockPos(93, 64, 200), Rotation.NONE);
        PlacedPiece eastTower = new PlacedPiece("wall_tower", new BlockPos(122, 64, 200), Rotation.NONE);
        PlacedPiece westTower = new PlacedPiece("wall_tower", new BlockPos(86, 64, 200), Rotation.NONE);
        PlacedPiece quay = new PlacedPiece("quay", new BlockPos(104, 59, 182), Rotation.NONE);
        PlacedPiece barracks = new PlacedPiece("barracks", new BlockPos(109, 64, 215), Rotation.NONE);
        List<PlacedPiece> layout = List.of(gate, quay, eastWall, westWall, barracks, eastTower, westTower);

        List<Assignment> soldiers = GarrisonPosts.plan(layout, 16, 0);
        assertEquals(6 + 2 + 2 + 1, soldiers.size(), "every post once: gate 6, walls 2, towers 2, barracks 1");
        assertEquals(new BlockPos(106, 65, 210), soldiers.get(0).pos(), "land gate");
        assertEquals(Direction.SOUTH, soldiers.get(0).facing());
        assertEquals(new BlockPos(108, 65, 210), soldiers.get(1).pos(), "land gate");
        assertEquals(new BlockPos(116, 69, 202), soldiers.get(2).pos(), "the nearer wall first");
        assertEquals(Direction.NORTH, soldiers.get(2).facing(), "wall guards face the sea");
        assertEquals(new BlockPos(94, 69, 202), soldiers.get(3).pos(), "then the other run's wall");
        assertEquals(new BlockPos(91, 74, 201), soldiers.get(4).pos(), "then the nearer tower (the west run's is shorter)");
        assertEquals(new BlockPos(127, 74, 201), soldiers.get(5).pos(), "then the other tower");
        assertEquals(new BlockPos(116, 65, 218), soldiers.get(6).pos(), "then the barracks");

        List<Assignment> six = GarrisonPosts.plan(layout, 6, 1);
        assertEquals(soldiers.subList(0, 6), six.subList(1, 7), "the default garrison: land gate, walls and towers");
        assertEquals(Role.OFFICER, six.get(0).role());
        assertEquals(List.of(), GarrisonPosts.plan(List.of(quay, eastWall), 6, 1), "no gate, no garrison");
        assertEquals(0, GarrisonPosts.plan(layout, 0, 0).size());
    }

    @Test
    void postsTurnWithTheirPiece() {
        // sea to the east: the gate is turned clockwise; local (6, 1, 10) goes to (-10, 1, 6) from the origin
        PlacedPiece gate = gate(Rotation.CLOCKWISE_90);
        Assignment officer = GarrisonPosts.plan(List.of(gate), 0, 1).get(0);
        assertEquals(new BlockPos(100 - 7, 65, 200 + 6), officer.pos());
        assertEquals(Direction.SOUTH, officer.facing(), "east turned clockwise");
        Assignment guard = GarrisonPosts.plan(List.of(gate), 1, 0).get(0);
        assertEquals(new BlockPos(100 - 10, 65, 200 + 6), guard.pos());
        assertEquals(Direction.WEST, guard.facing(), "landward: away from the sea in the east");
    }

    @Test
    void pieceNamesComeFromThePoolElement() {
        assertEquals("wall_tower", GarrisonPosts.pieceName("Single[Left[pirates_n_ships:navy_outpost/wall_tower]]"));
        assertEquals("fort_gate", GarrisonPosts.pieceName("LegacySingle[Left[pirates_n_ships:navy_outpost/fort_gate]]"));
        assertEquals("", GarrisonPosts.pieceName("Single[Left[pirates_n_ships:village/dock_head]]"));
    }

    private static PlacedPiece gate(Rotation rotation) {
        return new PlacedPiece(GarrisonPosts.FORT_GATE, new BlockPos(100, 64, 200), rotation);
    }
}
