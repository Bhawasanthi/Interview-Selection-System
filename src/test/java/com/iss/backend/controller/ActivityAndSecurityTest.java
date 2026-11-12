package com.iss.backend.controller;

import com.iss.backend.entity.Student;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.service.ActivityService;
import com.iss.backend.service.InterviewMarkingService;
import com.iss.backend.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Site activity tracking, login protection and the security fixes. Everything is rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityAndSecurityTest {

    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InterviewMarkingService markingService;
    @Autowired private SettingsService settings;

    private Student student(String password) {
        Student s = new Student();
        s.setUsername("activity_test_student");
        s.setFullName("Activity Test Student");
        s.setPassword(passwordEncoder.encode(password));
        s.setEmail("activity@test.lk");
        return studentRepository.save(s);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------- visits ----------

    @Test
    void pageVisitIsRecordedWithAVisitorCookie() throws Exception {
        int before = count("SELECT COUNT(*) FROM site_visit WHERE path = '/about'");
        mockMvc.perform(get("/about").header("User-Agent", BROWSER))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("iss_vid"))
                .andExpect(cookie().httpOnly("iss_vid", true));
        assertThat(count("SELECT COUNT(*) FROM site_visit WHERE path = '/about'")).isEqualTo(before + 1);
    }

    @Test
    void staticFilesAndBotsAreNotCounted() throws Exception {
        int before = count("SELECT COUNT(*) FROM site_visit");
        mockMvc.perform(get("/css/app.css").header("User-Agent", BROWSER));
        mockMvc.perform(get("/").header("User-Agent", "Googlebot/2.1"));
        assertThat(count("SELECT COUNT(*) FROM site_visit")).isEqualTo(before);
    }

    @Test
    void loggedInVisitRecordsUserAndRole() throws Exception {
        mockMvc.perform(get("/admin/reports").header("User-Agent", BROWSER).with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT role FROM site_visit WHERE username = 'admin1' AND path = '/admin/reports' "
                + "ORDER BY visit_id DESC LIMIT 1", String.class)).isEqualTo("Admin");
    }

    // ---------- logins ----------

    @Test
    void successfulAndFailedLoginsAreRecorded() throws Exception {
        student("Secret@123");
        mockMvc.perform(formLogin("/login").user("activity_test_student").password("wrong"))
                .andExpect(redirectedUrl("/?loginError"));
        mockMvc.perform(formLogin("/login").user("activity_test_student").password("Secret@123"))
                .andExpect(redirectedUrl("/student/dashboard"));

        assertThat(count("SELECT COUNT(*) FROM login_event WHERE username = 'activity_test_student' AND event_type = 'FAILED'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT role FROM login_event WHERE username = 'activity_test_student' AND event_type = 'LOGIN'",
                String.class)).isEqualTo("Student");
    }

    @Test
    void accountIsBlockedAfterTooManyWrongPasswords() throws Exception {
        student("Secret@123");
        for (int i = 1; i < ActivityService.MAX_FAILED_PER_USER; i++) {
            mockMvc.perform(formLogin("/login").user("activity_test_student").password("wrong" + i))
                    .andExpect(redirectedUrl("/?loginError"));
        }
        mockMvc.perform(formLogin("/login").user("activity_test_student").password("wrong-last"))
                .andExpect(redirectedUrl("/?loginError=locked"));
        // Even the right password is refused while blocked
        mockMvc.perform(formLogin("/login").user("activity_test_student").password("Secret@123"))
                .andExpect(redirectedUrl("/?loginError=locked"));
        assertThat(count("SELECT COUNT(*) FROM login_event WHERE username = 'activity_test_student' AND event_type = 'LOCKED'")).isEqualTo(1);
    }

    @Test
    void activityPageShowsLoginHistory() throws Exception {
        student("Secret@123");
        mockMvc.perform(formLogin("/login").user("activity_test_student").password("wrong"));
        mockMvc.perform(get("/admin/activity").with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Login History")))
                .andExpect(content().string(containsString("activity_test_student")))
                .andExpect(content().string(containsString("Wrong password")));
        mockMvc.perform(get("/admin/activity").with(user("bhawa").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ---------- security fixes ----------

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/admin/schools/save").with(user("admin1").roles("ADMIN"))
                        .param("schoolName", "CSRF School").param("district", "Colombo").param("schoolAddress", "Road"))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForUnknownAccounts() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf()).param("usernameOrEmail", "nobody@nowhere.lk"))
                .andExpect(flash().attribute("forgotSent", containsString("If this matches an account")));
        mockMvc.perform(post("/forgot-password").with(csrf()).param("usernameOrEmail", "panel1"))
                .andExpect(flash().attribute("forgotSent", containsString("If this matches an account")));
    }

    @Test
    void marksAreLockedAfterFinalSelection() {
        Integer interviewId = jdbc.queryForObject("""
                SELECT i.interview_id FROM interview i JOIN application a ON a.application_id = i.application_id
                WHERE a.status = 'SELECTED' LIMIT 1""", Integer.class);
        assertThatThrownBy(() -> markingService.saveInterviewMarks(interviewId, 9, 0, 0, 0, 0,
                "YES", "YES", "YES", "YES", "NONE", BigDecimal.TEN, BigDecimal.valueOf(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingTheSite() throws Exception {
        Student s = student("Secret@123");
        settings.saveAll(Map.of("pwtemp.student." + s.getStudentId(), "true"));
        try {
            mockMvc.perform(get("/student/dashboard").with(user("activity_test_student").roles("STUDENT")))
                    .andExpect(redirectedUrl("/student/profile?changePassword"));
            mockMvc.perform(get("/student/profile").with(user("activity_test_student").roles("STUDENT")))
                    .andExpect(status().isOk());
        } finally {
            settings.saveAll(Map.of("pwtemp.student." + s.getStudentId(), ""));
        }
    }
}
