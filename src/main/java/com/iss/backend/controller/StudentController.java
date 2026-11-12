package com.iss.backend.controller;

import com.iss.backend.entity.*;
import com.iss.backend.repository.*;
import com.iss.backend.service.AccountService;
import com.iss.backend.service.EmailService;
import com.iss.backend.service.SettingsService;
import com.iss.backend.web.ExtraCurricular;
import com.iss.backend.web.Status;
import com.iss.backend.web.Subjects;
import com.iss.backend.web.TimelineStep;
import com.iss.backend.web.Validation;

import static com.iss.backend.web.Validation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Controller
public class StudentController {

    @Autowired private StudentRepository studentRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private SubjectStreamRepository subjectStreamRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private OlResultRepository olResultRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EmailService emailService;
    @Autowired private AccountService accountService;
    @Autowired private SettingsService settingsService;

    /** Oldest accepted Date of Birth, in years before today (catches mistyped years). */
    private static final int MAX_AGE_YEARS = 30;

    private static void requireText(List<String> errors, String value, String label, int maxLength) {
        String error = Validation.text(value, label, maxLength);
        if (error != null) errors.add(error);
    }

    private static LocalDate parseDate(String s) {
        return Validation.date(s);
    }

    private Student student(Principal principal) {
        return studentRepository.findByUsername(principal.getName());
    }

    private Application applicationOf(Student student) {
        List<Application> apps = applicationRepository.findByStudentStudentId(student.getStudentId());
        return apps.isEmpty() ? null : apps.get(0);
    }

