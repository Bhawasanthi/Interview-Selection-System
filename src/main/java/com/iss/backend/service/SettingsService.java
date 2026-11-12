package com.iss.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System-wide settings managed from Admin > System Settings.
 * Stored as key/value rows in the system_setting table, which is created on
 * startup if it does not exist (so no manual database change is needed).
 */
@Service
public class SettingsService {

    // General
    public static final String ACADEMIC_YEAR = "academic_year";
    public static final String LETTER_EXAM_YEAR = "letter_exam_year";
    public static final String SITE_NOTICE = "site_notice";

    // Admissions
    public static final String REGISTRATION_OPEN = "registration_open";
    public static final String APPLICATIONS_OPEN = "applications_open";
    public static final String APPLICATION_DEADLINE = "application_deadline";

    // Interviews
    public static final String DEFAULT_VENUE = "default_interview_venue";
    public static final String DEFAULT_TIME = "default_interview_time";

    @Autowired private JdbcTemplate jdbc;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS system_setting (
                    setting_key   VARCHAR(100)  NOT NULL PRIMARY KEY,
                    setting_value VARCHAR(2000) NULL
                )""");
        reload();
    }

    private void reload() {
        cache.clear();
        jdbc.query("SELECT setting_key, setting_value FROM system_setting",
                rs -> { cache.put(rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2)); });
    }

    public String get(String key, String fallback) {
        String v = cache.get(key);
        return v == null ? fallback : v;
    }

    public void saveAll(Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue().trim();
            int updated = jdbc.update("UPDATE system_setting SET setting_value = ? WHERE setting_key = ?", value, e.getKey());
            if (updated == 0) {
                jdbc.update("INSERT INTO system_setting (setting_key, setting_value) VALUES (?, ?)", e.getKey(), value);
            }
        }
        reload();
    }


    // =====================================================
    // TYPED ACCESSORS (with defaults matching the original behaviour)
    // =====================================================

    public int academicYear() {
        try {
            return Integer.parseInt(get(ACADEMIC_YEAR, ""));
        } catch (NumberFormatException e) {
            return Year.now().getValue();
        }
    }

    /** The year printed in "Interview Selection for G.C.E. Advanced Level ____". */
    public String letterExamYear() {
        return get(LETTER_EXAM_YEAR, "2022");
    }

    public String siteNotice() {
        return get(SITE_NOTICE, "");
    }

    public boolean registrationOpen() {
        return !"false".equals(get(REGISTRATION_OPEN, "true"));
    }

    public boolean applicationsOpenFlag() {
        return !"false".equals(get(APPLICATIONS_OPEN, "true"));
    }

    public LocalDate applicationDeadline() {
        String v = get(APPLICATION_DEADLINE, "");
        try {
            return v.isBlank() ? null : LocalDate.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** Applications are accepted only while switched on and before the deadline has passed. */
    public boolean applicationsOpen() {
        LocalDate deadline = applicationDeadline();
        return applicationsOpenFlag() && (deadline == null || !LocalDate.now().isAfter(deadline));
    }

    public String defaultVenue() {
        return get(DEFAULT_VENUE, "");
    }

    public String defaultTime() {
        return get(DEFAULT_TIME, "09:00");
    }

    // =====================================================
    // ADMISSION DETAILS (set by each section head for their stream)
    // =====================================================

    /** Date (yyyy-MM-dd), time (HH:mm) and venue of the admission for a stream; blank when not set. */
    public Map<String, String> admissionDetails(int streamId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", get("admission." + streamId + ".date", ""));
        m.put("time", get("admission." + streamId + ".time", ""));
        m.put("venue", get("admission." + streamId + ".venue", ""));
        return m;
    }

    public boolean hasAdmissionDetails(int streamId) {
        Map<String, String> d = admissionDetails(streamId);
        return !d.get("date").isBlank() && !d.get("time").isBlank() && !d.get("venue").isBlank();
    }

    public void saveAdmissionDetails(int streamId, String date, String time, String venue) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("admission." + streamId + ".date", date);
        values.put("admission." + streamId + ".time", time);
        values.put("admission." + streamId + ".venue", venue);
        saveAll(values);
    }

    /** Every setting with its current value, for the settings page. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("academicYear", academicYear());
        m.put("letterExamYear", letterExamYear());
        m.put("siteNotice", siteNotice());
        m.put("registrationOpen", registrationOpen());
        m.put("applicationsOpenFlag", applicationsOpenFlag());
        m.put("applicationDeadline", applicationDeadline());
        m.put("applicationsOpen", applicationsOpen());
        m.put("defaultVenue", defaultVenue());
        m.put("defaultTime", defaultTime());
        return m;
    }
}
