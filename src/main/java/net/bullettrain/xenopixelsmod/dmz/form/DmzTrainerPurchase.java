package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.Locale;

/**
 * Server-side decision for one "learn this form type from a trainer" request.
 *
 * <p>Kept free of Minecraft types so the eligibility rules — race gating, DragonMineZ's
 * {@code buyFromMaster} flag, the configured maximum level, and the TP price for the next level —
 * are exercised directly by tests rather than only through a live purchase.
 */
public record DmzTrainerPurchase(boolean allowed, int cost, int nextLevel, String message) {
    private static DmzTrainerPurchase denied(String message) {
        return new DmzTrainerPurchase(false, 0, 0, message);
    }

    /**
     * @param metadata      the form group the trainer offers
     * @param playerRace    the buyer's DragonMineZ race, or {@code null} when they have none
     * @param currentLevel  the buyer's current skill level in {@code metadata.formType}
     * @param trainingPoints the buyer's available TP; DragonMineZ stores this as a float
     */
    public static DmzTrainerPurchase evaluate(DmzFormMetadata metadata, String playerRace,
                                              int currentLevel, float trainingPoints) {
        if (metadata == null) return denied("This trainer has nothing to teach");
        String formType = metadata.formType == null ? "" : metadata.formType;
        if (!metadata.masterLearningEnabled) {
            return denied(formType + " cannot be learned from a master");
        }
        if (metadata.race != null && !metadata.race.isBlank()
                && !metadata.race.equalsIgnoreCase(playerRace == null ? "" : playerRace)) {
            return denied("This form skill is restricted to " + metadata.race.toLowerCase(Locale.ROOT));
        }
        int level = Math.max(0, currentLevel);
        if (level == 0 && !metadata.buyFromMaster) {
            return denied(formType + " cannot be purchased from a master");
        }
        if (metadata.skillCosts == null || level >= metadata.skillCosts.size()) {
            return denied(formType + " is already at its configured maximum level");
        }
        Integer price = metadata.skillCosts.get(level);
        int cost = Math.max(0, price == null ? 0 : price);
        if (trainingPoints < cost) {
            return denied("Not enough TP: " + cost + " required");
        }
        return new DmzTrainerPurchase(true, cost, level + 1,
                formType + " upgraded to level " + (level + 1));
    }
}
