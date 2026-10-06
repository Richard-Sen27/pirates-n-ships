package com.richardsenger.piratesnships.platform.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A list of listeners of type {@code T}, invoked through a single combined {@link #invoker()}. Common code
 * registers listeners; loader modules call {@code invoker()} from their native event subscriptions.
 */
public final class Event<T> {

    private final Function<List<T>, T> invokerFactory;
    private volatile List<T> listeners = List.of();
    private volatile T invoker;

    private Event(Function<List<T>, T> invokerFactory) {
        this.invokerFactory = invokerFactory;
        this.invoker = invokerFactory.apply(listeners);
    }

    /** Creates an event whose invoker is built from the current listener list. */
    public static <T> Event<T> create(Function<List<T>, T> invokerFactory) {
        return new Event<>(invokerFactory);
    }

    /** Adds a listener. Register during mod initialization (a module's {@code registerEvents()}). */
    public synchronized void register(T listener) {
        List<T> copy = new ArrayList<>(listeners);
        copy.add(listener);
        listeners = List.copyOf(copy);
        invoker = invokerFactory.apply(listeners);
    }

    /** The combined listener. Called by the loader module. */
    public T invoker() {
        return invoker;
    }

    /** Whether any listener is registered (lets loaders skip work). */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }
}
