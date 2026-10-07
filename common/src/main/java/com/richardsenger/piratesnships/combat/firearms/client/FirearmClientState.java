package com.richardsenger.piratesnships.combat.firearms.client;

import net.minecraft.world.item.ItemStack;

/**
 * The local player's use state as far as {@code FirearmItem}'s item bar needs it. The item is common code that also
 * runs on a dedicated server, so it reads the client through this interface: the default answers "not loading", and
 * {@link FirearmsClient#init()} installs {@link LocalFirearmClientState} on the physical client. This interface must
 * not reference client-only classes.
 */
public interface FirearmClientState {

    /** No client (dedicated server, GameTests): nothing is being loaded. */
    FirearmClientState NONE = stack -> -1;

    /**
     * Ticks the local player has held {@code stack} in a running loading session, or -1 when the local player isn't
     * loading this stack.
     */
    int loadingHeldTicks(ItemStack stack);

    static FirearmClientState get() {
        return Holder.current;
    }

    static void set(FirearmClientState state) {
        Holder.current = state == null ? NONE : state;
    }

    /** Storage of the installed state (interfaces can't have mutable static fields). */
    final class Holder {
        private static volatile FirearmClientState current = NONE;

        private Holder() {
        }
    }
}
