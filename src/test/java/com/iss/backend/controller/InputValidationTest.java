package com.iss.backend.controller;

import com.iss.backend.repository.SelectionConditionRepository;
import com.iss.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Invalid input is rejected with a message instead of being saved, cut off, or causing an error page. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InputValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SelectionConditionRepository conditionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void staffCannotTakeAStudentsUsername() throws Exception {
        Integer id = userRepository.findByUserName("panel1").getUserId();
        mockMvc.perform(post("/admin/staff/update/" + id).with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("name", "Panel One").param("username", "bhawa").param("returnTo", "panel-members"))
                .andExpect(redirectedUrl("/admin/panel-members"))
                .andExpect(flash().attribute("error", containsString("already taken")));
        assertThat(userRepository.findByUserName("panel1")).isNotNull();
    }

    @Test
    void staffPasswordChangeNeedsMinimumLength() throws Exception {
        Integer id = userRepository.findByUserName("panel1").getUserId();
        mockMvc.perform(post("/admin/staff/update/" + id).with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("name", "Panel One").param("username", "panel1").param("newPassword", "123"))
                .andExpect(flash().attribute("error", containsString("at least 6")));
    }

    @Test
    void staffNameTooLongIsRejected() throws Exception {
        mockMvc.perform(post("/admin/staff/add").with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("name", "A".repeat(101)).param("username", "newstaff").param("password", "secret1")
                        .param("roleId", "2"))
                .andExpect(flash().attribute("error", containsString("100 characters")));
        assertThat(userRepository.findByUserName("newstaff")).isNull();
    }

    @Test
    void blankSchoolAndDuplicateStreamAreRejected() throws Exception {
        mockMvc.perform(post("/admin/schools/save").with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("schoolName", " ").param("district", "Colombo").param("schoolAddress", "Road"))
                .andExpect(flash().attribute("error", containsString("School name is required")));
        mockMvc.perform(post("/admin/streams/save").with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("streamName", "MATHS"))
                .andExpect(flash().attribute("error", containsString("already exists")));
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        mockMvc.perform(post("/admin/settings/save").with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("section", "admissions").param("applicationDeadline", "31/12/2026"))
                .andExpect(flash().attribute("error", containsString("valid application deadline")));
        mockMvc.perform(post("/admin/settings/save").with(csrf()).with(user("admin1").roles("ADMIN"))
                        .param("section", "interviews").param("defaultTime", "nine"))
                .andExpect(flash().attribute("error", containsString("valid default interview time")));
    }

    @Test
    void invalidConditionsDoNotWipeSavedConditions() throws Exception {
        int before = conditionRepository.findByStreamStreamIdAndYear(1, 2026).size();
        mockMvc.perform(post("/sectionhead/conditions/save").with(csrf()).with(user("mathshead").roles("SECTION_HEAD"))
                        .param("year", "2026").param("subjectName", "Mathematics").param("minGrade", "Z"))
                .andExpect(flash().attribute("error", containsString("minimum grade")));
        assertThat(conditionRepository.findByStreamStreamIdAndYear(1, 2026)).hasSize(before);
    }

    @Test
    void interviewCannotBeAssignedToAnotherStreamsPanel() throws Exception {
        Integer otherStreamPanel = userRepository.findByUserName("panel1").getUserId();   // Maths panel
        mockMvc.perform(post("/sectionhead/send-invitations").with(csrf()).with(user("sciencehead").roles("SECTION_HEAD"))
                        .param("applicationIds", "18")
                        .param("interviewDate", LocalDate.now().plusDays(5).toString())
                        .param("interviewTime", "09:00").param("venue", "Hall")
                        .param("panelUserId", String.valueOf(otherStreamPanel)))
                .andExpect(flash().attribute("error", containsString("your own stream")));
    }

    @Test
    void badAdmissionDetailsShowAnErrorInsteadOfCrashing() throws Exception {
        mockMvc.perform(post("/sectionhead/send-final-notifications").with(csrf()).with(user("mathshead").roles("SECTION_HEAD"))
                        .param("admissionDate", "not-a-date").param("admissionTime", "09:00").param("venue", "Hall")
                        .param("action", "save"))
                .andExpect(redirectedUrl("/sectionhead/final-notifications"))
                .andExpect(flash().attribute("error", containsString("valid admission date")));
    }

    @Test
    void signupRequiresValidDetails() throws Exception {
        mockMvc.perform(post("/student-signup").with(csrf())
                        .param("fullName", "New Student").param("email", "new@test.lk")
                        .param("username", "new student").param("password", "secret1").param("confirmPassword", "secret1"))
                .andExpect(flash().attribute("signupError", containsString("Username must be")));
        mockMvc.perform(post("/student-signup").with(csrf())
                        .param("fullName", "New Student").param("email", "new@test.lk")
                        .param("username", "newstudent").param("password", "123").param("confirmPassword", "123"))
                .andExpect(flash().attribute("signupError", containsString("at least 6")));
        mockMvc.perform(post("/student-signup").with(csrf())
                        .param("fullName", "New Student").param("email", "not-an-email")
                        .param("username", "newstudent").param("password", "secret1").param("confirmPassword", "secret1"))
                .andExpect(flash().attribute("signupError", containsString("valid email")));
    }

    /** The app's database connections run in strict mode, so over-long values fail instead of being cut off. */
    @Test
    void databaseConnectionsUseStrictMode() {
        String mode = jdbcTemplate.queryForObject("SELECT @@SESSION.sql_mode", String.class);
        assertThat(mode).contains("STRICT_TRANS_TABLES");
    }

    @Test
    void profileNameCannotBeBlank() throws Exception {
        mockMvc.perform(post("/panel/profile").with(csrf()).with(user("panel1").roles("INTERVIEW_PANEL"))
                        .param("name", "  "))
                .andExpect(flash().attribute("error", containsString("Name is required")));
    }
}
