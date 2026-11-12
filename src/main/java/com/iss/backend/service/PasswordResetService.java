package com.iss.backend.service;

import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * "Forgot password" handling.
 *
 * Passwords are stored as one-way BCrypt hashes, so they can never be recovered or sent.
 *  - Students enter their registered email address (or username). If it is registered, a
 *    one-time link is emailed; opening it lets the student choose a new password.
 *    If the email address is not registered, the student is told so.
 *  - Admin: the reset link is emailed to the school's official mailbox.
 *  - Other staff (created by the admin, no email address): the request is sent to the
 *    administrator, who resets the password and gives them a temporary one.
 *  - If email is not set up (or cannot be sent), student requests also go to the administrator.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Minimum time between two reset emails for the same account. */
    private static final long COOLDOWN_SECONDS = 2 * 60;

    /** How long a reset link stays valid. */
    public static final long LINK_VALID_MINUTES = 30;

    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SettingsService settings;
    @Autowired private EmailService emailService;

    public enum Outcome {
        LINK_SENT,          // reset link emailed
        EMAIL_NOT_FOUND,    // no student registered with that email / username
        COOLDOWN,           // a link was sent moments ago
        ADMIN_REQUEST,      // passed to the administrator
        EMAIL_UNAVAILABLE   // admin account, but email is not set up
    }

    /** Outcome plus the (masked) address the link went to, e.g. ha*******2@gmail.com. */
    public record Result(Outcome outcome, String maskedEmail) {}


    // =====================================================
    // REQUEST FROM THE LOGIN PAGE
    // =====================================================

    /**
     * @param usernameOrEmail what the person typed
     * @param baseUrl         the site address used to build the reset link, e.g. http://localhost:8080
     */
    public Result requestReset(String usernameOrEmail, String baseUrl) {
        String key = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        if (key.isEmpty()) return new Result(Outcome.EMAIL_NOT_FOUND, null);

        // Staff accounts (username only - staff have no email address)
        User staff = userRepository.findByUserName(key);
        if (staff != null) {
            boolean isAdmin = "admin".equalsIgnoreCase(staff.getRole().getRoleName());
            if (!isAdmin) {
                recordStaffRequest(staff);
                return new Result(Outcome.ADMIN_REQUEST, null);
            }
            if (!emailService.isConfigured()) return new Result(Outcome.EMAIL_UNAVAILABLE, null);
            if (coolingDown("user", staff.getUserId())) return new Result(Outcome.COOLDOWN, mask(EmailService.SCHOOL_EMAIL));
            boolean sent = sendLink(EmailService.SCHOOL_EMAIL, staff.getName(), staff.getUserName(), "user", staff.getUserId(), baseUrl);
            return new Result(sent ? Outcome.LINK_SENT : Outcome.EMAIL_UNAVAILABLE, mask(EmailService.SCHOOL_EMAIL));
        }

        // Students: by email address (or username)
        List<Student> matches = new ArrayList<>();
        if (key.contains("@")) {
            matches.addAll(studentRepository.findByEmailIgnoreCase(key));
        } else {
            Student byUsername = studentRepository.findByUsername(key);
            if (byUsername != null) matches.add(byUsername);
        }

        if (matches.isEmpty()) return new Result(Outcome.EMAIL_NOT_FOUND, null);

        // Without email the request goes to the administrator instead
        if (!emailService.isConfigured()) {
            matches.forEach(this::recordStudentRequest);
            return new Result(Outcome.ADMIN_REQUEST, null);
        }

        String sentTo = null;
        boolean anySent = false, passedToAdmin = false, cooling = false;
        for (Student st : matches) {
            if (st.getEmail() == null || st.getEmail().isBlank()) {
                recordStudentRequest(st);
                passedToAdmin = true;
                continue;
            }
            if (coolingDown("student", st.getStudentId())) {
                cooling = true;
                sentTo = st.getEmail();
                continue;
            }
            if (sendLink(st.getEmail(), st.getFullName(), st.getUsername(), "student", st.getStudentId(), baseUrl)) {
                anySent = true;
                sentTo = st.getEmail();
            } else {
                recordStudentRequest(st);
                passedToAdmin = true;
            }
        }

        if (anySent) return new Result(Outcome.LINK_SENT, mask(sentTo));
        if (cooling) return new Result(Outcome.COOLDOWN, mask(sentTo));
        return new Result(passedToAdmin ? Outcome.ADMIN_REQUEST : Outcome.EMAIL_NOT_FOUND, null);
    }


    // =====================================================
    // RESET LINKS
    // =====================================================

    /** The account a reset link belongs to. */
    public record LinkTarget(String kind, Integer id, String username, String name) {}

    private boolean sendLink(String to, String name, String username, String kind, Integer id, String baseUrl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expires = Instant.now().getEpochSecond() + LINK_VALID_MINUTES * 60;
        String link = baseUrl + "/reset-password?token=" + token;

        try {
            emailService.sendPasswordResetLink(to, name, username, link, LINK_VALID_MINUTES);
        } catch (Exception e) {
            log.warn("Password reset link for {} could not be sent: {}", username, e.toString());
            return false;
        }
        // Only one link at a time per account: the newest link replaces any older one
        settings.saveAll(Map.of(
                "pwtoken." + token, kind + "|" + id + "|" + expires,
                "pwlatest." + kind + "." + id, token,
                "pwreset." + kind + "." + id, String.valueOf(Instant.now().getEpochSecond())));
        return true;
    }

    /** Resolves a link token, or returns null when it is unknown, used or expired. */
    public LinkTarget findLink(String token) {
        if (token == null || token.isBlank()) return null;
        String v = settings.get("pwtoken." + token, "");
        String[] parts = v.split("\\|");
        if (parts.length != 3) return null;
        try {
            String kind = parts[0];
            Integer id = Integer.valueOf(parts[1]);
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) return null;
            if (!token.equals(settings.get("pwlatest." + kind + "." + id, ""))) return null;   // replaced by a newer link

            if ("student".equals(kind)) {
                Student st = studentRepository.findById(id).orElse(null);
                return st == null ? null : new LinkTarget(kind, id, st.getUsername(), st.getFullName());
            }
            User u = userRepository.findById(id).orElse(null);
            return u == null ? null : new LinkTarget(kind, id, u.getUserName(), u.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sets the new password from a reset link. @return an error message, or null on success. */
    public String resetWithLink(String token, String password, String confirm) {
        LinkTarget target = findLink(token);
        if (target == null) return "This reset link is invalid or has expired. Please request a new one.";
        if (password == null || password.length() < AccountService.MIN_PASSWORD_LENGTH) {
            return "The new password must be at least " + AccountService.MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!password.equals(confirm)) return "The passwords do not match.";

        String hash = passwordEncoder.encode(password);
        if ("student".equals(target.kind())) {
            Student st = studentRepository.findById(target.id()).orElseThrow();
            st.setPassword(hash);
            studentRepository.save(st);
        } else {
            User u = userRepository.findById(target.id()).orElseThrow();
            u.setPassword(hash);
            userRepository.save(u);
        }

        // One-time link: remove it, and clear any temporary-password / request flags
        settings.saveAll(Map.of(
                "pwtoken." + token, "",
                "pwlatest." + target.kind() + "." + target.id(), "",
                "pwtemp." + target.kind() + "." + target.id(), "",
                "pwreq." + target.kind() + "." + target.id(), ""));
        log.info("Password changed with a reset link for {}", target.username());
        return null;
    }

    /** ha*******2@gmail.com */
    private static String mask(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String shown = name.length() <= 2 ? name.substring(0, 1) + "*"
                : name.substring(0, 2) + "*".repeat(Math.max(1, name.length() - 3)) + name.charAt(name.length() - 1);
        return shown + email.substring(at);
    }


    // =====================================================
    // STAFF REQUESTS (handled by the administrator)
    // =====================================================

    public record StaffRequest(User user, String requestedAt) {}

    private void recordStaffRequest(User staff) {
        settings.saveAll(Map.of("pwreq.user." + staff.getUserId(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))));
        log.info("Password reset requested for staff account {}", staff.getUserName());
    }

    /** Open requests from section heads / panel members. */
    public List<StaffRequest> staffRequests() {
        List<StaffRequest> list = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            String at = settings.get("pwreq.user." + u.getUserId(), "");
            if (!at.isBlank()) list.add(new StaffRequest(u, at));
        }
        return list;
    }

    /** Admin action: gives a staff member a new temporary password (shown once to the admin). */
    public String adminResetStaff(User staff) {
        String temp = setStaffTempPassword(staff);
        settings.saveAll(Map.of("pwreq.user." + staff.getUserId(), ""));
        return temp;
    }

    public void dismissStaffRequest(User staff) {
        settings.saveAll(Map.of("pwreq.user." + staff.getUserId(), ""));
    }


    // =====================================================
    // STUDENT REQUESTS (when email cannot be used)
    // =====================================================

    public record StudentRequest(Student student, String requestedAt) {}

    private void recordStudentRequest(Student student) {
        settings.saveAll(Map.of("pwreq.student." + student.getStudentId(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))));
        log.info("Password reset request for student {} passed to the administrator", student.getUsername());
    }

    public List<StudentRequest> studentRequests() {
        List<StudentRequest> list = new ArrayList<>();
        for (Student st : studentRepository.findAll()) {
            String at = settings.get("pwreq.student." + st.getStudentId(), "");
            if (!at.isBlank()) list.add(new StudentRequest(st, at));
        }
        return list;
    }

    /** Admin action: gives a student a new temporary password (shown once to the admin). */
    public String adminResetStudent(Student student) {
        String temp = generateTempPassword();
        student.setPassword(passwordEncoder.encode(temp));
        studentRepository.save(student);
        markTemporary("student", student.getStudentId(), true);
        settings.saveAll(Map.of("pwreq.student." + student.getStudentId(), ""));
        return temp;
    }

    public void dismissStudentRequest(Student student) {
        settings.saveAll(Map.of("pwreq.student." + student.getStudentId(), ""));
    }

    /** Total open requests (staff + students), for the admin's notification bell. */
    public int openRequestCount() {
        return staffRequests().size() + studentRequests().size();
    }


    // =====================================================
    // TEMPORARY PASSWORD FLAG ("please change your password")
    // =====================================================

    public boolean mustChangeStudent(Integer studentId) {
        return "true".equals(settings.get("pwtemp.student." + studentId, ""));
    }

    public boolean mustChangeStaff(Integer userId) {
        return "true".equals(settings.get("pwtemp.user." + userId, ""));
    }

    /** Called after the person sets their own password: clears the temporary flag and cancels any unused reset link. */
    public void clearStudent(Integer studentId) {
        markTemporary("student", studentId, false);
        settings.saveAll(Map.of("pwlatest.student." + studentId, ""));
    }

    public void clearStaff(Integer userId) {
        markTemporary("user", userId, false);
        settings.saveAll(Map.of("pwlatest.user." + userId, ""));
    }


    // =====================================================
    // HELPERS
    // =====================================================

    private String setStaffTempPassword(User staff) {
        String temp = generateTempPassword();
        staff.setPassword(passwordEncoder.encode(temp));
        userRepository.save(staff);
        markTemporary("user", staff.getUserId(), true);
        return temp;
    }

    private boolean coolingDown(String kind, Integer id) {
        try {
            long last = Long.parseLong(settings.get("pwreset." + kind + "." + id, "0"));
            return Instant.now().getEpochSecond() - last < COOLDOWN_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void markTemporary(String kind, Integer id, boolean temporary) {
        settings.saveAll(Map.of("pwtemp." + kind + "." + id, temporary ? "true" : ""));
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        return sb.toString();
    }
}
