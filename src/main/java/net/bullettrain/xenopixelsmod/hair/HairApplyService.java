package net.bullettrain.xenopixelsmod.hair;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairStrand;
import com.dragonminez.common.network.C2S.UpdateCustomHairC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.compat.util.LazyOptional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * PR-D7c — HairMaker Apply via verified DMZ write path
 * {@link UpdateCustomHairC2S#handle} (client send:
 * {@link NetworkHandler#sendToServer} + {@code new UpdateCustomHairC2S}, same shape as
 * {@code HairEditorScreen#syncHairToServer}).
 *
 * <p><b>Replace-current-style:</b> builds a full {@link CustomHair} from the maker document and
 * overwrites that style slot only. Does not load or merge the player's existing hair into the
 * document. Path READY / runtime unverified.
 *
 * <p>Does <b>not</b> write {@code CustomizationManager} {@code hair_style_*} slots.
 */
public final class HairApplyService {
    /** Evidence citation for UI / CHANGELOG. */
    public static final String WRITE_PATH =
            "UpdateCustomHairC2S#handle via NetworkHandler#sendToServer "
                    + "(HairEditorScreen#syncHairToServer pattern)";

    @FunctionalInterface
    public interface Transport {
        void send(int hairIndex, CustomHair hair);
    }

    /** Pure plan of what Apply will send (unit-testable without the network). */
    public record ApplyPlan(
            int hairIndex,
            String styleName,
            String hairName,
            String globalColor,
            List<FacePlan> faces) {
        public ApplyPlan {
            faces = List.copyOf(faces);
        }
    }

    public record FacePlan(String faceName, List<HairStrandModel> strands) {
        public FacePlan {
            Objects.requireNonNull(faceName, "faceName");
            strands = List.copyOf(strands);
        }
    }

    private HairApplyService() {}

    public static int styleIndex(String style) {
        if (style == null) {
            return 0;
        }
        int idx = HairMakerDocument.STYLE_NAMES.indexOf(style);
        return idx < 0 ? 0 : idx;
    }

    /** Build the apply plan from the maker document (current style geometry only). */
    public static ApplyPlan plan(HairMakerDocument document) {
        Objects.requireNonNull(document, "document");
        List<FacePlan> faces = new ArrayList<>(HairMakerDocument.FACE_NAMES.size());
        for (String faceName : HairMakerDocument.FACE_NAMES) {
            List<HairStrandModel> copies = new ArrayList<>();
            for (HairStrandModel strand : document.faceStrands(faceName)) {
                copies.add(strand.copy());
            }
            faces.add(new FacePlan(faceName, copies));
        }
        return new ApplyPlan(
                styleIndex(document.style()),
                document.style(),
                document.name(),
                document.globalColor(),
                faces);
    }

    /** Map a plan to a DMZ {@link CustomHair} using public strand APIs only. */
    public static CustomHair toCustomHair(ApplyPlan plan) {
        Objects.requireNonNull(plan, "plan");
        CustomHair hair = new CustomHair();
        hair.setName(plan.hairName());
        hair.setGlobalColor(plan.globalColor());
        for (FacePlan facePlan : plan.faces()) {
            CustomHair.HairFace face = parseFace(facePlan.faceName());
            if (face == null) {
                continue;
            }
            List<HairStrandModel> models = facePlan.strands();
            for (int i = 0; i < models.size(); i++) {
                HairStrand target = hair.getStrand(face, i);
                if (target == null) {
                    continue;
                }
                copyStrand(models.get(i), target);
            }
        }
        return hair;
    }

    /**
     * Client Apply: mirror onto local {@link Character} (DMZ editor parity) then send
     * {@link UpdateCustomHairC2S} for the current style index only.
     */
    public static void applyClient(HairMakerDocument document) {
        ApplyPlan planned = plan(document);
        CustomHair hair = toCustomHair(planned);
        mirrorLocal(planned.hairIndex(), hair);
        NetworkHandler.sendToServer(new UpdateCustomHairC2S(planned.hairIndex(), hair));
    }

    /**
     * Test / injectable transport — builds {@link CustomHair} and delivers to {@code transport}
     * without touching Minecraft client state or {@link NetworkHandler}.
     */
    public static void applyClient(HairMakerDocument document, Transport transport) {
        Objects.requireNonNull(transport, "transport");
        ApplyPlan planned = plan(document);
        transport.send(planned.hairIndex(), toCustomHair(planned));
    }

    private static void mirrorLocal(int hairIndex, CustomHair hair) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) {
            return;
        }
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        StatsData data = opt.orElse(null);
        if (data == null) {
            return;
        }
        Character character = data.getCharacter();
        if (character == null) {
            return;
        }
        CustomHair copy = hair.copy();
        switch (hairIndex) {
            case 1 -> character.setHairSSJ(copy);
            case 2 -> character.setHairSSJ2(copy);
            case 3 -> character.setHairSSJ3(copy);
            default -> character.setHairBase(copy);
        }
        character.setHairId(0);
    }

    private static CustomHair.HairFace parseFace(String faceName) {
        if (faceName == null || faceName.isBlank()) {
            return null;
        }
        try {
            return CustomHair.HairFace.valueOf(faceName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void copyStrand(HairStrandModel model, HairStrand target) {
        target.setLength(model.length());
        target.setLengthScale(model.lengthScale());
        target.setRotation(model.rotationX(), model.rotationY(), model.rotationZ());
        target.setScale(model.scaleX(), model.scaleY(), model.scaleZ());
        target.setCurve(model.curveX(), model.curveY(), model.curveZ());
        if (model.color() != null) {
            target.setColor(model.color());
        } else {
            target.setColor(null);
        }
        // HairStrand has no public cube setters — verified load path only (cw/ch/cd).
        CompoundTag tag = target.save();
        if (model.cubeWidth() != 2f) {
            tag.putFloat("cw", model.cubeWidth());
        }
        if (model.cubeHeight() != 2f) {
            tag.putFloat("ch", model.cubeHeight());
        }
        if (model.cubeDepth() != 2f) {
            tag.putFloat("cd", model.cubeDepth());
        }
        if (tag.contains("cw") || tag.contains("ch") || tag.contains("cd")) {
            target.load(tag);
        }
    }
}
