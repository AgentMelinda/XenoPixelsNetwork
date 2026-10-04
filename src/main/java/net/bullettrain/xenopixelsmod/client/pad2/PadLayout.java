package net.bullettrain.xenopixelsmod.client.pad2;

import com.dragonminez.client.util.KeyBinds;
import net.bullettrain.xenopixelsmod.client.PartyClientControls;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.Bt3DirectBind;
import net.bullettrain.xenopixelsmod.client.combat.XenoTargetingControls;
import net.bullettrain.xenopixelsmod.client.pad.PadChords;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Budokai Tenkaichi 3 gamepad layout, as a table.
 *
 * <p>The previous integration expressed the same layout as six hundred lines of registration
 * calls, each carrying its own inline gate. It was correct, but the layout could not be
 * <em>read</em>: answering "what does B do while the left trigger is held" meant tracing
 * predicates through the file. Here one row is one binding, and the whole layout is one list.
 *
 * <p><b>No Controlify types appear in this file.</b> Inputs are named by {@link PadInput} and
 * resolved to Controlify's constants in {@link PadBinds}, which is what lets the layout be checked
 * without Controlify installed — and what makes a mistake in it a compile error rather than a dead
 * button discovered in a fight.
 *
 * <p>Key mappings are held as {@link Supplier}s, not values. {@code Minecraft.getInstance().options}
 * is null early in startup and Controlify's pre-init can run before it exists; a table that read
 * the mappings eagerly would either crash or silently capture nulls.
 */
public final class PadLayout {

    /**
     * What decides whether a row may press, beyond its chord layer.
     *
     * <p>An enum rather than a predicate per row, because there are only three and naming them
     * makes the table legible. {@link PadBinds} turns each into the real condition.
     */
    public enum Gate {
        /** The default: BT3 mode is the active layer. */
        BT3_MODE,
        /** BT3 mode, and DragonMineZ flight is active. Flight steering and ascend. */
        FLYING,
        /**
         * BT3 mode, and no chord face button is held.
         *
         * <p>Lock-on's own gate. Reaching for a chord holds the left bumper as a modifier; without
         * this the same press would also cycle the target out from under the move being aimed.
         */
        NO_CHORD_HELD
    }

    /** How a radial entry draws itself. Resolved to a Controlify {@code RadialIcon} in {@link PadBinds}. */
    public sealed interface Icon {
        /** Two or three letters in a colour, for an action with no artwork of its own. */
        record Text(String label, int color) implements Icon {}

        /**
         * A technique slot's number.
         *
         * <p>The number rather than the technique: what sits in a slot is chosen in game and
         * changes freely, but a radial icon is registered once at startup.
         */
        record TechniqueSlot(int slot) implements Icon {}
    }

    /** Colours by area, so the radial reads at a glance. */
    public static final int COMBAT = 0xFFFFAA00;
    public static final int TARGETING = 0xFF55FFFF;
    public static final int PARTY = 0xFF55FF55;

    /**
     * One binding.
     *
     * @param id     the binding id, unique within this mod
     * @param key    the mapping this row presses, resolved late; null for a row that presses nothing
     * @param input  the default physical input, or {@link PadInput#NONE} to ship unbound
     * @param layer  the chord layer, or null when the row is not layered
     * @param route  the {@code /xenobind} switch that can retire this chord, or null for always
     * @param gate   what else must be true
     * @param icon   the radial icon, or null when this row is not a radial candidate
     */
    public record Row(String id, @Nullable Supplier<KeyMapping> key, PadInput input,
                      @Nullable PadChords.Layer layer, @Nullable Bt3DirectBind route,
                      Gate gate, @Nullable Icon icon) {

        /** Whether this row offers itself to the radial menu. */
        public boolean radialCandidate() {
            return icon != null;
        }

        /** The full binding id, as Controlify and the radial slot list see it. */
        public String fullId() {
            return "xenopixelsmod:" + id;
        }
    }

    private PadLayout() {
    }

