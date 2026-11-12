package com.iss.backend.web;

import java.util.List;

/**
 * The logged-in person, as shown in the sidebar and top bar of every workspace.
 * role is one of: admin, sectionhead, panel, student.
 */
public record CurrentUser(
        String name,
        String username,
        String role,
        String roleLabel,
        String streamName,
        List<Alert> alerts) {

    public record Alert(String icon, String text, String url) {}

    public String home() {
        return "/" + role + "/dashboard";
    }

    public String initials() {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    public String workspaceLabel() {
        return switch (role) {
            case "admin" -> "Administration";
            case "sectionhead" -> "Section Head Workspace";
            case "panel" -> "Interview Panel Workspace";
            default -> "Student Portal";
        };
    }
}
