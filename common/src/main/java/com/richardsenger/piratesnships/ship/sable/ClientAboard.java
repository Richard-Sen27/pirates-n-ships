package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/**
 * The ship the local player is on (CAM1), one {@link AboardRule} shared by the ship HUD
 * ({@code ship.hull.client.ClientShipStatus}) and the camera sway ({@code hazards.waves.client.WaveClient}). Ticked at
 * client tick end by {@code ShipHud#tick}. Physical client only for {@link #tick(Minecraft)} and {@link #orientation};
 * {@link #update}, {@link #aboard()} and {@link #reset()} touch no client classes.
 *
 * <p>Sable: {@code ActiveSableCompanion#getTrackingOrVehicleSubLevel} (l.449, the sub-level the entity stands on, set
 * in {@code mixin/entity/entity_sublevel_collision/EntityMixin} l.109-180 only while it touches the ship from above,
 * else the one containing its vehicle), {@code sublevel/ClientSubLevel#renderPose(float)} (l.313) and
 * {@code sublevel/SubLevel#isRemoved} (l.192).
 */
public final class ClientAboard {

    private static final AboardRule<Object> RULE = new AboardRule<>();
    private static @Nullable Level lastLevel;

    private ClientAboard() {
    }

    /** Client tick end: feeds Sable's tracking and the player's state into the rule. */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            lastLevel = null;
            update(null, false, true);
            return;
        }
        boolean dimensionChanged = lastLevel != null && lastLevel != mc.level;
        lastLevel = mc.level;
        SubLevel sub = Sable.HELPER.getTrackingOrVehicleSubLevel(p);
        ClientSubLevel tracked = sub instanceof ClientSubLevel c && !c.isRemoved() && !dimensionChanged ? c : null;
        boolean inWater = p.isInWater();
        boolean flying = p.getAbilities().flying || p.isFallFlying();
        boolean climbing = p.onClimbable();
        boolean holding = climbing || (!p.onGround() && !inWater && !flying && !p.isPassenger());
        // tracked is null here whenever these count: ground or a vehicle that is not on a ship
        boolean leaving = dimensionChanged || inWater || p.isPassenger() || (p.onGround() && !climbing);
        update(tracked, holding, leaving);
    }

    /** One tick of the shared rule (see {@link AboardRule#tick}). */
    public static void update(@Nullable Object tracked, boolean holding, boolean leaving) {
        RULE.tick(tracked, holding, leaving);
    }

    /** The HUD's aboard state: tracked, held through a jump or climb, or within the grace after. */
    public static boolean aboard() {
        return RULE.aboard();
    }

    /** The client ship the player is on, kept through jumps and climbs; null when none or it was removed. */
    public static @Nullable ClientSubLevel ship() {
        return RULE.ship() instanceof ClientSubLevel c && !c.isRemoved() ? c : null;
    }

    /** Plot-to-world rotation (copy) of {@link #ship()} at render time, or null. */
    public static @Nullable Quaterniond orientation(float partialTick) {
        ClientSubLevel c = ship();
        return c == null ? null : new Quaterniond(c.renderPose(partialTick).orientation());
    }

    public static void reset() {
        RULE.reset();
        lastLevel = null;
    }
}
