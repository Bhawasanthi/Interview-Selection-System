package com.iss.backend.web;

import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.service.PasswordResetService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Someone who logged in with a temporary password (given by the administrator) can only use their
 * profile page until they choose a new password; every other workspace page sends them there.
 */
@Component
public class TemporaryPasswordFilter extends OncePerRequestFilter {

    @Autowired @Lazy private PasswordResetService passwordResetService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/admin/") || path.startsWith("/sectionhead/") || path.startsWith("/panel/")
                || path.startsWith("/student/") || path.equals("/register"))
                || path.matches("/(admin|sectionhead|panel|student)/profile(/.*)?");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String profile = profilePath(auth);
            if (profile != null && usingTemporaryPassword(auth.getName())) {
                response.sendRedirect(request.getContextPath() + profile + "?changePassword");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String profilePath(Authentication auth) {
        String role = VisitTrackingFilter.roleLabel(auth);
        if (role == null) return null;
        return switch (role) {
            case "Admin" -> "/admin/profile";
            case "Section Head" -> "/sectionhead/profile";
            case "Interview Panel" -> "/panel/profile";
            default -> "/student/profile";
        };
    }

    private boolean usingTemporaryPassword(String username) {
        User staff = userRepository.findByUserName(username);
        if (staff != null) return passwordResetService.mustChangeStaff(staff.getUserId());
        Student student = studentRepository.findByUsername(username);
        return student != null && passwordResetService.mustChangeStudent(student.getStudentId());
    }
}
