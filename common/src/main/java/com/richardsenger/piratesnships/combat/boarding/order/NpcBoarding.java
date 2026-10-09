package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingConfig;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.combat.cannon.npc.ShipHostility;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageShips;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NPC captains board (BRD3, docs/design.md §8.4, §9): a pirate or navy ship of the world simulation (a materialised
 * voyage, {@link VoyageShips}; a pirate captain's ship among them) looks every second at the player ships lying close.
 * Its attack decision is the gunnery's ({@link ShipHostility}, WS4a: the pirates fight a ship under the navy or merchant
 * flag or none, the navy a Jolly Roger, a ship whose cover is blown or whose owner has a bounty); the boarding step
 * comes after it: a ship it would fire on, with a way across ({@link BoardingWorld#crossings}), lain alongside for
 * {@code boarding.npc_board_delay} seconds, and the captain gives "Board!" when its guns have fired on her or its ship
 * is the faster or the heavier, and its fighters are not outnumbered ({@link BoardingRules#npcStep}). The fighters (the
 * pirates, soldiers and officer standing on its deck) cross like crew ({@link BoardingParties#boardWithFighters}) and
 * fight the player and his crew; the captain calls them back when his ship floods or they are outnumbered
 * ({@link BoardingRules#npcRecalls}). {@code boarding.npc_boarding} switches it off.
 *
 * <p>Ships outside the world simulation can take part through {@link #adopt} (the GameTests; later NPC ships of other
 * kinds).
 */
public final class NpcBoarding {

    /** Ticks between two looks. */
    static final int INTERVAL = 20;

    /** Ships adopted as NPC ships of a faction, outside the voyages. */
    private static final Map<UUID, Faction> ADOPTED = new LinkedHashMap<>();
    /** (NPC ship, enemy ship) → game time they were first seen alongside. */
    private static final Map<List<UUID>, Long> CONTACT = new HashMap<>();

    private NpcBoarding() {
    }

    /** Treats {@code ship} as an NPC ship of {@code faction} (its fighters: the faction's mobs on its deck). */
    public static void adopt(UUID ship, Faction faction) {
        ADOPTED.put(ship, faction);
    }

    public static void release(UUID ship) {
        ADOPTED.remove(ship);
        CONTACT.keySet().removeIf(k -> k.get(0).equals(ship));
    }

    public static void clear() {
        ADOPTED.clear();
        CONTACT.clear();
    }

    /** The flooded fraction of {@code ship}'s hull (0 without a hull analysis). */
    static double flooded(ServerLevel level, ShipBody ship) {
        return VoyageEndings.floodFraction(level, ship);
    }

    /** Every {@link #INTERVAL} ticks: each NPC ship of the level looks at the player ships close to it. */
    public static void onLevelTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % INTERVAL != 0) return;
        if (!BoardingConfig.NPC_BOARDING.get() || !BoardingConfig.BOARD_ORDER.get()) {
            CONTACT.clear();
            return;
        }
        for (Map.Entry<UUID, Faction> e : npcShips(level.getServer()).entrySet()) {
            ShipBody ship = SableShips.byId(level, e.getKey());
            if (ship == null || ship.isRemoved() || ShipSplits.isWreck(ship)) continue;
            look(level, ship, e.getValue(), now);
        }
    }

    /** The NPC ships with their factions: the pirate and navy voyages afloat, and the adopted ships. */
    static Map<UUID, Faction> npcShips(MinecraftServer server) {
        Map<UUID, Faction> out = new LinkedHashMap<>(ADOPTED);
        for (Voyage v : Voyages.active(server)) {
            if (v.state() != Voyage.State.MATERIALISED || v.shipId().isEmpty()) continue;
            if (v.faction() == Faction.PIRATES || v.faction() == Faction.NAVY) out.putIfAbsent(v.shipId().get(), v.faction());
        }
        return out;
    }

    /** One NPC ship's look (see the class comment). */
    static void look(ServerLevel level, ShipBody ship, Faction faction, long now) {
        if (BoardingParties.out(ship.id())) return;
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        List<LivingEntity> fighters = fighters(level, ship, faction);
        for (ShipBody other : SableShips.all(level)) {
            if (other.id().equals(ship.id()) || other.isRemoved() || ShipSplits.isWreck(other)) continue;
            List<UUID> key = List.of(ship.id(), other.id());
            if (!BoardingWorld.near(ship, other, BoardingConfig.BOARD_GAP.get() + 2.0)) {
                CONTACT.remove(key);
                continue;
            }
            Optional<ShipData> data = registry.find(other.id());
            boolean enemy = data.isPresent() && data.get().owner().isPresent() && enemy(level, faction, data.get());
            boolean way = enemy && !BoardingWorld.crossings(level, ship, other).isEmpty();
            int defenders = enemy ? defenders(level, other) : 0;
            BoardingRules.NpcView view = new BoardingRules.NpcView(enemy, way, gunsEngaged(ship, other),
                    speed(ship) > speed(other) + 0.05, ship.mass() > other.mass(), fighters.size(), defenders);
            long since = CONTACT.computeIfAbsent(key, k -> now);
            BoardingRules.NpcStep step = BoardingRules.npcStep(view, (int) Math.min(Integer.MAX_VALUE, now - since),
                    BoardingConfig.NPC_BOARD_DELAY.get() * 20, BoardingConfig.NPC_RECALL_OUTNUMBERED.get());
            switch (step) {
                case NONE -> CONTACT.remove(key);
                case WAIT -> {
                }
                case BOARD -> {
                    List<SeafarerMob> mobs = new ArrayList<>();
                    for (LivingEntity f : fighters) if (f instanceof SeafarerMob m) mobs.add(m);
                    BoardingParties.Outcome o = BoardingParties.boardWithFighters(level, ship, other, mobs);
                    CONTACT.remove(key);
                    Constants.LOG.debug("NPC ship {} boards ship {}: {}", ship.id(), other.id(), o.result());
                    return;
                }
            }
        }
    }

    /** Whether a ship of {@code faction} fights the ship described by {@code data} (the gunnery's rule). */
    static boolean enemy(ServerLevel level, Faction faction, ShipData data) {
        FlagReading flag = data.flag();
        long lawNow = LawService.now(level.getServer());
        ShipStance stance = ShipStance.of(flag.shown(), flag.isStruck(), data.coverBlown(lawNow));
        boolean wanted = faction == Faction.NAVY && data.owner().map(o -> LawService.hasBounty(level.getServer(), o)).orElse(false);
        return ShipHostility.hostile(faction, flag, wanted, stance);
    }

    /** Its gun crews aim at {@code other}: the gunnery's target, or the ship one of its crews aimed at last. */
    static boolean gunsEngaged(ShipBody ship, ShipBody other) {
        GunneryState s = Gunnery.state(ship.id());
        if (s.mode() == GunneryState.Mode.TARGET && other.id().equals(s.target())) return true;
        return Gunnery.engaged(ship).map(other.id()::equals).orElse(false);
    }

    private static double speed(ShipBody ship) {
        Vector3d v = ship.linearVelocity();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    /** The fighters of {@code faction} standing on {@code ship}. */
    static List<LivingEntity> fighters(ServerLevel level, ShipBody ship, Faction faction) {
        MobFaction mf = faction == Faction.NAVY ? MobFaction.NAVY : MobFaction.PIRATE;
        AABB box = com.richardsenger.piratesnships.crew.npc.CrewStations.worldBox(ship, 2);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof SeafarerMob m && m.isAlive()
                && m.faction() == mf && BoardingParties.boarder(m) == null && BoardingFight.aboard(m, ship.id()));
    }

    /** The people aboard {@code other} who would fight NPC boarders. */
    static int defenders(ServerLevel level, ShipBody other) {
        AABB box = com.richardsenger.piratesnships.crew.npc.CrewStations.worldBox(other, 2);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> BoardingFight.combatant(e, true)
                && BoardingFight.aboard(e, other.id())).size();
    }
}
