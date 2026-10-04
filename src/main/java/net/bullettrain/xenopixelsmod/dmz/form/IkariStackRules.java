package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.Locale;
import java.util.Set;

/**
 * Which forms the Ikari stack may sit on (2026-10-02 owner: "just make it stackable with ssj1 to
 * 3"): Super Saiyan 1 in any grade, mastered Super Saiyan, Super Saiyan 2 and Super Saiyan 3.
 * Nothing else - not base, not Super Saiyan 4, not the god forms.
 *
 * <p>An allow-list in code rather than an {@code incompatibleWith} list in the form file: that
 * list names what is refused, so every form added later, by this mod or a server's own packs,
 * would be allowed until someone remembered to add it.
 */
public final class IkariStackRules {
    /** The stack group and skill id of {@code forms/xenopixels_ikari.json}. */
    public static final String GROUP = "xenopixels_ikari";

    /** DragonMineZ 2.1.3's Saiyan groups: ssgrades holds Super Saiyan 1 and its two grades. */
    private static final String GRADES = "ssgrades";
    private static final String SUPER_SAIYAN = "supersaiyan";
    private static final Set<String> SUPER_SAIYAN_FORMS =
            Set.of("supersaiyanmastered", "supersaiyan2", "supersaiyan3");

    private IkariStackRules() {
    }

    public static boolean isIkari(String stackGroup) {
        return stackGroup != null && GROUP.equalsIgnoreCase(stackGroup);
    }

    /** Whether Ikari may be stacked on this active form; a null or blank form is base. */
    public static boolean allows(String formGroup, String form) {
        if (formGroup == null || form == null || formGroup.isBlank() || form.isBlank()) return false;
        String group = formGroup.toLowerCase(Locale.ROOT);
        if (group.equals(GRADES)) return true;
        return group.equals(SUPER_SAIYAN) && SUPER_SAIYAN_FORMS.contains(form.toLowerCase(Locale.ROOT));
    }
}
