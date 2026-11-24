package com.loglens.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Uses a tiny configured limit so the size checks can be tested without large payloads. */
@SpringBootTest(properties = "loglens.max-upload-bytes=1024")
@AutoConfigureMockMvc
class LogControllerLimitsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadOverTheConfiguredLimitIsRejectedWith413() throws Exception {
        byte[] big = new byte[2000];
        Arrays.fill(big, (byte) 'a');
        mockMvc.perform(multipart("/api/logs/analyze").file(new MockMultipartFile("file", "big.log", "text/plain", big)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(containsString("maximum allowed size")));
    }

    @Test
    void textOverTheConfiguredLimitIsRejectedWith413() throws Exception {
        String body = "{\"text\":\"" + "a".repeat(2000) + "\"}";
        mockMvc.perform(post("/api/logs/analyze-text").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void uploadWithinTheLimitStillWorks() throws Exception {
        mockMvc.perform(multipart("/api/logs/analyze")
                        .file(new MockMultipartFile("file", "ok.log", "text/plain", "ERROR boom\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.errorCount").value(1));
    }
}
