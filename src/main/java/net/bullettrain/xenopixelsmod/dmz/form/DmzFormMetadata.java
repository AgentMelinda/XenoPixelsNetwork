package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DmzFormMetadata {
    public int schemaVersion = 1;
    public long revision;
    public String race = "";
    public String group = "";
    public String formType = "";
    public String formTypeIcon = "";
    public boolean masterLearningEnabled;
    public boolean anyNativeMaster;
    public boolean buyFromMaster;
    public List<String> nativeMasters = new ArrayList<>();
    public List<TrainerRef> customNpcTrainers = new ArrayList<>();
    public List<Integer> skillCosts = new ArrayList<>(List.of(0));
    /** Player-facing group names by locale; {@code en_us} is the mandatory fallback entry. */
    public Map<String, String> groupNames = new LinkedHashMap<>();
    public Map<String, FormEntry> forms = new LinkedHashMap<>();

    public FormEntry form(String formId) {
        return forms.computeIfAbsent(formId == null ? "" : formId, ignored -> new FormEntry());
    }

    public static final class FormEntry {
        public String icon = "";
        public Map<String, String> names = new LinkedHashMap<>();
    }

    public static final class TrainerRef {
        public String uuid = "";
        public String name = "";
        public String dimension = "";
        /** Designates this trainer as a DMZ skill master; the module predicate still applies. */
        public boolean skillMaster;
        /** Player-facing menu heading; falls back to the group name when blank. */
        public String menuTitle = "";
        /** Locale-to-body-text map for the master menu; {@code en_us} is the fallback entry. */
        public Map<String, String> menuBody = new LinkedHashMap<>();
        /** Form ids this trainer offers; empty means every form in the group. */
        public List<String> offeredForms = new ArrayList<>();
    }
}
