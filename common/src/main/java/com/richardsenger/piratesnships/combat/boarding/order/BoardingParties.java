package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingConfig;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.cannon.CannonStation;
import com.richardsenger.piratesnships.combat.cannon.SwivelStation;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import com.richardsenger.piratesnships.crew.galley.ProvisionContainer;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.walk.CrewWalk;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.helm.HelmStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The boarding parties out (BRD3, docs/design.md §8.4): who boards which ship from which, and where each boarder is on
 * its way. One party per ship. In memory only: after a restart the boarders are ordinary crew and mobs wherever they
 * stand (a crew member that was across walks nowhere and has lost its station).
 *
 * <p><b>The order</b> ({@link #boardWithCrew}, the whistle's "Board!"): the ship lies alongside another with a way across
 * ({@link BoardingWorld#crossings}; a ship with a gangway wins, else the nearest). Every crew member of the ship goes
 * except the gunners and the helmsman ({@code boarding.boarders_keep_stations}, {@link BoardingRules#boards}): it leaves
 * its station (remembered with its pin), takes a sword from a chest of the ship when its hand is empty
 * ({@code boarding.take_arms}), else fights with its fists ({@code boarding.unarmed_damage}).
 *
 * <p><b>A boarder's way</b>, ticked every server tick: {@code OUT}, walking over its own deck to the start of its
 * crossing (the pathfinder in the ship's plot, WALK1; the last blocks steered straight); {@code CROSS_OUT}, walking the
 * plank's board to its far end and onto the deck beyond, or jumping the gap ({@link BoardingHop}) or stepping over
 * touching hulls; {@code ABOARD}, fighting; {@code BACK} and {@code CROSS_BACK}, the same home over the nearest way back.
 * A boarder that does not get across within {@code boarding.cross_timeout_ticks} is set down on the far side (like
 * WALK1's seat at once, nobody stalls). Back home it walks to its station again (its pin kept).
 *
 * <p><b>Every {@code boarding.order_check_ticks}</b>: the dead leave the party; a boarder aboard without a live foe takes
 * the nearest ({@link BoardingFight}); the defenders aboard without a target take the nearest boarder; the boarded
 * deck held with no foe left for {@code boarding.capture_seconds} is taken ("Her deck is yours" to the captain; for an
 * NPC party nothing is said); after that, or on "Back aboard", the boarders head home over the nearest way back; one
 * without a way (the plank broken, the ships apart) stays and fights on, and heads home when a way opens. An NPC
 * captain calls his party back by {@link BoardingRules#npcRecalls}.
 */
public final class BoardingParties {

    public static final String KEY = "message." + Constants.MOD_ID + ".boarding.";
    public static final String KEY_BOARD = KEY + "board";
    public static final String KEY_BACK = KEY + "back";
    public static final String KEY_NO_WAY = KEY + "no_way";
    public static final String KEY_NO_HANDS = KEY + "no_hands";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_CAPTURED = KEY + "captured";

    /** Crossing speed (the walk's speed modifier). */
    private static final double SPEED = 1.0;
    /** Blocks around a boarded ship searched for foes. */
    private static final double FOE_REACH = 2.0;
    /** Ticks between two path searches while walking to the crossing. */
    private static final int REPATH_TICKS = 20;
    /** Ticks a boarder's entity may go unfound (an unloaded chunk) before it leaves the party. */
    private static final int MISSING_TICKS = 1200;
    /** Blocks from the crossing's start within which a boarder whose path ended walks straight on. */
    private static final double STEER_DISTANCE = 1.6;

    /** Where a boarder is on its way. */
    public enum Phase { OUT, CROSS_OUT, ABOARD, BACK, CROSS_BACK }

    /** One boarder: the station it left (for crew), its phase and its crossing. */
    public static final class Boarder {
        final UUID id;
        final @Nullable StationRef station;
        final boolean pinned;
        Phase phase = Phase.OUT;
        UUID from;
        UUID to;
        @Nullable BoardingWays.Crossing crossing;
        long since;
        int via;
        boolean hopped;
        long hopAt;
        long lastSearch = Long.MIN_VALUE / 2;
        /** Game time the boarder's entity was first not found (-1: found). */
        long missingSince = -1;
        /** It did not get across in time once and was set down (the fallback). */
        boolean setDown;

        Boarder(UUID id, @Nullable StationRef station, boolean pinned, UUID from, UUID to, long now) {
            this.id = id;
            this.station = station;
            this.pinned = pinned;
            this.from = from;
            this.to = to;
            this.since = now;
        }

        public Phase phase() {
            return phase;
        }

        /** On its way across or back: it does not fight. */
        public boolean inTransit() {
            return phase != Phase.ABOARD;
        }

        public @Nullable StationRef station() {
            return station;
        }

        /** Whether it was ever set down across by the fallback instead of crossing by itself. */
        public boolean wasSetDown() {
            return setDown;
        }
    }

    /** A boarding party: from ship {@code home} onto ship {@code target}. */
    public static final class Party {
        final UUID home;
        final UUID target;
        final ResourceKey<Level> dimension;
        final boolean npc;
        final @Nullable UUID captain;
        /** The player who gave the order, while it is around (in memory like the party). */
        final @Nullable Player captainEntity;
        final Map<UUID, Boarder> boarders = new LinkedHashMap<>();
        boolean recalled;
        boolean captured;
        int clear;
        long lastCheck;

        Party(UUID home, UUID target, ResourceKey<Level> dimension, boolean npc, @Nullable Player captain, long now) {
            this.home = home;
            this.target = target;
            this.dimension = dimension;
            this.npc = npc;
            this.captain = captain == null ? null : captain.getUUID();
            this.captainEntity = captain;
            this.lastCheck = now;
        }

        public UUID home() {
            return home;
        }

        public UUID target() {
            return target;
        }

        /** Sent by an NPC captain: its boarders also fight players. */
        public boolean npc() {
            return npc;
        }

        /** The player who gave the order, or null (an NPC captain, the ship screen of an absent owner). */
        public @Nullable UUID captain() {
            return captain;
        }

        public boolean recalled() {
            return recalled;
        }

        public boolean captured() {
            return captured;
        }

        public List<UUID> boarders() {
            return List.copyOf(boarders.keySet());
        }

        public @Nullable Boarder boarder(UUID id) {
            return boarders.get(id);
        }
    }

    /** What {@link #boardWithCrew} or {@link #recall} did. */
    public enum Result { BOARDING, RECALLED, NO_WAY, NO_HANDS, DISABLED, NOT_OUT }

    /** The result, how many boarders it concerns and the boarded ship. */
    public record Outcome(Result result, int boarders, @Nullable UUID target) {
        static Outcome of(Result r) {
            return new Outcome(r, 0, null);
        }

        /** The action-bar line for the captain. */
        public Component message() {
            return switch (result) {
                case BOARDING -> Component.translatable(KEY_BOARD, boarders);
                case RECALLED, NOT_OUT -> Component.translatable(KEY_BACK);
                case NO_WAY -> Component.translatable(KEY_NO_WAY);
                case NO_HANDS -> Component.translatable(KEY_NO_HANDS);
                case DISABLED -> Component.translatable(KEY_DISABLED);
            };
        }
    }

    private static final Map<UUID, Party> PARTIES = new LinkedHashMap<>();
    /** Boarder entity → its party's home ship. */
    private static final Map<UUID, UUID> INDEX = new HashMap<>();

    private BoardingParties() {
    }

    // ------------------------------------------------------------------ queries

    /** Whether any party is out (a cheap guard for the per-entity queries). */
    public static boolean any() {
        return !PARTIES.isEmpty();
    }

    /** Whether a party of ship {@code home} is out: the whistle's entry says "Back aboard". */
    public static boolean out(UUID home) {
        return PARTIES.containsKey(home);
    }

    /** Every party out. */
    public static List<Party> parties() {
        return List.copyOf(PARTIES.values());
    }

    /** The party of ship {@code home}, or null. */
    public static @Nullable Party party(UUID home) {
        return PARTIES.get(home);
    }

    /** The party {@code e} belongs to, or null. */
    public static @Nullable Party partyOf(Entity e) {
        UUID home = INDEX.get(e.getUUID());
        return home == null ? null : PARTIES.get(home);
    }

    /** {@code e} as a boarder, or null. */
    public static @Nullable Boarder boarder(Entity e) {
        Party p = partyOf(e);
        return p == null ? null : p.boarders.get(e.getUUID());
    }

    // ------------------------------------------------------------------ the order

    /**
     * The whistle's boarding toggle given on {@code ship} by {@code captain} (may be null): "Back aboard" for the party
     * {@link #partyFor} finds, else "Board!" from {@code ship}.
     */
    public static Outcome toggle(ServerLevel level, ShipBody ship, @Nullable Player captain) {
        Party p = partyFor(level, ship.id(), captain == null ? null : captain.getUUID());
        return p != null ? recall(level, p.home) : boardWithCrew(level, ship, captain);
    }

    /**
     * The party a "Back aboard" given on {@code ship} by player {@code player} calls: the party of {@code ship}, else a
     * player's party boarding {@code ship} that this player sent or whose ship he owns (he boarded along and calls
     * from her deck). Null for none.
     */
    public static @Nullable Party partyFor(ServerLevel level, UUID ship, @Nullable UUID player) {
        Party own = PARTIES.get(ship);
        if (own != null) return own;
        if (player == null) return null;
        for (Party p : PARTIES.values()) {
            if (p.npc || !p.target.equals(ship)) continue;
            if (player.equals(p.captain) || ShipRegistry.get(level.getServer()).find(p.home).flatMap(ShipData::owner)
                    .map(player::equals).orElse(false)) {
                return p;
            }
        }
        return null;
    }

    /**
     * "Board!" from ship {@code home} given by {@code captain} (may be null): the crew go across to the ship lying
     * alongside. While a party of the ship is out, use {@link #recall}.
     */
    public static Outcome boardWithCrew(ServerLevel level, ShipBody home, @Nullable Player captain) {
        if (!BoardingConfig.BOARD_ORDER.get()) return Outcome.of(Result.DISABLED);
        if (out(home.id())) return Outcome.of(Result.BOARDING);
        ShipBody target = target(level, home);
        if (target == null) return Outcome.of(Result.NO_WAY);
        List<CrewMember> hands = hands(level, home);
        if (hands.isEmpty()) return Outcome.of(Result.NO_HANDS);
        Party party = new Party(home.id(), target.id(), level.dimension(), false, captain, level.getGameTime());
        for (CrewMember c : hands) {
            StationRef station = c.assignment();
            boolean pinned = c.isPinned();
            if (c.rest() != null) CrewRest.getUp(c, true);
            if (station != null) CrewStations.release(level, c);
            if (c.walk() != null) CrewWalk.cancel(c);
            if (c.isPassenger()) c.stopRiding(); // a meal seat removes itself
            c.setBoarding(true);
            arm(level, home, c);
            join(level, party, c, station, pinned);
        }
        PARTIES.put(home.id(), party);
        Constants.LOG.debug("{} hands of ship {} board ship {}", hands.size(), home.id(), target.id());
        return new Outcome(Result.BOARDING, hands.size(), target.id());
    }

    /**
     * An NPC captain's "Board!" from ship {@code home} onto ship {@code target} with his {@code fighters} (BRD3, NPC
     * boarding): they cross the same way and also fight players. Returns how many go.
     */
    public static Outcome boardWithFighters(ServerLevel level, ShipBody home, ShipBody target, List<? extends Mob> fighters) {
        if (!BoardingConfig.BOARD_ORDER.get()) return Outcome.of(Result.DISABLED);
        if (out(home.id())) return Outcome.of(Result.BOARDING);
        if (BoardingWorld.crossings(level, home, target).isEmpty()) return Outcome.of(Result.NO_WAY);
        Party party = new Party(home.id(), target.id(), level.dimension(), true, null, level.getGameTime());
        int n = 0;
        for (Mob m : fighters) {
            if (!m.isAlive() || INDEX.containsKey(m.getUUID()) || !BoardingFight.aboard(m, home.id())) continue;
            join(level, party, m, null, false);
            n++;
        }
        if (n == 0) return Outcome.of(Result.NO_HANDS);
        PARTIES.put(home.id(), party);
        Constants.LOG.debug("{} fighters of ship {} board ship {}", n, home.id(), target.id());
        return new Outcome(Result.BOARDING, n, target.id());
    }

    private static void join(ServerLevel level, Party party, Mob mob, @Nullable StationRef station, boolean pinned) {
        Boarder b = new Boarder(mob.getUUID(), station, pinned, party.home, party.target, level.getGameTime());
        party.boarders.put(mob.getUUID(), b);
        INDEX.put(mob.getUUID(), party.home);
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    /**
     * "Back aboard" for the party of {@code home}: boarders still on their own deck take their stations again at once,
     * those across head home over the nearest way back.
     */
    public static Outcome recall(ServerLevel level, UUID home) {
        Party party = PARTIES.get(home);
        if (party == null) return Outcome.of(Result.NOT_OUT);
        party.recalled = true;
        int n = party.boarders.size();
        for (Boarder b : new ArrayList<>(party.boarders.values())) {
            if (b.phase == Phase.OUT && level.getEntity(b.id) instanceof Mob mob) home(level, party, b, mob);
        }
        party.lastCheck = Long.MIN_VALUE / 2; // look at once
        if (party.boarders.isEmpty()) PARTIES.remove(home);
        return new Outcome(Result.RECALLED, n, party.target);
    }

    /**
     * The ship the crew of {@code home} board: of the ships with a way across, one joined by a gangway first, else the
     * one whose centre is nearest. Null when there is none.
     */
    static @Nullable ShipBody target(ServerLevel level, ShipBody home) {
        ShipBody best = null;
        boolean bestPlank = false;
        double bestD = Double.MAX_VALUE;
        Vec3 c = home.worldBounds().getCenter();
        for (ShipBody other : SableShips.all(level)) {
            if (other.id().equals(home.id()) || other.isRemoved() || ShipSplits.isWreck(other)) continue;
            List<BoardingWays.Crossing> ways = BoardingWorld.crossings(level, home, other);
            if (ways.isEmpty()) continue;
            boolean plank = ways.stream().anyMatch(w -> w.kind() == BoardingWays.Kind.PLANK);
            double d = other.worldBounds().getCenter().distanceToSqr(c);
            if (best == null || plank && !bestPlank || plank == bestPlank && d < bestD) {
                best = other;
                bestPlank = plank;
                bestD = d;
            }
        }
        return best;
    }

    /** The crew of {@code home} who go: aboard it or at its stations, not prisoners, by {@link BoardingRules#boards}. */
    static List<CrewMember> hands(ServerLevel level, ShipBody home) {
        boolean keep = BoardingConfig.BOARDERS_KEEP_STATIONS.get();
        List<CrewMember> out = new ArrayList<>();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(home, 4), CrewMember::isAlive)) {
            if (BrigService.isPrisoner(c) || c.isBoarding() || INDEX.containsKey(c.getUUID())) continue;
            StationRef a = c.assignment();
            boolean ours = a != null ? a.ship().equals(home.id()) : BoardingFight.aboard(c, home.id());
            if (!ours) continue;
            if (BoardingRules.boards(post(level, a), keep)) out.add(c);
        }
        return out;
    }

    /** The post a crew member at station {@code ref} (null: none) holds. */
    static BoardingRules.Post post(ServerLevel level, @Nullable StationRef ref) {
        if (ref == null) return BoardingRules.Post.NONE;
        StationKind<?> kind = Stations.kindAt(level, ref);
        if (kind instanceof CannonStation || kind instanceof SwivelStation) return BoardingRules.Post.GUN;
        if (kind instanceof HelmStation) return BoardingRules.Post.HELM;
        return BoardingRules.Post.OTHER;
    }

    /**
     * A crew member with an empty hand takes a sword (any item with a melee weapon definition: cutlass, saber, rapier)
     * from a container of its ship ({@code boarding.take_arms}); it drops it when it dies. Every boarder gets the punch
     * of {@code boarding.unarmed_damage}.
     */
    static void arm(ServerLevel level, ShipBody home, CrewMember c) {
        AttributeInstance damage = c.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(BoardingConfig.UNARMED_DAMAGE.get());
        if (!BoardingConfig.TAKE_ARMS.get() || !c.getMainHandItem().isEmpty()) return;
        for (BlockEntity be : home.plotBlockEntities()) {
            if (!(be instanceof Container box) || be instanceof ProvisionContainer) continue;
            for (int i = 0; i < box.getContainerSize(); i++) {
                ItemStack st = box.getItem(i);
                if (st.isEmpty() || MeleeWeapons.forStack(st, MeleeWeapons.WEAPONS.of(level)).isEmpty()) continue;
                ItemStack sword = box.removeItem(i, 1);
                c.setItemSlot(EquipmentSlot.MAINHAND, sword);
                c.setDropChance(EquipmentSlot.MAINHAND, 2.0f); // it is the ship's sword: always dropped, undamaged
                box.setChanged();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ the tick

    /** Every server tick of {@code level}: moves the boarders, and every {@code order_check_ticks} looks around. */
    public static void onLevelTick(ServerLevel level) {
        if (PARTIES.isEmpty()) return;
        long now = level.getGameTime();
        for (Party party : new ArrayList<>(PARTIES.values())) {
            if (party.dimension != level.dimension()) continue;
            ShipBody home = SableShips.byId(level, party.home);
            ShipBody target = SableShips.byId(level, party.target);
            if (home == null || target == null) {
                if (gone(level, party.home) || gone(level, party.target)) disband(level, party);
                continue; // unloaded: the party waits
            }
            boolean check = now - party.lastCheck >= BoardingConfig.ORDER_CHECK_TICKS.get();
            if (check) {
                int dt = (int) Math.min(Integer.MAX_VALUE, Math.max(1, now - party.lastCheck));
                party.lastCheck = now;
                look(level, party, home, target, Math.min(dt, BoardingConfig.ORDER_CHECK_TICKS.get()));
            }
            for (Boarder b : new ArrayList<>(party.boarders.values())) {
                if (level.getEntity(b.id) instanceof Mob mob && mob.isAlive()) move(level, party, b, mob, check, now);
            }
            if (party.boarders.isEmpty()) PARTIES.remove(party.home);
        }
    }

    private static boolean gone(ServerLevel level, UUID ship) {
        return SableShips.byId(level, ship) == null && ShipRegistry.get(level.getServer()).find(ship).isEmpty();
    }

    /** The party's look around (see the class comment). */
    private static void look(ServerLevel level, Party party, ShipBody home, ShipBody target, int dt) {
        long now = level.getGameTime();
        for (Boarder b : new ArrayList<>(party.boarders.values())) {
            Entity e = level.getEntity(b.id);
            if (e == null) {
                // not found: unloaded for now, or gone for good; it leaves the party only after a while
                if (b.missingSince < 0) b.missingSince = now;
                else if (now - b.missingSince > MISSING_TICKS) leave(party, b);
            } else if (!(e instanceof Mob m) || !m.isAlive() || e.isRemoved()) {
                leave(party, b);
            } else {
                b.missingSince = -1;
            }
        }
        if (!BoardingConfig.BOARD_ORDER.get()) party.recalled = true;
        List<LivingEntity> foes = foesAboard(level, party, target);
        if (party.npc && !party.recalled && BoardingRules.npcRecalls(party.boarders.size(), foes.size(),
                NpcBoarding.flooded(level, home), BoardingConfig.NPC_RECALL_FLOODED.get(), BoardingConfig.NPC_RECALL_OUTNUMBERED.get())) {
            party.recalled = true;
            Constants.LOG.debug("The captain of ship {} calls his boarders back from ship {}", party.home, party.target);
        }
        int aboard = 0;
        for (Boarder b : party.boarders.values()) {
            if (b.phase == Phase.ABOARD && level.getEntity(b.id) instanceof Mob m && BoardingFight.aboard(m, party.target)) aboard++;
        }
        party.clear = BoardingRules.clearTicks(party.clear, foes.size(), aboard, dt);
        if (!party.captured && BoardingRules.captured(party.clear, BoardingConfig.CAPTURE_SECONDS.get())) {
            party.captured = true;
            announceCapture(level, party);
        }
        defend(level, party, target, foes);
    }

    /** The combatants standing on the boarded ship who are not of the party. */
    static List<LivingEntity> foesAboard(ServerLevel level, Party party, ShipBody target) {
        AABB box = CrewStations.worldBox(target, FOE_REACH);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !party.boarders.containsKey(e.getUUID())
                && BoardingFight.aboard(e, party.target) && BoardingFight.combatant(e, party.npc));
    }

    /** Defenders without a target take the nearest boarder aboard their deck. */
    private static void defend(ServerLevel level, Party party, ShipBody target, List<LivingEntity> defenders) {
        List<Mob> boardersAboard = new ArrayList<>();
        for (Boarder b : party.boarders.values()) {
            if (b.phase == Phase.ABOARD && level.getEntity(b.id) instanceof Mob m && BoardingFight.aboard(m, party.target)) {
                boardersAboard.add(m);
            }
        }
        if (boardersAboard.isEmpty()) return;
        for (LivingEntity d : defenders) {
            if (!(d instanceof Mob mob) || INDEX.containsKey(d.getUUID())) continue;
            LivingEntity current = mob.getTarget();
            if (current != null && current.isAlive()) continue;
            Mob nearest = nearest(mob, boardersAboard, 24.0);
            if (nearest == null || !BoardingFight.foes(mob, nearest)) continue;
            if (mob instanceof CrewMember c) {
                AttributeInstance damage = c.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null) damage.setBaseValue(BoardingConfig.UNARMED_DAMAGE.get());
            }
            mob.setTarget(nearest);
        }
    }

    private static @Nullable <T extends LivingEntity> T nearest(Entity from, List<T> among, double reach) {
        T best = null;
        double bestD = reach * reach;
        for (T e : among) {
            double d = from.distanceToSqr(e);
            if (d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** Tells the captain who gave the order (or the ship's owner) that the other deck is taken. */
    private static void announceCapture(ServerLevel level, Party party) {
        Constants.LOG.debug("Boarders of ship {} hold ship {}", party.home, party.target);
        if (party.npc) return;
        Player player = party.captainEntity != null && party.captainEntity.isAlive() && !party.captainEntity.isRemoved()
                ? party.captainEntity : null;
        if (player == null) {
            Optional<UUID> who = Optional.ofNullable(party.captain)
                    .or(() -> ShipRegistry.get(level.getServer()).find(party.home).flatMap(ShipData::owner));
            player = who.map(id -> (Player) level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        }
        if (player != null) player.displayClientMessage(Component.translatable(KEY_CAPTURED), true);
    }

    /** One boarder's tick. */
    private static void move(ServerLevel level, Party party, Boarder b, Mob mob, boolean check, long now) {
        switch (b.phase) {
            case OUT, BACK -> toStart(level, party, b, mob, now);
            case CROSS_OUT, CROSS_BACK -> cross(level, party, b, mob, now);
            case ABOARD -> {
                if (check) aboard(level, party, b, mob, now);
            }
        }
    }

    /** Walking over its deck to the start of its crossing. */
    private static void toStart(ServerLevel level, Party party, Boarder b, Mob mob, long now) {
        ShipBody from = SableShips.byId(level, b.from);
        ShipBody to = SableShips.byId(level, b.to);
        if (from == null || to == null) return;
        if (b.crossing == null) {
            b.crossing = BoardingWays.nearest(BoardingWorld.crossings(level, from, to), from.toPlot(mob.position()));
            b.since = now;
            if (b.crossing == null) {
                if (b.phase == Phase.OUT) {
                    home(level, party, b, mob); // no way across any more: back to its station
                } else {
                    b.phase = Phase.ABOARD; // no way back: it stays and fights on
                }
                return;
            }
        }
        BoardingWays.Crossing c = b.crossing;
        Vec3 local = from.toPlot(mob.position());
        Vec3 start = Vec3.atBottomCenterOf(c.start());
        double dh = Math.hypot(local.x - start.x, local.z - start.z);
        double dy = Math.abs(local.y - start.y);
        if (dh < 0.5 && dy < 1.0) {
            b.phase = b.phase == Phase.OUT ? Phase.CROSS_OUT : Phase.CROSS_BACK;
            b.since = now;
            b.via = 0;
            b.hopped = false;
            mob.getNavigation().stop();
            return;
        }
        if (now - b.since > BoardingConfig.CROSS_TIMEOUT_TICKS.get()) {
            setDown(level, party, b, mob, to, c, now);
            return;
        }
        PathNavigation nav = mob.getNavigation();
        boolean onPath = !nav.isDone();
        if (onPath && now - b.lastSearch < 2 * REPATH_TICKS) return; // on its way; a fresh path now and then (a turning deck)
        if (!onPath && dh < STEER_DISTANCE && dy < 1.0) {
            steer(mob, from.toWorld(start)); // the path ended beside the start: the last steps straight
            return;
        }
        if (now - b.lastSearch >= REPATH_TICKS / 2 && (mob.onGround() || mob.isInWater())) {
            nav.stop();
            Path p = nav.createPath(c.start(), 0); // a plot cell: Sable paths in the ship's plot (WALK1)
            if (p != null) nav.moveTo(p, SPEED);
            b.lastSearch = now;
        }
    }

    /** Crossing: along the board, over the touching hulls, or the jump over the gap. */
    private static void cross(ServerLevel level, Party party, Boarder b, Mob mob, long now) {
        ShipBody from = SableShips.byId(level, b.from);
        ShipBody to = SableShips.byId(level, b.to);
        BoardingWays.Crossing c = b.crossing;
        if (from == null || to == null || c == null) return;
        if (now - b.since > BoardingConfig.CROSS_TIMEOUT_TICKS.get()) {
            setDown(level, party, b, mob, to, c, now);
            return;
        }
        Vec3 landing = to.toWorld(Vec3.atBottomCenterOf(c.landing()));
        if (c.hop()) {
            if (!b.hopped) {
                if (!mob.onGround()) return;
                Vec3 d = landing.subtract(mob.position());
                double h = Math.hypot(d.x, d.z);
                BoardingHop.Launch l = BoardingHop.launch(h, c.rise());
                double ux = h < 1e-6 ? 0 : d.x / h;
                double uz = h < 1e-6 ? 0 : d.z / h;
                mob.getNavigation().stop();
                mob.setDeltaMovement(ux * l.horizontal(), l.vertical(), uz * l.horizontal());
                mob.hasImpulse = true;
                mob.setOnGround(false);
                mob.getLookControl().setLookAt(landing.x, landing.y + 1.5, landing.z);
                b.hopped = true;
                b.hopAt = now;
                return;
            }
            if (now - b.hopAt > 2 && mob.onGround()) {
                if (BoardingFight.aboard(mob, b.to)) {
                    arrive(level, party, b, mob, now);
                } else if (BoardingFight.aboard(mob, b.from)) {
                    b.hopped = false; // fell back onto its own deck: once more from the start
                    b.phase = b.phase == Phase.CROSS_OUT ? Phase.OUT : Phase.BACK;
                }
            }
            return;
        }
        List<BoardingWays.Point> via = c.via();
        Vec3 aim = landing;
        if (b.via < via.size()) {
            BoardingWays.Point p = via.get(b.via);
            ShipBody frame = p.frame() == BoardingWays.Frame.FROM ? from : to;
            aim = frame.toWorld(p.at());
            if (Math.hypot(aim.x - mob.getX(), aim.z - mob.getZ()) < 0.4) b.via++;
        }
        steer(mob, aim);
        if (b.via >= via.size() && Math.hypot(landing.x - mob.getX(), landing.z - mob.getZ()) < 0.8
                && BoardingFight.aboard(mob, b.to)) {
            arrive(level, party, b, mob, now);
        }
    }

    /**
     * Whether {@code mob} stands on a gangway: a plank cell at or under its feet in the frame of the ship it stands on.
     * A plank laid from the own ship lies in the own ship's plot, so a boarder at the board's far end over the other
     * deck still stands on its own ship for Sable; it is not back home.
     */
    static boolean onPlank(ServerLevel level, Mob mob) {
        ShipBody on = com.richardsenger.piratesnships.ship.sable.ShipEntities.standingOrRiding(mob);
        Vec3 at = on == null ? mob.position() : on.toPlot(mob.position());
        BlockPos feet = BlockPos.containing(at.add(0, 0.1, 0));
        return level.getBlockState(feet).is(BoardingContent.PLANK.get()) || level.getBlockState(feet.below()).is(BoardingContent.PLANK.get());
    }

    private static void steer(Mob mob, Vec3 world) {
        mob.getMoveControl().setWantedPosition(world.x, world.y, world.z, SPEED);
        mob.getLookControl().setLookAt(world.x, world.y + mob.getEyeHeight(), world.z);
    }

    /** The fallback: a boarder that took too long is set down on the far side of its crossing. */
    private static void setDown(ServerLevel level, Party party, Boarder b, Mob mob, ShipBody to, BoardingWays.Crossing c, long now) {
        Vec3 w = to.toWorld(Vec3.atBottomCenterOf(c.landing()).add(0, 0.05, 0));
        Constants.LOG.debug("Boarder {} did not get across in time: set down on ship {}", b.id, to.id());
        b.setDown = true;
        mob.getNavigation().stop();
        mob.moveTo(w.x, w.y, w.z, mob.getYRot(), mob.getXRot());
        mob.setDeltaMovement(Vec3.ZERO);
        arrive(level, party, b, mob, now);
    }

    /** Across: aboard the boarded ship, or home. */
    private static void arrive(ServerLevel level, Party party, Boarder b, Mob mob, long now) {
        mob.getNavigation().stop();
        b.crossing = null;
        b.since = now;
        b.hopped = false;
        if (b.phase == Phase.CROSS_BACK || b.phase == Phase.BACK) {
            home(level, party, b, mob);
        } else {
            b.phase = Phase.ABOARD;
        }
    }

    /** A boarder on the other deck: fight, or head home. */
    private static void aboard(ServerLevel level, Party party, Boarder b, Mob mob, long now) {
        if (BoardingFight.aboard(mob, party.home) && !onPlank(level, mob)) {
            if (BoardingRules.headsHome(party.recalled, party.captured)) {
                home(level, party, b, mob); // knocked back onto its own deck after the call: done
            } else {
                b.phase = Phase.OUT; // knocked back onto its own deck: across again
                b.from = party.home;
                b.to = party.target;
                b.crossing = null;
                b.since = now;
            }
            return;
        }
        if (BoardingRules.headsHome(party.recalled, party.captured)) {
            ShipBody target = SableShips.byId(level, party.target);
            ShipBody home = SableShips.byId(level, party.home);
            BoardingWays.Crossing back = target == null || home == null ? null
                    : BoardingWays.nearest(BoardingWorld.crossings(level, target, home), target.toPlot(mob.position()));
            if (back != null) {
                b.phase = Phase.BACK;
                b.from = party.target;
                b.to = party.home;
                b.crossing = back;
                b.since = now;
                b.lastSearch = Long.MIN_VALUE / 2;
                mob.setTarget(null);
                return;
            }
            // no way back: it stays and fights on
        }
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive() && BoardingFight.foes(mob, current)) return;
        ShipBody target = SableShips.byId(level, party.target);
        if (target == null) return;
        LivingEntity foe = nearest(mob, foesAboard(level, party, target), 24.0);
        mob.setTarget(foe);
        if (foe == null) mob.getNavigation().stop();
    }

    /** Home: off the party, and a crew member walks back to the station it left. */
    private static void home(ServerLevel level, Party party, Boarder b, Mob mob) {
        leave(party, b);
        mob.setTarget(null);
        mob.getNavigation().stop();
        if (mob instanceof CrewMember c) {
            c.setBoarding(false);
            StationRef s = b.station;
            if (s != null && s.ship().equals(party.home) && Stations.at(level, s.pos()) != null) {
                CrewStations.assign(level, c, s.pos(), b.pinned);
            }
        }
    }

    private static void leave(Party party, Boarder b) {
        party.boarders.remove(b.id);
        INDEX.remove(b.id);
    }

    /** Ends a party whose ship is gone for good: everyone is an ordinary crew member or mob where it stands. */
    private static void disband(ServerLevel level, Party party) {
        for (Boarder b : new ArrayList<>(party.boarders.values())) {
            leave(party, b);
            if (level.getEntity(b.id) instanceof CrewMember c) c.setBoarding(false);
        }
        PARTIES.remove(party.home);
    }

    /** Forgets every party (server stop; tests). */
    public static void clear() {
        PARTIES.clear();
        INDEX.clear();
    }

    /** Ends the party of {@code home} at once, without walking anyone back (tests, a removed ship). */
    public static void forget(ServerLevel level, UUID home) {
        Party p = PARTIES.get(home);
        if (p != null) disband(level, p);
    }
}
