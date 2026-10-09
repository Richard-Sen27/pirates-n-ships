package com.richardsenger.piratesnships.ship.screen.client;

import com.richardsenger.piratesnships.ship.screen.ShipScreenPayloads;
import com.richardsenger.piratesnships.ship.screen.ShipScreenView;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Client copy of the last ship screen state the server sent (HGUI1). Uses no client-only classes, so the payload
 * handler may reference it on both sides; {@link ShipScreenClient#init} installs the opener and clears it when the
 * client leaves a world. {@link #version()} grows with every state, {@link #messageVersion()} with every message.
 */
public final class ClientShipScreenState {

    private static volatile Optional<ShipScreenView> view = Optional.empty();
    private static volatile Optional<Component> message = Optional.empty();
    private static volatile boolean ok = true;
    private static volatile long version;
    private static volatile long messageVersion;
    private static volatile Runnable opener = () -> { };

    private ClientShipScreenState() {
    }

    /** Client init only: what happens when the server opens the screen. */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    public static void accept(ShipScreenPayloads.State state) {
        view = state.view();
        if (state.message().isPresent()) {
            message = state.message();
            ok = state.ok();
            messageVersion++;
        }
        version++;
        if (state.open() && state.view().isPresent()) {
            message = Optional.empty();
            opener.run();
        }
    }

    public static Optional<ShipScreenView> view() {
        return view;
    }

    public static Optional<Component> message() {
        return message;
    }

    public static boolean ok() {
        return ok;
    }

    public static long version() {
        return version;
    }

    public static long messageVersion() {
        return messageVersion;
    }

    public static void reset() {
        view = Optional.empty();
        message = Optional.empty();
        version++;
    }
}
