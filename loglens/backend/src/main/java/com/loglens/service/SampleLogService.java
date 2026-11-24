package com.loglens.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/** Loads the bundled sample log (samples/sample-application.log, packaged on the classpath). */
@Service
public class SampleLogService {

    public static final String FILENAME = "sample-application.log";
    public static final String NOTICE =
            "SAMPLE DATA: this is a synthetic log written for demonstration. "
                    + "The results are not from a real system and are not benchmarks.";
    static final String RESOURCE = "samples/" + FILENAME;

    public String loadContent() {
        try (InputStream in = SampleLogService.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The sample log resource is missing from the application.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("The sample log could not be read.");
        }
    }
}
