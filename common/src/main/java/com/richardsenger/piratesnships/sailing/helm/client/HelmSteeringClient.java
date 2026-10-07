package com.richardsenger.piratesnships.sailing.helm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.sailing.helm.HelmReleasePayload;
import com.richardsenger.piratesnships.sailing.helm.HelmSessionPayload;
import com.richardsenger.piratesnships.sailing.helm.HelmWheelPayload;
import com.richardsenger.piratesnships.sailing.helm.WheelInput;
import com.richardsenger.piratesnships.sailing.helm.WheelMath;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of steering by the wheel (docs/design.md §5.3, HELM1), modelled on {@code SwivelAimClient}.
 *
 * <ul>
 *   <li>Pressing use on a helm (not sneaking) lets vanilla send the block use and remembers the helm; while use stays
 *       down, vanilla's repeated uses are cancelled. The server answers with a {@link HelmSessionPayload} when the
 *       player took the wheel (on an assembled ship with wheel steering on).</li>
 *   <li>While the session lasts and use is held, horizontal mouse movement and the strafe keys turn the wheel
 *       ({@link WheelInput}); once per tick the delta goes to the server ({@link HelmWheelPayload}). The strafe keys
 *       are taken from the movement while steering. With {@code helm.wheel.lock_view} the mouse's share of the
 *       view's yaw is taken back, so the view stays put while the mouse turns the wheel (other turns, such as the
 *       ship carrying the player round, are left alone).</li>
 *   <li>Letting go of use sends {@link HelmReleasePayload}; the wheel stays where it is.</li>
 * </ul>
 *
 * <p>The mouse is read and the view held per frame from {@link HelmOverlay} (drawn after the world, so the view shows
 * at most one frame's mouse movement) and once per tick as a fallback (F1 hides the HUD). A per-frame event that fires
 * after the mouse turned the player and before the world is drawn would remove that one frame.
 */
public final class HelmSteeringClient {

    private static final WheelInput INPUT = new WheelInput();
    /** Use was pressed on this helm; waiting for the server's answer while use is held. */
    private static @Nullable BlockPos pending;
    /** The helm whose wheel the player holds. */
    private static @Nullable BlockPos active;
    private static double lastMouseX = Double.NaN;
    private static boolean keysTaken;
    // predicted wheel angle (same math as the server) for the renderer and the overlay
    private static float predicted;
    private static float predictedPrevious;

    private HelmSteeringClient() {
    }

    public static void init() {
        ClientEvents.INTERACTION_KEY.register(HelmSteeringClient::onInteraction);
        ClientEvents.CLIENT_TICK_START.register(HelmSteeringClient::onTick);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> stop());
    }

    /** The helm held right now, or null. */
    public static @Nullable BlockPos activeHelm() {
        return active;
    }

    /** The locally predicted wheel angle of {@code pos} while the player holds that wheel, else null. */
    public static @Nullable Float predictedWheel(BlockPos pos, float partialTick) {
        if (active == null || !active.equals(pos)) {
            return null;
        }
        return Mth.lerp(partialTick, predictedPrevious, predicted);
    }

    /** The predicted wheel angle of the held helm (0 when none). */
    public static float predictedWheel() {
        return predicted;
    }

    private static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input != ClientEvents.InteractionInput.USE) {
            return ClientEvents.InteractionKeyResult.PASS;
        }
        if (pending != null || active != null) {
            return ClientEvents.InteractionKeyResult.CANCEL; // held: the wheel is being turned, no other use
        }
        if (hand == InteractionHand.MAIN_HAND && mc.player != null && mc.level != null && HelmConfig.DRAG_STEERING.get()
                && !mc.player.isSecondaryUseActive() && mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof HelmBlock) {
            pending = hit.getBlockPos(); // a plot position on a ship, as the block use the server receives
        }
        return ClientEvents.InteractionKeyResult.PASS;
    }

    /** Server: the session started or ended. */
    public static void onSession(HelmSessionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.active()) {
            if (mc.options.keyUse.isDown()) {
                start(payload.pos(), payload.wheel());
            } else {
                Services.NETWORK.sendToServer(new HelmReleasePayload(payload.pos())); // let go before the answer came
                pending = null;
            }
        } else if (payload.pos().equals(active) || payload.pos().equals(pending)) {
            stop();
        }
    }

    private static void start(BlockPos pos, float wheel) {
        active = pos;
        pending = null;
        predicted = wheel;
        predictedPrevious = wheel;
        INPUT.reset();
        lastMouseX = Double.NaN;
    }

    private static void stop() {
        active = null;
        pending = null;
        INPUT.reset();
        lastMouseX = Double.NaN;
        if (keysTaken) {
            keysTaken = false;
            KeyMapping.setAll(); // the strafe keys move the player again as they are held now
        }
    }

    /** Start of a client tick, before the player's movement input is read. */
    private static void onTick(Minecraft mc) {
        if (active == null) {
            if (pending != null && !mc.options.keyUse.isDown()) {
                pending = null;
            }
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stop();
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            Services.NETWORK.sendToServer(new HelmReleasePayload(active));
            stop();
            return;
        }
        frame(mc);
        boolean left = takeKey(mc, mc.options.keyLeft);
        boolean right = takeKey(mc, mc.options.keyRight);
        keysTaken = true;
        double max = HelmConfig.MAX_DEGREES_PER_TICK.get();
        double delta = INPUT.drain(HelmConfig.MOUSE_DEGREES_PER_UNIT.get(), left, right, HelmConfig.KEY_DEGREES_PER_TICK.get(), max);
        predictedPrevious = predicted;
        double lock = HelmConfig.lockAngle();
        predicted = (float) WheelMath.turn(predicted, 0.0, delta, max, lock).wheel();
        if (delta != 0.0) {
            Services.NETWORK.sendToServer(new HelmWheelPayload(active, (float) delta));
        }
    }

    /**
     * Reads the mouse since the last call into the wheel input and, with the view lock, takes the mouse's share of the
     * yaw back. Called per frame (from the HUD) and per tick; each call only sees the movement since the last one.
     */
    public static void frame(Minecraft mc) {
        if (active == null) {
            return;
        }
        double x = mc.mouseHandler.xpos();
        if (Double.isNaN(lastMouseX)) {
            lastMouseX = x;
            return;
        }
        double dx = x - lastMouseX;
        lastMouseX = x;
        LocalPlayer player = mc.player;
        // the game turns the player only while the window is active, the mouse grabbed and no screen open
        if (dx == 0.0 || player == null || !mc.isWindowActive() || !mc.mouseHandler.isMouseGrabbed() || mc.screen != null) {
            return;
        }
        double degrees = WheelInput.viewDegrees(dx, mc.options.sensitivity().get());
        INPUT.addMouse(degrees);
        if (HelmConfig.LOCK_VIEW.get()) {
            float back = (float) degrees;
            player.setYRot(player.getYRot() - back);
            player.yRotO -= back;
        }
    }

    /** Whether the key is physically held, and takes it from the game (so the strafe keys do not move the player). */
    private static boolean takeKey(Minecraft mc, KeyMapping key) {
        InputConstants.Key bound = InputConstants.getKey(key.saveString());
        if (bound.getType() != InputConstants.Type.KEYSYM || bound.getValue() == InputConstants.UNKNOWN.getValue()) {
            return key.isDown(); // a mouse button: read it, leave it alone
        }
        boolean down = InputConstants.isKeyDown(mc.getWindow().getWindow(), bound.getValue());
        key.setDown(false);
        return down;
    }
}
