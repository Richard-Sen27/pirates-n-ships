package com.richardsenger.piratesnships.hazards.waves.client;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.ClientWaves;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.WaveSprayPayload;
import com.richardsenger.piratesnships.hazards.waves.WaveSyncPayload;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The client side of the waves (WV1, physical client only): the synced sea ({@link ClientWaves}), bow spray with a
 * splash ({@code wave_effects.spray}) and the optional camera sway ({@code wave_effects.camera_sway}).
 */
public final class WaveClient {

    /**
     * Sign of the camera roll for a ship heeled toward the viewer's right. A ship-fixed camera would roll with the deck;
     * the sign of NeoForge's roll (applied about the view axis in {@code GameRenderer}) is checked in the playtest.
     */
    static final float ROLL_SIGN = 1.0f;

    private WaveClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWaves.reset());
        ClientEvents.COMPUTE_CAMERA_ROLL.register(WaveClient::cameraRoll);
    }

    public static void onSync(WaveSyncPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        ClientWaves.accept(payload, level == null ? 0.0 : level.getGameTime());
    }

    /** Spray and a splash at a bow that dug into a wave. */
    public static void onSpray(WaveSprayPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !HazardConfig.SPRAY.get()) {
            return;
        }
        RandomSource r = level.random;
        double strength = Math.min(1.5, Math.max(0.0, p.strength()));
        int count = 12 + (int) (strength * 24) + (ClientWaves.state() == SeaState.STORM ? 10 : 0);
        double sideX = -p.dirZ(), sideZ = p.dirX();
        for (int i = 0; i < count; i++) {
            double side = (r.nextDouble() - 0.5) * 2.5;
            double x = p.x() + sideX * side + p.dirX() * r.nextDouble() * 0.6;
            double z = p.z() + sideZ * side + p.dirZ() * r.nextDouble() * 0.6;
            double up = 0.25 + r.nextDouble() * (0.3 + 0.3 * strength);
            double out = 0.1 + r.nextDouble() * 0.15;
            level.addParticle(ParticleTypes.SPLASH, x, p.y() + 0.1, z,
                    p.dirX() * out + sideX * side * 0.08, up, p.dirZ() * out + sideZ * side * 0.08);
            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.CLOUD, x, p.y() + 0.3, z, p.dirX() * 0.05, 0.05 + r.nextDouble() * 0.05, p.dirZ() * 0.05);
            }
        }
        level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.GENERIC_SPLASH, SoundSource.AMBIENT,
                (float) (0.35 + 0.3 * Math.min(1.0, strength)), 0.8f + r.nextFloat() * 0.3f, false);
    }

    /** {@link ClientEvents#COMPUTE_CAMERA_ROLL}: adds {@code camera_sway_fraction} of the ship's heel across the view. */
    static float cameraRoll(float partialTick, float roll) {
        if (!HazardConfig.CAMERA_SWAY.get()) {
            return roll;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return roll;
        }
        Quaterniond q = ClientShipPoses.shipOrientation(player, partialTick);
        if (q == null) {
            return roll;
        }
        Vector3d up = q.transform(new Vector3d(0, 1, 0));
        double yaw = Math.toRadians(player.getViewYRot(partialTick));
        return roll + ROLL_SIGN * (float) (HazardConfig.CAMERA_SWAY_FRACTION.get() * CameraSway.heelAcrossViewDegrees(up, yaw));
    }
}