    private Interview interviewOf(Application app) {
        if (app == null) return null;
        List<Interview> list = interviewRepository.findByApplicationApplicationId(app.getApplicationId());
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Common model for every student page.
     * NOTE: the application is exposed as "app" - Thymeleaf reserves ${application}.
     */
    private Application addCommon(Principal principal, Model model) {
        Student student = student(principal);
        Application app = applicationOf(student);
        model.addAttribute("student", student);
        model.addAttribute("app", app);
        return app;
    }


    // =====================================================
    // DASHBOARD
    // =====================================================

    @GetMapping("/student/dashboard")
    public String dashboard(Principal principal, Model model) {
        Application app = addCommon(principal, model);
        model.addAttribute("steps", TimelineStep.forApplication(app));
        model.addAttribute("interview", interviewOf(app));
        model.addAttribute("applicationsOpen", settingsService.applicationsOpen());
        model.addAttribute("applicationDeadline", settingsService.applicationDeadline());
        model.addAttribute("admissionReady", admissionLetterFor(app) != null);
        return "student/dashboard";
    }


    // =====================================================
    // MY APPLICATION
    // =====================================================

    @GetMapping("/student/application")
    public String application(Principal principal, Model model) {
        Application app = addCommon(principal, model);

        if (app != null) {
            model.addAttribute("olResults", olResultRepository.findByApplicationApplicationId(app.getApplicationId()));
            return "student/application";
        }

        addFormModel(model);
        return "student/application-form";
    }

    private void addFormModel(Model model) {
        model.addAttribute("applicationsOpen", settingsService.applicationsOpen());
        model.addAttribute("applicationDeadline", settingsService.applicationDeadline());
        model.addAttribute("schools", schoolRepository.findAll());
        model.addAttribute("streams", subjectStreamRepository.findAll());
        model.addAttribute("coreSubjects", Subjects.CORE_SUBJECTS);
        model.addAttribute("category1Subjects", Subjects.CATEGORY_1_SUBJECTS);
        model.addAttribute("category2Subjects", Subjects.CATEGORY_2_SUBJECTS);
        model.addAttribute("category3Subjects", Subjects.CATEGORY_3_SUBJECTS);
        model.addAttribute("grades", Subjects.GRADES);
        model.addAttribute("extraCurricularLevels", ExtraCurricular.LEVELS);
        // Dates of birth and school entry must be in the past (yesterday at the latest)
        model.addAttribute("latestPastDate", LocalDate.now().minusDays(1));
        model.addAttribute("earliestBirthDate", LocalDate.now().minusYears(MAX_AGE_YEARS));
    }

    // Old URL
    @GetMapping("/register")
    public String showForm() {
        return "redirect:/student/application";
    }

    @PostMapping({"/student/application", "/register"})
    public String submitForm(
            Principal principal,
            @RequestParam String fullName,
            @RequestParam String nameWithInitials,
            @RequestParam String permanentAddress,
            @RequestParam String phoneNumber,
            @RequestParam(required = false) String whatsappNumber,
            @RequestParam String gender,
            @RequestParam String dateOfBirth,
            @RequestParam String learningMedium,
            @RequestParam String email,
            @RequestParam String guardianName,
            @RequestParam String guardianPhone,
            @RequestParam Integer ageYears,
            @RequestParam Integer ageMonths,
            @RequestParam Integer ageDays,
            @RequestParam String nic,
            @RequestParam String olSchoolAddress,
            @RequestParam String olSchoolAdmissionNumber,
            @RequestParam String district,
            @RequestParam String dateEnteredSchool,
            @RequestParam String gradeEntered,

            @RequestParam(required = false) List<String> extraCurricular,
            @RequestParam String indexNumber,
            @RequestParam List<String> subjectName,
            @RequestParam List<String> grade,
            @RequestParam(required = false) Integer streamId,
            @RequestParam String desiredSubject1,
            @RequestParam String desiredSubject2,
            @RequestParam String desiredSubject3,
            @RequestParam(required = false) Integer schoolId,
            Model model,
            RedirectAttributes ra) {

        Student student = student(principal);

        // One application per student
        if (applicationOf(student) != null) {
            ra.addFlashAttribute("error", "You have already submitted an application.");
            return "redirect:/student/application";
        }

        if (!settingsService.applicationsOpen()) {
            ra.addFlashAttribute("error", "Applications are closed. Please contact the school administration.");
            return "redirect:/student/application";
        }

        // ---------- validation (mirrors the checks in the form) ----------
        List<String> errors = new ArrayList<>();

        requireText(errors, fullName, "Full Name", 150);
        requireText(errors, nameWithInitials, "Name with Initials", 100);
        if (!isBlank(fullName) && !NAME.matcher(fullName.trim()).matches()) {
            errors.add("Full Name can contain letters, spaces and full stops only.");
        }
        if (!isBlank(nameWithInitials) && !NAME.matcher(nameWithInitials.trim()).matches()) {
            errors.add("Name with Initials can contain letters, spaces and full stops only.");
        }
        requireText(errors, permanentAddress, "Permanent Address", 255);

        if (!PHONE.matcher(trim(phoneNumber)).matches()) {
            errors.add("Telephone Number must be exactly 10 digits (numbers only).");
        }
        if (!isBlank(whatsappNumber) && !PHONE.matcher(whatsappNumber.trim()).matches()) {
            errors.add("WhatsApp Number must be exactly 10 digits (numbers only).");
        }
        if (!List.of("Male", "Female").contains(gender)) errors.add("Select a gender.");
        if (!List.of("Sinhala", "Tamil", "English").contains(learningMedium)) errors.add("Select a learning medium.");
        String emailError = Validation.email(email, "Email address");
        if (emailError != null) errors.add(emailError);

        LocalDate dob = parseDate(dateOfBirth);
        if (dob == null) {
            errors.add("Enter a valid Date of Birth.");
        } else if (!dob.isBefore(LocalDate.now())) {
            errors.add("Date of Birth must be before today. It cannot be today or a future date.");
        } else if (dob.isBefore(LocalDate.now().minusYears(MAX_AGE_YEARS))) {
            errors.add("Check the Date of Birth - it is more than " + MAX_AGE_YEARS + " years ago.");
        }

        requireText(errors, guardianName, "Guardian's Name", 150);
        if (!PHONE.matcher(trim(guardianPhone)).matches()) {
            errors.add("Guardian's Telephone Number must be exactly 10 digits (numbers only).");
        }

        if (ageYears == null || ageYears < 0 || ageYears > 99
                || ageMonths == null || ageMonths < 0 || ageMonths > 11
                || ageDays == null || ageDays < 0 || ageDays > 31) {
            errors.add("Enter a valid age (months 0-11, days 0-31).");
        }

        if (!NIC.matcher(trim(nic)).matches()) {
            errors.add("NIC Number must be exactly 12 digits (numbers only).");
        } else {
            Student owner = studentRepository.findByNic(nic.trim());
            if (owner != null && !owner.getStudentId().equals(student.getStudentId())) {
                errors.add("This NIC Number is already registered with another account.");
            }
        }

        requireText(errors, olSchoolAddress, "O/L School Address", 255);
        if (!DIGITS.matcher(trim(olSchoolAdmissionNumber)).matches()) {
            errors.add("School Admission Number can contain numbers only.");
        }
        requireText(errors, district, "District", 50);
        LocalDate entered = parseDate(dateEnteredSchool);
        if (entered == null) {
            errors.add("Enter a valid Date Entered That School.");
        } else if (!entered.isBefore(LocalDate.now())) {
            errors.add("Date Entered That School must be before today. It cannot be today or a future date.");
        } else if (dob != null && !entered.isAfter(dob)) {
            errors.add("Date Entered That School must be after the Date of Birth.");
        }
        requireText(errors, gradeEntered, "Grade Entered", 10);

        String activities = ExtraCurricular.toStoredText(extraCurricular);
        if (activities == null) {
            errors.add("Tick at least one Extra-Curricular / Co-Curricular level, or \"None\" on its own.");
        }

        if (!DIGITS.matcher(trim(indexNumber)).matches()) {
            errors.add("O/L Index Number can contain numbers only.");
        }
        if (subjectName.size() != grade.size() || subjectName.size() != Subjects.CORE_SUBJECTS.size() + 3
                || !Subjects.allOlSubjects().containsAll(subjectName)
                || !Subjects.GRADES.containsAll(grade)
                || new HashSet<>(subjectName).size() != subjectName.size()) {
            errors.add("Enter a grade for every O/L subject, choosing a different subject from each basket.");
        }

        SubjectStream stream = streamId == null ? null : subjectStreamRepository.findById(streamId).orElse(null);
        if (stream == null) errors.add("Select the subject stream you are applying for.");
        if (isBlank(desiredSubject1) || isBlank(desiredSubject2) || isBlank(desiredSubject3)) {
            errors.add("Select three A/L subjects.");
        } else if (new HashSet<>(List.of(desiredSubject1, desiredSubject2, desiredSubject3)).size() < 3) {
            errors.add("The three A/L subjects must be different.");
        }

        School school = schoolId == null ? null : schoolRepository.findById(schoolId).orElse(null);
        if (school == null) errors.add("Select the school you are applying to.");

        if (!errors.isEmpty()) {
            // Show the form again with what was typed (available to the page as ${param.*})
            addCommon(principal, model);
            addFormModel(model);
            model.addAttribute("formErrors", errors);
            model.addAttribute("previousSubjects", List.of(trim(desiredSubject1), trim(desiredSubject2), trim(desiredSubject3)));
            return "student/application-form";
        }

        // ---------- save ----------
        student.setFullName(fullName.trim());
        student.setNameWithInitials(nameWithInitials.trim());
        student.setPermanentAddress(permanentAddress.trim());
        student.setPhoneNumber(phoneNumber.trim());
        student.setWhatsappNumber(isBlank(whatsappNumber) ? null : whatsappNumber.trim());
        student.setGender(gender);
        student.setDateOfBirth(dob);
        student.setLearningMedium(learningMedium);
        student.setEmail(email.trim());
        student.setGuardianName(guardianName.trim());
        student.setGuardianPhone(guardianPhone.trim());
        student.setAgeYears(ageYears);
        student.setAgeMonths(ageMonths);
        student.setAgeDays(ageDays);
        student.setNic(nic.trim());
        student.setOlSchoolAddress(olSchoolAddress.trim());
        student.setOlSchoolAdmissionNumber(olSchoolAdmissionNumber.trim());
        student.setDistrict(district.trim());
        student.setDateEnteredSchool(entered);
        student.setGradeEntered(gradeEntered.trim());

        student.setExtraCurricularActivities(activities);
        student.setIndexNumber(indexNumber.trim());
        studentRepository.save(student);

        Application application = new Application();
        application.setStudent(student);
        application.setSchool(school);
        application.setStream(stream);
        application.setApplicationDate(LocalDate.now());
        application.setDesiredSubject1(desiredSubject1);
        application.setDesiredSubject2(desiredSubject2);
        application.setDesiredSubject3(desiredSubject3);
        applicationRepository.save(application);

        for (int i = 0; i < subjectName.size(); i++) {
            OlResult result = new OlResult();
            result.setApplication(application);
            result.setSubjectName(subjectName.get(i));
            result.setGrade(grade.get(i));
            olResultRepository.save(result);
        }

        ra.addFlashAttribute("success", "Application Submitted Successfully! Thank you, " + student.getFullName()
                + ". Your application has been received.");
        return "redirect:/student/dashboard";
    }


    // =====================================================
    // APPLICATION STATUS
    // =====================================================

    @GetMapping("/student/status")
    public String status(Principal principal, Model model) {
        Application app = addCommon(principal, model);
        model.addAttribute("steps", TimelineStep.forApplication(app));
        return "student/status";
    }


    // =====================================================
    // INTERVIEW DETAILS
    // =====================================================

    @GetMapping("/student/interview")
    public String interview(Principal principal, Model model) {
        Application app = addCommon(principal, model);
        model.addAttribute("interview", interviewOf(app));
        return "student/interview";
    }


    // =====================================================
    // INTERVIEW NOTIFICATION
    // =====================================================

    @GetMapping("/student/notifications")
    public String notifications(Principal principal, Model model) {
        Application app = addCommon(principal, model);
        Interview interview = interviewOf(app);

        model.addAttribute("notifications", app == null ? List.of()
                : notificationRepository.findByApplicationApplicationIdOrderBySentDateDesc(app.getApplicationId()));
        model.addAttribute("interview", interview);

        if (interview != null) {
            model.addAttribute("letter", emailService.interviewLetter(
                    app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress(),
                    app.getStudent().getFullName(), app.getStream().getStreamName(),
                    interview.getInterviewDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    interview.getInterviewDate().format(DateTimeFormatter.ofPattern("hh:mm a")),
                    interview.getVenue()));
        }
        model.addAttribute("admissionLetter", admissionLetterFor(app));
        model.addAttribute("notQualifiedLetter", notQualifiedLetterFor(app));
        return "student/notifications";
    }


    // =====================================================
    // SELECTION RESULT
    // =====================================================

    @GetMapping("/student/result")
    public String result(Principal principal, Model model) {
        Application app = addCommon(principal, model);
        model.addAttribute("admissionLetter", admissionLetterFor(app));
        model.addAttribute("notQualifiedLetter", notQualifiedLetterFor(app));
        if (app != null && settingsService.hasAdmissionDetails(app.getStream().getStreamId())) {
            Map<String, String> d = settingsService.admissionDetails(app.getStream().getStreamId());
            model.addAttribute("admission", d);
            model.addAttribute("admissionDateText", LocalDate.parse(d.get("date")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            model.addAttribute("admissionTimeText", LocalTime.parse(d.get("time")).format(DateTimeFormatter.ofPattern("hh:mm a")));
        }
        return "student/result";
    }

    /** The "not selected for interview" letter, for students whose O/L results did not meet the conditions. */
    private String notQualifiedLetterFor(Application app) {
        if (app == null || !Status.NOT_QUALIFIED.equals(app.getStatus())) return null;
        return emailService.notQualifiedLetter(app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress(),
                app.getStudent().getFullName(), app.getStream().getStreamName());
    }

    /** The admission letter, once the student is selected and the section head has set the admission details. */
    private String admissionLetterFor(Application app) {
        if (app == null || !Status.SELECTED.equals(app.getStatus())
                || !settingsService.hasAdmissionDetails(app.getStream().getStreamId())) {
            return null;
        }
        Map<String, String> d = settingsService.admissionDetails(app.getStream().getStreamId());
        return emailService.admissionLetter(
                app.getSchool().getSchoolName(), app.getSchool().getSchoolAddress(), app.getStream().getStreamName(),
                LocalDate.parse(d.get("date")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                LocalTime.parse(d.get("time")).format(DateTimeFormatter.ofPattern("hh:mm a")),
                d.get("venue"));
    }


    // =====================================================
    // PROFILE
    // =====================================================

    @GetMapping("/student/profile")
    public String profile(Principal principal, Model model) {
        addCommon(principal, model);
        return "student/profile";
    }

    @PostMapping("/student/profile")
    public String updateProfile(Principal principal,
                                @RequestParam String fullName,
                                @RequestParam String email,
                                @RequestParam(required = false) String phoneNumber,
                                @RequestParam(required = false) String whatsappNumber,
                                RedirectAttributes ra) {
        String error = first(
                name(fullName, "Full Name", 150),
                email(email, "Email address"),
                !isBlank(phoneNumber) && !PHONE.matcher(phoneNumber.trim()).matches() ? "Telephone Number must be exactly 10 digits." : null,
                !isBlank(whatsappNumber) && !PHONE.matcher(whatsappNumber.trim()).matches() ? "WhatsApp Number must be exactly 10 digits." : null);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/student/profile";
        }

        Student student = student(principal);
        student.setFullName(fullName.trim());
        student.setEmail(email.trim());
        student.setPhoneNumber(isBlank(phoneNumber) ? null : phoneNumber.trim());
        student.setWhatsappNumber(isBlank(whatsappNumber) ? null : whatsappNumber.trim());
        studentRepository.save(student);

        ra.addFlashAttribute("success", "Your profile has been updated.");
        return "redirect:/student/profile";
    }

    @PostMapping("/student/profile/password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        String error = accountService.changeStudentPassword(student(principal),
                currentPassword, newPassword, confirmPassword);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Your password has been changed." : error);
        return "redirect:/student/profile";
    }
}