    private static Row key(String id, Supplier<KeyMapping> key, PadInput input,
                           @Nullable PadChords.Layer layer) {
        return new Row(id, key, input, layer, null, Gate.BT3_MODE, null);
    }

    private static Row routed(String id, Supplier<KeyMapping> key, PadInput input,
                              PadChords.Layer layer, Bt3DirectBind route) {
        return new Row(id, key, input, layer, route, Gate.BT3_MODE, null);
    }

    private static Row radial(String id, Supplier<KeyMapping> key, Icon icon) {
        return new Row(id, key, PadInput.NONE, null, null, Gate.BT3_MODE, icon);
    }

    /**
     * The whole layout, in the order it is registered.
     *
     * <p>Built fresh on each call rather than cached in a static: the {@code Minecraft.options}
     * mappings below are only valid once the game has built them, and a cached list would freeze
     * whatever was true the first time anything asked.
     */
    public static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        // ---- Base layer: the four face buttons, unmodified ----------------------------------
        rows.add(key("melee", () -> Bt3CombatClient.CHARGE_FIST, PadInput.WEST,
                PadChords.Layer.BASE));
        rows.add(key("dash", () -> Bt3CombatClient.DRAGON_DASH, PadInput.SOUTH,
                PadChords.Layer.BASE));
        rows.add(key("guard", () -> Bt3CombatClient.GUARD, PadInput.EAST, PadChords.Layer.BASE));
        // Beam surge shares the melee button for the same reason it shares the left mouse button
        // on the keyboard: it only does anything while one of your own waves is firing, so it
        // costs the melee press nothing to sit on top of it.
        rows.add(key("beam_surge", () -> Bt3CombatClient.BEAM_SURGE, PadInput.WEST,
                PadChords.Layer.BASE));
        // DragonMineZ's ki blast is not a key binding: it is the chord "Second Function + use
        // item", read in ClientStatsEventsMixin. One pad button therefore holds two keys down,
        // which is two rows on the same input rather than one row with a special case.
        rows.add(key("ki_blast", () -> KeyBinds.SECOND_FUNCTION_KEY, PadInput.NORTH,
                PadChords.Layer.BASE));
        if (mc.options != null) {
            rows.add(key("ki_blast_use", () -> mc.options.keyUse, PadInput.NORTH,
                    PadChords.Layer.BASE));
        }

        // ---- Charge layer: hold LT ----------------------------------------------------------
        rows.add(routed("z_burst", () -> Bt3CombatClient.Z_BURST, PadInput.SOUTH,
                PadChords.Layer.CHARGE, Bt3DirectBind.Z_BURST));
        rows.add(routed("ultimate", () -> Bt3CombatClient.ULTIMATE, PadInput.WEST,
                PadChords.Layer.CHARGE, Bt3DirectBind.ULTIMATE));
        rows.add(routed("sparking", () -> Bt3CombatClient.SPARKING, PadInput.EAST,
                PadChords.Layer.CHARGE, Bt3DirectBind.SPARKING));
        rows.add(key("charged_kick", () -> Bt3CombatClient.CHARGE_KICK, PadInput.NORTH,
                PadChords.Layer.CHARGE));

        // ---- Descend layer: hold RT ---------------------------------------------------------
        // RT+Y is also the charged kick, so a player can kick while descending. It goes through
        // the layer machinery like everything else so the base ki blast stands down for it;
        // gating it on the raw trigger instead let both fire from one press.
        rows.add(key("charged_kick_rt", () -> Bt3CombatClient.CHARGE_KICK, PadInput.NORTH,
                PadChords.Layer.DESCEND));

        // ---- Lock layer: hold LB ------------------------------------------------------------
        // Zanzoken and Multi-Form ship unbound on the keyboard because the key space is full.
        // The pad's is not, so here they get a real default.
        rows.add(routed("zanzoken", () -> Bt3CombatClient.ZANZOKEN, PadInput.WEST,
                PadChords.Layer.LOCK, Bt3DirectBind.ZANZOKEN));
        rows.add(routed("multiform", () -> Bt3CombatClient.MULTIFORM, PadInput.NORTH,
                PadChords.Layer.LOCK, Bt3DirectBind.MULTIFORM));
        rows.add(routed("hakai", () -> Bt3CombatClient.HAKAI, PadInput.EAST,
                PadChords.Layer.LOCK, Bt3DirectBind.HAKAI));
        rows.add(routed("sonic_right", () -> Bt3CombatClient.SONIC_SWAY_RIGHT, PadInput.SOUTH,
                PadChords.Layer.LOCK, Bt3DirectBind.SONIC_SWAY_RIGHT));

