package com.iss.backend.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;

/**
 * Input checks shared by every form. Lengths match the database columns: MySQL here is not in
 * strict mode, so a value that is too long would otherwise be cut off silently.
 * Each check returns an error message, or null when the value is fine.
 */
public final class Validation {

    public static final Pattern PHONE = Pattern.compile("\\d{10}");
    public static final Pattern NIC = Pattern.compile("\\d{12}");
    public static final Pattern DIGITS = Pattern.compile("\\d{1,20}");
    public static final Pattern NAME = Pattern.compile("[\\p{L} .]+");
    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    private Validation() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Required text no longer than maxLength. */
    public static String text(String value, String label, int maxLength) {
        if (isBlank(value)) return label + " is required.";
        if (value.trim().length() > maxLength) return label + " must be " + maxLength + " characters or fewer.";
        return null;
    }

    /** Optional text no longer than maxLength. */
    public static String optionalText(String value, String label, int maxLength) {
        return isBlank(value) ? null : text(value, label, maxLength);
    }

    /** Person's name: letters, spaces and full stops. */
    public static String name(String value, String label, int maxLength) {
        String error = text(value, label, maxLength);
        if (error != null) return error;
        return NAME.matcher(value.trim()).matches() ? null : label + " can contain letters, spaces and full stops only.";
    }

    public static String email(String value, String label) {
        if (isBlank(value)) return label + " is required.";
        if (value.trim().length() > 150) return label + " must be 150 characters or fewer.";
        return EMAIL.matcher(value.trim()).matches() ? null : "Enter a valid " + label.toLowerCase() + ".";
    }

    public static String username(String value) {
        if (isBlank(value)) return "Username is required.";
        return USERNAME.matcher(value.trim()).matches() ? null
                : "Username must be 3-50 characters: letters, numbers, dots, dashes or underscores (no spaces).";
    }

    public static String password(String value, int minLength) {
        if (value == null || value.length() < minLength) return "Passwords must be at least " + minLength + " characters.";
        if (value.length() > 100) return "Passwords must be 100 characters or fewer.";
        return null;
    }

    public static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalTime time(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** The first non-null error, or null. */
    public static String first(String... errors) {
        for (String e : errors) if (e != null) return e;
        return null;
    }
}
