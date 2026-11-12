package com.iss.backend.controller;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import com.iss.backend.service.AccountService;
import com.iss.backend.service.ActivityService;
import com.iss.backend.service.EmailService;
import com.iss.backend.service.MailConfigService;
import com.iss.backend.service.PasswordResetService;
import com.iss.backend.service.SettingsService;
import com.iss.backend.service.ExportService;
import com.iss.backend.service.InterviewMarkingService;
import com.iss.backend.web.Filters;
import com.iss.backend.web.Status;
import com.iss.backend.web.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String SECTION_HEAD = "Section Head";
    private static final String INTERVIEW_PANEL = "Interview Panel";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SubjectStreamRepository streamRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private OlResultRepository olResultRepository;
    @Autowired private SelectionConditionRepository conditionRepository;
    @Autowired private CutoffRecordRepository cutoffRecordRepository;
    @Autowired private MarkingConfigRepository markingConfigRepository;
    @Autowired private ExportService exportService;
    @Autowired private InterviewMarkingService markingService;
    @Autowired private AccountService accountService;
    @Autowired private EmailService emailService;
    @Autowired private MailConfigService mailConfigService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private SettingsService settingsService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ActivityService activityService;


    // =====================================================
    // DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Application> apps = applicationRepository.findAll();

        model.addAttribute("totalStudents", studentRepository.count());
        model.addAttribute("totalApplications", apps.size());
        model.addAttribute("sectionHeadCount", userRepository.findByRoleRoleNameIgnoreCase(SECTION_HEAD).size());
        model.addAttribute("panelCount", userRepository.findByRoleRoleNameIgnoreCase(INTERVIEW_PANEL).size());
        model.addAttribute("pendingInterviews", interviewRepository.countByStatus(Status.SCHEDULED));
        model.addAttribute("selectedCount", countStatus(apps, Status.SELECTED));

        model.addAttribute("streamRows", streamSummary(apps));
        model.addAttribute("recent", apps.stream()
                .sorted(Comparator.comparing(Application::getApplicationDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .toList());
        return "admin/dashboard";
    }


    // =====================================================
    // STUDENTS
    // =====================================================

    @GetMapping("/students")
    public String students(@RequestParam(required = false) String q, Model model) {
        List<Student> students = Filters.students(studentRepository.findAll(), q);

        Map<Integer, Application> appByStudent = new HashMap<>();
        for (Application a : applicationRepository.findAll()) {
            appByStudent.putIfAbsent(a.getStudent().getStudentId(), a);
        }

        model.addAttribute("students", students);
        model.addAttribute("appByStudent", appByStudent);
        model.addAttribute("q", q);
        return "admin/students";
    }

    @GetMapping("/students/{id}")
    public String studentDetail(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        Student student = studentRepository.findById(id).orElse(null);
        if (student == null) {
            ra.addFlashAttribute("error", "That student could not be found.");
            return "redirect:/admin/students";
        }
        List<Application> apps = applicationRepository.findByStudentStudentId(id);
        Application app = apps.isEmpty() ? null : apps.get(0);

        model.addAttribute("student", student);
        model.addAttribute("app", app);
        model.addAttribute("olResults", app == null ? List.of()
                : olResultRepository.findByApplicationApplicationId(app.getApplicationId()));
        return "admin/student-detail";
    }


    // =====================================================
    // APPLICATIONS
    // =====================================================

    @GetMapping("/applications")
    public String applications(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) Integer stream,
                               Model model) {
        List<Application> apps = Filters.applications(applicationRepository.findAll(), q, status, stream);

        model.addAttribute("applications", apps);
        model.addAttribute("streams", streamRepository.findAll());
        model.addAttribute("statuses", Status.ALL);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("stream", stream);
        return "admin/applications";
    }


    // =====================================================
    // SCHOOLS
    // =====================================================

    @GetMapping("/schools")
    public String schools(Model model) {
        List<School> schools = schoolRepository.findAll();
        Map<Integer, Long> appCounts = applicationRepository.findAll().stream()
                .collect(Collectors.groupingBy(a -> a.getSchool().getSchoolId(), Collectors.counting()));

        model.addAttribute("schools", schools);
        model.addAttribute("appCounts", appCounts);
        return "admin/schools";
    }

    @PostMapping("/schools/save")
    public String saveSchool(@RequestParam(required = false) Integer schoolId,
                             @RequestParam String schoolName,
                             @RequestParam String district,
                             @RequestParam String schoolAddress,
                             RedirectAttributes ra) {
        String error = Validation.first(
                Validation.text(schoolName, "School name", 150),
                Validation.text(district, "District", 50),
                Validation.text(schoolAddress, "School address", 255));
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/schools";
        }
        School school = schoolId != null ? schoolRepository.findById(schoolId).orElseThrow() : new School();
        school.setSchoolName(schoolName.trim());
        school.setDistrict(district.trim());
        school.setSchoolAddress(schoolAddress.trim());
        schoolRepository.save(school);

        ra.addFlashAttribute("success", schoolId != null ? "School details updated." : "School added.");
        return "redirect:/admin/schools";
    }

    @PostMapping("/schools/{id}/delete")
    public String deleteSchool(@PathVariable Integer id, RedirectAttributes ra) {
        if (applicationRepository.existsBySchoolSchoolId(id) || cutoffRecordRepository.existsBySchoolSchoolId(id)) {
            ra.addFlashAttribute("error", "This school has applications or cutoff records, so it cannot be deleted.");
        } else {
            schoolRepository.deleteById(id);
            ra.addFlashAttribute("success", "School deleted.");
        }
        return "redirect:/admin/schools";
    }


    // =====================================================
    // STREAMS
    // =====================================================

    @GetMapping("/streams")
    public String streams(Model model) {
        List<Application> apps = applicationRepository.findAll();
        model.addAttribute("streamRows", streamSummary(apps));
        return "admin/streams";
    }

    @PostMapping("/streams/save")
    public String saveStream(@RequestParam(required = false) Integer streamId,
                             @RequestParam String streamName,
                             RedirectAttributes ra) {
        String error = Validation.text(streamName, "Stream name", 50);
        if (error == null && streamRepository.findAll().stream().anyMatch(s ->
                s.getStreamName().equalsIgnoreCase(streamName.trim()) && !s.getStreamId().equals(streamId))) {
            error = "A stream called \"" + streamName.trim() + "\" already exists.";
        }
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/streams";
        }
        SubjectStream stream = streamId != null ? streamRepository.findById(streamId).orElseThrow() : new SubjectStream();
        stream.setStreamName(streamName.trim());
        streamRepository.save(stream);

        ra.addFlashAttribute("success", streamId != null ? "Stream renamed." : "Stream added.");
        return "redirect:/admin/streams";
    }

    @PostMapping("/streams/{id}/delete")
    public String deleteStream(@PathVariable Integer id, RedirectAttributes ra) {
        boolean inUse = applicationRepository.existsByStreamStreamId(id)
                || userRepository.existsByStreamStreamId(id)
                || conditionRepository.existsByStreamStreamId(id)
                || cutoffRecordRepository.existsByStreamStreamId(id);
        if (inUse) {
            ra.addFlashAttribute("error", "This stream is used by applications, staff or selection rules, so it cannot be deleted.");
        } else {
            streamRepository.deleteById(id);
            ra.addFlashAttribute("success", "Stream deleted.");
        }
        return "redirect:/admin/streams";
    }


    // =====================================================
    // SECTION HEADS / PANEL MEMBERS
    // =====================================================

    @GetMapping("/section-heads")
    public String sectionHeads(Model model) {
        return staffPage(model, SECTION_HEAD, "section-heads", "Section Heads");
    }

    @GetMapping("/panel-members")
    public String panelMembers(Model model) {
        return staffPage(model, INTERVIEW_PANEL, "panel-members", "Panel Members");
    }

    private String staffPage(Model model, String roleName, String page, String title) {
        model.addAttribute("staffList", userRepository.findByRoleRoleNameIgnoreCase(roleName).stream()
                .sorted(Comparator.comparing((User u) -> u.getStream() == null ? "" : u.getStream().getStreamName())
                        .thenComparing(User::getName))
                .toList());
        model.addAttribute("staffRole", roleByName(roleName));
        model.addAttribute("streams", streamRepository.findAll());
        model.addAttribute("page", page);
        model.addAttribute("pageTitle", title);
        model.addAttribute("resetRequests", passwordResetService.staffRequests().stream()
                .map(r -> r.user().getUserId()).toList());
        return "admin/staff";
    }

    @PostMapping("/staff/add")
    public String addStaff(@RequestParam String name,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam Integer roleId,
                           @RequestParam(required = false) Integer streamId,
                           @RequestParam(defaultValue = "section-heads") String returnTo,
                           RedirectAttributes ra) {

        String error = Validation.first(
                Validation.name(name, "Name", 100),
                Validation.username(username),
                Validation.password(password, AccountService.MIN_PASSWORD_LENGTH));
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/" + safePage(returnTo);
        }
        if (userRepository.findByUserName(username.trim()) != null
                || studentRepository.findByUsername(username.trim()) != null) {
            ra.addFlashAttribute("error", "The username \"" + username.trim() + "\" is already taken.");
            return "redirect:/admin/" + safePage(returnTo);
        }

        User newUser = new User();
        newUser.setName(name.trim());
        newUser.setUserName(username.trim());
        newUser.setPassword(passwordEncoder.encode(password)); // BCrypt hash - never store plain text
        newUser.setRole(roleRepository.findById(roleId).orElseThrow());
        if (streamId != null) {
            newUser.setStream(streamRepository.findById(streamId).orElseThrow());
        }
        userRepository.save(newUser);

        ra.addFlashAttribute("success", name + " has been added.");
        return "redirect:/admin/" + safePage(returnTo);
    }

    @PostMapping("/staff/update/{userId}")
    public String updateStaff(@PathVariable Integer userId,
                              @RequestParam String name,
                              @RequestParam String username,
                              @RequestParam(required = false) String newPassword,
                              @RequestParam(required = false) Integer streamId,
                              @RequestParam(defaultValue = "section-heads") String returnTo,
                              RedirectAttributes ra) {

        User user = userRepository.findById(userId).orElseThrow();

        boolean changingPassword = newPassword != null && !newPassword.isBlank();
        String error = Validation.first(
                Validation.name(name, "Name", 100),
                Validation.username(username),
                changingPassword ? Validation.password(newPassword, AccountService.MIN_PASSWORD_LENGTH) : null);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/" + safePage(returnTo);
        }

        // Students are checked too: login looks up staff first, so a clash would lock the student out
        User clash = userRepository.findByUserName(username.trim());
        if ((clash != null && !clash.getUserId().equals(userId)) || studentRepository.findByUsername(username.trim()) != null) {
            ra.addFlashAttribute("error", "The username \"" + username.trim() + "\" is already taken.");
            return "redirect:/admin/" + safePage(returnTo);
        }

        user.setName(name.trim());
        user.setUserName(username.trim());
        user.setStream(streamId != null ? streamRepository.findById(streamId).orElseThrow() : null);

        // Blank password field = "keep current password", so only re-hash if something was typed
        if (changingPassword) {
            user.setPassword(passwordEncoder.encode(newPassword));
            passwordResetService.dismissStaffRequest(user);
            passwordResetService.clearStaff(user.getUserId());
        }
        userRepository.save(user);

        ra.addFlashAttribute("success", user.getName() + "'s account has been updated.");
        return "redirect:/admin/" + safePage(returnTo);
    }

    @PostMapping("/staff/{userId}/reset-password")
    public String resetStaffPassword(@PathVariable Integer userId,
                                     @RequestParam(defaultValue = "section-heads") String returnTo,
                                     RedirectAttributes ra) {
        User user = userRepository.findById(userId).orElseThrow();
        String temp = passwordResetService.adminResetStaff(user);
        ra.addFlashAttribute("resetPasswordFor", user.getName() + " (" + user.getUserName() + ")");
        ra.addFlashAttribute("resetPasswordValue", temp);
        return "/password-requests".equals(returnTo) ? "redirect:/admin/password-requests" : "redirect:/admin/" + safePage(returnTo);
    }

    @PostMapping("/staff/{userId}/dismiss-reset")
    public String dismissReset(@PathVariable Integer userId, RedirectAttributes ra) {
        passwordResetService.dismissStaffRequest(userRepository.findById(userId).orElseThrow());
        ra.addFlashAttribute("success", "Password reset request dismissed.");
        return "redirect:/admin/password-requests";
    }

    @GetMapping("/password-requests")
    public String passwordRequests(Model model) {
        model.addAttribute("requests", passwordResetService.staffRequests());
        model.addAttribute("studentRequests", passwordResetService.studentRequests());
        return "admin/password-requests";
    }

    @PostMapping("/students/{id}/reset-password")
    public String resetStudentPassword(@PathVariable Integer id,
                                       @RequestParam(defaultValue = "student") String returnTo,
                                       RedirectAttributes ra) {
        Student student = studentRepository.findById(id).orElseThrow();
        String temp = passwordResetService.adminResetStudent(student);
        ra.addFlashAttribute("resetPasswordFor", (student.getFullName() == null ? "" : student.getFullName() + " ")
                + "(" + student.getUsername() + ")");
        ra.addFlashAttribute("resetPasswordValue", temp);
        return "requests".equals(returnTo) ? "redirect:/admin/password-requests" : "redirect:/admin/students/" + id;
    }

    @PostMapping("/students/{id}/dismiss-reset")
    public String dismissStudentReset(@PathVariable Integer id, RedirectAttributes ra) {
        passwordResetService.dismissStudentRequest(studentRepository.findById(id).orElseThrow());
        ra.addFlashAttribute("success", "Password reset request dismissed.");
        return "redirect:/admin/password-requests";
    }

    @PostMapping("/staff/delete/{userId}")
    public String deleteStaff(@PathVariable Integer userId,
                              @RequestParam(defaultValue = "section-heads") String returnTo,
                              Principal principal,
                              RedirectAttributes ra) {
        User user = userRepository.findById(userId).orElseThrow();

        if (user.getUserName().equals(principal.getName())) {
            ra.addFlashAttribute("error", "You cannot delete your own account.");
        } else if (interviewRepository.existsByUserUserId(userId)) {
            ra.addFlashAttribute("error", user.getName() + " is assigned to interviews, so the account cannot be deleted.");
        } else {
            try {
                userRepository.delete(user);
                ra.addFlashAttribute("success", user.getName() + "'s account has been deleted.");
            } catch (DataIntegrityViolationException e) {
                ra.addFlashAttribute("error", "This account is linked to other records and cannot be deleted.");
            }
        }
        return "redirect:/admin/" + safePage(returnTo);
    }


    // =====================================================
    // SELECTION RULES (read-only overview of all streams)
    // =====================================================

    @GetMapping("/selection-rules")
    public String selectionRules(@RequestParam(required = false) Integer year, Model model) {
        int y = year != null ? year : settingsService.academicYear();

        Map<Integer, List<SelectionCondition>> byStream = conditionRepository.findByYear(y).stream()
                .collect(Collectors.groupingBy(c -> c.getStream().getStreamId()));
        Map<Integer, CutoffRecord> cutoffs = cutoffRecordRepository.findByYear(y).stream()
                .collect(Collectors.toMap(c -> c.getStream().getStreamId(), c -> c, (a, b) -> a));

        model.addAttribute("year", y);
        model.addAttribute("streams", streamRepository.findAll());
        model.addAttribute("conditionsByStream", byStream);
        model.addAttribute("cutoffs", cutoffs);
        return "admin/selection-rules";
    }


    // =====================================================
    // REPORTS
    // =====================================================

    // =====================================================
    // SITE ACTIVITY (visitors and logins)
    // =====================================================

    private static final List<Integer> ACTIVITY_RANGES = List.of(1, 7, 30, 90, 365);
    private static final List<String> LOGIN_TYPES =
            List.of(ActivityService.LOGIN, ActivityService.FAILED, ActivityService.LOCKED, ActivityService.LOGOUT);

    @GetMapping("/activity")
    public String activity(@RequestParam(defaultValue = "7") int days,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) String q,
                           Model model) {
        days = ACTIVITY_RANGES.contains(days) ? days : 7;
        type = type != null && LOGIN_TYPES.contains(type) ? type : null;
        q = q == null || q.isBlank() ? null : q.trim();

        List<ActivityService.DayCount> daily = activityService.daily(days);
        model.addAttribute("summary", activityService.summary(days));
        model.addAttribute("daily", daily);
        model.addAttribute("dailyMax", daily.stream().mapToLong(ActivityService.DayCount::views).max().orElse(0));
        model.addAttribute("topPages", activityService.topPages(days, 10));
        Map<String, Long> byRole = activityService.viewsByRole(days);
        model.addAttribute("byRole", byRole);
        model.addAttribute("byRoleTotal", byRole.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("logins", activityService.loginEvents(days, type, q, 200));
        model.addAttribute("recentVisits", activityService.recentVisits(25));
        model.addAttribute("days", days);
        model.addAttribute("ranges", ACTIVITY_RANGES);
        model.addAttribute("loginTypes", LOGIN_TYPES);
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("lockMinutes", ActivityService.LOCK_MINUTES);
        model.addAttribute("maxFailed", ActivityService.MAX_FAILED_PER_USER);
        return "admin/activity";
    }

    /** Login history for the chosen range as Excel (default) or PDF (?format=pdf). */
    @GetMapping("/activity/export")
    public ResponseEntity<byte[]> exportActivity(@RequestParam(defaultValue = "30") int days,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String format) {
        days = ACTIVITY_RANGES.contains(days) ? days : 30;
        type = type != null && LOGIN_TYPES.contains(type) ? type : null;
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<List<String>> rows = activityService.loginEvents(days, type, q, 10_000).stream()
                .map(r -> List.of(r.at().format(fmt), loginTypeLabel(r.type()),
                        Objects.toString(r.username(), "-"), Objects.toString(r.role(), "-"),
                        Objects.toString(r.ip(), "-"), r.device()))
                .toList();
        return exportService.download(format, "login-history",
                "Login History - last " + days + (days == 1 ? " day" : " days"),
                new String[]{"Date & Time", "Event", "Username", "Role", "IP Address", "Browser / Device"}, rows);
    }

    private static String loginTypeLabel(String type) {
        return switch (type) {
            case ActivityService.LOGIN -> "Logged in";
            case ActivityService.FAILED -> "Wrong password";
            case ActivityService.LOCKED -> "Blocked (too many attempts)";
            case ActivityService.LOGOUT -> "Logged out";
            default -> type;
        };
    }


    @GetMapping("/reports")
    public String reports(Model model) {
        List<Application> apps = applicationRepository.findAll();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : Status.ALL) byStatus.put(s, countStatus(apps, s));

        model.addAttribute("byStatus", byStatus);
        model.addAttribute("total", apps.size());
        model.addAttribute("streamRows", streamSummary(apps));
        model.addAttribute("selected", apps.stream().filter(a -> Status.SELECTED.equals(a.getStatus())).toList());
        model.addAttribute("statuses", Status.ALL);
        return "admin/reports";
    }

    /** Selected students as Excel (default) or PDF (?format=pdf). */
    @GetMapping("/export-selected")
    public ResponseEntity<byte[]> exportSelected(@RequestParam(defaultValue = "xlsx") String format) {
        return exportService.download(format, "selected_students", "Selected Students",
                ExportService.APPLICATION_HEADERS,
                ExportService.applicationRows(applicationRepository.findByStatus(Status.SELECTED)));
    }

    /** Applications as Excel (default) or PDF (?format=pdf), with the same search and filters as the Applications page. */
    @GetMapping("/export-applications")
    public ResponseEntity<byte[]> exportApplications(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer stream,
                                                     @RequestParam(defaultValue = "xlsx") String format) {
        boolean all = status == null || status.isBlank() || !Status.ALL.contains(status);
        List<Application> apps = Filters.applications(applicationRepository.findAll(), q, all ? null : status, stream);
        String title = all ? "All Applications" : ExportService.statusLabel(status) + " Applications";
        String file = all ? "all_applications" : status.toLowerCase() + "_applications";
        if (stream != null) {
            String streamName = streamRepository.findById(stream).map(SubjectStream::getStreamName).orElse(null);
            if (streamName != null) {
                title = streamName + " - " + title;
                file = streamName.toLowerCase().replaceAll("\\W+", "_") + "_" + file;
            }
        }
        return exportService.download(format, file, title,
                ExportService.APPLICATION_HEADERS, ExportService.applicationRows(apps));
    }

    /** Students list (with the page's search) as Excel or PDF. */
    @GetMapping("/export-students")
    public ResponseEntity<byte[]> exportStudents(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "xlsx") String format) {
        Map<Integer, Application> appByStudent = new HashMap<>();
        for (Application a : applicationRepository.findAll()) {
            appByStudent.putIfAbsent(a.getStudent().getStudentId(), a);
        }
        List<List<String>> rows = Filters.students(studentRepository.findAll(), q).stream().map(st -> {
            Application app = appByStudent.get(st.getStudentId());
            return List.of(
                    Objects.toString(st.getFullName(), ""), Objects.toString(st.getUsername(), ""),
                    Objects.toString(st.getEmail(), ""), Objects.toString(st.getNic(), ""),
                    Objects.toString(st.getIndexNumber(), ""), Objects.toString(st.getPhoneNumber(), ""),
                    app == null ? "-" : app.getStream().getStreamName(),
                    app == null ? "Not applied" : ExportService.statusLabel(app.getStatus()));
        }).toList();
        return exportService.download(format, "students", "Registered Students",
                new String[]{"Name", "Username", "Email", "NIC", "Index Number", "Phone", "Stream", "Application"}, rows);
    }

    @GetMapping("/export-schools")
    public ResponseEntity<byte[]> exportSchools(@RequestParam(defaultValue = "xlsx") String format) {
        Map<Integer, Long> appCounts = applicationRepository.findAll().stream()
                .collect(Collectors.groupingBy(a -> a.getSchool().getSchoolId(), Collectors.counting()));
        List<List<String>> rows = schoolRepository.findAll().stream().map(sc -> List.of(
                Objects.toString(sc.getSchoolName(), ""), Objects.toString(sc.getDistrict(), ""),
                Objects.toString(sc.getSchoolAddress(), ""),
                String.valueOf(appCounts.getOrDefault(sc.getSchoolId(), 0L)))).toList();
        return exportService.download(format, "schools", "Schools",
                new String[]{"School", "District", "Address", "Applications"}, rows);
    }

    @GetMapping("/export-streams")
    public ResponseEntity<byte[]> exportStreams(@RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> rows = streamSummary(applicationRepository.findAll()).stream().map(r -> List.of(
                ((SubjectStream) r.get("stream")).getStreamName(),
                String.valueOf(r.get("applications")), String.valueOf(r.get("pending")),
                String.valueOf(r.get("shortlisted")), String.valueOf(r.get("interviewed")),
                String.valueOf(r.get("selected")), String.valueOf(r.get("heads")), String.valueOf(r.get("panel")))).toList();
        return exportService.download(format, "streams", "Stream Summary",
                new String[]{"Stream", "Applications", "Pending", "Shortlisted", "Interviewed", "Selected",
                        "Section Heads", "Panel Members"}, rows);
    }

    /** Section heads or panel members (page = section-heads | panel-members) as Excel or PDF. */
    @GetMapping("/export-staff/{page}")
    public ResponseEntity<byte[]> exportStaff(@PathVariable String page,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        boolean heads = "section-heads".equals(page);
        List<List<String>> rows = userRepository.findByRoleRoleNameIgnoreCase(heads ? SECTION_HEAD : INTERVIEW_PANEL).stream()
                .sorted(Comparator.comparing((User u) -> u.getStream() == null ? "" : u.getStream().getStreamName())
                        .thenComparing(User::getName))
                .map(u -> List.of(Objects.toString(u.getName(), ""), u.getUserName(),
                        u.getStream() == null ? "-" : u.getStream().getStreamName()))
                .toList();
        return exportService.download(format, heads ? "section_heads" : "panel_members",
                heads ? "Section Heads" : "Interview Panel Members",
                new String[]{"Name", "Username", "Stream"}, rows);
    }


    // =====================================================
    // SYSTEM SETTINGS (marking scale)
    // =====================================================

    @GetMapping("/settings")
    public String settings(@RequestParam(required = false) Integer year,
                           @RequestParam(defaultValue = "general") String tab,
                           Model model) {
        int y = year != null ? year : settingsService.academicYear();

        List<MarkingConfig> configs = markingConfigRepository.findByYear(y).stream()
                .sorted(Comparator.comparing(MarkingConfig::getConfigId))
                .toList();

        // Group by criterion so each one renders as its own block
        Map<String, List<MarkingConfig>> grouped = new LinkedHashMap<>();
        for (MarkingConfig c : configs) {
            grouped.computeIfAbsent(c.getCriterionType(), k -> new ArrayList<>()).add(c);
        }

        List<Integer> years = markingConfigRepository.findAll().stream()
                .map(MarkingConfig::getYear).distinct().sorted(Comparator.reverseOrder()).toList();

        model.addAttribute("year", y);
        model.addAttribute("grouped", grouped);
        model.addAttribute("years", years);
        model.addAttribute("emailConfigured", emailService.isConfigured());
        model.addAttribute("emailSender", emailService.senderAddress());

        // Email settings form (the saved password itself is never sent back to the browser)
        MailConfigService.MailConfig mail = mailConfigService.current();
        Map<String, Object> mailForm = new LinkedHashMap<>();
        mailForm.put("host", mail.host());
        mailForm.put("port", mail.port());
        mailForm.put("security", mail.security());
        mailForm.put("username", mail.username().startsWith("YOUR_") ? "" : mail.username());
        mailForm.put("senderName", mail.senderName());
        mailForm.put("enabled", mail.enabled());
        mailForm.put("passwordSaved", mail.hasCredentials());
        model.addAttribute("mail", mailForm);
        model.addAttribute("settings", settingsService.snapshot());
        model.addAttribute("tab", year != null ? "marking" : tab);

        // System information
        model.addAttribute("info", Map.of(
                "students", studentRepository.count(),
                "applications", applicationRepository.count(),
                "staff", userRepository.count(),
                "schools", schoolRepository.count(),
                "streams", streamRepository.count(),
                "interviews", interviewRepository.count()));
        return "admin/settings";
    }

    @PostMapping("/settings/save")
    public String saveSettings(@RequestParam String section,
                               @RequestParam(required = false) Integer academicYear,
                               @RequestParam(required = false) String letterExamYear,
                               @RequestParam(required = false) String siteNotice,
                               @RequestParam(required = false) String registrationOpen,
                               @RequestParam(required = false) String applicationsOpen,
                               @RequestParam(required = false) String applicationDeadline,
                               @RequestParam(required = false) String defaultVenue,
                               @RequestParam(required = false) String defaultTime,
                               RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<>();

        switch (section) {
            case "general" -> {
                if (academicYear == null || academicYear < 2000 || academicYear > 2100) {
                    ra.addFlashAttribute("error", "Enter an academic year between 2000 and 2100.");
                    return "redirect:/admin/settings?tab=general";
                }
                String error = Validation.first(
                        Validation.optionalText(letterExamYear, "Letter exam year", 20),
                        Validation.optionalText(siteNotice, "Site notice", 500));
                if (error != null) {
                    ra.addFlashAttribute("error", error);
                    return "redirect:/admin/settings?tab=general";
                }
                values.put(SettingsService.ACADEMIC_YEAR, String.valueOf(academicYear));
                values.put(SettingsService.LETTER_EXAM_YEAR, letterExamYear == null ? "" : letterExamYear);
                values.put(SettingsService.SITE_NOTICE, siteNotice == null ? "" : siteNotice);
            }
            case "admissions" -> {
                if (!Validation.isBlank(applicationDeadline) && Validation.date(applicationDeadline) == null) {
                    ra.addFlashAttribute("error", "Enter a valid application deadline date.");
                    return "redirect:/admin/settings?tab=admissions";
                }
                // Unchecked switches are not submitted, so absence means "off"
                values.put(SettingsService.REGISTRATION_OPEN, String.valueOf(registrationOpen != null));
                values.put(SettingsService.APPLICATIONS_OPEN, String.valueOf(applicationsOpen != null));
                values.put(SettingsService.APPLICATION_DEADLINE, applicationDeadline == null ? "" : applicationDeadline);
            }
            case "interviews" -> {
                String error = Validation.first(
                        Validation.optionalText(defaultVenue, "Default venue", 150),
                        !Validation.isBlank(defaultTime) && Validation.time(defaultTime) == null
                                ? "Enter a valid default interview time." : null);
                if (error != null) {
                    ra.addFlashAttribute("error", error);
                    return "redirect:/admin/settings?tab=interviews";
                }
                values.put(SettingsService.DEFAULT_VENUE, defaultVenue == null ? "" : defaultVenue);
                values.put(SettingsService.DEFAULT_TIME, defaultTime == null || defaultTime.isBlank() ? "09:00" : defaultTime);
            }
            default -> {
                return "redirect:/admin/settings";
            }
        }

        settingsService.saveAll(values);
        ra.addFlashAttribute("success", "Settings saved.");
        return "redirect:/admin/settings?tab=" + section;
    }

    @PostMapping("/settings/email")
    public String saveEmailSettings(@RequestParam String username,
                                    @RequestParam(required = false) String password,
                                    @RequestParam(defaultValue = "smtp.gmail.com") String host,
                                    @RequestParam(defaultValue = "587") Integer port,
                                    @RequestParam(defaultValue = "STARTTLS") String security,
                                    @RequestParam(required = false) String senderName,
                                    @RequestParam(required = false) String enabled,
                                    @RequestParam(defaultValue = "save") String action,
                                    RedirectAttributes ra) {
        String address = username.trim();
        if (!address.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            ra.addFlashAttribute("error", "Enter a valid email address for the sending account.");
            return "redirect:/admin/settings?tab=email";
        }
        if (port < 1 || port > 65535) {
            ra.addFlashAttribute("error", "Enter a valid SMTP port (587 for Gmail).");
            return "redirect:/admin/settings?tab=email";
        }

        mailConfigService.save(host.trim(), port, security, address, password,
                senderName == null ? "" : senderName.trim(), enabled != null);

        if (!mailConfigService.current().hasCredentials()) {
            ra.addFlashAttribute("warning", "Email settings saved. Enter the App Password to start sending emails.");
            return "redirect:/admin/settings?tab=email";
        }

        if ("verify".equals(action)) {
            return verifyEmail(ra);
        }
        ra.addFlashAttribute("success", "Email settings saved." + (enabled != null ? "" : " Email sending is switched off."));
        return "redirect:/admin/settings?tab=email";
    }

    @PostMapping("/settings/email/verify")
    public String verifyEmail(RedirectAttributes ra) {
        MailConfigService.MailConfig mail = mailConfigService.current();
        if (!mail.hasCredentials()) {
            ra.addFlashAttribute("error", "Enter the Gmail address and App Password first.");
            return "redirect:/admin/settings?tab=email";
        }
        try {
            mailConfigService.testConnection();
            ra.addFlashAttribute("success", "Connected to " + mail.host() + " as " + mail.username()
                    + ". Email is working" + (mail.enabled() ? "." : ", but sending is switched off."));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Connection failed. " + MailConfigService.explain(e));
        }
        return "redirect:/admin/settings?tab=email";
    }

    @PostMapping("/settings/test-email")
    public String testEmail(@RequestParam String to, RedirectAttributes ra) {
        String invalid = Validation.email(to, "Email address");
        if (invalid != null) {
            ra.addFlashAttribute("error", invalid);
            return "redirect:/admin/settings?tab=email";
        }
        if (!emailService.isConfigured()) {
            ra.addFlashAttribute("error", mailConfigService.current().hasCredentials()
                    ? "Email sending is switched off. Turn it on and save first."
                    : "Enter the Gmail address and App Password and save them first.");
            return "redirect:/admin/settings?tab=email";
        }
        try {
            emailService.sendTestEmail(to.trim());
            ra.addFlashAttribute("success", "Test email sent to " + to.trim() + ". Check the inbox (and the spam folder).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "The test email could not be sent. " + MailConfigService.explain(e));
        }
        return "redirect:/admin/settings?tab=email";
    }

    @PostMapping("/marking-config/save")
    public String saveConfig(@RequestParam List<Integer> configId,
                             @RequestParam List<BigDecimal> marks,
                             @RequestParam Integer year,
                             RedirectAttributes ra) {
        if (configId.size() != marks.size()
                || marks.stream().anyMatch(m -> m == null || m.signum() < 0 || m.compareTo(BigDecimal.valueOf(100)) > 0)) {
            ra.addFlashAttribute("error", "Every mark must be a number between 0 and 100.");
            return "redirect:/admin/settings?year=" + year;
        }
        for (int i = 0; i < configId.size(); i++) {
            MarkingConfig config = markingConfigRepository.findById(configId.get(i)).orElseThrow();
            config.setMarks(marks.get(i));
            markingConfigRepository.save(config);
        }
        ra.addFlashAttribute("success", "Marking scale for " + year + " saved.");
        return "redirect:/admin/settings?year=" + year;
    }

    @PostMapping("/marking-config/copy")
    public String copyConfig(@RequestParam Integer fromYear, @RequestParam Integer toYear, RedirectAttributes ra) {
        if (toYear < 2000 || toYear > 2100) {
            ra.addFlashAttribute("error", "Enter a year between 2000 and 2100.");
            return "redirect:/admin/settings?tab=marking";
        }
        if (!markingConfigRepository.findByYear(toYear).isEmpty()) {
            ra.addFlashAttribute("error", "A marking scale for " + toYear + " already exists.");
            return "redirect:/admin/settings?year=" + toYear;
        }
        for (MarkingConfig src : markingConfigRepository.findByYear(fromYear)) {
            MarkingConfig copy = new MarkingConfig();
            copy.setYear(toYear);
            copy.setCriterionType(src.getCriterionType());
            copy.setOptionLabel(src.getOptionLabel());
            copy.setMarks(src.getMarks());
            markingConfigRepository.save(copy);
        }
        markingService.ensureScoredCriteria(toYear);
        ra.addFlashAttribute("success", "Marking scale copied from " + fromYear + " to " + toYear + ".");
        return "redirect:/admin/settings?year=" + toYear;
    }


    // =====================================================
    // PROFILE
    // =====================================================

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("user", userRepository.findByUserName(principal.getName()));
        return "admin/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Principal principal, @RequestParam String name, RedirectAttributes ra) {
        String error = accountService.updateStaffName(userRepository.findByUserName(principal.getName()), name);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your profile has been updated." : error);
        return "redirect:/admin/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        String error = accountService.changeStaffPassword(
                userRepository.findByUserName(principal.getName()), currentPassword, newPassword, confirmPassword);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your password has been changed." : error);
        return "redirect:/admin/profile";
    }


    // =====================================================
    // HELPERS
    // =====================================================

    /** One row per stream: application counts at each stage plus staff counts. */
    private List<Map<String, Object>> streamSummary(List<Application> apps) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<User> staff = userRepository.findAll();

        for (SubjectStream s : streamRepository.findAll()) {
            List<Application> mine = apps.stream()
                    .filter(a -> a.getStream().getStreamId().equals(s.getStreamId()))
                    .toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stream", s);
            row.put("applications", mine.size());
            row.put("pending", countStatus(mine, Status.APPLIED));
            row.put("shortlisted", mine.stream().filter(a -> Status.PASSED_SHORTLIST.contains(a.getStatus())).count());
            row.put("interviewed", mine.stream().filter(a -> Status.INTERVIEW_DONE.contains(a.getStatus())).count());
            row.put("selected", countStatus(mine, Status.SELECTED));
            row.put("heads", staff.stream().filter(u -> isRole(u, SECTION_HEAD) && inStream(u, s)).count());
            row.put("panel", staff.stream().filter(u -> isRole(u, INTERVIEW_PANEL) && inStream(u, s)).count());
            rows.add(row);
        }
        return rows;
    }

    private static long countStatus(List<Application> apps, String status) {
        return apps.stream().filter(a -> status.equals(a.getStatus())).count();
    }

    private static boolean isRole(User u, String roleName) {
        return u.getRole() != null && roleName.equalsIgnoreCase(u.getRole().getRoleName());
    }

    private static boolean inStream(User u, SubjectStream s) {
        return u.getStream() != null && u.getStream().getStreamId().equals(s.getStreamId());
    }

    private Role roleByName(String roleName) {
        return roleRepository.findAll().stream()
                .filter(r -> roleName.equalsIgnoreCase(r.getRoleName()))
                .findFirst()
                .orElseThrow();
    }

    /** Only allow redirects back to the two staff pages. */
    private static String safePage(String returnTo) {
        return "panel-members".equals(returnTo) ? "panel-members" : "section-heads";
    }
}
