package com.loglens.controller;

import com.loglens.dto.AnalysisResponse;
import com.loglens.dto.AnalyzeTextRequest;
import com.loglens.dto.RulesResponse;
import com.loglens.dto.SampleLogResponse;
import com.loglens.exception.InvalidLogFileException;
import com.loglens.service.FileValidationService;
import com.loglens.service.LogAnalysisService;
import com.loglens.service.SampleLogService;
import com.loglens.service.TroubleshootingEngine;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Thin HTTP layer: validation and orchestration live in the services. */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogAnalysisService analysisService;
    private final FileValidationService validationService;
    private final SampleLogService sampleLogService;
    private final TroubleshootingEngine troubleshootingEngine;

    public LogController(LogAnalysisService analysisService,
                         FileValidationService validationService,
                         SampleLogService sampleLogService,
                         TroubleshootingEngine troubleshootingEngine) {
        this.analysisService = analysisService;
        this.validationService = validationService;
        this.sampleLogService = sampleLogService;
        this.troubleshootingEngine = troubleshootingEngine;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestParam("file") MultipartFile file,
                                    @RequestParam(name = "redact", defaultValue = "true") boolean redact) {
        validationService.validateUpload(file.getOriginalFilename(), file.getSize());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidLogFileException("The uploaded file could not be read.");
        }
        String text = validationService.decode(bytes);
        return analysisService.analyze(text, validationService.sanitizeFilename(file.getOriginalFilename()), redact);
    }

    @PostMapping(path = "/analyze-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse analyzeText(@Valid @RequestBody AnalyzeTextRequest request) {
        String text = validationService.validateText(request.text());
        return analysisService.analyze(text, "pasted text", request.redactOrDefault());
    }

    @GetMapping("/sample")
    public SampleLogResponse sample() {
        String content = sampleLogService.loadContent();
        return new SampleLogResponse(SampleLogService.FILENAME, SampleLogService.NOTICE, content,
                analysisService.analyzeSample(content));
    }

    @GetMapping("/error-rules")
    public RulesResponse errorRules() {
        return new RulesResponse(troubleshootingEngine.describeRules(), TroubleshootingEngine.DISCLAIMER);
    }
}
