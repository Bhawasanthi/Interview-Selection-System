package com.iss.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Site activity shown in Admin > Site Activity: page visits (anonymous visitors and logged-in users)
 * and every login, failed login and logout. Also used to block repeated wrong-password attempts.
 * The site_visit and login_event tables are created on startup if they do not exist.
 */
@Service
public class ActivityService {

    public static final String LOGIN = "LOGIN";
    public static final String FAILED = "FAILED";
    public static final String LOCKED = "LOCKED";
    public static final String LOGOUT = "LOGOUT";

    /** Wrong passwords allowed for one username within LOCK_MINUTES before it is blocked. */
    public static final int MAX_FAILED_PER_USER = 5;
    /** Wrong passwords allowed from one IP address (any usernames) within LOCK_MINUTES. */
    public static final int MAX_FAILED_PER_IP = 30;
    public static final int LOCK_MINUTES = 15;

    /** Visits and login events older than this are deleted. */
    private static final int KEEP_DAYS = 365;

    @Autowired private JdbcTemplate jdbc;

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS site_visit (
                    visit_id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    visited_at  DATETIME     NOT NULL,
                    visitor_id  VARCHAR(36)  NOT NULL,
                    username    VARCHAR(50)  NULL,
                    role        VARCHAR(30)  NULL,
                    path        VARCHAR(255) NOT NULL,
                    ip_address  VARCHAR(45)  NULL,
                    user_agent  VARCHAR(255) NULL,
                    referrer    VARCHAR(255) NULL,
                    KEY idx_visit_time (visited_at),
                    KEY idx_visit_visitor (visitor_id)
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS login_event (
                    event_id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    event_at    DATETIME     NOT NULL,
                    event_type  VARCHAR(10)  NOT NULL,
                    username    VARCHAR(50)  NULL,
                    role        VARCHAR(30)  NULL,
                    ip_address  VARCHAR(45)  NULL,
                    user_agent  VARCHAR(255) NULL,
                    KEY idx_login_time (event_at),
                    KEY idx_login_user (username, event_at)
                )""");
        purgeOld();
    }

    @Scheduled(cron = "0 30 2 * * *")
    void purgeOld() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_DAYS);
        jdbc.update("DELETE FROM site_visit WHERE visited_at < ?", cutoff);
        jdbc.update("DELETE FROM login_event WHERE event_at < ?", cutoff);
    }


    // =====================================================
    // RECORDING
    // =====================================================

    public void recordVisit(String visitorId, String username, String role, String path,
                            String ip, String userAgent, String referrer) {
        jdbc.update("""
                INSERT INTO site_visit (visited_at, visitor_id, username, role, path, ip_address, user_agent, referrer)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                LocalDateTime.now(), visitorId, cut(username, 50), cut(role, 30), cut(path, 255),
                cut(ip, 45), cut(userAgent, 255), cut(referrer, 255));
    }

    public void recordLogin(String type, String username, String role, String ip, String userAgent) {
        jdbc.update("""
                INSERT INTO login_event (event_at, event_type, username, role, ip_address, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)""",
                LocalDateTime.now(), type, cut(username, 50), cut(role, 30), cut(ip, 45), cut(userAgent, 255));
    }

    /** True when this username or IP address has had too many wrong passwords recently. */
    public boolean isLockedOut(String username, String ip) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(LOCK_MINUTES);
        Integer byUser = username == null ? 0 : jdbc.queryForObject("""
                SELECT COUNT(*) FROM login_event
                WHERE username = ? AND event_type = 'FAILED' AND event_at >= ?
                  AND event_at > COALESCE((SELECT MAX(event_at) FROM login_event
                                           WHERE username = ? AND event_type = 'LOGIN'), '1970-01-01')""",
                Integer.class, username, since, username);
        if (byUser != null && byUser >= MAX_FAILED_PER_USER) return true;
        Integer byIp = ip == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_event WHERE ip_address = ? AND event_type = 'FAILED' AND event_at >= ?",
                Integer.class, ip, since);
        return byIp != null && byIp >= MAX_FAILED_PER_IP;
    }


    // =====================================================
    // REPORTING
    // =====================================================

    public record Summary(long viewsToday, long visitorsToday, long views, long visitors,
                          long logins, long failed, long locked, long activeNow, long distinctUsers) {}

    public record DayCount(LocalDate day, long views, long visitors) {}

    public record PageCount(String path, long views, long visitors) {}

    public record LoginRow(LocalDateTime at, String type, String username, String role,
                           String ip, String userAgent) {
        public String device() { return describeAgent(userAgent); }
    }

    public record VisitRow(LocalDateTime at, String username, String role, String path,
                           String ip, String userAgent) {
        public String device() { return describeAgent(userAgent); }
    }

    public Summary summary(int days) {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime from = rangeStart(days);
        return new Summary(
                count("SELECT COUNT(*) FROM site_visit WHERE visited_at >= ?", today),
                count("SELECT COUNT(DISTINCT visitor_id) FROM site_visit WHERE visited_at >= ?", today),
                count("SELECT COUNT(*) FROM site_visit WHERE visited_at >= ?", from),
                count("SELECT COUNT(DISTINCT visitor_id) FROM site_visit WHERE visited_at >= ?", from),
                count("SELECT COUNT(*) FROM login_event WHERE event_type = 'LOGIN' AND event_at >= ?", from),
                count("SELECT COUNT(*) FROM login_event WHERE event_type = 'FAILED' AND event_at >= ?", from),
                count("SELECT COUNT(*) FROM login_event WHERE event_type = 'LOCKED' AND event_at >= ?", from),
                count("SELECT COUNT(DISTINCT visitor_id) FROM site_visit WHERE visited_at >= ?",
                        LocalDateTime.now().minusMinutes(15)),
                count("SELECT COUNT(DISTINCT username) FROM login_event WHERE event_type = 'LOGIN' AND event_at >= ?", from));
    }

    /** One entry per day in the range (days with no visits included as zero). */
    public List<DayCount> daily(int days) {
        Map<LocalDate, DayCount> byDay = new LinkedHashMap<>();
        LocalDate first = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) byDay.put(first.plusDays(i), new DayCount(first.plusDays(i), 0, 0));
        jdbc.query("""
                SELECT DATE(visited_at), COUNT(*), COUNT(DISTINCT visitor_id) FROM site_visit
                WHERE visited_at >= ? GROUP BY DATE(visited_at)""",
                rs -> {
                    LocalDate d = rs.getDate(1).toLocalDate();
                    if (byDay.containsKey(d)) byDay.put(d, new DayCount(d, rs.getLong(2), rs.getLong(3)));
                }, rangeStart(days));
        return new ArrayList<>(byDay.values());
    }

    public List<PageCount> topPages(int days, int limit) {
        return jdbc.query("""
                SELECT path, COUNT(*), COUNT(DISTINCT visitor_id) FROM site_visit
                WHERE visited_at >= ? GROUP BY path ORDER BY COUNT(*) DESC LIMIT ?""",
                (rs, i) -> new PageCount(rs.getString(1), rs.getLong(2), rs.getLong(3)), rangeStart(days), limit);
    }

    /** Page views by who made them: Guest (not logged in) or the role name. */
    public Map<String, Long> viewsByRole(int days) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT COALESCE(role, 'Guest'), COUNT(*) FROM site_visit
                WHERE visited_at >= ? GROUP BY COALESCE(role, 'Guest') ORDER BY COUNT(*) DESC""",
                rs -> { result.put(rs.getString(1), rs.getLong(2)); }, rangeStart(days));
        return result;
    }

    public List<LoginRow> loginEvents(int days, String type, String q, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_at, event_type, username, role, ip_address, user_agent
                FROM login_event WHERE event_at >= ?""");
        List<Object> args = new ArrayList<>(List.of(rangeStart(days)));
        if (type != null && !type.isBlank()) {
            sql.append(" AND event_type = ?");
            args.add(type);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (username LIKE ? OR ip_address LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY event_at DESC, event_id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new LoginRow(
                rs.getTimestamp(1).toLocalDateTime(), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6)), args.toArray());
    }

    public List<VisitRow> recentVisits(int limit) {
        return jdbc.query("""
                SELECT visited_at, username, role, path, ip_address, user_agent
                FROM site_visit ORDER BY visited_at DESC, visit_id DESC LIMIT ?""",
                (rs, i) -> new VisitRow(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), limit);
    }


    // =====================================================
    // HELPERS
    // =====================================================

    private static LocalDateTime rangeStart(int days) {
        return LocalDate.now().minusDays(days - 1L).atStartOfDay();
    }

    private long count(String sql, LocalDateTime since) {
        Long n = jdbc.queryForObject(sql, Long.class, Timestamp.valueOf(since));
        return n == null ? 0 : n;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** A short "Browser on OS" label from a User-Agent header. */
    public static String describeAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("SamsungBrowser") ? "Samsung Internet"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari"
                : ua.contains("curl") || ua.contains("Java") || ua.contains("python") ? "Script"
                : "Other";
        String os = ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : null;
        return os == null ? browser : browser + " on " + os;
    }
}
