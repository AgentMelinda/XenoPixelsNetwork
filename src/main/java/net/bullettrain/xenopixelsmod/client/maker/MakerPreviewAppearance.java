package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.hair.HairApplyService;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.minecraft.world.entity.player.Player;

/**
 * Temporary Character mutations for maker live preview. Apply before
 * {@code EntityPreviewRenderContext.renderEntityInInventory}; restore in finally.
 *
 * <p>Never sends C2S. Uses verified DMZ Character setters only
 * ({@code NpcFullDmzRenderer#syncCharacter} / {@link HairApplyService} mirror path).
 */
public final class MakerPreviewAppearance {
    private com.dragonminez.common.config.FormConfig.FormData formData;
    private String race;
    private Integer hairIndex;
    private CustomHair hair;
    private String hairColor;
    private String bodyColor;
    private String bodyColor2;
    private String bodyColor3;
    private String eye1Color;
    private String eye2Color;
    private String auraColor;
    private Integer bodyType;
    private Integer eyesType;
    private Integer noseType;
    private Integer mouthType;
    private Integer tattooType;
    private String gender;
    private String formGroup;
    private String formId;
    private boolean clearForm;

    public MakerPreviewAppearance() {
    }

    /** Preview-only tint for the selected strand (never sent on Apply). */
    public static final String SEGMENT_HIGHLIGHT_COLOR = "#00E676";

    public static MakerPreviewAppearance fromHair(HairMakerDocument document) {
        MakerPreviewAppearance a = new MakerPreviewAppearance();
        if (document == null) {
            return a;
        }
        HairApplyService.ApplyPlan plan = HairApplyService.plan(document);
        a.activeForm(null, null);
        a.hairIndex = plan.hairIndex();
        a.hair = HairApplyService.toCustomHair(plan);
        tintSelectedStrand(a.hair, document.face(), document.strandIndex());
        a.hairColor = document.globalColor();
        return a;
    }

