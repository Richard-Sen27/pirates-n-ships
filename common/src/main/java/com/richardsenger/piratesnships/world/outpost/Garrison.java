package com.richardsenger.piratesnships.world.outpost;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a navy outpost's garrison when the outpost generates (WG3, design.md §10.1): {@code garrison_soldiers} navy
 * soldiers and {@code garrison_officers} officers at the posts of {@link GarrisonPosts}, each {@code stationary} (it
 * keeps its post, still turns and fights) and persistent, facing its post's direction. The navy never spawns
 * naturally (its types are {@code misc}), and the garrison neither despawns nor respawns.
 *
 * <p>Called from the structure's {@code afterPlace} once per chunk: every chunk places the posts inside it, so each
 * post is placed exactly once by world generation. Placing the structure again over a loaded outpost (a GameTest,
 * {@code /place structure}) skips posts where a navy mob already stands; during world generation the region sees no
 * entities, and none are needed there. A post filled by terrain stays empty ({@link #free}).
 */
public final class Garrison {

    private Garrison() {
    }

    /** The outpost's pieces as {@link GarrisonPosts} reads them (non-pool pieces are skipped). */
    public static List<GarrisonPosts.PlacedPiece> placedPieces(PiecesContainer pieces) {
        List<GarrisonPosts.PlacedPiece> out = new ArrayList<>();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            out.add(new GarrisonPosts.PlacedPiece(GarrisonPosts.pieceName(p.getElement().toString()), p.getPosition(), p.getRotation()));
        }
        return out;
    }

    /** The configured garrison plan of an outpost made of {@code pieces}. */
    public static List<GarrisonPosts.Assignment> plan(PiecesContainer pieces) {
        return GarrisonPosts.plan(placedPieces(pieces), WorldConfig.NAVY_OUTPOST_GARRISON_SOLDIERS.get(),
                WorldConfig.NAVY_OUTPOST_GARRISON_OFFICERS.get());
    }

    /** Places the garrison's posts inside {@code chunkBox}. Returns the number of mobs placed. */
    public static int place(WorldGenLevel level, BoundingBox chunkBox, PiecesContainer pieces) {
        int placed = 0;
        for (GarrisonPosts.Assignment a : plan(pieces)) {
            if (!chunkBox.isInside(a.pos())) continue;
            boolean officer = a.role() == GarrisonPosts.Role.OFFICER;
            if (!MobConfig.enabled(officer ? MobKind.NAVY_OFFICER : MobKind.NAVY_SOLDIER).get()) continue;
            if (!free(level, a.pos()) || manned(level, a.pos())) continue;
            EntityType<? extends SeafarerMob> type = officer ? MobContent.NAVY_OFFICER.get() : MobContent.NAVY_SOLDIER.get();
            SeafarerMob mob = type.create(level.getLevel());
            if (mob == null) continue;
            float yaw = a.facing().toYRot();
            mob.moveTo(a.pos().getX() + 0.5, a.pos().getY(), a.pos().getZ() + 0.5, yaw, 0f);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setStationary(true);
            mob.setPersistenceRequired();
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(a.pos()), MobSpawnType.STRUCTURE, null);
            if (level.addFreshEntity(mob)) placed++;
        }
        return placed;
    }

    /**
     * The post and the block above it have no collision. The templates carry no air (art/README.md), so on a beach
     * that rises above the gate's paving the terrain can fill a post; that post stays empty rather than smother a mob.
     */
    private static boolean free(WorldGenLevel level, BlockPos post) {
        for (BlockPos p : List.of(post, post.above())) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** A navy mob already stands at the post (always false during world generation: the region sees no entities). */
    private static boolean manned(WorldGenLevel level, BlockPos post) {
        return !level.getEntitiesOfClass(SeafarerMob.class, new AABB(post).inflate(0.5),
                m -> m.faction() == MobFaction.NAVY).isEmpty();
    }
}
