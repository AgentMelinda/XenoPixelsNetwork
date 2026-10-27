package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.Objects;

/** Runs build cancellation even when owner-ticket cleanup fails, while retaining both failures for diagnosis. */
final class DragonBallBuildCleanup {
    private DragonBallBuildCleanup() {}

    static void releaseThenCancel(Runnable releaseTicket, Runnable cancel) {
        Objects.requireNonNull(releaseTicket, "releaseTicket");
        Objects.requireNonNull(cancel, "cancel");
        Throwable failure = null;
        try {
            releaseTicket.run();
        } catch (RuntimeException | Error releaseFailure) {
            failure = releaseFailure;
        }
        try {
            cancel.run();
        } catch (RuntimeException | Error cancelFailure) {
            if (failure == null) failure = cancelFailure;
            else if (failure != cancelFailure) failure.addSuppressed(cancelFailure);
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
    }
}
