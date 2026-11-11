# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service does

`cp-court-list-publishing-service` (HMCTS Common Platform) is a Spring Boot 4 service that builds court lists from Common Platform query APIs, publishes them to **CaTH** (Courts and Tribunals Hearings / Publishing Hub, via Azure APIM), generates PDFs through the document generator, and stores files in Azure Blob Storage. The context path is `/courtlistpublishing-service` and the default port is `8082`.

## Commands

Use the Gradle wrapper. The toolchain is **Java 25** (`build.gradle`). The README says Java 21, but that is out of date. Compilation uses `-Xlint:unchecked -Werror`, so unchecked warnings fail the build.

```bash
./gradlew test                                   # unit tests (excludes @Tag("pact"))
./gradlew test --tests 'uk.gov.hmcts.cp.services.CaTHServiceTest'            # single class
./gradlew test --tests 'uk.gov.hmcts.cp.services.CaTHServiceTest.someMethod' # single method
./gradlew integration                            # integration tests against the docker-compose stack (Docker must be running)
./gradlew integration --tests 'uk.gov.hmcts.cp.http.FileDownloadIntegrationTest'
./gradlew build                                  # runs test AND integration, so it needs Docker
./gradlew bootJar                                # jar only
./gradlew pmdMain                                # PMD with .github/pmd-ruleset.xml (runs only when named explicitly)
./gradlew pactVerificationTest                   # Pact provider verification (needs PACT_* env vars, see README)
./gradlew composeDown                            # integration doesn't tear the stack down (finalizedBy is commented out)
```

- Unit tests run with `failFast = true` and attach Mockito as a javaagent.
- `CourtListStatusRepositoryTest` uses Testcontainers Postgres.
- Local env vars come from `.env`, loaded by `.envrc` through direnv.

## Integration test stack

`integration` depends on `composeUp`, which builds `bootJar` and starts `docker/docker-compose.integration.yml`:
- `app` runs with `SPRING_PROFILES_ACTIVE=integration`, with JDWP debug on host port `8787` (override with `CP_CLP_IT_DEBUG_PORT` if that port is taken).
- Postgres on `55432`.
- WireMock on `8089`. Its stubs live in `src/integrationTest/resources/wiremock/{mappings,__files}`; add new upstream stubs here.
- Azurite on `10000`.

Integration tests in `src/integrationTest/java/.../http` are plain HTTP clients against the running container (`app.baseUrl` and `wiremock.baseUrl` system properties). They are not `@SpringBootTest`. Because the app runs inside the container, **you must rebuild the image to pick up code changes**; `composeUp` does this.

The `integration` profile swaps in `StubCourtListPublisher` (`@Primary`), which posts to WireMock instead of the real APIM/CaTH, and `AzureIntegrationConfig`, which points blob storage at Azurite.

## Architecture

### API contract comes from an external artifact
Controllers implement interfaces generated in `uk.gov.hmcts.cp:api-cp-crime-court-list-publisher` (`apiSpecVersion` in `gradle.properties`), for example `CourtListPublishApi`. Request and response models (`uk.gov.hmcts.cp.openapi.model.*`, `CourtListType`, and others) also come from that artifact. Endpoint paths, new enum values, and model changes are made in the spec repo first; then you bump the version here.

### Async publish flow (task-manager-service)
Long-running work is queued through `uk.gov.hmcts.cp:task-manager-service`. `TaskManagerConfig` enables scheduling, and the JobExecutor polls the DB for jobs.

