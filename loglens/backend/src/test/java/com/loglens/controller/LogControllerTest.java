package com.loglens.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LogControllerTest {

    private static final String LOG = String.join("\n",
            "2026-10-22 10:30:12 ERROR Request failed password=hunter2",
            "java.lang.NullPointerException: x is null",
            "\tat com.example.A.b(A.java:1)",
            "2026-10-22 10:30:13 INFO done") + "\n";

    @Autowired
    private MockMvc mockMvc;

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/plain", bytes);
    }

    @Test
    void analyzeUploadReturnsStructuredResult() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze").file(file("app.log", LOG.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.sourceName").value("app.log"))
                .andExpect(jsonPath("$.summary.totalLines").value(4))
                .andExpect(jsonPath("$.summary.errorCount").value(1))
                .andExpect(jsonPath("$.summary.infoCount").value(1))
                .andExpect(jsonPath("$.summary.redactionApplied").value(true))
                .andExpect(jsonPath("$.logLevelCounts.length()").value(7))
                .andExpect(jsonPath("$.uniqueErrorGroups").value(1))
                .andExpect(jsonPath("$.unknownLineCount").value(0))
                .andExpect(jsonPath("$.errorGroups[0].id").exists())
                .andExpect(jsonPath("$.errorGroups[0].exceptionType").value("java.lang.NullPointerException"))
                .andExpect(jsonPath("$.errorGroups[0].occurrenceCount").value(1))
                .andExpect(jsonPath("$.errorGroups[0].firstOccurrence.lineNumber").value(1))
                .andExpect(jsonPath("$.errorGroups[0].rootCauseCandidate.type").value("java.lang.NullPointerException"))
                .andExpect(jsonPath("$.errorGroups[0].stackTrace.relevantFrames[0].lineNumber").value(1))
                .andExpect(jsonPath("$.errorGroups[0].suggestions[0].ruleId").value("null-pointer"))
                .andExpect(jsonPath("$.errorGroups[0].representativeLines[0].text").value(containsString("[REDACTED]")))
                .andExpect(jsonPath("$.privacyNote").exists());
    }

    @Test
    void redactCanBeDisabledPerRequest() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze")
                        .file(file("app.log", LOG.getBytes(StandardCharsets.UTF_8)))
                        .param("redact", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.redactionApplied").value(false))
                .andExpect(jsonPath("$.warnings[0]").value(containsString("Redaction is turned off")));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze").file(file("app.log", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("empty")));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze").file(file("app.exe", "ERROR x".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported file type")));
    }

    @Test
    void rejectsBinaryContent() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze").file(file("app.log", new byte[] {'E', 0, 'R'})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("binary")));
    }

    @Test
    void rejectsRequestWithoutFilePart() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No file was provided")));
    }

    @Test
    void rejectsNonMultipartRequestToUploadEndpoint() throws Exception {
        mockMvc.perform(post("/api/logs/analyze").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void analyzeTextReturnsSameShapeAsUpload() throws Exception {
        String body = "{\"text\":" + jsonString(LOG) + "}";
        mockMvc.perform(post("/api/logs/analyze-text").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.sourceName").value("pasted text"))
                .andExpect(jsonPath("$.uniqueErrorGroups").value(1))
                .andExpect(jsonPath("$.errorGroups[0].exceptionType").value("java.lang.NullPointerException"));
    }

    @Test
    void analyzeTextRejectsBlankTextWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/logs/analyze-text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.details[0]").value(containsString("text")));
    }

    @Test
    void analyzeTextRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/logs/analyze-text").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or not valid JSON."));
    }

    @Test
    void sampleEndpointReturnsLabelledSampleAnalysis() throws Exception {
        mockMvc.perform(get("/api/logs/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("sample-application.log"))
                .andExpect(jsonPath("$.notice").value(containsString("SAMPLE DATA")))
                .andExpect(jsonPath("$.content").value(containsString("SAMPLE DATA")))
                .andExpect(jsonPath("$.analysis.summary.sampleData").value(true))
                .andExpect(jsonPath("$.analysis.uniqueErrorGroups").value(9))
                .andExpect(jsonPath("$.analysis.summary.totalLines").value(72));
    }

    @Test
    void errorRulesEndpointListsLocalRules() throws Exception {
        mockMvc.perform(get("/api/logs/error-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(greaterThan(5)))
                .andExpect(jsonPath("$.rules[0].id").exists())
                .andExpect(jsonPath("$.rules[0].possibleCause").exists())
                .andExpect(jsonPath("$.disclaimer").value(containsString("not a confirmed diagnosis")));
    }

    @Test
    void errorResponsesUseOneConsistentShapeAndHideInternals() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze").file(file("app.log", new byte[0])))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
