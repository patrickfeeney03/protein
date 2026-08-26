# AGENTS Guide

This file is for coding agents working in this repository.
It captures build/test commands, style rules, and repo-specific behavior.

## 1) Stack and Scope
- Spring Boot 3.5.8 with Maven wrapper (`mvnw`).
- Java version is 21 (`pom.xml` -> `<java.version>21</java.version>`).
- Main concerns in this codebase:
  - REST APIs for auth, food, comments, and generic data.
  - JPA entities/repositories with PostgreSQL.
  - Price fetchers/updaters for Aldi/Lidl/Dunnes/Tesco.
  - Security via Spring Security Google OAuth2 login (`oauth2Login`) establishing the session + remember-me.

## 2) Repository Layout
- `src/main/java/com/example/demo`
  - `controllers/` REST endpoint classes.
  - `services/` business logic.
  - `repositories/` Spring Data JPA interfaces.
  - `entities/` JPA entities + request/response records.
  - `DTOs/` transport DTO classes/records.
  - root package also contains security/filter/scraper/fetcher/updater classes.
- `src/main/resources`
  - `application.properties` (base),
  - `application-dev.properties`,
  - `application-prod.properties`.
- `src/test/java/com/example/demo`
  - JUnit 5 Spring Boot tests.
- `target/` is generated output; do not edit.

## 3) Build, Lint, Test, and Run Commands
Always use the Maven wrapper from repo root.

### Build / Package
- Full clean build: `./mvnw clean package`
- Build without tests: `./mvnw -DskipTests clean package`
- Compile only: `./mvnw compile`

### Test
- Run all tests: `./mvnw test`
- Run one test class: `./mvnw -Dtest=DemoApplicationTests test`
- Run one test method: `./mvnw -Dtest=DemoApplicationTests#contextLoads test`
- Run multiple methods in one class: `./mvnw -Dtest=DemoApplicationTests#methodA+methodB test`
- Run by wildcard pattern: `./mvnw -Dtest='*ServiceTest' test`
- Keep full stack traces: `./mvnw -Dtest=DemoApplicationTests -DtrimStackTrace=false test`

Notes for single-test runs:
- `-Dtest=...` targets JUnit tests through Maven Surefire.
- Method-level targeting uses `ClassName#methodName`.
- If no tests match, Surefire fails by default; check naming and package.

### Lint / Static Analysis
- There is no configured lint tool (no Checkstyle/SpotBugs/PMD plugin in `pom.xml`).
- Treat `./mvnw test` as the minimum quality gate.
- Optional sanity command when touching many files: `./mvnw -DskipTests compile`.

### Run App
- Default profile: `./mvnw spring-boot:run`
- Dev profile: `./mvnw -Dspring-boot.run.profiles=dev spring-boot:run`
- Prod profile: `./mvnw -Dspring-boot.run.profiles=prod spring-boot:run`

## 4) Configuration and Environment
- Development DB defaults in `application-dev.properties`:
  - url `jdbc:postgresql://localhost:5432/appdb`
  - user `app`
  - password `devpassword` (local/dev only)
- Production profile expects `DB_PASS` env var.
- Hikari pool and session timeout are set in `application.properties`.
- Scraper/fetcher behavior:
  - Tesco fetcher may cache API key in `~/.tesco-mango-key.properties`.
  - Optional env var: `MANGO_KEY_TTL_MS`.

## 5) Code Style and Conventions
Follow existing patterns in neighboring files first.

### Formatting
- Use 4-space indentation in most handwritten code.
- Some generated/older files use tabs; preserve file-local style when editing.
- Keep one top-level class per file.
- Keep methods focused; extract helpers for parsing/transforms.
- Avoid trailing whitespace and noisy reformatting-only diffs.

### Imports
- Prefer explicit imports over fully qualified type names in method bodies.
- Existing code uses some wildcard imports (`*`) for Spring/JPA packages.
- For new files, prefer explicit imports unless wildcard use is already local convention.
- Remove unused imports when touching a file.

### Types and Nullability
- Use wrapper types (`Long`, `Float`, etc.) for nullable DB/API fields.
- Use primitives (`long`, `int`, `boolean`) only when null is impossible.
- Use `Optional<T>` for lookup/parse outcomes (repo methods, fetcher results).
- Use `var` for obvious local inference; use explicit types when clarity is better.
- Prefer records for compact immutable request/response shapes.

### Naming
- Packages: lowercase.
- Classes/interfaces/enums/records: `PascalCase`.
- Methods/fields/locals: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Repository methods: Spring Data derived naming (`findBy...`, `existsBy...`).
- There is legacy `localtestingcontroller`; do not copy this naming style.

### Layering and Architecture
- Controllers should:
  - handle HTTP mapping/auth context,
  - delegate business logic to services,
  - avoid direct repository access.
- Services should:
  - enforce business/authorization rules,
  - orchestrate repositories/fetchers,
  - map entities to DTOs.
- Repositories should stay declarative (interface methods only).

### DTO/Entity Rules
- Do not expose password hashes or sensitive fields in API DTOs.
- Keep entity-to-DTO mapping centralized in service helper methods.
- Update DTO and mapping code together when fields change.
- Preserve existing date/time conventions (`Instant`, `LocalDate`).

### Error Handling
- API-layer/business errors generally use `ResponseStatusException` with proper status.
- For external price fetchers, prefer:
  - log at debug/info,
  - return `Optional.empty()` instead of throwing upstream.
- Avoid `System.out.println` in new code; prefer SLF4J logger.
- Include useful context in logs (ids, URLs), but never secrets.

### Security and Auth
- Security is configured in `SecurityBeans` (Google OAuth2 `oauth2Login`) + `GoogleLoginSuccessHandler` + `SessionUserFilter`.
- New protected endpoints should respect current auth/session flow.
- Public routes must be explicitly permitted in security config.
- Keep remember-me/session behavior consistent with existing setup.

## 6) Testing Guidelines
- Test framework: JUnit 5 + Spring Boot test starter.
- Place tests under mirrored package in `src/test/java`.
- Name tests `*Test` or `*Tests`.
- For service logic, prefer focused unit/slice tests where possible.
- For endpoint/security behavior, use Spring integration tests.
- Add/adjust tests for behavior changes; do not rely on manual verification only.

## 7) Git and Change Hygiene
- Do not commit generated output (`target/`) or IDE files.
- Keep commits scoped and messages short, sentence-case, no prefix.
  - Example style: `Fetch price if Aldi Url is present`
- If a change touches config/env behavior, call it out in PR notes.

## 8) Cursor / Copilot Instructions
- `.cursorrules`: not present.
- `.cursor/rules/`: not present.
- `.github/copilot-instructions.md`: not present.

If any of the above files are added later, merge their rules into this guide.

## 9) Agent Checklist Before Finishing
- Run relevant tests (at minimum targeted tests for changed logic).
- If tests are absent, run `./mvnw -DskipTests compile` as baseline sanity check.
- Confirm no accidental secrets or env values are introduced.
- Confirm imports/build remain clean and changes stay in intended layers.