        // ---- The modifiers, which are also actions in their own right -----------------------
        rows.add(key("ki_charge", () -> KeyBinds.KI_CHARGE, PadInput.LEFT_TRIGGER, null));
        rows.add(new Row("lock_on", () -> Bt3CombatClient.LOCK_NEXT, PadInput.LEFT_SHOULDER,
                null, null, Gate.NO_CHORD_HELD, null));
        // DragonMineZ's own lock-on, on the same button as ours.
        //
        // Reachable by emulation, unlike the technique slots: ClientStatsEvents calls
        // KeyBinds.LOCK_ON.consumeClick(), and Controlify's KeyMappingMixin increments clickCount
        // as well as setting isDown - so a consumeClick() reader sees an emulated press. The
        // technique slots go through KeyBinds.isPhysicallyDown, which reads GLFW directly, and no
        // emulation can reach those.
        //
        // Two rows on one input, the same shape the ki blast already uses for its two keys.
        rows.add(new Row("dmz_lock_on", () -> KeyBinds.LOCK_ON, PadInput.LEFT_SHOULDER,
                null, null, Gate.NO_CHORD_HELD, null));
        rows.add(key("descend", () -> KeyBinds.DESCEND, PadInput.RIGHT_TRIGGER, null));

        // ---- Unlayered -----------------------------------------------------------------------
        if (mc.options != null) {
            // Ascend while flying rides the same button as the flight toggle.
            rows.add(new Row("ascend", () -> mc.options.keyJump, PadInput.RIGHT_SHOULDER,
                    null, null, Gate.FLYING, null));
            // Descend while flying is SHIFT, not KeyBinds.DESCEND.
            //
            // Measured from dragonminez-2.1.3 with javap: FlySkillEvent.handleFlightMovement reads
            // exactly six mappings - Options.keyUp/keyDown/keyLeft/keyRight for steering,
            // Options.keyJump to climb and Options.keyShift to drop. KeyBinds.DESCEND does not
            // appear in it at all, so the RT row below moved the player on the ground and did
            // nothing whatsoever in the air.
            //
            // On L3 rather than a trigger: both triggers are chord modifiers, and dropping while
            // flying wants a button you can hold without changing what every face button means.
            // Ground descend stays on RT.
            rows.add(new Row("fly_descend", () -> mc.options.keyShift, PadInput.LEFT_STICK_BUTTON,
                    null, null, Gate.FLYING, null));
        }
        rows.add(key("transform", () -> KeyBinds.ACTION_KEY, PadInput.RIGHT_STICK_BUTTON, null));
        rows.add(key("stats_menu", () -> KeyBinds.STATS_MENU, PadInput.BACK, null));
        // BASE-layered rather than unlayered, so holding a trigger stands them down and the d-pad
        // becomes the ki technique selector instead. Unmodified they behave exactly as before.
        rows.add(key("chase", () -> Bt3CombatClient.CHASE, PadInput.DPAD_UP,
                PadChords.Layer.BASE));
        rows.add(key("backstep", () -> Bt3CombatClient.BACKSTEP, PadInput.DPAD_DOWN,
                PadChords.Layer.BASE));
        rows.add(new Row("sonic_left", () -> Bt3CombatClient.SONIC_SWAY_LEFT, PadInput.DPAD_LEFT,
                PadChords.Layer.BASE, Bt3DirectBind.SONIC_SWAY_LEFT, Gate.BT3_MODE, null));

