package com.iss.backend.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extra-curricular / co-curricular achievement levels ticked on the application form.
 * Stored in student.extra_curricular_activities as the labels joined with ", ".
 * The codes match the EXTRACURRICULAR options in the marking scale.
 */
public final class ExtraCurricular {

    public static final String NONE = "NONE";

    /** Code -> label, in the order shown on the form. */
    public static final Map<String, String> LEVELS = new LinkedHashMap<>();
    static {
        LEVELS.put("ALL_ISLAND", "All Island");
        LEVELS.put("PROVINCIAL", "Provincial");
        LEVELS.put("INTERNATIONAL", "International");
        LEVELS.put(NONE, "None");
    }

    /** Highest level first - used to pre-select the level on the interview marking form. */
    private static final List<String> RANK = List.of("INTERNATIONAL", "ALL_ISLAND", "PROVINCIAL", NONE);

    private ExtraCurricular() {}

    /**
     * Validates the ticked codes and turns them into the stored text.
     *
     * @return the text to store, or null if the selection is invalid
     */
    public static String toStoredText(List<String> codes) {
        if (codes == null || codes.isEmpty()) return null;
        List<String> clean = codes.stream().distinct().toList();
        if (!LEVELS.keySet().containsAll(clean)) return null;
        if (clean.contains(NONE) && clean.size() > 1) return null;   // "None" cannot be combined

        List<String> labels = new ArrayList<>();
        LEVELS.forEach((code, label) -> { if (clean.contains(code)) labels.add(label); });
        return String.join(", ", labels);
    }

    /** The highest level mentioned in the stored text, or NONE. Works with older free-text answers too. */
    public static String highestLevel(String stored) {
        if (stored == null || stored.isBlank()) return NONE;
        String text = stored.toLowerCase();
        for (String code : RANK) {
            String label = LEVELS.get(code).toLowerCase();
            if (!NONE.equals(code) && (text.contains(label) || text.contains(label.replace(" ", "-")))) return code;
        }
        return NONE;
    }
}
