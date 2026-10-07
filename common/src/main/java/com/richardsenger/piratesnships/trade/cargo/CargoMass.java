package com.richardsenger.piratesnships.trade.cargo;

/**
 * How cargo weight becomes Sable mass (design.md §4.9, CW1). Pure.
 *
 * <p><b>Unit conversion.</b> Cargo weight is counted in the {@code crew.provisions} units of {@link CargoWeight} (one
 * food item 0.25, one water ration 1, one iron ingot 1). Sable's mass unit is the kpg (refs/sable wiki "Block Physics
 * Properties": a plank block weighs 0.5, a stone block 2). One weight unit is {@link #KPG_PER_UNIT} = 0.02 kpg, so a
 * crate full of sugar (2,048 items × 0.25 = 512 units) adds 10.24 kpg, about twenty planks' worth, and the starter
 * sloop's six cargo containers full at their nominal weight (5,376 units) add 108 kpg to a ship whose blocks weigh
 * roughly 300 kpg (estimated from its block list: about 580 wooden blocks at 0.5). The
 * value is a gameplay choice (inventories hold far more than a real crate), tuned so a fully laden sloop sits clearly
 * lower without sinking; {@code cargo_trade.weight_factor} scales it at run time.
 *
 * <p><b>Our containers</b> carry their content as a {@code load} block-state property 0..4 (empty, a quarter, half, three
 * quarters, full). Sable's mass comes from static datapack JSON ({@code physics_block_properties}), so the mass of each
 * state is fixed at datagen: {@code base + load / 4 × KPG_PER_UNIT × nominalWeight}. The run-time config enters through
 * the state choice instead: {@link #loadState} rounds {@code weight × weight_factor / nominalWeight} to the nearest
 * quarter, clamped to full, and gives 0 when {@code cargo_weight_affects_ships} is off. A container heavier than its
 * nominal weight (a crate full of iron ingots, 2,048 units) counts as full.
 *
 * <p><b>Vanilla containers</b> (chests, barrels, shulker boxes, hoppers) have no state to change; they get a downward
 * force of {@link #forceOf} = {@code KPG_PER_UNIT × weight_factor × weight × gravity} at their position, the weight a
 * mass of that size would have.
 */
public final class CargoMass {

    /** Sable mass [kpg] of one cargo weight unit at weight factor 1. */
    public static final double KPG_PER_UNIT = 0.02;

    /** The highest {@code load} state (full). */
    public static final int MAX_LOAD = 4;

    /**
     * The physics of one of our containers.
     *
     * @param baseMass      Sable mass of the empty block [kpg] (the Sable tag mass it had before CW1)
     * @param nominalWeight the cargo weight that counts as full [units]
     */
    public record Profile(double baseMass, double nominalWeight) {

        /** The mass a full container adds [kpg]. */
        public double fullExtraMass() {
            return KPG_PER_UNIT * nominalWeight;
        }

        /** Sable mass of the block in load state {@code load} (clamped to 0..4) [kpg]. */
        public double mass(int load) {
            int l = Math.max(0, Math.min(MAX_LOAD, load));
            return baseMass + fullExtraMass() * l / MAX_LOAD;
        }

        /** The load state for {@code weight} of content. */
        public int loadState(double weight, CargoWeight.Params p) {
            return CargoMass.loadState(CargoWeight.shipEffect(weight, p), nominalWeight);
        }
    }

    /** Cargo crate: wood like a plank ({@code #sable:light}); full = 32 stacks of a good weighing 0.5 (1,024 units). */
    public static final Profile CRATE = new Profile(0.5, 32 * 64 * 0.5);
    /** Cargo barrel: {@code #sable:light}; full = its 1,536 items of a good weighing 0.5 (768 units). */
    public static final Profile BARREL = new Profile(0.5, 1536 * 0.5);
    /** Pantry: {@code #sable:light}; full = 27 stacks of food at 0.25 (432 units). */
    public static final Profile PANTRY = new Profile(0.5, 27 * 64 * 0.25);
    /** Water barrel: {@code #sable:heavy} (2.0) as before; full = 16 rations at 1 (16 units). */
    public static final Profile WATER_BARREL = new Profile(2.0, 16 * 1.0);

    private CargoMass() {
    }

    /**
     * The load state 0..4 for an effective weight ({@link CargoWeight#shipEffect}, already 0 when the toggle is off):
     * the nearest quarter of {@code nominalWeight}, clamped to 0..4. Half a quarter rounds up.
     */
    public static int loadState(double effectiveWeight, double nominalWeight) {
        if (!(effectiveWeight > 0) || !(nominalWeight > 0)) {
            return 0;
        }
        double quarters = effectiveWeight / nominalWeight * MAX_LOAD;
        return (int) Math.max(0, Math.min(MAX_LOAD, Math.floor(quarters + 0.5)));
    }

    /** Mass [kpg] that {@code weight} units of cargo amount to on a ship (0 when the toggle is off). */
    public static double massOf(double weight, CargoWeight.Params p) {
        return KPG_PER_UNIT * CargoWeight.shipEffect(weight, p);
    }

    /**
     * Magnitude of the downward force [N, Sable units: kpg·m/s²] on a vanilla container holding {@code weight} units,
     * under gravity of magnitude {@code gravity} (Sable's default is 11 m/s²).
     */
    public static double forceOf(double weight, CargoWeight.Params p, double gravity) {
        return massOf(weight, p) * Math.max(0, gravity);
    }
}
