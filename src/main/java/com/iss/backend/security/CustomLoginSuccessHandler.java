package com.iss.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.service.ActivityService;
import com.iss.backend.service.PasswordResetService;
import com.iss.backend.web.VisitTrackingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    // @Lazy: SecurityConfig -> this handler -> PasswordResetService -> PasswordEncoder (in SecurityConfig)
    @Autowired @Lazy private PasswordResetService passwordResetService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ActivityService activityService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        activityService.recordLogin(ActivityService.LOGIN, authentication.getName(),
                VisitTrackingFilter.roleLabel(authentication), request.getRemoteAddr(), request.getHeader("User-Agent"));

        String role = authentication.getAuthorities().stream()
                .findFirst().map(GrantedAuthority::getAuthority).orElse("");

        String redirectUrl = switch (role) {
            case "ROLE_ADMIN" -> "/admin/dashboard";
            case "ROLE_SECTION_HEAD" -> "/sectionhead/dashboard";
            case "ROLE_INTERVIEW_PANEL" -> "/panel/dashboard";
            case "ROLE_STUDENT" -> "/student/dashboard";
            default -> "/";
        };

        // Logged in with a temporary password: go straight to the profile to choose a new one
        if (usingTemporaryPassword(authentication.getName()) && !"/".equals(redirectUrl)) {
            redirectUrl = redirectUrl.replace("/dashboard", "/profile") + "?changePassword";
        }

        response.sendRedirect(redirectUrl);
    }

    private boolean usingTemporaryPassword(String username) {
        User staff = userRepository.findByUserName(username);
        if (staff != null) return passwordResetService.mustChangeStaff(staff.getUserId());
        Student student = studentRepository.findByUsername(username);
        return student != null && passwordResetService.mustChangeStudent(student.getStudentId());
    }
}