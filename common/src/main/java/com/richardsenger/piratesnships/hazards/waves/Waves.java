package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.waves.WaveForces;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;

/**
 * Wiring of the waves (WV1, docs/design.md §5.4), called by the hazards module: the sea state per level
 * ({@link SeaStates}), the forces on ships ({@code sailing.waves.WaveForces}, force group {@code pirates_n_ships:waves}),
 * the sync to clients, {@code /pirates waves}, spray and camera sway on the client. Config: {@code waves} and
 * {@code wave_effects} in {@code hazard.HazardConfig}.
 */
public final class Waves {

    private Waves() {
    }

    public static void registerContent() {
        ShipForces.registerWaves();
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(WaveSyncPayload.TYPE, WaveSyncPayload.CODEC,
                (payload, player) -> com.richardsenger.piratesnships.hazards.waves.client.WaveClient.onSync(payload));
        Services.NETWORK.registerToClient(WaveSprayPayload.TYPE, WaveSprayPayload.CODEC,
                (payload, player) -> com.richardsenger.piratesnships.hazards.waves.client.WaveClient.onSpray(payload));
    }

    public static void registerEvents() {
        // registered in this order, so the forces sample the sea of the same tick
        CommonEvents.LEVEL_TICK_END.register(SeaStates::onLevelTick);
        CommonEvents.LEVEL_TICK_END.register(WaveForces::onLevelTick);
        SableShips.onPhysicsTick(WaveForces::onPhysicsTick);
        SableShips.onShipRemoved((level, id, destroyed) -> WaveForces.onShipRemoved(level, id));
        CommonEvents.SERVER_TICK_END.register(WaveSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(WaveSync::onLogin);
        CommonEvents.SERVER_STOPPED.register(server -> {
            SeaStates.clearAll();
            WaveForces.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> WaveCommands.register(dispatcher));
    }

    public static void initClient() {
        com.richardsenger.piratesnships.hazards.waves.client.WaveClient.init();
    }

    public static void lang(LangBuilder lang) {
        lang.add(ShipForces.WAVES_KEY, "Waves")
                .add(WaveCommands.KEY_INFO, "Sea: %s (heading for %s), waves up to %s blocks, coming from %s°")
                .add(WaveCommands.KEY_OVERRIDE, "Sea: %s (held at %s by /pirates waves set), waves up to %s blocks, coming from %s°")
                .add(WaveCommands.KEY_DISABLED, "Waves are off in the server config (waves.enabled)")
                .add(WaveCommands.KEY_SET, "Sea held at %s (waves from %s°); /pirates waves clear returns it to the weather")
                .add(WaveCommands.KEY_CLEARED, "The sea follows the weather again")
                .add(WaveCommands.KEY_UNKNOWN, "Unknown sea state: %s (calm, moderate, rough or storm)");
    }
}
