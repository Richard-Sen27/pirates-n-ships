package com.richardsenger.piratesnships.sailing.effects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Wind streaks in loose puffs (WD2, docs/design.md §5.4 "Wind streaks"): real air moves in gusts, so most streaks are
 * born in clusters of {@link WindStreakStyle#puffMinSize()} to {@link WindStreakStyle#puffSize()} within
 * {@link WindStreakStyle#puffSpread()} blocks of a centre over {@link WindStreakStyle#puffTicks()} ticks, with a thin
 * trickle of single streaks between. The split keeps the mean: of a rate {@code r} streaks per tick, the trickle
 * spawns {@code r × (1 − share)} and puffs are born at {@code r × share / meanPuffSize}, so over time exactly {@code r}
 * streaks per tick appear, the same number the steady WD1 rate gave. A gust raises the share
 * ({@link WindStreakStyle#puffShare(double)}). Holds the puffs still being born; pure, the random numbers and the
 * puff centres come from the caller. One instance per client.
 */
public final class WindPuffs {

    /**
     * One streak to spawn.
     *
     * @param x        world x
     * @param y        world y
     * @param z        world z
     * @param speedU   uniform in [0, 1) for {@link WindStreakStyle#streak}: a puff's members share theirs closely
     * @param headingU likewise for the heading
     * @param inPuff   whether it belongs to a puff (false: the trickle)
     */
    public record Spawn(double x, double y, double z, double speedU, double headingU, boolean inPuff) {
    }

    private record Pending(Spawn spawn, int delay) {
    }

    private final List<Pending> pending = new ArrayList<>();

    /**
     * One tick: the trickle, the puffs born this tick, and the members of earlier puffs due now.
     *
     * @param rate   mean streaks per tick ({@link WindStreakRules#rate} × the particle setting)
     * @param gust   gust intensity in [0, 1]
     * @param random uniforms in [0, 1)
     * @param centre a new start point {@code {x, y, z}} (for a single streak or a puff's centre)
     * @param spawn  receives each streak due this tick
     */
    public void tick(double rate, double gust, WindStreakStyle style, DoubleSupplier random, Supplier<double[]> centre,
                     Consumer<Spawn> spawn) {
        if (rate > 0.0) {
            double share = style.puffShare(gust);
            int single = SpawnRules.count(rate * (1.0 - share), random.getAsDouble());
            for (int i = 0; i < single; i++) {
                double[] c = centre.get();
                spawn.accept(new Spawn(c[0], c[1], c[2], random.getAsDouble(), random.getAsDouble(), false));
            }
            int puffs = SpawnRules.count(rate * share / style.meanPuffSize(), random.getAsDouble());
            for (int i = 0; i < puffs; i++) {
                birth(style, random, centre.get());
            }
        }
        Iterator<Pending> it = pending.iterator();
        List<Pending> later = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            it.remove();
            if (p.delay() <= 0) {
                spawn.accept(p.spawn());
            } else {
                later.add(new Pending(p.spawn(), p.delay() - 1));
            }
        }
        pending.addAll(later);
    }

    private void birth(WindStreakStyle style, DoubleSupplier random, double[] c) {
        int size = style.puffSize(random.getAsDouble());
        double groupSpeed = random.getAsDouble(), groupHeading = random.getAsDouble();
        for (int m = 0; m < size; m++) {
            int delay = Math.min(style.puffTicks() - 1, (int) Math.floor(random.getAsDouble() * style.puffTicks()));
            double[] d = SpawnRules.ringPoint(0.0, 0.0, 0.0, style.puffSpread(), random.getAsDouble(), random.getAsDouble());
            double dy = (random.getAsDouble() - 0.5) * style.puffSpread() * 0.5;
            Spawn s = new Spawn(c[0] + d[0], c[1] + dy, c[2] + d[1],
                    WindStreakStyle.cohere(groupSpeed, random.getAsDouble()),
                    WindStreakStyle.cohere(groupHeading, random.getAsDouble()), true);
            pending.add(new Pending(s, delay));
        }
    }

    /** Streaks of puffs still waiting to be born. */
    public int pendingCount() {
        return pending.size();
    }

    /** Forgets the puffs still being born (a new level, the streaks turned off). */
    public void clear() {
        pending.clear();
    }
}
