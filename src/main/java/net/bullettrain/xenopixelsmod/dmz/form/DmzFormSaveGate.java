package net.bullettrain.xenopixelsmod.dmz.form;

/**
 * Coalesces form-editor edits into one save.
 *
 * <p>Typed fields would otherwise send a packet per keystroke, and a second save sent before the
 * first is acknowledged would carry a stale revision and be rejected by
 * {@link DmzFormEditorService}. So a save only leaves once the draft exists on the server, no
 * save is in flight, and the operator has stopped typing for the debounce window. Edits made
 * while a save is in flight stay pending and go out with the next send.
 */
public final class DmzFormSaveGate {
    private final long debounceMillis;
    private boolean dirty;
    private long dirtySince;

    public DmzFormSaveGate(long debounceMillis) {
        this.debounceMillis = debounceMillis;
    }

    /** Records an edit made at {@code now}. */
    public void touch(long now) {
        dirty = true;
        dirtySince = now;
    }

    public boolean dirty() {
        return dirty;
    }

    /** True when the pending edits should be sent now. */
    public boolean shouldSend(long now, boolean inFlight, boolean created) {
        return dirty && created && !inFlight && now - dirtySince >= debounceMillis;
    }

    /** Marks the pending edits as sent. */
    public void sent() {
        dirty = false;
    }
}
