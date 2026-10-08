package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the visible anchor (docs/design.md §5.2): keeps exactly one {@link AnchorEntity} per ship with a
 * capstan, placed where the ship's {@link ShipAnchor} body is every game tick (its motion is {@link AnchorPhysics}),
 * and plays the chain (running out, heaving in, dragging), splash and landing effects.
 *
 * <p>Life cycle: the entity is spawned by {@link #sync} (called for every ship with a sailing runtime each tick), so
 * a placed capstan, an assembled ship and a loaded ship all get their anchor on the next tick. It is discarded when
 * its capstan is broken ({@link #capstanRemoved}), when the ship is removed for any reason (Sable removal
 * listener; a stowed anchor is also killed by Sable through {@code sable:destroy_with_sub_level}), and by itself
 * when nothing synced it for {@link AnchorEntity#ORPHAN_TICKS} ticks. It is never saved.
 */
public final class AnchorEntities {

    /** How far across the ship the hull-side search looks. */
    static final int REACH = 32;
    /** Ticks between two chain sounds while the anchor moves. */
    static final int CHAIN_INTERVAL = 4;
    /** Ticks between two capstan scans of a ship without a known capstan. */
    static final int RESCAN_TICKS = 100;

    private static final Map<ServerLevel, Map<UUID, Track>> TRACKS = new WeakHashMap<>();

    private static final class Track {
        @Nullable AnchorEntity entity;
        @Nullable BlockPos capstan;
        long nextScan;
        boolean wasInWater;
        AnchorState.Phase lastPhase = AnchorState.Phase.RAISED;
        boolean wasResting;
        int chainCooldown;
    }

    private AnchorEntities() {
    }

    public static void registerEvents() {
        SableShips.onShipRemoved((level, id, destroyed) -> forget(level, id));
        CommonEvents.SERVER_STOPPED.register(server -> TRACKS.clear());
    }

    /** The ship's anchor entity, or null. */
    public static @Nullable AnchorEntity of(ServerLevel level, UUID ship) {
        Map<UUID, Track> m = TRACKS.get(level);
        Track t = m == null ? null : m.get(ship);
        return t == null || t.entity == null || t.entity.isRemoved() ? null : t.entity;
    }

    /** Plot position of the hawse of a capstan: the ring of its stowed anchor at the nearer hull side ({@link HullSide}). */
    public static Vec3 hawse(ServerLevel level, BowFrame bow, BlockPos capstan) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        HullSide.Placement p = HullSide.find(bow.dx(), bow.dz(), REACH,
                (dx, dy, dz) -> !level.getBlockState(m.setWithOffset(capstan, dx, dy, dz)).isAir());
        double[] o = p.hawseOffset();
        return new Vec3(capstan.getX() + o[0], capstan.getY() + o[1], capstan.getZ() + o[2]);
    }

    /**
     * Game tick of one ship: spawns, moves or removes its anchor entity to match {@code anchor} (null = stowed), which
     * lies where its body is (AN2a), and hands it the chain's {@code status}.
     */
    public static void sync(ShipBody ship, BowFrame bow, @Nullable ShipAnchor anchor, @Nullable AnchorStatus status) {
        ServerLevel level = ship.level();
        Track t = TRACKS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(ship.id(), k -> new Track());
        boolean out = anchor != null && anchor.state().isOut();
        BlockPos capstan = out ? anchor.capstan() : stowCapstan(level, ship, t);
        if (capstan == null) {
            discard(t);
            return;
        }
        t.capstan = capstan;
        boolean armsAlongX = bow.dx() != 0;
        AnchorEntity e = t.entity;
        boolean valid = e != null && !e.isRemoved() && e.capstan().equals(capstan);
        if (!out) {
            if (!valid || e.isOut()) {
                discard(t);
                Vec3 hawse = hawse(level, bow, capstan);
                e = AnchorEntity.create(level, ship.id(), capstan, hawse, armsAlongX, false);
                e.moveTo(hawse.x, hawse.y - AnchorTravel.HEIGHT, hawse.z, 0, 0);
                t.entity = level.addFreshEntity(e) ? e : null;
            }
            if (t.entity != null) {
                t.entity.markSynced();
            }
            t.lastPhase = AnchorState.Phase.RAISED;
            t.wasResting = false;
            return;
        }
        Vec3 hawseWorld = ship.toWorld(anchor.hawse());
        Vec3 pos = anchor.position();
        if (!valid || !e.isOut()) {
            discard(t);
            e = AnchorEntity.create(level, ship.id(), capstan, anchor.hawse(), armsAlongX, true);
            e.moveTo(pos.x, pos.y, pos.z, 0, 0);
            t.entity = level.addFreshEntity(e) ? e : null;
            t.wasInWater = inWater(level, pos);
            t.lastPhase = anchor.state().phase();
            t.wasResting = anchor.resting();
        } else {
            e.setPos(pos);
        }
        if (t.entity != null) {
            t.entity.markSynced();
            t.entity.setChain(status, pos);
        }
        effects(level, t, anchor, status, hawseWorld, pos);
        t.lastPhase = anchor.state().phase();
        t.wasResting = anchor.resting();
    }

    /** {@link CapstanBlock#onPlace}: a capstan on a ship makes the ship look for its anchor's capstan at once. */
    public static void capstanPlaced(ServerLevel level, BlockPos pos) {
        Track t = track(level, pos);
        if (t != null && t.capstan == null) {
            t.nextScan = 0;
        }
    }

    /** {@link CapstanBlock#onRemove}: the anchor of a broken capstan goes with it. */
    public static void capstanRemoved(ServerLevel level, BlockPos pos) {
        Track t = track(level, pos);
        if (t != null && pos.equals(t.capstan)) {
            discard(t);
            t.capstan = null;
            t.nextScan = 0;
        }
    }

    private static @Nullable Track track(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        Map<UUID, Track> m = TRACKS.get(level);
        return ship == null || m == null ? null : m.get(ship.id());
    }

    private static void forget(ServerLevel level, UUID ship) {
        Map<UUID, Track> m = TRACKS.get(level);
        Track t = m == null ? null : m.remove(ship);
        if (t != null) {
            discard(t);
        }
    }

    private static void discard(Track t) {
        if (t.entity != null && !t.entity.isRemoved()) {
            t.entity.discard();
        }
        t.entity = null;
    }

    private static @Nullable BlockPos stowCapstan(ServerLevel level, ShipBody ship, Track t) {
        if (t.capstan != null && level.getBlockState(t.capstan).getBlock() instanceof CapstanBlock) {
            return t.capstan;
        }
        t.capstan = null;
        long now = level.getGameTime();
        if (now < t.nextScan) {
            return null;
        }
        t.nextScan = now + RESCAN_TICKS;
        BlockPos best = null;
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof CapstanBlock && (best == null || p.compareTo(best) < 0)) {
                best = p.immutable();
            }
        }
        return best;
    }

    private static boolean inWater(ServerLevel level, Vec3 pos) {
        return level.getFluidState(BlockPos.containing(pos.x, pos.y + 0.2, pos.z)).is(FluidTags.WATER);
    }

    private static void effects(ServerLevel level, Track t, ShipAnchor a, @Nullable AnchorStatus status, Vec3 hawseWorld, Vec3 pos) {
        AnchorState.Phase phase = a.state().phase();
        boolean water = inWater(level, pos);
        boolean wasInWater = t.wasInWater;
        t.wasInWater = water;
        if (!AnchorConfig.SOUNDS.get()) {
            return;
        }
        boolean dragging = status != null && status.dragging();
        boolean moving = phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.RAISING || dragging;
        if (moving && --t.chainCooldown <= 0) {
            t.chainCooldown = CHAIN_INTERVAL;
            play(level, hawseWorld, AnchorContent.chainSound(), AnchorConfig.CHAIN_VOLUME.get(), 0.75f + level.random.nextFloat() * 0.3f);
        }
        if ((phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.RAISING) && water != wasInWater) {
            double surface = Math.floor(pos.y + 0.2) + (water ? 1.0 : 0.0);
            play(level, new Vec3(pos.x, surface, pos.z), AnchorContent.splashSound(), AnchorConfig.SPLASH_VOLUME.get(), 0.9f + level.random.nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.SPLASH, pos.x, surface, pos.z, 40, 0.5, 0.05, 0.5, 0.2);
            if (water) {
                level.sendParticles(ParticleTypes.BUBBLE, pos.x, surface - 0.6, pos.z, 20, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (a.resting() && !t.wasResting && phase != AnchorState.Phase.RAISING) {
            play(level, pos, AnchorContent.thudSound(), AnchorConfig.THUD_VOLUME.get(), 0.7f + level.random.nextFloat() * 0.2f);
            groundPuff(level, pos, 30, 0.6, 0.1);
            if (water) {
                level.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y + 0.5, pos.z, 25, 0.6, 0.3, 0.6, 0.05);
            }
        }
        if (dragging && level.getGameTime() % 5 == 0) {
            groundPuff(level, pos, 6, 0.4, 0.05); // the anchor ploughs the seabed
        }
    }

    private static void groundPuff(ServerLevel level, Vec3 pos, int count, double spread, double speed) {
        BlockState ground = level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.5, pos.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.x, pos.y + 0.1, pos.z,
                    count, spread, 0.05, spread, speed);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, double volume, float pitch) {
        if (volume > 0.0) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, (float) volume, pitch);
        }
    }
}
