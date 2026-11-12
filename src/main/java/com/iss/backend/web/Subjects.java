package com.iss.backend.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * G.C.E. O/L subject names. The application form and the section head's
 * selection conditions both use these, so auto selection can match results
 * to conditions by name.
 */
public final class Subjects {

    public static final List<String> CORE_SUBJECTS = Arrays.asList(
            "Sinhala Language & Literature", "Religion", "Mathematics",
            "Science", "History", "English"
    );

    public static final List<String> CATEGORY_1_SUBJECTS = Arrays.asList(
            "Business & Accounting Studies", "Geography", "Civic Education",
            "Entrepreneurship Studies", "Second Language (Sinhala)", "Second Language (Tamil)",
            "Pali", "Sanskrit", "French", "German", "Hindi", "Japanese",
            "Arabic", "Korean", "Chinese", "Russian"
    );

    public static final List<String> CATEGORY_2_SUBJECTS = Arrays.asList(
            "Music (Oriental)", "Music (Western)", "Music (Carnatic)", "Art",
            "Dancing (Oriental)", "Dancing (Bharata)",
            "Appreciation of English Literary Texts", "Appreciation of Sinhala Literary Texts",
            "Appreciation of Tamil Literary Texts", "Appreciation of Arabic Literary Texts",
            "Drama and Theatre (Sinhala)", "Drama and Theatre (Tamil)", "Drama and Theatre (English)"
    );

    public static final List<String> CATEGORY_3_SUBJECTS = Arrays.asList(
            "Information & Communication Technology", "Agriculture & Food Technology",
            "Aquatic Bioresources Technology", "Art & Crafts", "Home Economics",
            "Health & Physical Education", "Communication & Media Studies",
            "Design & Construction Technology", "Design & Mechanical Technology",
            "Design, Electrical & Electronic Technology",
            "Electronic Writing & Shorthand (Sinhala)", "Electronic Writing & Shorthand (Tamil)",
            "Electronic Writing & Shorthand (English)"
    );

    public static final List<String> GRADES = List.of("A", "B", "C", "S", "W");

    public static List<String> allOlSubjects() {
        List<String> all = new ArrayList<>(CORE_SUBJECTS);
        all.addAll(CATEGORY_1_SUBJECTS);
        all.addAll(CATEGORY_2_SUBJECTS);
        all.addAll(CATEGORY_3_SUBJECTS);
        return all;
    }

    private Subjects() {}
}
