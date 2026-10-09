package com.richardsenger.piratesnships.station.lookout.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudMarks;
import com.richardsenger.piratesnships.station.lookout.LookoutContent;
import com.richardsenger.piratesnships.station.lookout.SightingCue;
import com.richardsenger.piratesnships.station.lookout.SightingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the lookout's call (LKO2), wired from {@code LookoutModule.initClient()}: each call from
 * {@link SightingMarks} becomes one action-bar line ({@link SightingCue}, at most 40 characters) and one hail from the
 * nest; its mark on the compass rose comes from {@link SightingMarks#marks} through {@link ShipHudMarks}.
 */
public final class LookoutClient {

    private LookoutClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(LookoutClient::tick);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> SightingMarks.reset());
        ShipHudMarks.register(SightingMarks::marks);
    }

    private static void tick(Minecraft mc) {
        SightingMarks.tick();
        if (mc.player == null || mc.level == null) return;
        SightingPayload cue = SightingMarks.nextCue();
        if (cue == null) return;
        Language lang = Language.getInstance();
        mc.gui.setOverlayMessage(Component.literal(SightingCue.text(cue, lang::getOrDefault)), false);
        Vec3 at = SightingMarks.hailAt(mc.player.getEyePosition(), new Vec3(cue.nestX(), cue.nestY(), cue.nestZ()));
        mc.level.playLocalSound(at.x, at.y, at.z, LookoutContent.HAIL.get(), SoundSource.NEUTRAL, 1.0f, 1.0f, false);
    }
}
