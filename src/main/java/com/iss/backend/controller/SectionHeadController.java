package com.iss.backend.controller;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import com.iss.backend.service.AccountService;
import com.iss.backend.service.CutoffService;
import com.iss.backend.service.EmailService;
import com.iss.backend.service.ExportService;
import com.iss.backend.service.InterviewMarkingService;
import com.iss.backend.service.SelectionService;
import com.iss.backend.service.SettingsService;
import com.iss.backend.web.Filters;
import com.iss.backend.web.Status;
import com.iss.backend.web.Subjects;
import com.iss.backend.web.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/sectionhead")
public class SectionHeadController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");

    /** Notification.type for the "not selected for interview" letter. */
    public static final String NOT_QUALIFIED_LETTER = "NOT_QUALIFIED";

    @Autowired private UserRepository userRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private OlResultRepository olResultRepository;
    @Autowired private SelectionConditionRepository conditionRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewMarkRepository interviewMarkRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CutoffRecordRepository cutoffRecordRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private SelectionService selectionService;
    @Autowired private CutoffService cutoffService;
    @Autowired private InterviewMarkingService markingService;
    @Autowired private EmailService emailService;
    @Autowired private ExportService exportService;
    @Autowired private AccountService accountService;
    @Autowired private SettingsService settingsService;


    /** A section head without a stream can only use their profile page. */
    static class NoStreamAssigned extends RuntimeException {}

    @ExceptionHandler(NoStreamAssigned.class)
    public String noStream(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "No stream has been assigned to your account. Please contact the administrator.");
        return "redirect:/sectionhead/profile";
    }

    private User currentHead(Principal principal) {
        return userRepository.findByUserName(principal.getName());
    }

    private Integer streamId(User head) {
        if (head.getStream() == null) throw new NoStreamAssigned();
        return head.getStream().getStreamId();
    }

    private List<Application> streamApps(User head) {
        return applicationRepository.findByStreamStreamId(streamId(head));
    }


    // =====================================================
    // DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> apps = streamApps(head);
        int year = settingsService.academicYear();

        model.addAttribute("head", head);
        model.addAttribute("totalApplicants", apps.size());
        model.addAttribute("pendingCount", count(apps, Status.APPLIED));
        model.addAttribute("shortlistedCount", apps.stream().filter(a -> Status.PASSED_SHORTLIST.contains(a.getStatus())).count());
        model.addAttribute("scheduledCount", count(apps, Status.INTERVIEW_SCHEDULED));
        model.addAttribute("interviewCount", apps.stream().filter(a -> Status.INTERVIEW_DONE.contains(a.getStatus())).count());
        model.addAttribute("selectedCount", count(apps, Status.SELECTED));

        // Progress through the admission process for this year
        model.addAttribute("hasConditions", !conditionRepository.findByStreamStreamIdAndYear(streamId(head), year).isEmpty());
        model.addAttribute("cutoffRecord", cutoffRecordRepository.findByStreamStreamIdAndYear(streamId(head), year));

        model.addAttribute("recent", apps.stream()
                .sorted(Comparator.comparing(Application::getApplicationDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList());
        model.addAttribute("upcoming", upcomingInterviews(head).stream().limit(5).toList());
        return "sectionhead/dashboard";
    }


    // =====================================================
    // ALL APPLICANTS
    // =====================================================

    @GetMapping("/applicants")
    public String applicants(Principal principal,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String status,
                             Model model) {
        User head = currentHead(principal);
        List<Application> apps = streamApps(head);

        model.addAttribute("head", head);
        model.addAttribute("allApplicants", Filters.applications(apps, q, status, null));
        model.addAttribute("totalCount", apps.size());
        model.addAttribute("statuses", Status.ALL);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "sectionhead/applicants";
    }

    @GetMapping("/applicant-detail/{id}")
    public String applicantDetail(@PathVariable("id") Integer id, Principal principal,
                                  Model model, RedirectAttributes ra) {
        User head = currentHead(principal);
        Application app = applicationRepository.findApplicationWithDetails(id).orElse(null);

        if (app == null || !app.getStream().getStreamId().equals(streamId(head))) {
            ra.addFlashAttribute("error", "That applicant could not be found in your stream.");
            return "redirect:/sectionhead/applicants";
        }

        List<Interview> interviews = interviewRepository.findByApplicationApplicationId(id);
        Interview interview = interviews.isEmpty() ? null : interviews.get(0);

        // NOTE: must not be named "application" - Thymeleaf reserves
        // ${application} for the servlet context, which made student null.
        model.addAttribute("head", head);
        model.addAttribute("app", app);
        model.addAttribute("olResults", olResultRepository.findByApplicationApplicationId(id));
        model.addAttribute("interview", interview);
        model.addAttribute("marks", interview == null ? List.of()
                : interviewMarkRepository.findByInterviewInterviewId(interview.getInterviewId()));
        model.addAttribute("totalScore", interview == null ? null : markingService.getTotalScore(interview.getInterviewId()));
        return "sectionhead/applicant-detail";
    }

    // Old URL kept for compatibility - forwards to the detail page
    @GetMapping("/applicant/{applicationId}")
    public String viewApplicantDetail(@PathVariable("applicationId") Integer applicationId) {
        return "redirect:/sectionhead/applicant-detail/" + applicationId;
    }


    // =====================================================
    // SELECTION CONDITIONS
    // =====================================================

    @GetMapping("/conditions")
    public String conditions(Principal principal, @RequestParam(required = false) Integer year, Model model) {
        User head = currentHead(principal);
        int y = year != null ? year : settingsService.academicYear();

        model.addAttribute("head", head);
        model.addAttribute("year", y);
        model.addAttribute("conditions", conditionRepository.findByStreamStreamIdAndYear(streamId(head), y));
        model.addAttribute("olSubjects", Subjects.allOlSubjects());
        model.addAttribute("grades", Subjects.GRADES);
        return "sectionhead/conditions";
    }

    @PostMapping("/conditions/save")
    public String saveConditions(Principal principal,
                                 @RequestParam Integer year,
                                 @RequestParam(required = false) List<String> subjectName,
                                 @RequestParam(required = false) List<String> minGrade,
                                 RedirectAttributes ra) {
        User head = currentHead(principal);
        Integer streamId = streamId(head);

        // Check everything before replacing the saved conditions
        boolean valid = year >= 2000 && year <= 2100;
        if (valid && subjectName != null) {
            valid = minGrade != null && minGrade.size() == subjectName.size();
            for (int i = 0; valid && i < subjectName.size(); i++) {
                String subject = subjectName.get(i);
                if (subject == null || subject.isBlank()) continue;
                valid = Subjects.allOlSubjects().contains(subject) && Subjects.GRADES.contains(minGrade.get(i));
            }
        }
        if (!valid) {
            ra.addFlashAttribute("error", "Choose an O/L subject and a minimum grade (A, B, C, S or W) for every condition.");
            return "redirect:/sectionhead/conditions?year=" + year;
        }

        conditionRepository.deleteByStreamStreamIdAndYear(streamId, year);

        int saved = 0;
        Set<String> seen = new HashSet<>();
        if (subjectName != null) {
            for (int i = 0; i < subjectName.size(); i++) {
                String subject = subjectName.get(i);
                if (subject == null || subject.isBlank() || !seen.add(subject)) continue;
                SelectionCondition condition = new SelectionCondition();
                condition.setStream(head.getStream());
                condition.setYear(year);
                condition.setSubjectName(subject);
                condition.setMinGrade(minGrade.get(i));
                conditionRepository.save(condition);
                saved++;
            }
        }

        ra.addFlashAttribute("success", saved + " selection conditions saved for " + year + ".");
        return "redirect:/sectionhead/conditions?year=" + year;
    }

    // Old URL
    @GetMapping("/selection")
    public String selectionOld() {
        return "redirect:/sectionhead/conditions";
    }


    // =====================================================
    // AUTO SELECTION
    // =====================================================

    @GetMapping("/auto-selection")
    public String autoSelection(Principal principal, @RequestParam(required = false) Integer year, Model model) {
        User head = currentHead(principal);
        int y = year != null ? year : settingsService.academicYear();
        List<Application> apps = streamApps(head);

        model.addAttribute("head", head);
        model.addAttribute("year", y);
        model.addAttribute("conditions", conditionRepository.findByStreamStreamIdAndYear(streamId(head), y));
        model.addAttribute("totalApplicants", apps.size());
        model.addAttribute("pendingCount", count(apps, Status.APPLIED));
        model.addAttribute("shortlistedCount", count(apps, Status.SHORTLISTED));
        model.addAttribute("notQualifiedCount", count(apps, Status.NOT_QUALIFIED));
        model.addAttribute("unnotifiedCount", unnotifiedNotQualified(head).size());
        model.addAttribute("emailConfigured", emailService.isConfigured());
        model.addAttribute("lockedCount", apps.stream()
                .filter(a -> List.of(Status.INTERVIEW_SCHEDULED, Status.INTERVIEWED, Status.SELECTED, Status.NOT_SELECTED)
                        .contains(a.getStatus()))
                .count());
        return "sectionhead/auto-selection";
    }

    @PostMapping("/run-selection")
    public String runSelection(Principal principal, @RequestParam Integer year, RedirectAttributes ra) {
        User head = currentHead(principal);
        Integer streamId = streamId(head);

        if (conditionRepository.findByStreamStreamIdAndYear(streamId, year).isEmpty()) {
            ra.addFlashAttribute("error", "Set the selection conditions for " + year + " before running auto selection.");
            return "redirect:/sectionhead/conditions?year=" + year;
        }

        int shortlisted = selectionService.runSelection(streamId, year);
        long notQualified = count(applicationRepository.findByStreamStreamId(streamId), Status.NOT_QUALIFIED);

        ra.addFlashAttribute("success", "Auto selection complete: " + shortlisted
                + " students shortlisted, " + notQualified + " did not meet the conditions.");
        notifyNotQualified(head, ra);
        return "redirect:/sectionhead/shortlisted";
    }

    /** Emails the "not selected for interview" letter to not-qualified students who have not received it yet. */
    @PostMapping("/notify-not-qualified")
    public String sendNotQualifiedLetters(Principal principal, RedirectAttributes ra) {
        User head = currentHead(principal);
        if (unnotifiedNotQualified(head).isEmpty()) {
            ra.addFlashAttribute("success", "Every not-qualified student has already been emailed.");
        } else {
            notifyNotQualified(head, ra);
        }
        return "redirect:/sectionhead/auto-selection";
    }

    private void notifyNotQualified(User head, RedirectAttributes ra) {
        List<Application> pending = unnotifiedNotQualified(head);
        if (pending.isEmpty()) return;

        int sent = 0;
        for (Application app : pending) {
            try {
                emailService.sendNotQualifiedLetter(app.getStudent().getEmail(), app.getStudent().getFullName(),
                        app.getStream().getStreamName(), app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress());
                saveNotification(app, NOT_QUALIFIED_LETTER, "SENT");
                sent++;
            } catch (Exception e) {
                saveNotification(app, NOT_QUALIFIED_LETTER, "FAILED");
            }
        }
        if (sent < pending.size()) {
            ra.addFlashAttribute("warning", (pending.size() - sent) + " of " + pending.size()
                    + " not-qualified letters could not be emailed"
                    + (emailService.isConfigured() ? "." : " because email is not set up yet (Admin > System Settings > Email).")
                    + " Students can still read the letter in the Student Portal.");
        } else {
            ra.addFlashAttribute("info", "Not-qualified letters emailed to " + sent + (sent == 1 ? " student." : " students."));
        }
    }

    /** Not-qualified applicants in the stream who have no successfully sent not-qualified letter. */
    private List<Application> unnotifiedNotQualified(User head) {
        return streamApps(head).stream()
                .filter(a -> Status.NOT_QUALIFIED.equals(a.getStatus()))
                .filter(a -> notificationRepository.findByApplicationApplicationId(a.getApplicationId()).stream()
                        .noneMatch(n -> NOT_QUALIFIED_LETTER.equals(n.getType()) && "SENT".equals(n.getStatus())))
                .toList();
    }


    // =====================================================
    // SHORTLISTED STUDENTS
    // =====================================================

    @GetMapping("/shortlisted")
    public String shortlisted(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> list = streamApps(head).stream()
                .filter(a -> Status.SHORTLISTED.equals(a.getStatus()) || Status.INTERVIEW_SCHEDULED.equals(a.getStatus()))
                .toList();

        model.addAttribute("head", head);
        model.addAttribute("shortlisted", list);
        model.addAttribute("interviewByApp", interviewsByApp(list));
        return "sectionhead/shortlisted";
    }


    // =====================================================
    // INTERVIEW INVITATIONS
    // =====================================================

    @GetMapping("/invitations")
    public String invitations(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> toInvite = streamApps(head).stream()
                .filter(a -> Status.SHORTLISTED.equals(a.getStatus()))
                .toList();

        School school = toInvite.isEmpty() ? firstSchool() : toInvite.get(0).getSchool();

        model.addAttribute("head", head);
        model.addAttribute("toInvite", toInvite);
        model.addAttribute("upcoming", upcomingInterviews(head));
        model.addAttribute("panelMembers",
                userRepository.findByRoleRoleNameIgnoreCaseAndStreamStreamId("Interview Panel", streamId(head)));
        model.addAttribute("emailConfigured", emailService.isConfigured());
        model.addAttribute("defaultVenue", settingsService.defaultVenue());
        model.addAttribute("defaultTime", settingsService.defaultTime());
        model.addAttribute("minInterviewDate", LocalDate.now().plusDays(1));
        model.addAttribute("maxInterviewDate", LocalDate.now().plusYears(1));
        model.addAttribute("letterTemplate", emailService.interviewLetter(
                school != null ? school.getSchoolName() : "[School Name]",
                school != null ? school.getSchoolAddress() : "[School Address]",
                "[Student Name]", head.getStream().getStreamName(),
                "[Interview Date]", "[Interview Time]", "[Interview Venue]"));
        return "sectionhead/invitations";
    }

    @PostMapping("/send-invitations")
    public String sendInvitations(Principal principal,
                                  @RequestParam(value = "applicationIds", required = false) List<Integer> applicationIds,
                                  @RequestParam("interviewDate") String interviewDate,
                                  @RequestParam("interviewTime") String interviewTime,
                                  @RequestParam("venue") String venue,
                                  @RequestParam(value = "panelUserId", required = false) Integer panelUserId,
                                  RedirectAttributes ra) {
        User head = currentHead(principal);
        Integer streamId = streamId(head);

        if (applicationIds == null || applicationIds.isEmpty()) {
            ra.addFlashAttribute("error", "Select at least one student to invite.");
            return "redirect:/sectionhead/invitations";
        }

        String invalid = checkSchedule(interviewDate, interviewTime, venue);
        if (invalid != null) {
            ra.addFlashAttribute("error", invalid);
            return "redirect:/sectionhead/invitations";
        }
        LocalDate date = LocalDate.parse(interviewDate);
        LocalTime time = LocalTime.parse(interviewTime);

        // The interview is assigned to the chosen panel member (or the section head if the stream has none)
        User assignee = head;
        if (panelUserId != null) {
            assignee = userRepository.findByRoleRoleNameIgnoreCaseAndStreamStreamId("Interview Panel", streamId).stream()
                    .filter(u -> u.getUserId().equals(panelUserId))
                    .findFirst().orElse(null);
            if (assignee == null) {
                ra.addFlashAttribute("error", "Choose a panel member from your own stream.");
                return "redirect:/sectionhead/invitations";
            }
        }

        int created = 0, emailed = 0;
        for (Integer applicationId : applicationIds) {
            Application app = applicationRepository.findById(applicationId).orElse(null);
            if (app == null || !app.getStream().getStreamId().equals(streamId)
                    || !Status.SHORTLISTED.equals(app.getStatus())) {
                continue;
            }

            Interview interview = new Interview();
            interview.setApplication(app);
            interview.setUser(assignee);
            interview.setInterviewDate(LocalDateTime.of(date, time));
            interview.setVenue(venue.trim());
            interview.setStatus(Status.SCHEDULED);
            interviewRepository.save(interview);

            app.setStatus(Status.INTERVIEW_SCHEDULED);
            applicationRepository.save(app);
            created++;

            try {
                emailService.sendInterviewInvitation(
                        app.getStudent().getEmail(), app.getStudent().getFullName(),
                        app.getStream().getStreamName(), app.getSchool().getSchoolName(),
                        app.getSchool().getSchoolAddress(),
                        date.format(DMY), time.format(TIME), venue.trim());
                saveNotification(app, "INTERVIEW_INVITATION", "SENT");
                emailed++;
            } catch (Exception e) {
                saveNotification(app, "INTERVIEW_INVITATION", "FAILED");
            }
        }

        ra.addFlashAttribute("success", created + " interviews scheduled for " + date.format(DMY) + ".");
        if (emailed < created) {
            ra.addFlashAttribute("warning", (created - emailed) + " invitation emails could not be sent"
                    + (emailService.isConfigured() ? "." : " because email is not set up yet (Admin > System Settings > Email).")
                    + " Students can still see their interview details in the Student Portal.");
        }
        return "redirect:/sectionhead/invitations";
    }

    @PostMapping("/invitations/{interviewId}/cancel")
    public String cancelInterview(@PathVariable Integer interviewId, Principal principal, RedirectAttributes ra) {
        User head = currentHead(principal);
        Interview interview = interviewRepository.findById(interviewId).orElse(null);

        if (interview == null || !interview.getApplication().getStream().getStreamId().equals(streamId(head))
                || Status.COMPLETED.equals(interview.getStatus())) {
            ra.addFlashAttribute("error", "That interview cannot be cancelled.");
            return "redirect:/sectionhead/invitations";
        }

        Application app = interview.getApplication();
        interviewRepository.delete(interview);
        app.setStatus(Status.SHORTLISTED);
        applicationRepository.save(app);

        ra.addFlashAttribute("success", "Interview for " + app.getStudent().getFullName()
                + " cancelled. The student is back on the shortlist.");
        return "redirect:/sectionhead/invitations";
    }

    /** Changes an interview's date, time and venue, and optionally emails the updated letter. */
    @PostMapping("/invitations/{interviewId}/update")
    public String rescheduleInterview(@PathVariable Integer interviewId, Principal principal,
                                      @RequestParam("interviewDate") String interviewDate,
                                      @RequestParam("interviewTime") String interviewTime,
                                      @RequestParam("venue") String venue,
                                      @RequestParam(value = "notify", required = false) String notify,
                                      RedirectAttributes ra) {
        User head = currentHead(principal);
        Interview interview = interviewRepository.findById(interviewId).orElse(null);

        if (interview == null || !interview.getApplication().getStream().getStreamId().equals(streamId(head))
                || Status.COMPLETED.equals(interview.getStatus())) {
            ra.addFlashAttribute("error", "That interview cannot be changed.");
            return "redirect:/sectionhead/invitations";
        }

        String invalid = checkSchedule(interviewDate, interviewTime, venue);
        if (invalid != null) {
            ra.addFlashAttribute("error", invalid);
            return "redirect:/sectionhead/invitations";
        }
        LocalDate date = LocalDate.parse(interviewDate);
        LocalTime time = LocalTime.parse(interviewTime);
        interview.setInterviewDate(LocalDateTime.of(date, time));
        interview.setVenue(venue.trim());
        interviewRepository.save(interview);

        Application app = interview.getApplication();
        String name = app.getStudent().getFullName();

        if (notify == null) {
            ra.addFlashAttribute("success", "Interview for " + name + " updated.");
            return "redirect:/sectionhead/invitations";
        }

        try {
            emailService.sendInterviewInvitation(app.getStudent().getEmail(), name,
                    app.getStream().getStreamName(), app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress(),
                    date.format(DMY), time.format(TIME), venue.trim());
            saveNotification(app, "INTERVIEW_INVITATION", "SENT");
            ra.addFlashAttribute("success", "Interview for " + name + " updated and the new letter was emailed.");
        } catch (Exception e) {
            saveNotification(app, "INTERVIEW_INVITATION", "FAILED");
            ra.addFlashAttribute("warning", "Interview for " + name + " updated, but the email could not be sent"
                    + (emailService.isConfigured() ? "." : " because email is not set up yet (Admin > System Settings > Email).")
                    + " The new details are shown in the Student Portal.");
        }
        return "redirect:/sectionhead/invitations";
    }

    // Old URLs
    @GetMapping({"/interviews", "/notifications"})
    public String interviewsOld() {
        return "redirect:/sectionhead/invitations";
    }


    // =====================================================
    // INTERVIEWED STUDENTS
    // =====================================================

    @GetMapping("/interviewed")
    public String interviewed(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> list = streamApps(head).stream()
                .filter(a -> Status.INTERVIEW_DONE.contains(a.getStatus()))
                .toList();

        Map<Integer, BigDecimal> scores = scores(list);
        model.addAttribute("head", head);
        model.addAttribute("interviewed", sortByScore(list, scores));
        model.addAttribute("scores", scores);
        model.addAttribute("interviewByApp", interviewsByApp(list));
        return "sectionhead/interviewed";
    }


    // =====================================================
    // CUTOFF CALCULATION
    // =====================================================

    @GetMapping("/cutoff")
    public String cutoff(Principal principal, @RequestParam(required = false) Integer year, Model model) {
        User head = currentHead(principal);
        int y = year != null ? year : settingsService.academicYear();
        List<Application> ranked = streamApps(head).stream()
                .filter(a -> Status.INTERVIEW_DONE.contains(a.getStatus()))
                .toList();
        Map<Integer, BigDecimal> scores = scores(ranked);

        model.addAttribute("head", head);
        model.addAttribute("year", y);
        model.addAttribute("cutoffRecord", cutoffRecordRepository.findByStreamStreamIdAndYear(streamId(head), y));
        model.addAttribute("ranked", sortByScore(ranked, scores));
        model.addAttribute("scores", scores);
        model.addAttribute("awaitingCount", count(ranked, Status.INTERVIEWED));
        return "sectionhead/cutoff";
    }

    @PostMapping("/cutoff/set-capacity")
    public String setCapacity(Principal principal, @RequestParam Integer year,
                              @RequestParam Integer totalCapacity, @RequestParam Integer internalStudentsCount,
                              RedirectAttributes ra) {
        User head = currentHead(principal);
        streamId(head);

        if (totalCapacity < 0 || internalStudentsCount < 0 || internalStudentsCount > totalCapacity) {
            ra.addFlashAttribute("error", "Internal students cannot be more than the total capacity.");
            return "redirect:/sectionhead/cutoff?year=" + year;
        }

        School school = firstSchool(); // single-school system
        cutoffService.setCapacity(head.getStream(), school, year, totalCapacity, internalStudentsCount);
        int seats = totalCapacity - internalStudentsCount;
        ra.addFlashAttribute("success", "Capacity saved: " + seats
                + (seats == 1 ? " seat" : " seats") + " available for external applicants.");
        return "redirect:/sectionhead/cutoff?year=" + year;
    }

    @PostMapping("/cutoff/run")
    public String runCutoff(Principal principal, @RequestParam Integer year, RedirectAttributes ra) {
        User head = currentHead(principal);
        String message = cutoffService.runCutoffSelection(streamId(head), year);

        boolean ok = message.startsWith("Selection complete");
        ra.addFlashAttribute(ok ? "success" : "error", message);
        return ok ? "redirect:/sectionhead/final-selection" : "redirect:/sectionhead/cutoff?year=" + year;
    }


    // =====================================================
    // FINAL SELECTION
    // =====================================================

    @GetMapping("/final-selection")
    public String finalSelection(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> all = streamApps(head);
        List<Application> selected = all.stream().filter(a -> Status.SELECTED.equals(a.getStatus())).toList();
        List<Application> notSelected = all.stream().filter(a -> Status.NOT_SELECTED.equals(a.getStatus())).toList();

        Map<Integer, BigDecimal> scores = new HashMap<>(scores(selected));
        scores.putAll(scores(notSelected));

        model.addAttribute("head", head);
        model.addAttribute("selected", sortByScore(selected, scores));
        model.addAttribute("notSelected", sortByScore(notSelected, scores));
        model.addAttribute("scores", scores);
        model.addAttribute("cutoffRecord",
                cutoffRecordRepository.findByStreamStreamIdAndYear(streamId(head), settingsService.academicYear()));
        return "sectionhead/final-selection";
    }

    @GetMapping("/export-selected")
    public ResponseEntity<byte[]> exportSelected(Principal principal,
                                                 @RequestParam(defaultValue = "xlsx") String format) {
        User head = currentHead(principal);
        List<Application> selected = streamApps(head).stream()
                .filter(a -> Status.SELECTED.equals(a.getStatus()))
                .toList();
        Map<Integer, BigDecimal> scores = scores(selected);

        String[] headers = Arrays.copyOf(ExportService.APPLICATION_HEADERS, ExportService.APPLICATION_HEADERS.length + 2);
        headers[headers.length - 2] = "Interview Score";
        headers[headers.length - 1] = "Rank";
        List<Application> ranked = sortByScore(selected, scores);
        List<List<String>> rows = new ArrayList<>();
        List<List<String>> base = ExportService.applicationRows(ranked);
        for (int i = 0; i < ranked.size(); i++) {
            List<String> row = new ArrayList<>(base.get(i));
            row.add(scores.getOrDefault(ranked.get(i).getApplicationId(), BigDecimal.ZERO).toPlainString());
            row.add(String.valueOf(i + 1));
            rows.add(row);
        }

        String stream = head.getStream().getStreamName();
        return exportService.download(format,
                stream.toLowerCase().replaceAll("\\W+", "_") + "_selected_students",
                stream + " Stream - Selected Students", headers, rows);
    }


    // =====================================================
    // FINAL NOTIFICATIONS
    // =====================================================

    @GetMapping("/final-notifications")
    public String finalNotifications(Principal principal, Model model) {
        User head = currentHead(principal);
        List<Application> all = streamApps(head);
        School school = all.isEmpty() ? firstSchool() : all.get(0).getSchool();
        String schoolName = school != null ? school.getSchoolName() : "[School Name]";
        String schoolAddress = school != null ? school.getSchoolAddress() : "[School Address]";
        String stream = head.getStream().getStreamName();

        model.addAttribute("head", head);
        model.addAttribute("selectedCount", count(all, Status.SELECTED));
        model.addAttribute("notSelectedCount", count(all, Status.NOT_SELECTED));
        model.addAttribute("emailConfigured", emailService.isConfigured());
        model.addAttribute("admission", settingsService.admissionDetails(streamId(head)));
        model.addAttribute("selectedLetter", emailService.admissionLetter(schoolName, schoolAddress, stream,
                "[Admission Date]", "[Admission Time]", "[Venue]"));
        model.addAttribute("notSelectedLetter", emailService.regretLetter("[Student Name]", stream, schoolName, schoolAddress));
        model.addAttribute("history", notificationRepository.findByApplicationStreamStreamIdOrderBySentDateDesc(streamId(head)));
        return "sectionhead/final-notifications";
    }

    /**
     * Saves the admission date, time and venue (shown to selected students in the portal)
     * and, unless "save only" was chosen, emails the admission letter to every selected student.
     */
    @PostMapping("/send-final-notifications")
    public String sendFinalNotifications(Principal principal,
                                         @RequestParam String admissionDate,
                                         @RequestParam String admissionTime,
                                         @RequestParam String venue,
                                         @RequestParam(defaultValue = "send") String action,
                                         @RequestParam(required = false) String notifyNotSelected,
                                         RedirectAttributes ra) {
        User head = currentHead(principal);
        Integer streamId = streamId(head);

        LocalDate d = Validation.date(admissionDate);
        LocalTime t = Validation.time(admissionTime);
        String invalid = d == null || t == null ? "Enter a valid admission date and time."
                : d.isBefore(LocalDate.now()) ? "The admission date cannot be in the past."
                : Validation.text(venue, "Venue", 150);
        if (invalid != null) {
            ra.addFlashAttribute("error", invalid);
            return "redirect:/sectionhead/final-notifications";
        }
        settingsService.saveAdmissionDetails(streamId, d.toString(), t.toString(), venue.trim());

        if ("save".equals(action)) {
            ra.addFlashAttribute("success", "Admission details saved. Selected students can see them in the Student Portal.");
            return "redirect:/sectionhead/final-notifications";
        }

        List<Application> selected = streamApps(head).stream()
                .filter(a -> Status.SELECTED.equals(a.getStatus()))
                .toList();
        List<Application> notSelected = notifyNotSelected == null ? List.of() : streamApps(head).stream()
                .filter(a -> Status.NOT_SELECTED.equals(a.getStatus()))
                .toList();

        if (selected.isEmpty() && notSelected.isEmpty()) {
            ra.addFlashAttribute("error", "There are no selected students to notify yet. Run the cutoff calculation first.");
            return "redirect:/sectionhead/final-notifications";
        }

        String date = d.format(DMY);
        String time = t.format(TIME);
        int sent = 0, total = selected.size() + notSelected.size();

        for (Application app : selected) {
            try {
                emailService.sendAdmissionLetter(app.getStudent().getEmail(), app.getStream().getStreamName(),
                        app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress(), date, time, venue.trim());
                saveNotification(app, "FINAL_RESULT", "SENT");
                sent++;
            } catch (Exception e) {
                saveNotification(app, "FINAL_RESULT", "FAILED");
            }
        }
        for (Application app : notSelected) {
            try {
                emailService.sendRegretLetter(app.getStudent().getEmail(), app.getStudent().getFullName(),
                        app.getStream().getStreamName(), app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress());
                saveNotification(app, "FINAL_RESULT", "SENT");
                sent++;
            } catch (Exception e) {
                saveNotification(app, "FINAL_RESULT", "FAILED");
            }
        }

        if (sent == total) {
            ra.addFlashAttribute("success", "Letters emailed to " + sent + (sent == 1 ? " student." : " students."));
        } else {
            ra.addFlashAttribute("warning", sent + " of " + total + " letters were emailed"
                    + (emailService.isConfigured() ? "." : " because email is not set up yet (Admin > System Settings > Email).")
                    + " The admission details are still shown to students in the Student Portal.");
        }
        return "redirect:/sectionhead/final-notifications";
    }

    // Old URL
    @GetMapping("/reports")
    public String reportsOld() {
        return "redirect:/sectionhead/final-selection";
    }


    // =====================================================
    // PROFILE
    // =====================================================

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("user", currentHead(principal));
        return "sectionhead/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Principal principal, @RequestParam String name, RedirectAttributes ra) {
        String error = accountService.updateStaffName(currentHead(principal), name);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your profile has been updated." : error);
        return "redirect:/sectionhead/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        String error = accountService.changeStaffPassword(currentHead(principal),
                currentPassword, newPassword, confirmPassword);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your password has been changed." : error);
        return "redirect:/sectionhead/profile";
    }


    // =====================================================
    // HELPERS
    // =====================================================

    /** Interviews must be scheduled for a future date (tomorrow or later) with a venue. Returns an error or null. */
    private static String checkSchedule(String interviewDate, String interviewTime, String venue) {
        LocalDate date;
        try {
            date = LocalDate.parse(interviewDate);
            LocalTime.parse(interviewTime);
        } catch (Exception e) {
            return "Enter a valid interview date and time.";
        }
        if (!date.isAfter(LocalDate.now())) {
            return "The interview date must be a future date (from " + LocalDate.now().plusDays(1).format(DMY) + " onwards).";
        }
        if (date.isAfter(LocalDate.now().plusYears(1))) {
            return "The interview date must be within the next year.";
        }
        if (venue == null || venue.isBlank()) {
            return "Enter the interview venue.";
        }
        if (venue.trim().length() > 150) {
            return "The venue must be 150 characters or fewer.";
        }
        return null;
    }

    private List<Interview> upcomingInterviews(User head) {
        return interviewRepository.findByApplicationStreamStreamId(streamId(head)).stream()
                .filter(i -> Status.SCHEDULED.equals(i.getStatus()))
                .sorted(Comparator.comparing(Interview::getInterviewDate))
                .toList();
    }

    private Map<Integer, Interview> interviewsByApp(List<Application> apps) {
        Map<Integer, Interview> map = new HashMap<>();
        for (Application a : apps) {
            List<Interview> list = interviewRepository.findByApplicationApplicationId(a.getApplicationId());
            if (!list.isEmpty()) map.put(a.getApplicationId(), list.get(0));
        }
        return map;
    }

    private Map<Integer, BigDecimal> scores(List<Application> apps) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (Application a : apps) {
            List<Interview> list = interviewRepository.findByApplicationApplicationId(a.getApplicationId());
            map.put(a.getApplicationId(), list.isEmpty() ? BigDecimal.ZERO
                    : markingService.getTotalScore(list.get(0).getInterviewId()));
        }
        return map;
    }

    private static List<Application> sortByScore(List<Application> apps, Map<Integer, BigDecimal> scores) {
        return apps.stream()
                .sorted((a, b) -> scores.getOrDefault(b.getApplicationId(), BigDecimal.ZERO)
                        .compareTo(scores.getOrDefault(a.getApplicationId(), BigDecimal.ZERO)))
                .toList();
    }

    private void saveNotification(Application app, String type, String status) {
        Notification n = new Notification();
        n.setApplication(app);
        n.setType(type);
        n.setSentDate(LocalDateTime.now());
        n.setStatus(status);
        notificationRepository.save(n);
    }

    private School firstSchool() {
        List<School> schools = schoolRepository.findAll();
        return schools.isEmpty() ? null : schools.get(0);
    }

    private static long count(List<Application> apps, String status) {
        return apps.stream().filter(a -> status.equals(a.getStatus())).count();
    }
}
