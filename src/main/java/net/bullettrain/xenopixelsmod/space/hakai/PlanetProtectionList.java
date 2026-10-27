package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Canonical, capped protection-list parsing and mutation shared by config load and operator commands. */
public final class PlanetProtectionList {
    public static final int MAX_ENTRIES = 256;

    public enum UpdateResult { UPDATED, UNCHANGED, INVALID_ID, CAP_REACHED }

    public record LoadResult(List<String> ids, int invalidIds, int duplicateIds, int excessIds) {
        public LoadResult { ids = List.copyOf(ids); }
    }

    public record Update(List<String> ids, UpdateResult result) {
        public Update { ids = List.copyOf(ids); }
    }

    private PlanetProtectionList() {}

    public static LoadResult load(Iterable<String> input) {
        var ids = new ArrayList<String>();
        Set<String> seen = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;
        int excess = 0;
        if (input != null) for (String raw : input) {
            String id = normalize(raw);
            if (id == null) {
                invalid++;
                continue;
            }
            if (!seen.add(id)) {
                duplicates++;
                continue;
            }
            if (ids.size() >= MAX_ENTRIES) {
                excess++;
                continue;
            }
            ids.add(id);
        }
        return new LoadResult(ids, invalid, duplicates, excess);
    }

    public static Update update(List<String> current, String rawId, boolean protect) {
        String id = normalize(rawId);
        if (id == null) return new Update(current == null ? List.of() : current, UpdateResult.INVALID_ID);
        var ids = new ArrayList<>(current == null ? List.<String>of() : current);
        if (protect) {
            if (ids.contains(id)) return new Update(ids, UpdateResult.UNCHANGED);
            if (ids.size() >= MAX_ENTRIES) return new Update(ids, UpdateResult.CAP_REACHED);
            ids.add(id);
        } else if (!ids.remove(id)) {
            return new Update(ids, UpdateResult.UNCHANGED);
        }
        return new Update(ids, UpdateResult.UPDATED);
    }

    private static String normalize(String rawId) {
        String id = PlanetHakaiRules.normalizeBodyId(rawId);
        return id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") && ResourceLocation.tryParse(id) != null ? id : null;
    }
}
