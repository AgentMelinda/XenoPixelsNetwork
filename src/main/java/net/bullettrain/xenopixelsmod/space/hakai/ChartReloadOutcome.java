package net.bullettrain.xenopixelsmod.space.hakai;

/** Separates chart publication from ancillary persistence reported by the reload operation. */
record ChartReloadOutcome(boolean published, boolean ancillaryPersistenceSucceeded) {
    static ChartReloadOutcome classify(boolean reloadSucceeded, long revisionBefore, long revisionAfter) {
        boolean published = revisionAfter != revisionBefore;
        return new ChartReloadOutcome(published, reloadSucceeded);
    }

    boolean fullySucceeded() {
        return published && ancillaryPersistenceSucceeded;
    }
}
