package com.richardsenger.piratesnships.audio.creak;

import com.richardsenger.piratesnships.audio.AudioSounds;
import com.richardsenger.piratesnships.audio.CreakConfig;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Server side of hull creaking: once per level tick, feeds every afloat ship's rocking rate (roll and pitch rate in
 * the ship frame, from the physics engine) to its {@link CreakTrigger} and plays the creaks it returns at a random
 * point of the ship's plot box, with {@code ServerLevel#playSound(null, …)} so every nearby player hears it. Uses the
 * sailing runtime for the box, the bow and the submerged fraction, so it never scans blocks.
 */
public final class ShipCreaks {

    private static final Map<ServerLevel, Map<UUID, CreakTrigger>> STATE = new IdentityHashMap<>();
    /** Creaks played per ship since the server started (for GameTests and debugging). */
    private static final Map<UUID, Integer> PLAYED = new HashMap<>();
    private static final Random RANDOM = new Random();

    private ShipCreaks() {
    }

    public static void onLevelTick(ServerLevel level) {
        CreakTrigger.Params p = CreakConfig.params();
        if (!p.enabled()) {
            STATE.remove(level);
            return;
        }
        Map<UUID, CreakTrigger> triggers = null;
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        Quaterniond q = new Quaterniond();
        for (ShipBody ship : SableShips.all(level)) {
            SailingRuntime rt = SailingRuntimes.get(level, ship.id());
            if (rt == null || ship.isRemoved()) {
                continue;
            }
            if (triggers == null) {
                triggers = STATE.computeIfAbsent(level, l -> new HashMap<>());
            }
            CreakTrigger trigger = triggers.computeIfAbsent(ship.id(), id -> new CreakTrigger());
            double rate = 0.0;
            if (rt.lastSubmerged() > 0.0 && ship.velocities(lin, ang)) {
                rt.bow().shipToWorld(ship.orientation(q), q).transformInverse(ang);
                rate = Math.hypot(ang.x, ang.z); // pitch (ship x) and roll (ship z); yaw does not work the planks
            }
            CreakTrigger.Creak c = trigger.tick(rate, p, RANDOM);
            if (c != null) {
                play(level, ship, rt.bounds(), c);
            }
        }
    }

    private static void play(ServerLevel level, ShipBody ship, int[] b, CreakTrigger.Creak c) {
        Vector3d plot = new Vector3d(b[0] + c.fx() * (b[3] + 1 - b[0]), b[1] + c.fy() * (b[4] + 1 - b[1]),
                b[2] + c.fz() * (b[5] + 1 - b[2]));
        Vector3d w = ship.toWorld(plot, new Vector3d());
        level.playSound(null, w.x, w.y, w.z, AudioSounds.SHIP_CREAK.get(), SoundSource.BLOCKS, c.volume(), c.pitch());
        PLAYED.merge(ship.id(), 1, Integer::sum);
    }

    /** Number of creaks played by this ship since the server started. */
    public static int played(UUID ship) {
        return PLAYED.getOrDefault(ship, 0);
    }

    public static void onShipRemoved(ServerLevel level, UUID ship) {
        Map<UUID, CreakTrigger> m = STATE.get(level);
        if (m != null) {
            m.remove(ship);
        }
        PLAYED.remove(ship);
    }

    public static void onServerStopped() {
        STATE.clear();
        PLAYED.clear();
    }
}