    /** Bright green on the selected cube so the viewport shows which strand is active. */
    static void tintSelectedStrand(CustomHair hair, String faceName, int strandIndex) {
        if (hair == null || faceName == null || strandIndex < 0) {
            return;
        }
        CustomHair.HairFace face;
        try {
            face = CustomHair.HairFace.valueOf(faceName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        var strand = hair.getStrand(face, strandIndex);
        if (strand == null || strand.getLength() <= 0) {
            return;
        }
        strand.setColor(SEGMENT_HIGHLIGHT_COLOR);
    }

    public MakerPreviewAppearance formData(com.dragonminez.common.config.FormConfig.FormData data) {
        this.formData = data;
        return this;
    }

    public com.dragonminez.common.config.FormConfig.FormData formData() { return formData; }

    public MakerPreviewAppearance race(String race) {
        this.race = blankToNull(race);
        return this;
    }

    public MakerPreviewAppearance hair(CustomHair hair) {
        this.hair = hair;
        return this;
    }

    public MakerPreviewAppearance hairColor(String hex) {
        this.hairColor = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance bodyColor(String hex) {
        this.bodyColor = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance bodyColor2(String hex) {
        this.bodyColor2 = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance bodyColor3(String hex) {
        this.bodyColor3 = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance eye1Color(String hex) {
        this.eye1Color = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance eye2Color(String hex) {
        this.eye2Color = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance auraColor(String hex) {
        this.auraColor = blankToNull(hex);
        return this;
    }

    public MakerPreviewAppearance bodyType(Integer type) {
        this.bodyType = type;
        return this;
    }

    public MakerPreviewAppearance eyesType(Integer type) {
        this.eyesType = type;
        return this;
    }

    public MakerPreviewAppearance noseType(Integer type) {
        this.noseType = type;
        return this;
    }

    public MakerPreviewAppearance mouthType(Integer type) {
        this.mouthType = type;
        return this;
    }

    public MakerPreviewAppearance tattooType(Integer type) {
        this.tattooType = type;
        return this;
    }

    public MakerPreviewAppearance gender(String gender) {
        this.gender = blankToNull(gender);
        return this;
    }

    public MakerPreviewAppearance activeForm(String group, String form) {
        if (group == null || group.isBlank() || form == null || form.isBlank()) {
            this.clearForm = true;
            this.formGroup = null;
            this.formId = null;
        } else {
            this.clearForm = false;
            this.formGroup = group.trim();
            this.formId = form.trim();
        }
        return this;
    }

    public boolean isEmpty() {
        return race == null
                && hair == null
                && hairColor == null
                && bodyColor == null
                && bodyColor2 == null
                && bodyColor3 == null
                && eye1Color == null
                && eye2Color == null
                && auraColor == null
                && bodyType == null
                && eyesType == null
                && noseType == null
                && mouthType == null
                && tattooType == null
                && gender == null
                && !clearForm
                && formGroup == null;
    }

    /** Snapshot current Character fields that this appearance may overwrite. */
    public Snapshot snapshot(Player player) {
        return snapshotCharacter(characterOf(player));
    }

    Snapshot snapshotCharacter(Character c) {
        if (c == null) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(
                c.getRace(),
                c.getHairId(),
                copyHair(c.getHairBase()),
                copyHair(c.getHairSSJ()),
                copyHair(c.getHairSSJ2()),
                copyHair(c.getHairSSJ3()),
                c.getHairColor(),
                c.getBodyColor(),
                c.getBodyColor2(),
                c.getBodyColor3(),
                c.getEye1Color(),
                c.getEye2Color(),
                c.getAuraColor(),
                c.getBodyType(),
                c.getEyesType(),
                c.getNoseType(),
                c.getMouthType(),
                c.getTattooType(),
                c.getGender(),
                c.hasActiveForm() ? c.getActiveFormGroup() : null,
                c.hasActiveForm() ? c.getActiveForm() : null,
                c.isRenderHairBase(),
                c.hasActiveStackForm() ? c.getActiveStackFormGroup() : null,
                c.hasActiveStackForm() ? c.getActiveStackForm() : null);
    }

    /** Apply this appearance onto the player's Character (preview only). */
    public void apply(Player player) {
        applyCharacter(characterOf(player));
    }

    void applyCharacter(Character c) {
        if (c == null || isEmpty()) {
            return;
        }
        if (race != null) {
            c.setRace(race);
        }
        if (hair != null) {
            CustomHair copy = hair.copy();
            // HairEditorScreen preview always writes the working style into hairBase so the
            // model shows it even when the player is not transformed. Apply still uses hairIndex.
            c.setHairBase(copy);
            // Custom hair only draws when hairId is 0 (HairManager.getEffectiveHair).
            c.setHairId(0);
        }
        if (hairColor != null) {
            c.setHairColor(hairColor);
        }
        if (bodyColor != null) {
            c.setBodyColor(bodyColor);
        }
        if (bodyColor2 != null) {
            c.setBodyColor2(bodyColor2);
        }
        if (bodyColor3 != null) {
            c.setBodyColor3(bodyColor3);
        }
        if (eye1Color != null) {
            c.setEye1Color(eye1Color);
        }
        if (eye2Color != null) {
            c.setEye2Color(eye2Color);
        }
        if (auraColor != null) {
            c.setAuraColor(auraColor);
        }
        if (bodyType != null) {
            c.setBodyType(bodyType);
        }
        if (eyesType != null) {
            c.setEyesType(eyesType);
        }
        if (noseType != null) {
            c.setNoseType(noseType);
        }
        if (mouthType != null) {
            c.setMouthType(mouthType);
        }
        if (tattooType != null) {
            c.setTattooType(tattooType);
        }
        if (gender != null) {
            c.setGender(gender);
        }
        if (clearForm || formData != null) c.clearActiveStackForm();
        if (clearForm) {
            c.clearActiveForm();
        } else if (formGroup != null && formId != null) {
            c.setActiveForm(formGroup, formId);
        }
    }

    public void restore(Player player, Snapshot snapshot) {
        restoreCharacter(characterOf(player), snapshot);
    }

    void restoreCharacter(Character c, Snapshot snapshot) {
        if (c == null || snapshot == null || snapshot == Snapshot.EMPTY) {
            return;
        }
        c.setRace(snapshot.race);
        c.setHairId(snapshot.hairId);
        c.setHairBase(copyHair(snapshot.hairBase));
        c.setHairSSJ(copyHair(snapshot.hairSsj));
        c.setHairSSJ2(copyHair(snapshot.hairSsj2));
        c.setHairSSJ3(copyHair(snapshot.hairSsj3));
        c.setRenderHairBase(snapshot.renderHairBase);
        if (snapshot.hairColor != null) {
            c.setHairColor(snapshot.hairColor);
        }
        if (snapshot.bodyColor != null) {
            c.setBodyColor(snapshot.bodyColor);
        }
        if (snapshot.bodyColor2 != null) {
            c.setBodyColor2(snapshot.bodyColor2);
        }
        if (snapshot.bodyColor3 != null) {
            c.setBodyColor3(snapshot.bodyColor3);
        }
        if (snapshot.eye1Color != null) {
            c.setEye1Color(snapshot.eye1Color);
        }
        if (snapshot.eye2Color != null) {
            c.setEye2Color(snapshot.eye2Color);
        }
        if (snapshot.auraColor != null) {
            c.setAuraColor(snapshot.auraColor);
        }
        c.setBodyType(snapshot.bodyType);
        c.setEyesType(snapshot.eyesType);
        c.setNoseType(snapshot.noseType);
        c.setMouthType(snapshot.mouthType);
        c.setTattooType(snapshot.tattooType);
        if (snapshot.gender != null) {
            c.setGender(snapshot.gender);
        }
        if (snapshot.formGroup == null || snapshot.formId == null) {
            c.clearActiveForm();
        } else {
            c.setActiveForm(snapshot.formGroup, snapshot.formId);
        }
        if (snapshot.stackGroup == null || snapshot.stackId == null) c.clearActiveStackForm();
        else c.setActiveStackForm(snapshot.stackGroup, snapshot.stackId);
    }

    public static Character characterOf(Player player) {
        if (player == null) {
            return null;
        }
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        StatsData data = opt.orElse(null);
        return data == null ? null : data.getCharacter();
    }

    private static CustomHair copyHair(CustomHair hair) {
        return hair == null ? null : hair.copy();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Snapshot(
            String race,
            int hairId,
            CustomHair hairBase,
            CustomHair hairSsj,
            CustomHair hairSsj2,
            CustomHair hairSsj3,
            String hairColor,
            String bodyColor,
            String bodyColor2,
            String bodyColor3,
            String eye1Color,
            String eye2Color,
            String auraColor,
            int bodyType,
            int eyesType,
            int noseType,
            int mouthType,
            int tattooType,
            String gender,
            String formGroup,
            String formId,
            boolean renderHairBase,
            String stackGroup,
            String stackId) {
        public static final Snapshot EMPTY = new Snapshot(
                null, 0, null, null, null, null,
                null, null, null, null, null, null, null,
                0, 0, 0, 0, 0,
                null, null, null, false, null, null);
    }
}
