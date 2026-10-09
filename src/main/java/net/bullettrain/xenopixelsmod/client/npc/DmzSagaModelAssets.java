package net.bullettrain.xenopixelsmod.client.npc;

import java.util.Map;

/** Pinned DragonMineZ 2.1.3 Saga texture aliases; verified by tools/verify_dmz_saga_model_assets.py. */
final class DmzSagaModelAssets {
    private DmzSagaModelAssets() {}
    private static final Map<String, String> RIGS = Map.ofEntries(
            Map.entry("mini_buu", "saga_buufat"),
            Map.entry("saga_bio_broly_giant", "saga_bio_broly"),
            Map.entry("saga_broly_ssj_restricted", "saga_broly_base"),
            Map.entry("saga_cell_superperfect", "saga_cell_perfect"),
            Map.entry("saga_fgohan_base", "saga_future_gohan"),
            Map.entry("saga_fgohan_ssj", "saga_future_gohanssj"),
            Map.entry("saga_ftrunks_base", "saga_trunks"),
            Map.entry("saga_ftrunks_kid_base", "saga_trunks"),
            Map.entry("saga_ftrunks_kid_ssj", "saga_trunks_ssj"),
            Map.entry("saga_ftrunks_ssg3", "saga_trunks_ssg3"),
            Map.entry("saga_ftrunks_ssj", "saga_trunks_ssj"),
            Map.entry("saga_gohan_end_base", "saga_gohan_end"),
            Map.entry("saga_gohan_end_ultimate", "saga_gohan_end_ssj2"),
            Map.entry("saga_gohan_mid_base", "saga_gohan_mid"),
            Map.entry("saga_gohan_mid_ssj", "saga_gohan_mid"),
            Map.entry("saga_goku_early", "saga_goku"),
            Map.entry("saga_goku_early_noweights", "saga_goku"),
            Map.entry("saga_goku_end_base", "saga_goku"),
            Map.entry("saga_goku_end_ssj", "saga_goku_ssj"),
            Map.entry("saga_goku_end_ssj2", "saga_goku_ssj2"),
            Map.entry("saga_goku_end_ssj3", "saga_goku_ssj3"),
            Map.entry("saga_goku_mid_base", "saga_goku"),
            Map.entry("saga_goku_mid_ssj", "saga_goku_ssj"),
            Map.entry("saga_gotenks_ssj", "saga_gotenks"),
            Map.entry("saga_hirudegarn_incomplete1", "saga_hirudegarn"),
            Map.entry("saga_hirudegarn_incomplete2", "saga_hirudegarn"),
            Map.entry("saga_krillin", "saga_vegeta"),
            Map.entry("saga_mecha_frieza", "saga_frieza_base"),
            Map.entry("saga_metal_cooler", "saga_cooler"),
            Map.entry("saga_nail", "saga_piccolo"),
            Map.entry("saga_ozaruvegeta", "saga_ozaru"),
            Map.entry("saga_piccolo_kami", "saga_piccolo"),
            Map.entry("saga_saibaman1", "saga_saibaman"),
            Map.entry("saga_saibaman2", "saga_saibaman"),
            Map.entry("saga_saibaman3", "saga_saibaman"),
            Map.entry("saga_saibaman4", "saga_saibaman"),
            Map.entry("saga_saibaman5", "saga_saibaman"),
            Map.entry("saga_saibaman6", "saga_saibaman"),
            Map.entry("saga_slug_giant", "saga_slug"),
            Map.entry("saga_super_hirudegarn", "saga_hirudegarn"),
            Map.entry("saga_superbuu_gohan", "saga_superbuu"),
            Map.entry("saga_superbuu_gotenks", "saga_superbuu"),
            Map.entry("saga_superbuu_piccolo", "saga_superbuu"),
            Map.entry("saga_tien_early", "saga_goku"),
            Map.entry("saga_vegeta_end_base", "saga_vegeta"),
            Map.entry("saga_vegeta_end_ssj", "saga_vegeta"),
            Map.entry("saga_vegeta_end_ssj2", "saga_vegeta_ssj2"),
            Map.entry("saga_vegeta_majin", "saga_vegeta_ssg2"),
            Map.entry("saga_vegeta_mid_base", "saga_vegeta"),
            Map.entry("saga_vegeta_mid_ssg2", "saga_vegeta_ssg2"),
            Map.entry("saga_vegeta_mid_ssj", "saga_vegeta"),
            Map.entry("saga_vegeta_namek", "saga_vegeta"));

    static String rig(String skin) {
        String model = RIGS.get(skin);
        if (model != null) return model;
        // DMZ's numbered texture variants use their registered entity's original rig.
        String base = skin.replaceFirst("_[0-9]+$", "");
        return RIGS.get(base);
    }
}
