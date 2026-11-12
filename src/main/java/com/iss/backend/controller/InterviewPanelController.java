package com.iss.backend.controller;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import com.iss.backend.service.AccountService;
import com.iss.backend.service.ExportService;
import com.iss.backend.service.InterviewMarkingService;
import com.iss.backend.web.ExtraCurricular;
import com.iss.backend.web.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/panel")
public class InterviewPanelController {

    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewMarkRepository interviewMarkRepository;
    @Autowired private OlResultRepository olResultRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InterviewMarkingService markingService;
    @Autowired private AccountService accountService;
    @Autowired private ExportService exportService;


    /** A panel member without a stream can only use their profile page. */
    static class NoStreamAssigned extends RuntimeException {}

    @ExceptionHandler(NoStreamAssigned.class)
    public String noStream(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "No stream has been assigned to your account. Please contact the administrator.");
        return "redirect:/panel/profile";
    }

    private User panelUser(Principal principal) {
        return userRepository.findByUserName(principal.getName());
    }

    /** Every interview for the panel member's stream, earliest first. */
    private List<Interview> streamInterviews(User panel) {
        if (panel.getStream() == null) throw new NoStreamAssigned();
        return interviewRepository.findByApplicationStreamStreamId(panel.getStream().getStreamId()).stream()
                .sorted(Comparator.comparing(Interview::getInterviewDate))
                .toList();
    }

    /** Loads an interview only if it belongs to the panel member's stream. */
    private Interview ownInterview(User panel, Integer interviewId) {
        if (panel.getStream() == null) throw new NoStreamAssigned();
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null || interview.getApplication() == null
                || !interview.getApplication().getStream().getStreamId().equals(panel.getStream().getStreamId())) {
            return null;
        }
        return interview;
    }

    private static boolean isPending(Interview i) {
        return !Status.COMPLETED.equals(i.getStatus());
    }


    // =====================================================
    // DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        User panel = panelUser(principal);
        List<Interview> all = streamInterviews(panel);
        LocalDate today = LocalDate.now();

        model.addAttribute("panelUser", panel);
        model.addAttribute("assignedCount", all.size());
        model.addAttribute("pendingCount", all.stream().filter(InterviewPanelController::isPending).count());
        model.addAttribute("completedCount", all.stream().filter(i -> !isPending(i)).count());
        model.addAttribute("todayCount", all.stream()
                .filter(i -> i.getInterviewDate().toLocalDate().equals(today)).count());
        model.addAttribute("next", all.stream().filter(InterviewPanelController::isPending).limit(6).toList());
        return "panel/dashboard";
    }


    // =====================================================
    // INTERVIEW LISTS
    // =====================================================

    @GetMapping("/assigned")
    public String assigned(Principal principal, Model model) {
        return list(model, panelUser(principal), "assigned", "Assigned Interviews",
                "All interviews scheduled for your stream.", streamInterviews(panelUser(principal)));
    }

    @GetMapping("/pending")
    public String pending(Principal principal, Model model) {
        User panel = panelUser(principal);
        return list(model, panel, "pending", "Pending Interviews",
                "Interviews that are waiting for marks.",
                streamInterviews(panel).stream().filter(InterviewPanelController::isPending).toList());
    }

    @GetMapping("/completed")
    public String completed(Principal principal, Model model) {
        User panel = panelUser(principal);
        return list(model, panel, "completed", "Completed Interviews",
                "Interviews that have been marked.",
                streamInterviews(panel).stream().filter(i -> !isPending(i)).toList());
    }

    private String list(Model model, User panel, String active, String title, String subtitle, List<Interview> interviews) {
        model.addAttribute("panelUser", panel);
        model.addAttribute("active", active);
        model.addAttribute("pageTitle", title);
        model.addAttribute("pageSubtitle", subtitle);
        model.addAttribute("interviews", interviews);
        return "panel/interviews";
    }


    // =====================================================
    // STUDENT DETAILS
    // =====================================================

    @GetMapping("/students")
    public String students(Principal principal, Model model) {
        User panel = panelUser(principal);
        model.addAttribute("panelUser", panel);
        model.addAttribute("interviews", streamInterviews(panel));
        return "panel/students";
    }

    @GetMapping("/student/{interviewId}")
    public String studentDetail(@PathVariable Integer interviewId, Principal principal,
                                Model model, RedirectAttributes ra) {
        User panel = panelUser(principal);
        Interview interview = ownInterview(panel, interviewId);
        if (interview == null) {
            ra.addFlashAttribute("error", "That student could not be found in your stream.");
            return "redirect:/panel/students";
        }
        addDetail(model, interview);
        return "panel/student-detail";
    }


    // =====================================================
    // INTERVIEW MARKING
    // =====================================================

    @GetMapping("/marking")
    public String marking(Principal principal, Model model) {
        User panel = panelUser(principal);
        model.addAttribute("panelUser", panel);
        model.addAttribute("interviews", streamInterviews(panel).stream()
                .filter(InterviewPanelController::isPending).toList());
        return "panel/marking";
    }

    @GetMapping("/interview/{interviewId}")
    public String markInterview(@PathVariable Integer interviewId, Principal principal,
                                Model model, RedirectAttributes ra) {
        User panel = panelUser(principal);
        Interview interview = ownInterview(panel, interviewId);
        if (interview == null) {
            ra.addFlashAttribute("error", "That interview could not be found in your stream.");
            return "redirect:/panel/marking";
        }
        addDetail(model, interview);

        // Pre-fill the grade counts from the submitted O/L results; the panel verifies them against the certificate
        Map<String, Long> gradeCounts = new HashMap<>();
        for (OlResult r : olResultRepository.findByApplicationApplicationId(interview.getApplication().getApplicationId())) {
            gradeCounts.merge(r.getGrade(), 1L, Long::sum);
        }
        model.addAttribute("gradeCounts", gradeCounts);
        model.addAttribute("olSubjectCount", InterviewMarkingService.OL_SUBJECT_COUNT);

        Integer scaleYear = markingService.scaleYear(interview.getInterviewDate().getYear());
        model.addAttribute("scaleYear", scaleYear);
        model.addAttribute("assessmentMax",
                markingService.maxMarks(scaleYear, InterviewMarkingService.INTERVIEW_ASSESSMENT));
        model.addAttribute("documentsMax",
                markingService.maxMarks(scaleYear, InterviewMarkingService.DOCUMENTS_ELIGIBILITY));
        model.addAttribute("extracurricularLevel",
                ExtraCurricular.highestLevel(interview.getApplication().getStudent().getExtraCurricularActivities()));
        return "panel/interview-marking";
    }

    @PostMapping("/interview/{interviewId}/submit-marks")
    public String submitMarks(
            @PathVariable Integer interviewId,
            Principal principal,
            @RequestParam int countA,
            @RequestParam int countB,
            @RequestParam int countC,
            @RequestParam int countS,
            @RequestParam int countW,
            @RequestParam String gnApproval,
            @RequestParam String alAvailable,
            @RequestParam(defaultValue = "NO") String oneSubjectAvailable,
            @RequestParam(defaultValue = "NO") String twoSubjectAvailable,
            @RequestParam String extracurricular,
            @RequestParam BigDecimal interviewAssessment,
            @RequestParam BigDecimal documentsEligibility,
            RedirectAttributes ra) {

        Interview interview = ownInterview(panelUser(principal), interviewId);
        if (interview == null) {
            ra.addFlashAttribute("error", "That interview could not be found in your stream.");
            return "redirect:/panel/marking";
        }

        try {
            markingService.saveInterviewMarks(
                    interviewId,
                    countA, countB, countC, countS, countW,
                    gnApproval, alAvailable, oneSubjectAvailable, twoSubjectAvailable, extracurricular,
                    interviewAssessment, documentsEligibility);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/panel/interview/" + interviewId;
        }

        ra.addFlashAttribute("success", "Marks saved for " + interview.getApplication().getStudent().getFullName()
                + ". Total score: " + markingService.getTotalScore(interviewId) + ".");
        return "redirect:/panel/results";
    }


    // =====================================================
    // DOWNLOADS (Excel / PDF)
    // =====================================================

    @GetMapping("/export/{list}")
    public ResponseEntity<byte[]> exportInterviews(@PathVariable String list,
                                                   @RequestParam(defaultValue = "xlsx") String format,
                                                   Principal principal) {
        User panel = panelUser(principal);
        List<Interview> interviews = streamInterviews(panel);
        String title = switch (list) {
            case "pending" -> {
                interviews = interviews.stream().filter(InterviewPanelController::isPending).toList();
                yield "Pending Interviews";
            }
            case "completed" -> {
                interviews = interviews.stream().filter(i -> !isPending(i)).toList();
                yield "Completed Interviews";
            }
            default -> "Assigned Interviews";
        };

        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("hh:mm a");
        List<List<String>> rows = interviews.stream().map(i -> List.of(
                i.getInterviewDate().format(date),
                i.getInterviewDate().format(time),
                i.getApplication().getStudent().getFullName(),
                Objects.toString(i.getApplication().getStudent().getIndexNumber(), ""),
                i.getApplication().getStream().getStreamName(),
                Objects.toString(i.getVenue(), ""),
                i.getUser() != null ? i.getUser().getName() : "",
                ExportService.statusLabel(i.getStatus()))).toList();

        String stream = panel.getStream().getStreamName();
        return exportService.download(format,
                stream.toLowerCase().replaceAll("\\W+", "_") + "_" + title.toLowerCase().replace(' ', '_'),
                stream + " - " + title,
                new String[]{"Date", "Time", "Student", "Index Number", "Stream", "Venue", "Assigned To", "Status"},
                rows);
    }


    // =====================================================
    // INTERVIEW RESULTS
    // =====================================================

    @GetMapping("/results")
    public String results(Principal principal, Model model) {
        User panel = panelUser(principal);
        List<Interview> done = streamInterviews(panel).stream().filter(i -> !isPending(i)).toList();

        Map<Integer, Map<String, BigDecimal>> breakdown = new HashMap<>();
        Map<Integer, BigDecimal> totals = new HashMap<>();
        for (Interview i : done) {
            Map<String, BigDecimal> marks = new HashMap<>();
            for (InterviewMark m : interviewMarkRepository.findByInterviewInterviewId(i.getInterviewId())) {
                marks.put(m.getCriteria(), m.getScore());
            }
            breakdown.put(i.getInterviewId(), marks);
            totals.put(i.getInterviewId(), markingService.getTotalScore(i.getInterviewId()));
        }

        model.addAttribute("panelUser", panel);
        model.addAttribute("interviews", done.stream()
                .sorted((a, b) -> totals.get(b.getInterviewId()).compareTo(totals.get(a.getInterviewId())))
                .toList());
        model.addAttribute("breakdown", breakdown);
        model.addAttribute("totals", totals);
        return "panel/results";
    }


    // =====================================================
    // PROFILE
    // =====================================================

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("user", panelUser(principal));
        return "panel/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Principal principal, @RequestParam String name, RedirectAttributes ra) {
        String error = accountService.updateStaffName(panelUser(principal), name);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your profile has been updated." : error);
        return "redirect:/panel/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        String error = accountService.changeStaffPassword(panelUser(principal),
                currentPassword, newPassword, confirmPassword);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your password has been changed." : error);
        return "redirect:/panel/profile";
    }


    // =====================================================
    // HELPERS
    // =====================================================

    private void addDetail(Model model, Interview interview) {
        Application app = interview.getApplication();
        model.addAttribute("interview", interview);
        model.addAttribute("app", app);
        model.addAttribute("student", app.getStudent());
        model.addAttribute("olResults", olResultRepository.findByApplicationApplicationId(app.getApplicationId()));
        model.addAttribute("marks", interviewMarkRepository.findByInterviewInterviewId(interview.getInterviewId()));
        model.addAttribute("totalScore", markingService.getTotalScore(interview.getInterviewId()));
    }
}
