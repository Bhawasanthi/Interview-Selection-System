package com.iss.backend.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Opens every page as the role that uses it and fails on server errors or broken templates. */
@SpringBootTest
@AutoConfigureMockMvc
class AllPagesRenderTest {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            // public
            "-, /", "-, /about", "-, /contact", "-, /schools", "-, /login",
            // admin
            "admin1:ADMIN, /admin/dashboard", "admin1:ADMIN, /admin/students", "admin1:ADMIN, /admin/students/4",
            "admin1:ADMIN, /admin/applications", "admin1:ADMIN, /admin/applications?status=APPLIED",
            "admin1:ADMIN, /admin/schools", "admin1:ADMIN, /admin/streams", "admin1:ADMIN, /admin/section-heads",
            "admin1:ADMIN, /admin/panel-members", "admin1:ADMIN, /admin/password-requests",
            "admin1:ADMIN, /admin/selection-rules", "admin1:ADMIN, /admin/reports", "admin1:ADMIN, /admin/settings",
            "admin1:ADMIN, /admin/settings?tab=marking", "admin1:ADMIN, /admin/settings?year=2026",
            "admin1:ADMIN, /admin/settings?year=2030", "admin1:ADMIN, /admin/profile",
            "admin1:ADMIN, /admin/export-applications", "admin1:ADMIN, /admin/export-applications?format=pdf",
            "admin1:ADMIN, /admin/activity", "admin1:ADMIN, /admin/activity?days=1", "admin1:ADMIN, /admin/activity?days=365",
            "admin1:ADMIN, /admin/activity?days=30&type=FAILED&q=admin", "admin1:ADMIN, /admin/activity/export",
            "admin1:ADMIN, /admin/activity/export?format=pdf",
            // section head (Maths has a completed interview, Science has a scheduled one)
            "mathshead:SECTION_HEAD, /sectionhead/dashboard", "mathshead:SECTION_HEAD, /sectionhead/applicants",
            "mathshead:SECTION_HEAD, /sectionhead/applicant-detail/3", "mathshead:SECTION_HEAD, /sectionhead/conditions",
            "mathshead:SECTION_HEAD, /sectionhead/auto-selection", "mathshead:SECTION_HEAD, /sectionhead/shortlisted",
            "mathshead:SECTION_HEAD, /sectionhead/invitations", "mathshead:SECTION_HEAD, /sectionhead/interviewed",
            "mathshead:SECTION_HEAD, /sectionhead/cutoff", "mathshead:SECTION_HEAD, /sectionhead/final-selection",
            "mathshead:SECTION_HEAD, /sectionhead/final-notifications", "mathshead:SECTION_HEAD, /sectionhead/profile",
            "sciencehead:SECTION_HEAD, /sectionhead/auto-selection", "sciencehead:SECTION_HEAD, /sectionhead/invitations",
            "sciencehead:SECTION_HEAD, /sectionhead/shortlisted", "technologyhead:SECTION_HEAD, /sectionhead/dashboard",
            "technologyhead:SECTION_HEAD, /sectionhead/export-selected?format=pdf",
            // interview panel
            "panel1:INTERVIEW_PANEL, /panel/dashboard", "panel1:INTERVIEW_PANEL, /panel/assigned",
            "panel1:INTERVIEW_PANEL, /panel/pending", "panel1:INTERVIEW_PANEL, /panel/completed",
            "panel1:INTERVIEW_PANEL, /panel/students", "panel1:INTERVIEW_PANEL, /panel/student/1",
            "panel1:INTERVIEW_PANEL, /panel/marking", "panel1:INTERVIEW_PANEL, /panel/interview/1",
            "panel1:INTERVIEW_PANEL, /panel/results", "panel1:INTERVIEW_PANEL, /panel/profile",
            "sciencepanel1:INTERVIEW_PANEL, /panel/interview/5", "technologypanel1:INTERVIEW_PANEL, /panel/dashboard",
            "technologypanel1:INTERVIEW_PANEL, /panel/results", "technologypanel1:INTERVIEW_PANEL, /panel/export/completed?format=pdf",
            // students: selected, interview scheduled, pending, not qualified
            "bhawa:STUDENT, /student/dashboard", "bhawa:STUDENT, /student/application", "bhawa:STUDENT, /student/status",
            "bhawa:STUDENT, /student/interview", "bhawa:STUDENT, /student/notifications", "bhawa:STUDENT, /student/result",
            "bhawa:STUDENT, /student/profile",
            "kawya:STUDENT, /student/dashboard", "kawya:STUDENT, /student/interview", "kawya:STUDENT, /student/notifications",
            "teststudent1:STUDENT, /student/dashboard", "teststudent1:STUDENT, /student/result",
            "teststudent2:STUDENT, /student/dashboard", "teststudent2:STUDENT, /student/status",
            "teststudent2:STUDENT, /student/application",
    })
    void pageRenders(String who, String url) throws Exception {
        var request = get(url);
        if (!"-".equals(who)) {
            String[] parts = who.split(":");
            request = request.with(user(parts[0]).roles(parts[1]));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();

        assertThat(status).as("HTTP status of " + url).isLessThan(400);
        if (status == 302) {
            // A redirect to an error/profile page means the page itself could not be shown
            assertThat(result.getResponse().getRedirectedUrl()).as("redirect of " + url)
                    .doesNotContain("error").doesNotEndWith("/profile");
            assertThat(result.getFlashMap().get("error")).as("flash error of " + url).isNull();
        }
    }
}
