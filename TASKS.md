## Done

All four items have been addressed and verified (193 tests pass, BUILD SUCCESS):

1. **Fix security issues** — CSRF enabled (was disabled), HSTS headers, session-fixation via `changeSessionId()`, auth versioning to invalidate sessions on password change, rate limiting on auth endpoints, proper logout with token revocation, configurable remember-me key, input validation on all auth endpoints, minimal public endpoint list, admin role gates, forwarded-header IP extraction.

2. **Reduce code complexity** — `NutritionScanService.scan()` shrunk from ~68→43 lines via `processImagesAsync()` extraction; `parseMistralResponse()` shrunk from ~62→19 lines via `logAnnotationSnapshot()`, `mergeParsedResults()`, `logMergedResultSnapshot()` extraction; duplicated catch blocks unified into `handleScanError()`.

3. **Improve OCR and text manipulation** — Deduplicated weight-parsing in `ProductTextParser` (removed ~26 duplicated lines); removed dead `RawNutrient.confidence` field; fixed SLF4J arg/placeholder mismatch in `handleScanError()`.

4. **Have clear fallbacks when processes fail** — `AldiFoodScraper` wrapped two crash sites with try/catch; all 4 `*PriceUpdater` loops isolate per-item errors; `GlobalExceptionHandler` added catch-all `@ExceptionHandler(Exception.class)` returning safe JSON.
