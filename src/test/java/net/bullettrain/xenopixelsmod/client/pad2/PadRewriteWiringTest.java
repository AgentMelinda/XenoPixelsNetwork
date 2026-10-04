package net.bullettrain.xenopixelsmod.client.pad2;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Controlify rewrite and the widened radial, checked at the source level.
 *
 * <p>None of this can be exercised in a unit test: the registrar needs Controlify's pre-init, the
 * mixin needs a running client, and the layout needs DragonMineZ's key bindings. What <b>is</b>
 * checkable is the shape of the decisions, and that is where these bugs live — a mixin pointed at
 * the wrong call site, a package that loads Controlify when it should not, a switch read at the
 * wrong moment.
 *
 * <p>The load-bearing one is {@link #theRadialWideningNeverReachesControlifysOwnConfigScreen()}.
 */
class PadRewriteWiringTest {

    private static final String PAD2 = "src/main/java/net/bullettrain/xenopixelsmod/client/pad2";
    private static final String MIXIN =
            "src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/controlify";

    private static String raw(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static String code(String dir, String file) throws IOException {
        return raw(dir, file)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the widened radial

    @Test
    void theRadialWideningNeverReachesControlifysOwnConfigScreen() throws IOException {
        // The whole reason this is a call-site redirect and not a method redirect.
        //
        // createBindings has two callers. The in-game one passes a null EditMode - nothing is
        // editable, nothing is written back, so a longer array is inert. The config screen passes
        // a BindingEditMode whose setRadialItem does
        //     radialActions.set(i, ((RadialItemRecord) item).id())
        // which both casts to a package-private type our item is not, and indexes a list that
        // holds eight. Widening that path would be a ClassCastException on the way in and an
        // IndexOutOfBoundsException past slot eight.
        String mixin = code(MIXIN, "ControlifyWideRadialMixin.java");
        assertTrue(mixin.contains("method = \"handleKeybinds\""),
                "the redirect must name the in-game method, not createBindings itself");
        assertFalse(mixin.contains("ControllerConfigScreenFactory"),
                "the config screen's path must not be touched");
        assertFalse(mixin.contains("BindingEditMode"),
                "nor anything that only exists on the editable path");
    }

    @Test
    void theRedirectFailsSoftSoAControlifyUpdateCannotBreakStartup() throws IOException {
        // Everything above dev.isxander.controlify.api is internal. require = 0 is what turns
        // "Controlify moved this call" into "the player gets the stock eight-slot radial".
        assertTrue(code(MIXIN, "ControlifyWideRadialMixin.java").contains("require = 0"));
        assertTrue(code(PAD2, "PadWideRadial.java").contains("catch (RuntimeException | LinkageError"),
                "and a signature change at runtime falls back rather than crashing");
    }

    @Test
    void theWideningIsGatedOnControlifyBeingInstalled() throws IOException {
        // The plugin keys off the package name, so the mixin only has to live in the right place.
        String plugin = code("src/main/java/net/bullettrain/xenopixelsmod/mixin",
                "ConditionalMixinPlugin.java");
        assertTrue(plugin.contains(".compat.controlify."));
        assertTrue(plugin.contains("isModLoaded(\"controlify\")"));
        assertTrue(Files.exists(RepoRoot.of(MIXIN, "ControlifyWideRadialMixin.java")));
        assertTrue(raw("src/main/resources", "xenopixelsmod.compat.mixins.json")
                        .contains("compat.controlify.ControlifyWideRadialMixin"),
                "a mixin that is not listed is a mixin that never applies");
    }

    @Test
    void controlifysOwnEntriesAreCarriedOverAsObjectsNotRebuiltFromIds() throws IOException {
        // Rebuilding them would lose whatever Controlify put there - an item icon, a game-mode
        // entry, its own empty marker - and there is no public way to tell which it was.
        assertTrue(code(PAD2, "PadWideRadial.java").contains("System.arraycopy(stock, 0, widened"));
    }

    @Test
    void ourRadialItemIsDeliberatelyNotControlifysRecordType() throws IOException {
        // RadialItemRecord is the type the config screen's edit mode casts to. Using our own makes
        // it structurally impossible for one of ours to reach that cast.
        String source = code(PAD2, "PadWideRadial.java");
        assertTrue(source.contains("implements RadialMenuScreen.RadialItem"));
        assertFalse(source.contains("RadialItemRecord"));
    }

    @Test
    void anUnresolvableIdBecomesAnEmptySlotRatherThanBeingDropped() throws IOException {
        // Dropping one would renumber every slot after it, which reads as the radial rearranging
        // itself after an unrelated change.
        String source = code(PAD2, "PadWideRadial.java");
        assertEquals(4, source.split("return RadialItems\\.EMPTY_ACTION;", -1).length - 1,
                "null input, unparseable id, missing binding and no radial icon all fall back");
    }

    // ------------------------------------------------------------ the layout table

    @Test
    void theLayoutTableNamesNoControlifyType() throws IOException {
        // What makes the layout readable and checkable without Controlify installed - and what
        // turns a mistake in it into a compile error rather than a dead button found in a fight.
        String layout = raw(PAD2, "PadLayout.java");
        assertFalse(layout.contains("dev.isxander"), "PadLayout must stay Controlify-free");
        assertFalse(raw(PAD2, "PadInput.java").contains("dev.isxander"));
        assertFalse(raw(PAD2, "PadRadialSlots.java").contains("dev.isxander"));
        assertFalse(raw(PAD2, "PadRadialSlots.java").contains("net.minecraft"),
                "the slot rules are pure so they can be unit tested");
    }

    @Test
    void everySymbolicInputIsMappedToARealOne() throws IOException {
        // A switch over the enum rather than a map, so adding a PadInput without wiring it fails
        // to compile instead of silently shipping an unbound action.
        String binds = code(PAD2, "PadBinds.java");
        for (PadInput input : PadInput.values()) {
            assertTrue(binds.contains("case " + input.name() + " ->"),
                    input + " has no GamepadInputs constant behind it");
        }
    }

    @Test
    void keyMappingsAreResolvedLateBecauseOptionsMayNotExistYet() throws IOException {
        // Controlify's pre-init can run before Minecraft.options is built. A table that read the
        // mappings eagerly would capture nulls.
        String layout = code(PAD2, "PadLayout.java");
        assertTrue(layout.contains("Supplier<KeyMapping>"));
        assertTrue(layout.contains("mc.options != null"),
                "and the options-derived rows are skipped rather than added as nulls");
    }

    @Test
    void theRegistrarKeepsTheGateItHandedControlify() throws IOException {
        // held() must give the same answer the emulation does: most of this mod's combat polls the
        // hardware rather than asking KeyMapping.isDown(), and an ungated read reports a plain
        // melee press while the trigger is held and the button means Ultimate.
        String binds = code(PAD2, "PadBinds.java");
        assertTrue(binds.contains("keyEmulation(mapping, controller -> allows(row, controller))"));
        assertTrue(binds.contains("boolean active(ControllerEntity controller)"));
        assertTrue(binds.contains("return pressed(controller) && allows(row, controller)"),
                "the same allows() decides both");
    }

    @Test
    void chordFacesAreCollectedSoLockOnCanStandDown() throws IOException {
        String binds = code(PAD2, "PadBinds.java");
        assertTrue(binds.contains("CHORD_FACES"));
        assertTrue(binds.contains("NO_CHORD_HELD -> !anyChordFaceDown(controller)"));
    }

    // ------------------------------------------------------------ the switch

    @Test
    void onlyOnePackageEverRegisters() throws IOException {
        // Both declare the same binding ids, so both registering would collide.
        String entry = code("src/main/java/net/bullettrain/xenopixelsmod/client/pad",
                "XenoControlifyEntrypoint.java");
        int rewrite = entry.indexOf("pad2.PadBinds.register");
        int legacy = entry.indexOf("XenoPadBinds.register");
        assertTrue(rewrite > 0 && legacy > 0);
        assertTrue(entry.contains("if (rewrite)"), "an either/or, not both");
    }

    @Test
    void theFacadeRoutesOnWhatRegisteredNotOnTheLiveConfigValue() throws IOException {
        // A player flipping padRewrite mid-session must keep talking to the package that actually
        // holds the bindings. Reading the config live would point every call at a package that
        // registered nothing, which reads as the pad going dead rather than needing a restart.
        String facade = code("src/main/java/net/bullettrain/xenopixelsmod/client/pad",
                "XenoPadInput.java");
        assertTrue(facade.contains("static void useRewrite(boolean value)"));
        assertFalse(facade.contains("XenoClientConfig.padRewrite"),
                "the facade must not re-read the switch");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/pad",
                "XenoControlifyEntrypoint.java").contains("XenoPadInput.useRewrite(rewrite)"));
    }

    @Test
    void theInputFilterRoutesTheSameWay() throws IOException {
        // Asking the wrong package would check a conflict table that was never filled in, so every
        // Controlify default would keep firing underneath the BT3 layout - two actions per press.
        String mixin = code(MIXIN, "ControlifyBt3ModeMixin.java");
        assertTrue(mixin.contains("XenoPadInput.usingRewrite()"));
        assertTrue(mixin.contains("pad2.PadBinds.conflicts(binding)"));
        assertTrue(mixin.contains("pad.XenoPadBinds.conflicts(binding)"));
    }

    @Test
    void theOldIntegrationIsStillIntact() throws IOException {
        // Side by side is the point: a controller-only regression is only findable with a
        // controller in hand, and until then the proven path has to still be there.
        assertTrue(Files.exists(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/pad", "XenoPadBinds.java")));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/pad",
                "XenoPadBinds.java").contains("public static void register("));
    }

    // ------------------------------------------------------------ the ki technique bars

    @Test
    void theKiBarsGoThroughPacketsBecauseTheKeyboardChordIsUnreachable() throws IOException {
        // DragonMineZ raises these bars on held Alt/Ctrl and fires a slot with a number key. Both
        // halves of KeyBinds.isChordDown read raw GLFW - isBarModifierActive resolves to
        // Screen.hasAltDown(), and isPhysicallyDown calls InputConstants.isKeyDown whenever the
        // slot has a key bound. Controlify's emulation sets isDown and bumps clickCount and never
        // touches physical keyboard state, so emulating the technique keys does nothing at all.
        String ki = code(PAD2, "PadKiMenu.java");
        assertTrue(ki.contains("SelectTechniqueSlotC2S"), "the slot is chosen by packet");
        assertTrue(ki.contains("TechniqueChargeC2S.start"), "and charging starts by packet");
        assertFalse(ki.contains("keyEmulation"), "never by pressing the technique key");
        assertFalse(ki.contains("TECHNIQUE_SLOTS"), "nor by touching the mapping at all");
    }

    @Test
    void holdingTheTriggerIsWhatOverchargesIt() throws IOException {
        // setHolding(true) every tick while the trigger is down, setHolding(false) on release.
        String ki = code(PAD2, "PadKiMenu.java");
        assertTrue(ki.contains("setHolding(true, aim(player))"));
        assertTrue(ki.contains("setHolding(false, aim(player))"));
    }

    @Test
    void lettingGoFiresButStandingDownAbandons() throws IOException {
        // Leaving BT3 mode, opening a screen or unplugging the pad is not a player choosing to
        // shoot, so those clear the charge rather than releasing it.
        String ki = code(PAD2, "PadKiMenu.java");
        int reset = ki.indexOf("static void reset()");
        assertTrue(reset > 0);
        String body = ki.substring(reset, Math.min(ki.length(), reset + 260));
        assertFalse(body.contains("setHolding"), "reset must not fire the shot");
        assertTrue(code(PAD2, "PadBinds.java").contains("PadKiMenu.reset()"));
    }

    @Test
    void aDirectionMustBeReleasedBeforeItChargesAgain() throws IOException {
        // Otherwise holding the d-pad would restart the same technique every tick.
        assertTrue(code(PAD2, "PadKiMenu.java").contains("dpadLatched"));
    }

    @Test
    void theDpadStandsDownWhileATriggerIsHeld() throws IOException {
        // chase / backstep / sonic sway are BASE-layered so the chord machinery withholds them
        // when a trigger raises a ki bar - otherwise picking a technique would also dash.
        String layout = code(PAD2, "PadLayout.java");
        for (String id : new String[]{"chase", "backstep", "sonic_left"}) {
            int row = layout.indexOf("\"" + id + "\"");
            assertTrue(row > 0, id);
            assertTrue(layout.substring(row, Math.min(layout.length(), row + 200))
                            .contains("PadChords.Layer.BASE"),
                    id + " must be BASE-layered, not unlayered");
        }
    }

    @Test
    void theHudRaisesTheBarForAHeldTriggerToo() throws IOException {
        // The keyboard check cannot answer for a pad, so the overlay has to OR this in.
        String overlay = code("src/main/java/net/bullettrain/xenopixelsmod/client",
                "XenoTechniqueHotbarOverlay.java");
        assertTrue(overlay.contains("XenoPadInput.kiBarOffset()"));
    }

    @Test
    void theHudAsksThroughTheControlifyFreeFacade() throws IOException {
        // The HUD must not name a Controlify type: it draws with Controlify absent.
        assertFalse(raw("src/main/java/net/bullettrain/xenopixelsmod/client",
                "XenoTechniqueHotbarOverlay.java").contains("dev.isxander"));
        // And the facade answers -1 unless the rewrite is live, so the old package never shows a
        // bar nothing can drive.
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/pad",
                "XenoPadInput.java").contains("available() && rewrite"));
    }

    @Test
    void flightDescendUsesTheKeyDragonMineZActuallyReads() throws IOException {
        // Measured with javap: FlySkillEvent.handleFlightMovement reads keyUp/keyDown/keyLeft/
        // keyRight, keyJump and keyShift. KeyBinds.DESCEND does not appear in it, so the RT
        // descend row moved the player on the ground and did nothing in the air.
        String layout = code(PAD2, "PadLayout.java");
        int row = layout.indexOf("\"fly_descend\"");
        assertTrue(row > 0, "there must be a flight-descend row");
        String body = layout.substring(row, Math.min(layout.length(), row + 160));
        assertTrue(body.contains("keyShift"), "descend while flying is SHIFT");
        assertTrue(body.contains("LEFT_STICK_BUTTON"), "on L3, not a trigger");
        assertTrue(body.contains("Gate.FLYING"));
    }

    @Test
    void dragonMineZLockOnRidesOurLockOnButton() throws IOException {
        // Reachable by emulation, unlike the technique slots: ClientStatsEvents calls
        // LOCK_ON.consumeClick(), and Controlify's KeyMappingMixin increments clickCount.
        String layout = code(PAD2, "PadLayout.java");
        int row = layout.indexOf("\"dmz_lock_on\"");
        assertTrue(row > 0);
        String body = layout.substring(row, Math.min(layout.length(), row + 160));
        assertTrue(body.contains("KeyBinds.LOCK_ON"));
        assertTrue(body.contains("LEFT_SHOULDER"), "the same button as ours");
    }

    @Test
    void theRadialOpenerIsNeverSuppressedByTheBt3Layout() throws IOException {
        // It would be an unfortunate way to lose access to the menu this change just widened.
        assertTrue(code(PAD2, "PadBinds.java").contains("\"radial_menu\""));
    }
}