        // ---- Flight steering -----------------------------------------------------------------
        // DragonMineZ steers flight from Options.keyUp/keyDown/keyLeft/keyRight.isDown() in
        // FlySkillEvent.handleFlightMovement, but Controlify does not press those: its
        // ControllerPlayerMovement replaces LocalPlayer.input outright and writes analogue
        // impulses instead. So the stick walks you on the ground and does nothing at all in
        // Search or Combat Fly. Emulating the four movement keys gives DMZ what WASD gives it,
        // and the FLYING gate leaves ground movement on Controlify's analogue path untouched.
        if (mc.options != null) {
            rows.add(new Row("fly_forward", () -> mc.options.keyUp, PadInput.LEFT_STICK_UP,
                    null, null, Gate.FLYING, null));
            rows.add(new Row("fly_back", () -> mc.options.keyDown, PadInput.LEFT_STICK_DOWN,
                    null, null, Gate.FLYING, null));
            rows.add(new Row("fly_left", () -> mc.options.keyLeft, PadInput.LEFT_STICK_LEFT,
                    null, null, Gate.FLYING, null));
            rows.add(new Row("fly_right", () -> mc.options.keyRight, PadInput.LEFT_STICK_RIGHT,
                    null, null, Gate.FLYING, null));
        }

        // ---- Technique slots -----------------------------------------------------------------
        // DragonMineZ reaches these with Alt+1..4 and Ctrl+1..4, chords no gamepad can produce.
        // They ship unbound and live in the radial, which is why widening it to twenty matters.
        KeyMapping[] slots = KeyBinds.TECHNIQUE_SLOTS;
        if (slots != null) {
            for (int i = 0; i < slots.length; i++) {
                int index = i;
                rows.add(radial("technique_slot_" + (i + 1), () -> KeyBinds.TECHNIQUE_SLOTS[index],
                        new Icon.TechniqueSlot(i + 1)));
            }
        }

        // ---- Everything else this mod owns that no button could reach ------------------------
        rows.add(radial("dash_left", () -> Bt3CombatClient.DASH_LEFT, new Icon.Text("<", COMBAT)));
        rows.add(radial("dash_right", () -> Bt3CombatClient.DASH_RIGHT, new Icon.Text(">", COMBAT)));
        rows.add(radial("ki_blast_cancel", () -> Bt3CombatClient.KI_BLAST_CANCEL,
                new Icon.Text("KC", COMBAT)));
        // LB cycles targets forward; there is no button left for the other direction.
        rows.add(radial("lock_prev", () -> Bt3CombatClient.LOCK_PREV, new Icon.Text("LP", COMBAT)));
        rows.add(radial("ki_guidance", () -> Bt3CombatClient.KI_GUIDANCE,
                new Icon.Text("KG", COMBAT)));

        rows.add(radial("target_lock", () -> XenoTargetingControls.LOCK_TOGGLE,
                new Icon.Text("LK", TARGETING)));
        rows.add(radial("target_clear", () -> XenoTargetingControls.CLEAR_LOCK,
                new Icon.Text("CL", TARGETING)));
        rows.add(radial("target_cycle", () -> XenoTargetingControls.CYCLE_TARGET,
                new Icon.Text("CY", TARGETING)));
        rows.add(radial("target_lead", () -> XenoTargetingControls.TOGGLE_LEAD,
                new Icon.Text("LD", TARGETING)));

        // DragonMineZ's own utility / ki-attack radial (KeyBinds.UTILITY_MENU, default X). It had
        // no gamepad route at all, so a controller player could not open it.
        rows.add(radial("utility_menu", () -> KeyBinds.UTILITY_MENU,
                new Icon.Text("KI", COMBAT)));
        rows.add(radial("ki_sense", () -> KeyBinds.KI_SENSE, new Icon.Text("KS", TARGETING)));

        rows.add(radial("party_screen", () -> PartyClientControls.OPEN_PARTY,
                new Icon.Text("PT", PARTY)));
        rows.add(radial("party_ping", () -> PartyClientControls.PING_TARGET,
                new Icon.Text("PG", PARTY)));

        return List.copyOf(rows);
    }
}
