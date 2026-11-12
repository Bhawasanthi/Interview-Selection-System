package com.iss.backend.web;

import com.iss.backend.entity.Application;
import com.iss.backend.entity.Notification;
import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.ApplicationRepository;
import com.iss.backend.repository.InterviewRepository;
import com.iss.backend.repository.NotificationRepository;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.service.EmailService;
import com.iss.backend.service.PasswordResetService;
import com.iss.backend.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds "me" (the logged-in person, with their notification-bell items) and
 * "currentYear" to every page, so the shared layouts can render the sidebar,
 * top bar and public header without each controller repeating it.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SettingsService settingsService;
    @Autowired private EmailService emailService;
    @Autowired private PasswordResetService passwordResetService;

    /** The admission year chosen in System Settings (defaults to the calendar year). */
    @ModelAttribute("currentYear")
    public int currentYear() {
        return settingsService.academicYear();
    }

    /** Announcement shown on the home page and in every workspace (blank = hidden). */
    @ModelAttribute("siteNotice")
    public String siteNotice() {
        return settingsService.siteNotice();
    }

    /** Open password reset requests - shown as a badge on the admin sidebar (0 for everyone else). */
    @ModelAttribute("passwordRequestCount")
    public int passwordRequestCount(Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? passwordResetService.openRequestCount() : 0;
    }

    /** True while the logged-in person is still using a temporary (reset) password. */
    @ModelAttribute("mustChangePassword")
    public boolean mustChangePassword(Principal principal) {
        if (principal == null) return false;
        User staff = userRepository.findByUserName(principal.getName());
        if (staff != null) return passwordResetService.mustChangeStaff(staff.getUserId());
        Student student = studentRepository.findByUsername(principal.getName());
        return student != null && passwordResetService.mustChangeStudent(student.getStudentId());
    }

    @ModelAttribute("registrationOpen")
    public boolean registrationOpen() {
        return settingsService.registrationOpen();
    }

    @ModelAttribute("me")
    public CurrentUser me(Principal principal) {
        if (principal == null) return null;

        User staff = userRepository.findByUserName(principal.getName());
        if (staff != null) return fromStaff(staff);

        Student student = studentRepository.findByUsername(principal.getName());
        if (student != null) return fromStudent(student);

        return null;
    }

    private CurrentUser fromStaff(User user) {
        String roleName = user.getRole().getRoleName().trim().toLowerCase();
        String streamName = user.getStream() != null ? user.getStream().getStreamName() : null;

        return switch (roleName) {
            case "admin" -> new CurrentUser(user.getName(), user.getUserName(),
                    "admin", "Administrator", null, adminAlerts());
            case "section head" -> new CurrentUser(user.getName(), user.getUserName(),
                    "sectionhead", streamName != null ? streamName + " Section Head" : "Section Head",
                    streamName, sectionHeadAlerts(user));
            case "interview panel" -> new CurrentUser(user.getName(), user.getUserName(),
                    "panel", streamName != null ? streamName + " Interview Panel" : "Interview Panel",
                    streamName, panelAlerts(user));
            default -> null;
        };
    }

    private CurrentUser fromStudent(Student student) {
        String name = student.getFullName() != null ? student.getFullName() : student.getUsername();
        List<CurrentUser.Alert> alerts = new ArrayList<>();

        List<Application> apps = applicationRepository.findByStudentStudentId(student.getStudentId());
        if (apps.isEmpty()) {
            alerts.add(new CurrentUser.Alert("fa-file-pen",
                    "Complete and submit your application form.", "/student/application"));
        } else {
            for (Notification n : notificationRepository
                    .findByApplicationApplicationIdOrderBySentDateDesc(apps.get(0).getApplicationId())) {
                switch (String.valueOf(n.getType())) {
                    case "INTERVIEW_INVITATION" -> alerts.add(new CurrentUser.Alert("fa-calendar-check",
                            "You have been invited to an interview.", "/student/notifications"));
                    case "NOT_QUALIFIED" -> alerts.add(new CurrentUser.Alert("fa-envelope",
                            "Your interview selection result is available.", "/student/notifications"));
                    default -> alerts.add(new CurrentUser.Alert("fa-award",
                            "Your final selection result is available.", "/student/result"));
                }
            }
        }
        return new CurrentUser(name, student.getUsername(), "student", "Student", null, alerts);
    }

    private List<CurrentUser.Alert> adminAlerts() {
        List<CurrentUser.Alert> alerts = new ArrayList<>();
        int resetRequests = passwordResetService.openRequestCount();
        if (resetRequests > 0) {
            alerts.add(new CurrentUser.Alert("fa-key",
                    resetRequests + (resetRequests == 1 ? " person has" : " people have")
                            + " asked for a password reset.", "/admin/password-requests"));
        }
        if (!emailService.isConfigured()) {
            alerts.add(new CurrentUser.Alert("fa-envelope",
                    "Email is not set up. Letters are not being emailed to students.", "/admin/settings?tab=email"));
        }
        long pending = applicationRepository.countByStatus(Status.APPLIED);
        if (pending > 0) {
            alerts.add(new CurrentUser.Alert("fa-hourglass-half",
                    pending + " applications are waiting for auto selection.", "/admin/applications?status=APPLIED"));
        }
        long unassigned = userRepository.findAll().stream()
                .filter(u -> u.getStream() == null)
                .filter(u -> !"admin".equalsIgnoreCase(u.getRole().getRoleName()))
                .count();
        if (unassigned > 0) {
            alerts.add(new CurrentUser.Alert("fa-triangle-exclamation",
                    unassigned + " staff accounts have no stream assigned.", "/admin/section-heads"));
        }
        return alerts;
    }

    private List<CurrentUser.Alert> sectionHeadAlerts(User head) {
        List<CurrentUser.Alert> alerts = new ArrayList<>();
        if (head.getStream() == null) return alerts;

        List<Application> apps = applicationRepository.findByStreamStreamId(head.getStream().getStreamId());
        long pending = apps.stream().filter(a -> Status.APPLIED.equals(a.getStatus())).count();
        long toInvite = apps.stream().filter(a -> Status.SHORTLISTED.equals(a.getStatus())).count();
        long toCutoff = apps.stream().filter(a -> Status.INTERVIEWED.equals(a.getStatus())).count();

        if (pending > 0) alerts.add(new CurrentUser.Alert("fa-list-check",
                pending + " applications are waiting for auto selection.", "/sectionhead/auto-selection"));
        if (toInvite > 0) alerts.add(new CurrentUser.Alert("fa-envelope-open-text",
                toInvite + " shortlisted students need interview invitations.", "/sectionhead/invitations"));
        if (toCutoff > 0) alerts.add(new CurrentUser.Alert("fa-calculator",
                toCutoff + " interviewed students are waiting for the cutoff calculation.", "/sectionhead/cutoff"));
        return alerts;
    }

    private List<CurrentUser.Alert> panelAlerts(User panel) {
        List<CurrentUser.Alert> alerts = new ArrayList<>();
        if (panel.getStream() == null) return alerts;

        long pending = interviewRepository.findByApplicationStreamStreamId(panel.getStream().getStreamId())
                .stream()
                .filter(i -> !Status.COMPLETED.equals(i.getStatus()))
                .count();
        if (pending > 0) alerts.add(new CurrentUser.Alert("fa-clipboard-list",
                pending + " interviews are waiting for marks.", "/panel/pending"));
        return alerts;
    }
}
