# Verification status

This file records what was actually executed when the project was generated, so nobody has to guess.

## Executed and passing

- **Backend logic, 69 tests**: parser, normaliser, stack-trace parser, grouping, rule engine, redaction, file
  validation and analysis service (including the sample-log expectations). They were compiled and run with a
  plain JDK 21 against minimal stand-ins for JUnit and the few Spring annotations used, because Maven
  dependencies could not be downloaded in the generation environment.
- **Frontend logic, 18 tests**: filtering, sorting, validation, formatting and report building, run in Node
  with a tiny stand-in for Vitest's `describe/it/expect`.
- **Frontend build check**: every `.jsx` and `.js` file (including the component tests) was bundled with
  esbuild, which verifies syntax and that all local imports and named exports resolve.
- **Performance sanity check**: about 200,000 lines (plus adversarial long lines) parsed and grouped in about
  3 seconds in a single run on the generation machine. This is not a benchmark.

## Written but NOT executed yet

- `mvn clean test` with the real toolchain, including the Spring tests (`HealthControllerTest`,
  `LogControllerTest`, `LogControllerLimitsTest`), `GlobalExceptionHandler`, `CorsConfig`, `LogController` and the
  Maven resource configuration that bundles `samples/sample-application.log`.
- `npm install`, `npm run dev`, `npm run build` and `npm test` (the React component tests in
  `UploadPanel.test.jsx` and `Dashboard.test.jsx`).
- Manual use in a browser, including drag and drop and the chart rendering.

Run the commands in the README, and treat any failure there as a bug to fix.
