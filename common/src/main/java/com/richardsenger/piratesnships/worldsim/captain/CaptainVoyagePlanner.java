package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.worldsim.navy.Hunting;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyagePlanner;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * The CAPTAIN planner of the voyage scheduler (BOS2). Captains depart on their own schedule ({@link CaptainVoyages#check}),
 * so the scheduler's spawn roll stays off; while a voyage sails as a record the captain hunts ({@link CaptainHunt}),
 * and at the end of the route it ends (he comes home) unless it is in a chase. GameTest voyages are left to their tests.
 */
public final class CaptainVoyagePlanner implements VoyagePlanner {

    @Override
    public Voyage update(MinecraftServer server, Voyage voyage) {
        return Hunting.isTest(voyage) ? voyage : CaptainHunt.updateAbstract(server, voyage);
    }

    @Override
    public Optional<Voyage> onArrive(MinecraftServer server, Voyage voyage) {
        return CaptainHunt.onArrive(server, voyage);
    }
}
