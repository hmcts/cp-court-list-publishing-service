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
- `app` runs with `SPRING_PROFILES_ACTIVE=integration`, with JDWP debug on host port `8787`.
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