package com.iss.backend.web;

import com.iss.backend.entity.Application;
import com.iss.backend.entity.Student;

import java.util.List;
import java.util.Locale;

/** Server-side search/filter used by the applicant and student lists. */
public final class Filters {

    private Filters() {}

    public static List<Application> applications(List<Application> list, String q, String status, Integer streamId) {
        String query = normalise(q);
        return list.stream()
                .filter(a -> status == null || status.isBlank() || status.equals(a.getStatus()))
                .filter(a -> streamId == null || a.getStream().getStreamId().equals(streamId))
                .filter(a -> query.isEmpty() || matches(a.getStudent(), query))
                .toList();
    }

    public static List<Student> students(List<Student> list, String q) {
        String query = normalise(q);
        return list.stream()
                .filter(s -> query.isEmpty() || matches(s, query))
                .toList();
    }

    private static boolean matches(Student s, String query) {
        return contains(s.getFullName(), query)
                || contains(s.getEmail(), query)
                || contains(s.getIndexNumber(), query)
                || contains(s.getNic(), query)
                || contains(s.getUsername(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalise(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }
}