1. `POST` publish → `CourtListPublishController.publishCourtList` → `CourtListPublishStatusService.createOrUpdate` writes a row in `court_list_publish_status`.
2. `CourtListTaskTriggerService` creates `ExecutionInfo` with jobData (see `task/JobDataConstant`, which includes the caller's `CJSCPPUID` as userId) and assigns `PUBLISH_AND_PDF_GENERATION_TASK`.
3. `task/CourtListPublishAndPDFGenerationTask` (`@Task(...)`) does the following:
   - fetches the payload via `CourtListQueryService` (from progression and listing via `ProgressionQueryService`/`ListingQueryService`, plus `ReferenceDataService`);
   - transforms and validates it, then publishes to CaTH when `cath.publishing-enabled` is set;
   - refetches the payload without applications for the PDF, then generates the PDF and uploads it to blob storage;
   - updates the status row through `CourtListStatusUpdater`.

   The task catches errors per step and always returns `COMPLETED`. Failures are recorded on the status row and logged with `ALERT_PATTERN`.

SJP (Single Justice Procedure) lists follow the same split:
- `SjpCourtListPublishService` → `SjpTaskTriggerService` → `task/SjpPublishTask` (`SJP_PUBLISH_TASK`).
- `SjpToCathPayloadTransformer` builds the CaTH payload.
- SJP lists share `court_list_publish_status`, with `courtCentreId` null. The SJP list type is mapped onto `CourtListType` by `SjpStatusListTypeMapper`.

### Transformation → sanitize → schema-validate → publish
- `CourtListQueryService.buildCourtListDocumentFromPayload` picks a transformer. `PUBLIC` uses `OnlinePublicCourtListTransformationService`; everything else uses `StandardCourtListTransformationService`. Both extend `BaseCourtListTransformationService`.
- Output models are in `models/transformed`. Inbound upstream models are in `models/`.
- Documents are sanitized (`services/sanitization`):
  - `HtmlStrippingSanitizer`;
  - `WafPatternSanitizer`, which strips the substrings in `cath.waf-blocked-patterns` that the Azure WAF would reject;
  - `RequiredStringFieldsRegistry`.
- Sanitized documents are validated against JSON schemas in `src/main/resources/schema/`, using `JsonSchemaValidatorService` and the `PublicationSchema` enum.
- `CaTHService` maps `CourtListType` to the CaTH list type and sensitivity (`COURT_LIST_MAPPINGS`), builds the `DtsMeta` headers, uploads the raw payload to blob storage on a best-effort basis, and calls `CourtListPublisher`. That is `CaTHPublisher` → `PublishingService` (Azure AD token via `AzureIdentityService`/`DtsAzureConfig`) in real environments, and `StubCourtListPublisher` in integration.

### Downloads
`CourtListDownloadService` renders PDF/DOCX on demand via `DocumentGeneratorClient`:
- Crown courts (ref data `oucodeL1Code == "C"`) go through `generateCrownCourtPdf`, with Welsh template variants.
- Prison lists have their own endpoint and ACL rule. The standard download rejects `PRISON`.
- `FileController` serves stored blobs.

### Conditional beans
`AzureBlobService`, `PdfGenerationService`, `CourtListPdfHelper`, `CourtListPublisherBlobClientService` and `FileController` exist only when `azure.storage.enabled=true` (`AZURE_STORAGE_ENABLED`). Code that depends on them must take `Optional<...>` or tolerate their absence, as `CaTHService` does.

### Security / filters
- Authorization uses `cp-auth-rules-filter` with Drools rules in `src/main/resources/acl/court-list-publishing-rules.drl`. Rules are keyed on action names such as `courtlistpublishing-service.publish.post`, derived from the vendor media types the controllers return.
- Groups and permissions are defined in `acl/SecurityGroupConstants` and `acl/PermissionConstants`. A new endpoint needs a matching rule, because `deny-when-no-rules: true`.
- `cp-audit-filter-springboot` does auditing (Artemis JMS).
- Both filters are disabled by default via `CP_CLP_AUTHZ_ENABLED` and `CP_CLP_AUDIT_ENABLED`.
- The caller identity is the `CJSCPPUID` header. Some background calls use `courtlistpublishing.system-user-id`.

### Configuration
`application.yaml` imports per-concern files:
- `application-datasource.yml`
- `-server-management`
- `-azure`
- `-jwt`
- `-authz`
- `-audit`

It also imports secrets from `/mnt/secrets/rpe/` (configtree). Every setting is overridable through env vars (`CP_CLP_*`, `CATH_*`, `AZURE_*`, and so on). Don't put internal hostnames in defaults; they are set per environment.

### Database
- Postgres, with the schema managed by **Flyway only**. Hibernate runs with `ddl-auto: validate`.
- Migrations are in `src/main/resources/db/migration` and follow the `V10xx__...` numbering. `out-of-order: true` is enabled.
- Hikari runs with `auto-commit: false`, so writes need `@Transactional`.

---

<!-- Merged from hmcts/agentic-plugins-marketplace: plugins/agents/hmcts-sdlc-orchestrator/CLAUDE.md -->

# HMCTS SDLC Pipeline — Orchestrator

## Project context
This is an HMCTS engineering project. All work must comply with HMCTS engineering standards,
GDS Service Manual principles, and MOJ security and accessibility requirements.

Always load the following before any pipeline stage:
- `context/tech-stack.md`
- `context/hmcts-standards.md`
- `context/azure-cloud-native.md` — Cloud-Native posture and Shared Responsibility Model on Azure.
- `context/logging-standards.md` — mandatory JSON logging for Spring Boot services.

Load on demand when relevant:
- `context/azure-sdk-guide.md` — when working on any Azure service integration.
- `context/cloud-adoption-rationale.md` — only when lock-in or cloud-cost objections surface, or when an ADR weighs those trade-offs. Do not auto-load.

---

## Pipeline stages

Run stages in order. Do not skip or reorder. Halt at every human gate before proceeding.

| # | Stage          | Agent file                         | Gate   |
|---|----------------|------------------------------------|--------|
| 1 | Requirements   | agents/requirements-analyst.md     | Human  |
| 2 | Architecture & Design | agents/architecture-designer.md | Human|
| 3 | User Story     | agents/story-writer.md             | Human  |
| 4 | Test Specs     | agents/test-engineer.md            | Human  |
| 5 | Code           | agents/implementation.md           | Auto   |
| 6 | Code Review    | agents/code-reviewer.md            | Human  |
| 7 | Build & Test   | agents/ci-orchestrator.md          | Auto   |
| 8 | Deploy Sandbox | agents/deployer.md                 | Human  |

---

## Shared skills (available to all agents)

Skills split across the marketplace and this repo. Install the marketplace plugins once (see the plugin README Prerequisites section) — the file paths below resolve to pointer stubs or HMCTS overlays that reference the installed plugins.

| Skill file                              | Source                            | Use when                                      |
|-----------------------------------------|-----------------------------------|-----------------------------------------------|
| skills/write-acceptance-criteria.md     | marketplace: `bdd-workflow`       | Deriving testable ACs from any requirement    |
| skills/generate-bdd-specs.md            | marketplace: `bdd-workflow`       | Writing Cucumber/Gherkin feature files        |
| skills/accessibility-check.md           | marketplace: `accessibility-check` + HMCTS overlay | WCAG 2.1 AA review + GOV.UK Frontend guidance |
| skills/review-checklist.md              | marketplace: `review-checklist` + HMCTS overlay    | Code review checklist + Spring Boot / Azure / logging |
| skills/adr-template.md                  | marketplace: `adr-template`       | Recording any architecture decision           |
| skills/springboot-service-from-template/| local (HMCTS-specific)            | Standing up a new Spring Boot service from the HMCTS template |
| skills/springboot-api-from-template/    | local (HMCTS-specific)            | Standing up a new HMCTS Marketplace API spec repo |
| skills/cpp-test-authoring/              | local (HMCTS-specific)            | Writing or extending tests in `cpp-ui-e2e-serenity` (Serenity BDD/Cucumber) or `cpp-apitests` (JUnit 5 + REST Assured) |
| skills/export-design-artifact/          | local (HMCTS-specific)            | Exporting a self-contained HTML artifact (plan / decision / design-gap / comparison) from the bundled template gallery — **mandatory** for the implementation plan before Stage 5 |

---

## Artefact output convention

All pipeline artefacts are written to `/docs/pipeline/` in the repo:

```
docs/pipeline/
├── requirements.md
├── user-stories/
│   └── <story-id>.md
├── test-specs/
│   └── <story-id>.feature
├── adrs/
│   └── <NNN>-<title>.md
├── artifacts/
│   └── <NNN>-<slug>.html   # HTML artifacts (plan, decision, design-gap, comparison)
└── deploy-notes.md
```

HTML artifacts are produced via `skills/export-design-artifact/` from the bundled template gallery.
Each keeps its `<!-- claude-artifact: ... -->` marker (line 1 of `<head>`) so it is indexable, and
must be surfaced at the relevant human gate. An **implementation-plan** artifact is mandatory before
Stage 5 — see the Hard rules.

---

## Hard rules

- Never proceed past a human gate without explicit confirmation.
- Never invent requirements, ACs, or test data — flag unknowns as open questions.
- Every story must have a linked Jira ticket before the test stage begins.
- All code must pass the review checklist before CI is triggered.
- Accessibility (WCAG 2.1 AA) is non-negotiable for any user-facing output.
- Do not store PII, case data, or court reference numbers in artefacts or prompts.
- If confidence in a decision is low, write an ADR and surface it for review.
- **Export an HTML artifact** (via `skills/export-design-artifact/`) whenever a decision must be made,
  a plan needs improving, or a design gap is discovered — and **always export an implementation-plan
  artifact before Stage 5 (Code) begins**, even when the design is clean. No plan artifact at
  `docs/pipeline/artifacts/` → do not start coding.
- **Integration tests for new endpoints (backend features & bugfixes).** Any PR that adds or changes
  a REST endpoint / `@Handles` action / message-driven entry point MUST add **at least one integration
  test per new endpoint**, and the integration-test suite MUST be green when run locally — via
  `mvn clean && ./runIntegrationTests.sh` when that script exists at the repo root, otherwise the
  repo's documented IT command. Never weaken or skip an IT to go green.
- For Spring Boot services: the HMCTS templates (`service-hmcts-crime-springboot-template`, `api-hmcts-crime-template`) are the master source. Use `skills/springboot-service-from-template/` or `skills/springboot-api-from-template/` to adopt them — do not scaffold build files, Dockerfile, or logback config from scratch. Deviations require an ADR.
- JSON logging to stdout is mandatory for Spring Boot services. See `context/logging-standards.md`.
- Azure integrations use the Azure SDK via Managed Identity. Connection strings, SAS tokens, and account keys are not permitted in code, config, env vars, or Helm values. See `context/azure-sdk-guide.md`.

## Notes for this repo

- The `agents/`, `skills/` and `context/` paths above resolve inside the installed `hmcts-sdlc-orchestrator` plugin, not this repo.
- This is a Gradle project: the integration-test command is `./gradlew integration` (needs Docker), not `mvn`/`runIntegrationTests.sh`.
