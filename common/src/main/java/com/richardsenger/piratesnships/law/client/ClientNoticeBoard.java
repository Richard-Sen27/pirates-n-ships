package com.richardsenger.piratesnships.law.client;

import com.richardsenger.piratesnships.law.net.NoticeBoardPayloads;
import com.richardsenger.piratesnships.law.net.NoticeBoardView;

import java.util.Optional;

/**
 * Client copy of the open notice board (docs/design.md §13.2) for {@link NoticeBoardScreen}. Uses no client-only
 * classes, so the payload handlers may reference it on both sides; {@link LawClient#init} installs the screen opener
 * and clears it when the client leaves a world. {@link #version()} grows with every update so the screen can tell
 * when to redraw.
 */
public final class ClientNoticeBoard {

    private static volatile Optional<NoticeBoardPayloads.Open> board = Optional.empty();
    private static volatile Optional<NoticeBoardView> view = Optional.empty();
    private static volatile Optional<NoticeBoardPayloads.Result> lastResult = Optional.empty();
    private static volatile long version;
    private static volatile Runnable opener = () -> { };

    private ClientNoticeBoard() {
    }

    /** Client init only: what happens when a board is opened (opens the screen). */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    public static void open(NoticeBoardPayloads.Open open) {
        board = Optional.of(open);
        view = Optional.empty();
        lastResult = Optional.empty();
        version++;
        opener.run();
    }

    public static void accept(NoticeBoardPayloads.State state) {
        view = state.view();
        if (state.result().isPresent()) lastResult = state.result();
        if (state.view().isEmpty()) board = Optional.empty();
        version++;
    }

    public static Optional<NoticeBoardPayloads.Open> board() {
        return board;
    }

    public static Optional<NoticeBoardView> view() {
        return view;
    }

    public static Optional<NoticeBoardPayloads.Result> lastResult() {
        return lastResult;
    }

    /** Forgets the last result once the screen has shown it. */
    public static void clearResult() {
        lastResult = Optional.empty();
    }

    public static long version() {
        return version;
    }

    public static void reset() {
        board = Optional.empty();
        view = Optional.empty();
        lastResult = Optional.empty();
        version++;
    }
}
