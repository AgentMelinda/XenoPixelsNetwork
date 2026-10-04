package net.bullettrain.xenopixelsmod.features.progression;

import java.util.List;

/**
 * Mail attached to a quest reward.
 *
 * <p>This mod has no mailbox. The items are given with the rest of the reward, and the subject
 * and body are spoken in the completion bubble.
 */
public record QuestMail(String sender, String subject, String body, List<QuestReward.ItemGrant> items) {

    public static final QuestMail NONE = new QuestMail("", "", "", List.of());

    public QuestMail {
        sender = sender == null ? "" : sender.trim();
        subject = subject == null ? "" : subject.trim();
        body = body == null ? "" : body;
        items = List.copyOf(items == null ? List.of() : items);
    }

    public boolean isEmpty() {
        return sender.isEmpty() && subject.isEmpty() && body.isBlank() && items.isEmpty();
    }

    /** What the completion bubble says after the quest's own completion line. */
    public String spoken() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (!sender.isEmpty() || !subject.isEmpty()) {
            out.append(sender.isEmpty() ? "Mail" : sender);
            if (!subject.isEmpty()) {
                out.append(": ").append(subject);
            }
        }
        if (!body.isBlank()) {
            if (out.length() > 0) {
                out.append(" — ");
            }
            out.append(body.trim());
        }
        return out.toString();
    }
}
