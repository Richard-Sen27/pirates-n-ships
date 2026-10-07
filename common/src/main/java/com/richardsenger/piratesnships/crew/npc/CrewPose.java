package com.richardsenger.piratesnships.crew.npc;

/**
 * The looping body animation a crew member plays (rig contract in {@code art/README.md}, section "Entities"). Pure:
 * the client's animation controller only maps these to the animation names of the animation file.
 * <p>
 * Priority, highest first: {@link #WORK} (an order is being carried out at its station), {@link #SLEEP} (it lies in a
 * hammock, HM1/ART1d), {@link #SIT} (riding something that seats it; the station seat does not, there the crew member
 * stands), {@link #WALK} (the legs move), {@link #IDLE}.
 */
public enum CrewPose {
    IDLE("idle"),
    WALK("walk"),
    WORK("work"),
    SIT("sit"),
    SLEEP("sleep");

    private final String animation;

    CrewPose(String animation) {
        this.animation = animation;
    }

    /** The animation name in {@code animations/crew_member.animation.json}. */
    public String animation() {
        return animation;
    }

    /**
     * @param moving  the legs move (limb swing above GeckoLib's threshold; always zero while riding)
     * @param working the crew member carries out an order at its station ({@link CrewMember#isWorking()})
     * @param seated  it rides a vehicle that seats it (anything but the station seat, see {@link CrewMember#isSeated()})
     */
    public static CrewPose choose(boolean moving, boolean working, boolean seated) {
        return choose(moving, working, seated, false);
    }

    /**
     * @param resting it lies in a hammock ({@link CrewMember#isResting()}, synced); wins over {@code seated}, since
     *                the hammock seat is a vehicle too
     */
    public static CrewPose choose(boolean moving, boolean working, boolean seated, boolean resting) {
        if (working) return WORK;
        if (resting) return SLEEP;
        if (seated) return SIT;
        if (moving) return WALK;
        return IDLE;
    }
}
