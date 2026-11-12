package com.iss.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SectionHeadApplicantsPageTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void applicantsPageRenders() throws Exception {
        mockMvc.perform(get("/sectionhead/applicants").with(user("mathshead").roles("SECTION_HEAD")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Student 1")));
    }

    @Test
    void applicantDetailPageRenders() throws Exception {
        mockMvc.perform(get("/sectionhead/applicant-detail/3").with(user("mathshead").roles("SECTION_HEAD")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thimbirigolla")))
                .andExpect(content().string(containsString("O/L Results")));
    }

    @Test
    void otherStreamApplicantRedirects() throws Exception {
        mockMvc.perform(get("/sectionhead/applicant-detail/4").with(user("mathshead").roles("SECTION_HEAD")))
                .andExpect(redirectedUrl("/sectionhead/applicants"));
    }
}
