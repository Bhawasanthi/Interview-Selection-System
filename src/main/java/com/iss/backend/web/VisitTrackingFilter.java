package com.iss.backend.web;

import com.iss.backend.service.ActivityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records each page a person opens (not images, scripts, downloads or errors) for Admin > Site Activity.
 * Visitors are told apart by a random id in the iss_vid cookie, so "unique visitors" counts browsers.
 * Runs after Spring Security, so the logged-in user (if any) is known.
 */
@Component
public class VisitTrackingFilter extends OncePerRequestFilter {

    public static final String VISITOR_COOKIE = "iss_vid";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 365;
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f-]{36}");
    private static final Pattern BOT = Pattern.compile("(?i)bot|crawl|spider|slurp|preview|monitor");
    private static final Logger log = LoggerFactory.getLogger(VisitTrackingFilter.class);

    @Autowired private ActivityService activityService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"GET".equals(request.getMethod())
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/error") || path.equals("/favicon.ico") || path.contains("/export");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String visitorId = visitorId(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        chain.doFilter(request, response);

        String contentType = response.getContentType();
        String agent = request.getHeader("User-Agent");
        if (response.getStatus() != HttpServletResponse.SC_OK
                || contentType == null || !contentType.startsWith("text/html")
                || agent == null || BOT.matcher(agent).find()) {   // no User-Agent = a script or test, not a person
            return;
        }

        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        try {
            activityService.recordVisit(visitorId,
                    loggedIn ? auth.getName() : null,
                    loggedIn ? roleLabel(auth) : null,
                    request.getRequestURI().substring(request.getContextPath().length()),
                    request.getRemoteAddr(), agent, request.getHeader("Referer"));
        } catch (RuntimeException e) {
            // Tracking must never break the page itself
            log.warn("Could not record site visit: {}", e.getMessage());
        }
    }

    private static String visitorId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (VISITOR_COOKIE.equals(c.getName()) && UUID_PATTERN.matcher(c.getValue()).matches()) {
                    return c.getValue();
                }
            }
        }
        String id = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(VISITOR_COOKIE, id);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return id;
    }

    /** "Admin", "Section Head", "Interview Panel" or "Student" from the user's granted role. */
    public static String roleLabel(Authentication auth) {
        String authority = auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority()).orElse("");
        return switch (authority) {
            case "ROLE_ADMIN" -> "Admin";
            case "ROLE_SECTION_HEAD" -> "Section Head";
            case "ROLE_INTERVIEW_PANEL" -> "Interview Panel";
            case "ROLE_STUDENT" -> "Student";
            default -> null;
        };
    }
}
