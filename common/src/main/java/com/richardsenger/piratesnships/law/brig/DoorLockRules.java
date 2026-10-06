package com.richardsenger.piratesnships.law.brig;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who may open and lock a brig door. Pure. "Owner" is the player who placed the door; {@code extraAccess} is the
 * hook for "the ship's captain and crew" later (see {@link BrigDoorAccess}). A door without an owner (placed by a
 * command or a structure) can be locked by anyone, who then becomes its owner.
 */
public final class DoorLockRules {

    private DoorLockRules() {
    }

    /** {@code actor == null}: a mob or redstone. A locked door opens only for its owner or someone with access. */
    public static boolean canOpen(boolean locked, @Nullable UUID owner, @Nullable UUID actor, boolean extraAccess) {
        if (!locked) return true;
        if (actor == null) return false;
        return extraAccess || actor.equals(owner);
    }

    public static boolean canChangeLock(@Nullable UUID owner, @Nullable UUID actor, boolean extraAccess) {
        if (actor == null) return false;
        return owner == null || extraAccess || actor.equals(owner);
    }
}
