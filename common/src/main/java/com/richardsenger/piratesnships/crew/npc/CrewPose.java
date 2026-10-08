package com.richardsenger.piratesnships.crew.npc;

import org.jetbrains.annotations.Nullable;

/**
 * The body animation a crew member plays (rig contract in {@code art/README.md}, section "Entities"). Pure: the
 * client's animation controller only maps these to the animation names of the animation file.
 * <p>
 * Priority, highest first: a <b>station pose</b> (ART7: the helmsman's hold and turns, the gun crew's aim, load and
 * fire; chosen on the server by {@link StationPoses} and synced), {@link #WORK} (an order is being carried out at its
 * station), {@link #SLEEP} (it lies in a hammock, HM1/ART1d), {@link #SIT} (riding something that seats it; the
 * station seat does not, there the crew member stands), {@link #WALK} (the legs move), {@link #IDLE}.
 */
public enum CrewPose {
    IDLE("idle"),
    WALK("walk"),
    WORK("work"),
    SIT("sit"),
    SLEEP("sleep"),
    /** ART7: at the helm, both hands on the wheel. */
    HELM("helm_hold"),
    /** ART7: at the helm while the wheel turns to port (counter-clockwise as the helmsman sees it). */
    HELM_TURN_LEFT("helm_turn_left"),
    /** ART7: at the helm while the wheel turns to starboard (clockwise as the helmsman sees it). */
    HELM_TURN_RIGHT("helm_turn_right"),
    /** ART7: at a gun that its crew is aiming (fire at will or a chosen target): a hand on the breech, sighting. */
    CANNON_AIM("cannon_aim"),
    /** ART7: at a gun while the station loads it: ramming. */
    CANNON_LOAD("cannon_load"),
    /** ART7: at a gun while the station fires it (the fuse): the lunge with the linstock, played once. */
    CANNON_FIRE("cannon_fire", false),
    /** ART7, played since CRW3: at the capstan while the station drops or raises the anchor, chest to the bars, walking them. */
    CAPSTAN_PUSH("capstan_push");

    /** Station poses in the order of their synced id ({@link #stationId()}); never reorder, append only. */
    private static final CrewPose[] STATION = {HELM, HELM_TURN_LEFT, HELM_TURN_RIGHT, CANNON_AIM, CANNON_LOAD, CANNON_FIRE, CAPSTAN_PUSH};

    private final String animation;
    private final boolean loops;

    CrewPose(String animation) {
        this(animation, true);
    }

    CrewPose(String animation, boolean loops) {
        this.animation = animation;
        this.loops = loops;
    }

    /** The animation name in {@code animations/crew_member.animation.json}. */
    public String animation() {
        return animation;
    }

    /** Whether the animation loops; {@link #CANNON_FIRE} plays once. */
    public boolean loops() {
        return loops;
    }

    /** Whether this pose is chosen by a station ({@link StationPoses}) rather than by the crew member's own state. */
    public boolean isStationPose() {
        return stationId() >= 0;
    }

    /** The synced id of a station pose (0..), or -1 for the other poses. */
    public int stationId() {
        for (int i = 0; i < STATION.length; i++) {
            if (STATION[i] == this) return i;
        }
        return -1;
    }

    /** The station pose with the synced id {@code id}, or null (also for -1 and unknown ids). */
    public static @Nullable CrewPose byStationId(int id) {
        return id >= 0 && id < STATION.length ? STATION[id] : null;
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
        return choose(moving, working, seated, resting, null);
    }

    /**
     * @param station the station pose the server chose ({@link CrewMember#stationPose()}), or null; it wins over
     *                everything else (a helmsman holding a course or a gun crew loading is also "working")
     */
    public static CrewPose choose(boolean moving, boolean working, boolean seated, boolean resting, @Nullable CrewPose station) {
        if (station != null && station.isStationPose()) return station;
        if (working) return WORK;
        if (resting) return SLEEP;
        if (seated) return SIT;
        if (moving) return WALK;
        return IDLE;
    }
}
