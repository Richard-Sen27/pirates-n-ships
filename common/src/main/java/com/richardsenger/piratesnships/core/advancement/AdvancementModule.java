package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The {@code core.advancement} module (ADV1, {@code docs/advancements.md}): the mod's advancement tab, its criterion
 * triggers ({@link ModTriggers}, fired through {@link ModAdvancements} from the event sites), the polled criteria
 * ({@link AdvancementWatch}), the scaled experience ({@link AdvancementXp}) and the generated advancements
 * ({@link AdvancementData}).
 */
public final class AdvancementModule implements ModModule {

    @Override
    public String id() {
        return "core.advancement";
    }

    @Override
    public void registerConfig() {
        AdvancementConfig.init();
    }

    @Override
    public void registerContent() {
        ModTriggers.init();
        AdvancementAttachments.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToServer(SceneOpenedPayload.TYPE, SceneOpenedPayload.CODEC, SceneOpenedPayload::handle);
    }

    @Override
    public void registerEvents() {
        CommonEvents.PLAYER_TICK_END.register(AdvancementWatch::onPlayerTick);
        CommonEvents.PLAYER_LOGOUT.register(p -> AdvancementWatch.forget(p.getUUID()));
        CommonEvents.SERVER_STOPPED.register(server -> AdvancementWatch.clear());
        CannonShipHits.register(hit -> ModAdvancements.shipHit(shooter(hit.level().getServer(), hit.shooter(), hit.firingShip())));
    }

    /** The player who fired, else the online owner of the firing ship (a crew's broadside counts for its captain). */
    static @Nullable Player shooter(MinecraftServer server, @Nullable Entity shooter, @Nullable java.util.UUID firingShip) {
        if (shooter instanceof Player p) return p;
        if (firingShip == null) return null;
        return ShipRegistry.get(server).find(firingShip).flatMap(ShipData::owner)
                .map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
    }

    @Override
    public void gatherData(DataContributions data) {
        AdvancementData.gather(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AdvancementGameTests.class);
    }
}
