package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;

/**
 * Shared three-column maker chrome: left list, center settings, right live preview.
 *
 * <p>Keeps the preview well in a dedicated right column so it cannot float over the
 * form/style list (Form Maker misalignment regression).
 */
public final class MakerPreviewLayout {
    public static final String BANNER = "banner_top";
    public static final String LIST = "xeno_maker_form_list";
    public static final String SETTINGS = "xeno_maker_form_settings";
    public static final String PREVIEW = "xeno_maker_hair_preview";

    public static final int GAP = 8;
    public static final int BANNER_GAP = 36;
    public static final int FOOTER_GAP = 8;

    private MakerPreviewLayout() {
    }

    /**
     * Lay out banner + list + settings + preview inside a ScaledScreen UI box.
     *
     * @param uiWidth  {@code getUiWidth()}
     * @param uiHeight {@code getUiHeight()}
     * @param listId   atlas id for the left panel (defaults to {@link #LIST})
     * @param settingsId atlas id for the center panel (defaults to {@link #SETTINGS})
     */
    public static Columns compute(int uiWidth, int uiHeight, String listId, String settingsId) {
        String listKey = listId == null || listId.isBlank() ? LIST : listId;
        String settingsKey = settingsId == null || settingsId.isBlank() ? SETTINGS : settingsId;

        int bannerW = XenoAtlasSprites.get(BANNER).width();
        int bannerH = XenoAtlasSprites.get(BANNER).height();
        int listW = XenoAtlasSprites.get(listKey).width();
        int listH = XenoAtlasSprites.get(listKey).height();
        int settingsW = XenoAtlasSprites.get(settingsKey).width();
        int settingsH = XenoAtlasSprites.get(settingsKey).height();
        int previewW = XenoAtlasSprites.get(PREVIEW).width();
        int previewH = XenoAtlasSprites.get(PREVIEW).height();

        int contentW = listW + GAP + settingsW + GAP + previewW;
        int columnH = Math.max(listH, Math.max(settingsH, previewH));
        int contentH = bannerH + BANNER_GAP + columnH + 48;

        int originX = Math.max(8, (uiWidth - contentW) / 2);
        int originY = Math.max(4, (uiHeight - contentH) / 2);
        int bannerX = originX + (contentW - bannerW) / 2;
        int bannerY = originY;

        int listX = originX;
        int listY = bannerY + bannerH + BANNER_GAP;
        int settingsX = listX + listW + GAP;
        int settingsY = listY;
        int previewX = settingsX + settingsW + GAP;
        int previewY = listY;
        int footerY = listY + columnH + FOOTER_GAP;

        return new Columns(
                originX, originY, contentW, contentH,
                bannerX, bannerY, bannerW, bannerH,
                listX, listY, listW, listH,
                settingsX, settingsY, settingsW, settingsH,
                previewX, previewY, previewW, previewH,
                footerY);
    }

    /** Default list/settings atlas ids. */
    public static Columns compute(int uiWidth, int uiHeight) {
        return compute(uiWidth, uiHeight, LIST, SETTINGS);
    }

    /** True when the preview rect does not overlap the list rect (layout invariant). */
    public static boolean previewClearOfList(Columns c) {
        if (c == null) {
            return false;
        }
        return c.previewX >= c.listX + c.listW;
    }

    public record Columns(
            int originX,
            int originY,
            int contentW,
            int contentH,
            int bannerX,
            int bannerY,
            int bannerW,
            int bannerH,
            int listX,
            int listY,
            int listW,
            int listH,
            int settingsX,
            int settingsY,
            int settingsW,
            int settingsH,
            int previewX,
            int previewY,
            int previewW,
            int previewH,
            int footerY) {
    }
}
