package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.rpg.quest.QuestText;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Deserters walking off at a port (CRW2, docs/design.md §7.3, §7.5). Every {@link #INTERVAL} ticks, for every loaded
 * ship, each crew member marked as deserting ({@link CrewMember#isDeserting()}, set at dawn by {@link ShipDayTick})
 * leaves when {@link DesertionRules#decide} says so: at a port it becomes a neutral sailor standing on the quay (the
 * nearest berth of the port the ship is at), anywhere else it becomes one where it stands
 * ({@link CrewReplacement#replace}). The ship's owner is told "Jack walked off at Port Royal" (or "Jack has deserted").
 * Nothing happens while {@code crew.desertion.enabled} is off.
 */
public final class Desertions {

    /** Ticks between two looks at the deserters. */
    public static final int INTERVAL = 100;
    /** How far around a berth (horizontally) and above or below it the quay spot is looked for. */
    private static final int QUAY_REACH = 4;

    private Desertions() {
    }

    /** The current {@code crew.desertion} keys of CRW2. */
    public static DesertionRules.Settings settings() {
        return new DesertionRules.Settings(CrewConfig.DESERT_AT_PORT_ONLY.get(), CrewConfig.DESERT_PORT_RADIUS.get(),
                CrewConfig.DESERT_ANYWHERE_AFTER_DAYS.get());
    }

    /** End of each level tick: every {@link #INTERVAL} ticks, {@link #run}. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL == 0) {
            run(level);
        }
    }

    /** What one look did: the members that left at a port and anywhere. */
    public record Result(List<UUID> atPort, List<UUID> anywhere) {
        public static final Result NONE = new Result(List.of(), List.of());
    }

    /** Lets every deserter of every loaded ship in {@code level} go that may go now. Public for the GameTests. */
    public static Result run(ServerLevel level) {
        if (!CrewConfig.DESERTION_ENABLED.get()) {
            return Result.NONE;
        }
        DesertionRules.Settings s = settings();
        List<UUID> atPort = new ArrayList<>();
        List<UUID> anywhere = new ArrayList<>();
        for (ShipBody ship : List.copyOf(SableShips.all(level))) {
            List<CrewMember> deserters = new ArrayList<>();
            for (CrewMember c : ShipBunks.crewOf(level, ship)) {
                if (c.isDeserting()) deserters.add(c);
            }
            if (deserters.isEmpty()) continue;
            Optional<Port> port = portOf(level, ship, s.portRadius());
            for (CrewMember c : deserters) {
                switch (DesertionRules.decide(s, port.isPresent(), c.desertingDays())) {
                    case STAY -> { }
                    case AT_PORT -> {
                        walkOff(level, ship, c, port.orElseThrow());
                        atPort.add(c.getUUID());
                    }
                    case ANYWHERE -> {
                        tell(level, ship, Component.translatable(UpkeepText.DESERTED, c.getDisplayName()));
                        CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_DESERT));
                        CrewReplacement.replace(level, c, MobContent.SAILOR.get());
                        anywhere.add(c.getUUID());
                    }
                }
            }
        }
        return new Result(atPort, anywhere);
    }

    /**
     * The port {@code ship} is at: the registered port of this dimension whose box, inflated by {@code radius}, touches
     * the ship's world bounds; the one with the nearest centre when several do.
     */
    public static Optional<Port> portOf(ServerLevel level, ShipBody ship, double radius) {
        AABB bounds = ship.worldBounds();
        Vec3 c = bounds.getCenter();
        BlockPos centre = BlockPos.containing(c);
        PortIndex index = PortRegistry.get(level.getServer()).index();
        return index.all().stream()
                .filter(p -> p.dimension().equals(level.dimension()))
                .filter(p -> DesertionRules.atPort(p.box(), radius, bounds))
                .min(Comparator.comparingDouble((Port p) -> PortIndex.horizontalDistanceSqr(p.centre(), centre))
                        .thenComparing(p -> p.id().toString()));
    }

    /** {@code crew} walks off onto the quay of {@code port}: a sailor on the quay, the owner told. */
    private static void walkOff(ServerLevel level, ShipBody ship, CrewMember crew, Port port) {
        Component name = crew.getDisplayName();
        CrewStations.say(level, crew, Component.translatable(UpkeepText.SAY_WALK_OFF));
        @Nullable Vec3 quay = quay(level, port, ship.worldBounds().getCenter());
        SeafarerMob sailor = CrewReplacement.replace(level, crew, MobContent.SAILOR.get());
        if (sailor != null && quay != null) {
            sailor.teleportTo(quay.x, quay.y, quay.z);
            sailor.getNavigation().stop();
        }
        tell(level, ship, Component.translatable(UpkeepText.WALKED_OFF, name, QuestText.portName(port.id())));
    }

    /**
     * Where a deserter stands on the quay of {@code port}: the standable cell nearest the berth nearest {@code from}
     * (the port's centre without berths), within {@link #QUAY_REACH} blocks of it; null when none is loaded and free (it
     * then stays where it stood, aboard).
     */
    public static @Nullable Vec3 quay(ServerLevel level, Port port, Vec3 from) {
        BlockPos origin = DesertionRules.nearestBerth(port.berths(), from).map(Berth::pos).orElse(null);
        if (origin == null) {
            if (!level.hasChunkAt(port.centre())) return null;
            origin = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, port.centre());
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -QUAY_REACH; dx <= QUAY_REACH; dx++) {
            for (int dz = -QUAY_REACH; dz <= QUAY_REACH; dz++) {
                for (int dy = -QUAY_REACH; dy <= QUAY_REACH; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    double d = p.distSqr(origin);
                    if (d >= bestD || !level.hasChunkAt(p) || !standable(level, p)) continue;
                    best = p;
                    bestD = d;
                }
            }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    /** No collision and no fluid in the cell and the one above, a sturdy floor below. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        return open(level, p) && open(level, p.above()) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        var state = level.getBlockState(p);
        return state.getCollisionShape(level, p).isEmpty() && state.getFluidState().isEmpty();
    }

    /** A chat line to the ship's owner, wherever it is on the server. */
    private static void tell(ServerLevel level, ShipBody ship, Component line) {
        ShipRegistry.get(level.getServer()).find(ship.id()).flatMap(ShipData::owner)
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .ifPresent((ServerPlayer p) -> p.sendSystemMessage(line));
    }
}
