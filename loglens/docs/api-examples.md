# API examples

Base URL: `http://localhost:8080`. Commands are for Windows PowerShell. Use `curl.exe` (not the `curl`
alias) so multipart uploads work.

## Health

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

```json
{ "status": "UP", "application": "LogLens", "timestamp": "2026-10-22T10:30:00.123Z" }
```

## Upload a file: `POST /api/logs/analyze`

```powershell
curl.exe -F "file=@samples\sample-application.log" -F "redact=true" http://localhost:8080/api/logs/analyze
```

`redact` is optional and defaults to `true`.

## Analyze pasted text: `POST /api/logs/analyze-text`

```powershell
$body = @{ text = (Get-Content samples\sample-application.log -Raw); redact = $true } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/logs/analyze-text -ContentType 'application/json' -Body $body
```

### Abridged response

This is the real output shape for a tiny four-line log (`ERROR` + `NullPointerException` + frame + `INFO`),
shortened by omitting some fields (`...`). `analyzedAt` and the group `id` vary per input/run.

```json
{
  "summary": {
    "sourceName": "pasted text",
    "sampleData": false,
    "analyzedAt": "2026-10-24T08:22:39.321Z",
    "redactionApplied": true,
    "totalLines": 4, "parsedLines": 4, "blankLines": 0, "unknownLines": 0,
    "errorCount": 1, "warningCount": 0, "infoCount": 1, "debugTraceCount": 0,
    "uniqueErrorGroups": 1,
    "mostFrequentException": { "name": "java.lang.NullPointerException", "count": 1 },
    "mostFrequentErrorMessage": { "name": "user is null", "count": 1 }
  },
  "logLevelCounts": [
    { "level": "FATAL", "count": 0, "percentage": 0.0 },
    { "level": "ERROR", "count": 1, "percentage": 50.0 },
    { "level": "WARN",  "count": 0, "percentage": 0.0 },
    { "level": "INFO",  "count": 1, "percentage": 50.0 },
    "... DEBUG, TRACE, UNKNOWN ..."
  ],
  "uniqueErrorGroups": 1,
  "errorGroups": [
    {
      "id": "a6545b570529a256",
      "level": "ERROR",
      "exceptionType": "java.lang.NullPointerException",
      "normalizedMessage": "user is null",
      "occurrenceCount": 1,
      "firstOccurrence": { "lineNumber": 1, "timestamp": "2026-10-22 10:30:12" },
      "lastOccurrence":  { "lineNumber": 1, "timestamp": "2026-10-22 10:30:12" },
      "rootCauseCandidate": { "type": "java.lang.NullPointerException", "message": "user is null" },
      "stackTrace": {
        "mainException": { "type": "java.lang.NullPointerException", "message": "user is null" },
        "rootCauseCandidate": { "type": "java.lang.NullPointerException", "message": "user is null" },
        "rootCauseExplanation": "Heuristic: no 'Caused by' entries were found, so the main exception is the only candidate. It is not a confirmed root cause.",
        "causeChain": [ { "type": "java.lang.NullPointerException", "message": "user is null" } ],
        "suppressedExceptions": [],
        "relevantFrames": [
          { "className": "com.example.UserService", "methodName": "getUser",
            "fileName": "UserService.java", "lineNumber": 42, "applicationFrame": true }
        ],
        "rawLines": [ "java.lang.NullPointerException: user is null", "..." ],
        "truncated": false
      },
      "representativeLines": [ { "lineNumber": 1, "text": "2026-10-22 10:30:12 ERROR Request failed" } ],
      "suggestions": [
        {
          "ruleId": "null-pointer",
          "title": "NullPointerException",
          "possibleCause": "Code may be accessing a method or field on a reference that is null.",
          "suggestedChecks": [ "Inspect the reported source line and identify which reference is null.", "..." ],
          "generic": false,
          "disclaimer": "This is a possible explanation based on local rules, not a confirmed diagnosis."
        }
      ]
    }
  ],
  "topExceptions": [ { "name": "java.lang.NullPointerException", "count": 1 } ],
  "warnings": [],
  "unknownLineCount": 0,
  "privacyNote": "Logs can contain passwords, tokens, ..."
}
```

## Sample: `GET /api/logs/sample`

```powershell
Invoke-RestMethod http://localhost:8080/api/logs/sample | Select-Object filename, notice
```

Returns `{ filename, notice, content, analysis }`. The notice states that this is synthetic sample data.

## Rules: `GET /api/logs/error-rules`

```powershell
(Invoke-RestMethod http://localhost:8080/api/logs/error-rules).rules | Select-Object id, title
```

## Error format

Every failure uses the same body and never includes stack traces or file paths:

```json
{
  "timestamp": "2026-10-22T10:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "The uploaded file is empty.",
  "details": []
}
```

| Status | When |
| --- | --- |
| 400 | Empty, binary or unsupported file; blank text; malformed JSON; missing file part |
| 404 | Unknown path |
| 405 | Wrong HTTP method |
| 413 | File or text larger than `loglens.max-upload-bytes` (default 10 MB) |
| 415 | Wrong content type (for example JSON sent to the upload endpoint) |
| 500 | Unexpected error (generic message only) |
