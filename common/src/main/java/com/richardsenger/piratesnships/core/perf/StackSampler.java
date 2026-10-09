package com.richardsenger.piratesnships.core.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * A plain sampling profiler for the performance test (PERF1): a daemon thread reads the server thread's stack every
 * {@code intervalNanos} and hands it to {@link PerfStats.Samples}. Vanilla's profiler only names the sections vanilla
 * pushes (entity types, block entities); the mod's level-tick work has no sections, so this sampler attributes the
 * tick to the mod's methods. Reading a stack stops the server thread at a safepoint for a few microseconds, so the
 * test's tick times during the sampled window carry that overhead (the report says so).
 */
final class StackSampler {

    private final Thread target;
    private final long intervalNanos;
    private final PerfStats.Samples samples = new PerfStats.Samples();
    private volatile boolean running;
    private Thread thread;

    StackSampler(Thread target, long intervalNanos) {
        this.target = target;
        this.intervalNanos = intervalNanos;
    }

    void start() {
        running = true;
        thread = new Thread(this::run, "pirates_n_ships perf sampler");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops sampling and returns what was sampled. */
    PerfStats.Samples stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (samples) {
            return samples;
        }
    }

    private void run() {
        while (running && target.isAlive()) {
            StackTraceElement[] stack = target.getStackTrace();
            List<PerfStats.Frame> frames = new ArrayList<>(stack.length);
            for (StackTraceElement e : stack) frames.add(new PerfStats.Frame(e.getClassName(), e.getMethodName()));
            synchronized (samples) {
                samples.add(frames);
            }
            LockSupport.parkNanos(intervalNanos);
        }
    }
}
