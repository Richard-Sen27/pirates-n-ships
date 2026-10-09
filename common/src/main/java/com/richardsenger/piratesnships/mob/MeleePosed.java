package com.richardsenger.piratesnships.mob;

/**
 * An entity on the humanoid rig whose melee phase is synced to clients as a {@link MeleePose} (docs/design.md §8.5
 * "NPC duelists", §9): the M3 mobs ({@code mob.entity.SeafarerMob}) and the crew (MEL3, {@code crew.npc.CrewMember}).
 * Both keep it through {@link MeleePoseSync}; the client models read it into the procedural telegraph
 * ({@code mob.client.MeleeTelegraph}).
 */
public interface MeleePosed {

    /** The synced melee pose ({@link MeleePose#IDLE} out of a fight). */
    MeleePose meleePose();

    /** Client: 0..1 through the current melee phase (open-ended phases report 1). */
    float meleePoseProgress(float partialTick);
}
