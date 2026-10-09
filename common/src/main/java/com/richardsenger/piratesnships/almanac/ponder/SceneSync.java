package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.mob.MeleePose;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.decor.NameplateBlockEntity;
import com.richardsenger.piratesnships.ship.decor.ShipsBellBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * What the server syncs to a client, written by a Ponder scene (PND2, docs/almanac.md "Scenes"). In the game the server
 * decides an entity's animation state (a crew member working at its station, lying in its hammock, sitting at a meal;
 * a duelist's melee phase; a musketeer aiming) and a block entity's state (a flag being hoisted, a bell's last ring)
 * and syncs them; a Ponder scene has only a client-side copy of the world and no server, so it writes the values the
 * client would receive: entity data through the entity's own data accessors, block entity state through the tag keys
 * its sync uses ({@link SceneKit#editBlockEntity}).
 *
 * <p>Those accessors and keys are private to their classes; they are looked up by name here instead of widening their
 * classes for a guidebook. Our own classes are not obfuscated, so the names hold in a release jar;
 * {@code PonderStoryboardsTest} resolves every one of {@link #FIELDS} (a renamed field fails the build), and at
 * runtime a missing one only leaves the default look and logs once. Client only, called from scene instructions.
 */
final class SceneSync {

    /** A private static field of one of our classes that a scene reads. */
    record Ref(Class<?> owner, String name) {
    }

    static final Ref WORKING = new Ref(CrewMember.class, "DATA_WORKING");
    static final Ref RESTING = new Ref(CrewMember.class, "DATA_RESTING");
    static final Ref EATING = new Ref(CrewMember.class, "DATA_EATING");
    static final Ref STATION_POSE = new Ref(CrewMember.class, "DATA_STATION_POSE");
    static final Ref MELEE_POSE = new Ref(SeafarerMob.class, "DATA_MELEE_POSE");
    static final Ref AIMING = new Ref(SeafarerMob.class, "DATA_AIMING");
    /** The flagpole's saved and synced state ({@code FlagpoleState}) and cloth yaw. */
    static final Ref FLAGPOLE_STATE = new Ref(FlagpoleBlockEntity.class, "TAG");
    /** The bell's last ring (game time) and strike direction. */
    static final Ref BELL_RING_START = new Ref(ShipsBellBlockEntity.class, "TAG_RING_START");
    static final Ref BELL_STRIKE = new Ref(ShipsBellBlockEntity.class, "TAG_STRIKE");

    /** PND4: a name board's synced name and its place in its row (NAME2). */
    static final Ref NAME_TEXT = new Ref(NameplateBlockEntity.class, "TAG_TEXT");
    static final Ref NAME_ROW_INDEX = new Ref(NameplateBlockEntity.class, "TAG_ROW_INDEX");
    static final Ref NAME_ROW_LENGTH = new Ref(NameplateBlockEntity.class, "TAG_ROW_LENGTH");

    /** Every field a scene relies on. */
    static final List<Ref> FIELDS = List.of(WORKING, RESTING, EATING, STATION_POSE, MELEE_POSE, AIMING, FLAGPOLE_STATE,
            BELL_RING_START, BELL_STRIKE, NAME_TEXT, NAME_ROW_INDEX, NAME_ROW_LENGTH);

    private static boolean warned;

    private SceneSync() {
    }

    /** A crew member carrying out an order at its station: the {@link CrewPose#WORK} pose (hauling). */
    static void working(Entity e, boolean on) {
        if (e instanceof CrewMember) set(e, WORKING, on);
    }

    /** A crew member lying in its hammock: the {@link CrewPose#SLEEP} pose. */
    static void resting(Entity e, boolean on) {
        if (e instanceof CrewMember) set(e, RESTING, on);
    }

    /** A crew member at a meal by the pantry (it shows the work pose until the rig has an eating clip). */
    static void eating(Entity e, boolean on) {
        if (e instanceof CrewMember) set(e, EATING, on);
    }

    /** A crew member's station pose (ART7: helm, cannon, capstan), or none. */
    static void stationPose(Entity e, @Nullable CrewPose pose) {
        if (e instanceof CrewMember) set(e, STATION_POSE, (byte) (pose == null ? -1 : pose.stationId()));
    }

    /** A duelist's melee phase as its model telegraphs it ({@link MeleePose}). */
    static void melee(Entity e, MeleePose pose) {
        if (e instanceof SeafarerMob) set(e, MELEE_POSE, pose.pack());
    }

    /** A musketeer aiming. */
    static void aiming(Entity e, boolean on) {
        if (e instanceof SeafarerMob) set(e, AIMING, on);
    }

    /** The string constant {@code ref} (a tag key), or {@code fallback} when it cannot be read (logged once). */
    static String key(Ref ref, String fallback) {
        try {
            Object v = field(ref).get(null);
            return v instanceof String s ? s : fallback;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn(ref, ex);
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(Entity e, Ref ref, T value) {
        try {
            Object accessor = field(ref).get(null);
            if (!(accessor instanceof EntityDataAccessor<?>)) {
                throw new NoSuchFieldException(ref.name() + " is no data accessor");
            }
            e.getEntityData().set((EntityDataAccessor<T>) accessor, value);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn(ref, ex);
        }
    }

    /** The static field {@code ref}, made accessible. */
    static Field field(Ref ref) throws ReflectiveOperationException {
        Field f = ref.owner().getDeclaredField(ref.name());
        if (!Modifier.isStatic(f.getModifiers())) {
            throw new NoSuchFieldException(ref.owner().getSimpleName() + "." + ref.name() + " is not static");
        }
        f.setAccessible(true);
        return f;
    }

    private static void warn(Ref ref, Exception ex) {
        if (!warned) {
            warned = true;
            Constants.LOG.warn("Ponder scene: cannot use {}.{} ({}); the scene shows the default look",
                    ref.owner().getSimpleName(), ref.name(), ex.toString());
        }
    }
}
