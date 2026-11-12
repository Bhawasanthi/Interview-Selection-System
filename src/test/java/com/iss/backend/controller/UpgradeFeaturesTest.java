package com.iss.backend.controller;

import com.iss.backend.entity.Interview;
import com.iss.backend.entity.InterviewMark;
import com.iss.backend.entity.Student;
import com.iss.backend.repository.InterviewMarkRepository;
import com.iss.backend.repository.InterviewRepository;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.service.InterviewMarkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Checks for the upgrade features against the development database.
 * Tests that write data run in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UpgradeFeaturesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewMarkRepository interviewMarkRepository;

    // ---------- admin: password reset requests ----------

    @Test
    void adminSidebarLinksToPasswordResetRequests() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/password-requests\"")))
                .andExpect(content().string(containsString("Password Reset Requests")));
    }

    @Test
    void passwordResetRequestsPageIsHighlightedInSidebar() throws Exception {
        mockMvc.perform(get("/admin/password-requests").with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    // ---------- downloads: Excel and PDF ----------

    @Test
    void adminDownloadsSelectedAsPdfAndExcel() throws Exception {
        mockMvc.perform(get("/admin/export-selected").param("format", "pdf").with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(startsWith("%PDF")));
        mockMvc.perform(get("/admin/export-selected").with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")));
    }

    @Test
    void adminDownloadsApplicationsByStatusAsPdf() throws Exception {
        mockMvc.perform(get("/admin/export-applications").param("status", "APPLIED").param("format", "pdf")
                        .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("applied_applications.pdf")));
    }

    @Test
    void sectionHeadAndPanelDownloadPdf() throws Exception {
        mockMvc.perform(get("/sectionhead/export-selected").param("format", "pdf").with(user("mathshead").roles("SECTION_HEAD")))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("%PDF")));
        mockMvc.perform(get("/panel/export/assigned").param("format", "pdf").with(user("panel1").roles("INTERVIEW_PANEL")))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("%PDF")));
        mockMvc.perform(get("/panel/export/pending").with(user("panel1").roles("INTERVIEW_PANEL")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")));
    }

    // ---------- interview marking ----------

    @Test
    void markingPageShowsNewCriteria() throws Exception {
        mockMvc.perform(get("/panel/interview/5").with(user("sciencepanel1").roles("INTERVIEW_PANEL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Interview Assessment")))
                .andExpect(content().string(containsString("Supporting Documents &amp; Eligibility")));
    }

    /** Interviews in a year with no marking scale of its own used to score 0.00 for everything. */
    @Test
    @Transactional
    void marksAreSavedForYearWithoutItsOwnScaleAndAlRuleIsApplied() throws Exception {
        Interview interview = interviewRepository.findById(5).orElseThrow();
        interview.setInterviewDate(LocalDateTime.of(2031, 3, 1, 9, 0));   // no 2031 marking scale exists
        interviewRepository.save(interview);

        mockMvc.perform(post("/panel/interview/5/submit-marks").with(csrf())
                        .with(user("sciencepanel1").roles("INTERVIEW_PANEL"))
                        .param("countA", "3").param("countB", "2").param("countC", "2")
                        .param("countS", "1").param("countW", "1")
                        .param("gnApproval", "DIVISION")
                        .param("alAvailable", "NO")
                        .param("oneSubjectAvailable", "YES")     // must be forced to NO
                        .param("extracurricular", "PROVINCIAL")
                        .param("interviewAssessment", "8.5")
                        .param("documentsEligibility", "4"))
                .andExpect(redirectedUrl("/panel/results"));

        Map<String, BigDecimal> marks = interviewMarkRepository.findByInterviewInterviewId(5).stream()
                .collect(Collectors.toMap(InterviewMark::getCriteria, InterviewMark::getScore));
        assertThat(marks.get("OL_RESULT")).isEqualByComparingTo("31");   // 3x6 + 2x4 + 2x2 + 1x1 + 1x0 (2026 scale)
        assertThat(marks.get(InterviewMarkingService.INTERVIEW_ASSESSMENT)).isEqualByComparingTo("8.5");
        assertThat(marks.get(InterviewMarkingService.DOCUMENTS_ELIGIBILITY)).isEqualByComparingTo("4");
        assertThat(marks.get("ONE_SUBJECT_AVAILABLE")).isEqualByComparingTo("3");   // "NO" option on the 2026 scale
        assertThat(marks.get("TWO_SUBJECT_AVAILABLE")).isEqualByComparingTo("3");
    }

    @Test
    @Transactional
    void marksAboveMaximumAreRejected() throws Exception {
        mockMvc.perform(post("/panel/interview/5/submit-marks").with(csrf())
                        .with(user("sciencepanel1").roles("INTERVIEW_PANEL"))
                        .param("countA", "1").param("countB", "0").param("countC", "0")
                        .param("countS", "0").param("countW", "0")
                        .param("gnApproval", "DIVISION").param("alAvailable", "YES")
                        .param("oneSubjectAvailable", "YES").param("twoSubjectAvailable", "YES")
                        .param("extracurricular", "NONE")
                        .param("interviewAssessment", "11")
                        .param("documentsEligibility", "4"))
                .andExpect(redirectedUrl("/panel/interview/5"))
                .andExpect(flash().attribute("error", containsString("between 0 and 10")));
        assertThat(interviewMarkRepository.findByInterviewInterviewId(5)).isEmpty();
    }

    // ---------- section head: interview dates ----------

    @Test
    @Transactional
    void interviewCannotBeMovedToPastDate() throws Exception {
        mockMvc.perform(post("/sectionhead/invitations/5/update").with(csrf())
                        .with(user("sciencehead").roles("SECTION_HEAD"))
                        .param("interviewDate", LocalDate.now().toString())
                        .param("interviewTime", "09:00")
                        .param("venue", "Main Hall"))
                .andExpect(redirectedUrl("/sectionhead/invitations"))
                .andExpect(flash().attribute("error", containsString("future date")));
    }

    // ---------- student portal: not qualified ----------

    @Test
    void notQualifiedStudentSeesLetter() throws Exception {
        mockMvc.perform(get("/student/notifications").with(user("teststudent2").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("has not been selected for the interview")));
        mockMvc.perform(get("/student/result").with(user("teststudent2").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not Selected for Interview")));
    }

    // ---------- student application form ----------

    private Student newStudent() {
        Student s = new Student();
        s.setUsername("upgrade_test_student");
        s.setFullName("Upgrade Test Student");
        s.setPassword("x");
        s.setEmail("upgrade@test.lk");
        return studentRepository.save(s);
    }

    @Test
    @Transactional
    void applicationFormShowsExtraCurricularCheckboxes() throws Exception {
        newStudent();
        mockMvc.perform(get("/student/application").with(user("upgrade_test_student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"extraCurricular\"")))
                .andExpect(content().string(containsString("All Island")))
                .andExpect(content().string(containsString("International")));
    }

    @Test
    @Transactional
    void invalidApplicationIsRejectedWithEveryProblemListed() throws Exception {
        newStudent();
        mockMvc.perform(post("/student/application").with(csrf())
                        .with(user("upgrade_test_student").roles("STUDENT"))
                        .param("fullName", "Test Student").param("nameWithInitials", "T. Student")
                        .param("permanentAddress", "Colombo")
                        .param("phoneNumber", "07712345")            // too short
                        .param("whatsappNumber", "07712345ab")       // not numbers
                        .param("gender", "Male")
                        .param("dateOfBirth", LocalDate.now().plusDays(3).toString())   // future
                        .param("learningMedium", "Sinhala").param("email", "upgrade@test.lk")
                        .param("guardianName", "Parent").param("guardianPhone", "0771234567")
                        .param("ageYears", "16").param("ageMonths", "2").param("ageDays", "3")
                        .param("nic", "12345")                       // not 12 digits
                        .param("olSchoolAddress", "Somewhere").param("olSchoolAdmissionNumber", "A12")
                        .param("district", "Colombo")
                        .param("dateEnteredSchool", LocalDate.now().toString())          // not before today
                        .param("gradeEntered", "6").param("studentIdentityNumber", "S1")
                        .param("extraCurricular", "NONE", "PROVINCIAL")                  // None + a level
                        .param("indexNumber", "12AB")
                        .param("subjectName", "Sinhala Language & Literature").param("grade", "A")
                        .param("streamId", "1")
                        .param("desiredSubject1", "Combined Mathematics").param("desiredSubject2", "Physics")
                        .param("desiredSubject3", "Chemistry")
                        .param("schoolId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/application-form"))
                .andExpect(content().string(containsString("Telephone Number must be exactly 10 digits")))
                .andExpect(content().string(containsString("WhatsApp Number must be exactly 10 digits")))
                .andExpect(content().string(containsString("Date of Birth must be before today")))
                .andExpect(content().string(containsString("NIC Number must be exactly 12 digits")))
                .andExpect(content().string(containsString("School Admission Number can contain numbers only")))
                .andExpect(content().string(containsString("Date Entered That School must be before today")))
                .andExpect(content().string(containsString("O/L Index Number can contain numbers only")))
                .andExpect(content().string(containsString("&quot;None&quot; on its own")))
                // what was typed is kept
                .andExpect(content().string(containsString("value=\"Test Student\"")));
    }
}
