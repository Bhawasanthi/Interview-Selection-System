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

/** Every PDF button gives a real PDF file and every Excel button a real Excel file. */
@SpringBootTest
@AutoConfigureMockMvc
class ExportFormatTest {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest(name = "{1}?format={2}")
    @CsvSource({
            "admin1:ADMIN, /admin/export-applications, pdf", "admin1:ADMIN, /admin/export-applications, xlsx",
            "admin1:ADMIN, /admin/export-selected, pdf", "admin1:ADMIN, /admin/export-selected, xlsx",
            "admin1:ADMIN, /admin/activity/export, pdf", "admin1:ADMIN, /admin/activity/export, xlsx",
            "admin1:ADMIN, /admin/export-students, pdf", "admin1:ADMIN, /admin/export-students, xlsx",
            "admin1:ADMIN, /admin/export-schools, pdf", "admin1:ADMIN, /admin/export-schools, xlsx",
            "admin1:ADMIN, /admin/export-streams, pdf", "admin1:ADMIN, /admin/export-streams, xlsx",
            "admin1:ADMIN, /admin/export-staff/section-heads, pdf", "admin1:ADMIN, /admin/export-staff/panel-members, xlsx",
            "mathshead:SECTION_HEAD, /sectionhead/export-selected, pdf", "mathshead:SECTION_HEAD, /sectionhead/export-selected, xlsx",
            "panel1:INTERVIEW_PANEL, /panel/export/assigned, pdf", "panel1:INTERVIEW_PANEL, /panel/export/pending, pdf",
            "panel1:INTERVIEW_PANEL, /panel/export/completed, pdf", "panel1:INTERVIEW_PANEL, /panel/export/completed, xlsx",
    })
    void downloadHasTheRequestedFormat(String who, String url, String format) throws Exception {
        String[] parts = who.split(":");
        MvcResult result = mockMvc.perform(get(url).param("format", format).with(user(parts[0]).roles(parts[1])))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        byte[] body = result.getResponse().getContentAsByteArray();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        if ("pdf".equals(format)) {
            assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
            assertThat(disposition).endsWith(".pdf\"").doesNotContain(".xlsx");
            assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
        } else {
            assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
            assertThat(disposition).endsWith(".xlsx\"");
            assertThat(new String(body, 0, 2)).isEqualTo("PK");   // .xlsx files are zip archives
        }
    }

    /** Downloads from the Applications page contain only the rows the page is showing. */
    @org.junit.jupiter.api.Test
    void applicationsDownloadFollowsThePageFilters() throws Exception {
        MvcResult all = mockMvc.perform(get("/admin/export-applications").param("format", "xlsx")
                .with(user("admin1").roles("ADMIN"))).andReturn();
        MvcResult maths = mockMvc.perform(get("/admin/export-applications").param("format", "xlsx").param("stream", "1")
                .with(user("admin1").roles("ADMIN"))).andReturn();
        assertThat(rowCount(maths)).isPositive().isLessThan(rowCount(all));
        assertThat(maths.getResponse().getHeader("Content-Disposition")).contains("maths_all_applications.xlsx");
    }

    private static int rowCount(MvcResult result) throws Exception {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            return wb.getSheetAt(0).getLastRowNum();
        }
    }
}
