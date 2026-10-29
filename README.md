# Court List Publishing Service

`cp-court-list-publishing-service` is the HMCTS Common Platform (Crime) service that publishes court lists. It:

- builds court lists from the Listing, Progression and Reference Data services;
- publishes them to **CaTH** (Courts and Tribunals Hearings) through Azure API Management;
- generates PDFs and stores them in Azure Blob Storage;
- produces on-demand PDF and Word downloads;
- publishes national Single Justice Procedure (SJP) lists;
- publishes Crown court daily, advance and firm lists to CaTH in the HMCTS Crown formats;
- tracks the publish and file status of every list.

| | |
|---|---|
| Base path | `/courtlistpublishing-service` |
| Default port | `8082` |
| Stack | Java 25, Spring Boot 4, PostgreSQL 16 + Flyway, Azure Blob Storage, task-manager-service |
| API contract | [hmcts/api-cp-crime-court-list-publisher](https://github.com/hmcts/api-cp-crime-court-list-publisher) |

## Documentation

The full application guide lives in [`docs/`](docs). It comes as [Markdown](docs/court-list-publishing-service.md), which renders on GitHub with diagrams, and as a standalone [HTML page](docs/court-list-publishing-service.html) to open in a browser.

| If you want to… | Read |
|---|---|
| Understand what the service does, list types and business rules | [At a glance](docs/court-list-publishing-service.md#1-at-a-glance) · [Business guide](docs/court-list-publishing-service.md#2-business-guide) |
| Understand the architecture and components | [Architecture](docs/court-list-publishing-service.md#3-architecture) |
| Follow the publish, SJP, download and cleanup flows | [Key flows](docs/court-list-publishing-service.md#4-key-flows-step-by-step) · [Status lifecycle](docs/court-list-publishing-service.md#5-status-lifecycle) |
| Call the API | [API reference](docs/court-list-publishing-service.md#6-api-reference) |
| Work with the database | [Data model](docs/court-list-publishing-service.md#7-data-model) |
| Check who can do what | [Security and access control](docs/court-list-publishing-service.md#8-security-and-access-control) |
| Configure an environment | [Configuration](docs/court-list-publishing-service.md#9-configuration) |
| Develop and change the code | [Developer guide](docs/court-list-publishing-service.md#10-developer-guide) |
| Test the service | [QA and testing guide](docs/court-list-publishing-service.md#11-qa-and-testing-guide) |
| Support or troubleshoot it | [Operations and support](docs/court-list-publishing-service.md#12-operations-and-support) |
| Understand the build and deployment | [CI/CD pipeline](docs/court-list-publishing-service.md#13-cicd-pipeline) |

Other references:

- [`CLAUDE.md`](CLAUDE.md) is a condensed command and architecture reference for Claude Code.
- [`docker/README.md`](docker/README.md) covers the integration Docker assets and the Azurite storage key.
- [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) explains how to contribute.

## API contract (OpenAPI)

The REST API is defined first in a separate project, [hmcts/api-cp-crime-court-list-publisher](https://github.com/hmcts/api-cp-crime-court-list-publisher). It contains the spec [`court-list-publisher.openapi.yml`](https://github.com/hmcts/api-cp-crime-court-list-publisher/blob/main/src/main/resources/openapi/court-list-publisher.openapi.yml) and its [JSON schemas](https://github.com/hmcts/api-cp-crime-court-list-publisher/tree/main/src/main/resources/openapi/schema).

That project publishes the artifact `uk.gov.hmcts.cp:api-cp-crime-court-list-publisher`. The artifact contains the generated controller interfaces (for example `CourtListPublishApi` and `FilesApi`) and the models (`CourtListPublishRequest`, `CourtListType`, and others) that this service implements.

To change an endpoint, request/response model or enum value (such as a new `CourtListType`):

1. Make the change in the [OpenAPI project](https://github.com/hmcts/api-cp-crime-court-list-publisher) and release a new version.
2. Bump `apiSpecVersion` in [`gradle.properties`](gradle.properties).
3. Implement the change in the controllers under `src/main/java/uk/gov/hmcts/cp/controllers`.
4. Add a Drools rule for any new endpoint in [`court-list-publishing-rules.drl`](src/main/resources/acl/court-list-publishing-rules.drl). Requests with no matching rule are denied.

## Prerequisites

- **Java 25.** The Gradle toolchain is set to 25. Check with `java -version`.
- **Docker** (Docker Desktop or the Docker engine), needed for integration tests and the local database.
- **[direnv](https://direnv.net/)** (optional), to load local environment variables from `.env` through [`.envrc`](.envrc).

Always use the Gradle wrapper (`./gradlew`). It pins the Gradle version in [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).

## Build and test

```bash
./gradlew bootJar                 # build the application jar
./gradlew test                    # unit tests
./gradlew integration             # integration tests against a docker-compose stack (Docker must be running)
./gradlew build                   # unit + integration tests (needs Docker)
```

Run a single test class or method:

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.services.CaTHServiceTest'
./gradlew test --tests 'uk.gov.hmcts.cp.services.CaTHServiceTest.someTestMethod'
./gradlew integration --tests 'uk.gov.hmcts.cp.http.FileDownloadIntegrationTest'
```

Notes:

- The compiler runs with `-Werror`, so an unchecked-type warning fails the build.
- `./gradlew integration` builds the jar and the app image, then starts [`docker/docker-compose.integration.yml`](docker/docker-compose.integration.yml) and runs the tests. Unit tests stop at the first failure (`failFast`).
- The integration stack is **not** stopped automatically. Run `./gradlew composeDown` when you're finished.
- If Docker Compose can't connect and the error mentions `.../docker/desktop/docker.sock`, start Docker Desktop. With the Linux `docker.io` engine, run `unset DOCKER_HOST`, `export DOCKER_HOST=unix:///var/run/docker.sock`, or `docker context use default`.

### Integration test stack

| Container | Host port | Purpose |
|---|---|---|
| `app` | `8082` (debug `8787`) | The service, with profile `integration` |
| `db` | `55432` | PostgreSQL 16 |
| `wiremock` | `8089` | Stubs for Listing, Progression, Reference Data, usersgroups, Document Generator and CaTH. Mappings are in [`src/integrationTest/resources/wiremock`](src/integrationTest/resources/wiremock) |
| `azurite` | `10000` | Azure Blob Storage emulator (see [`docker/README.md`](docker/README.md)) |

The `integration` profile replaces the real CaTH publisher with a stub that posts to WireMock, and points blob storage at Azurite. To debug the running app, attach a remote debugger to port `8787`.

For the test layers, the scenario checklist and how to inspect results, see the [QA and testing guide](docs/court-list-publishing-service.md#11-qa-and-testing-guide).

## Running locally

1. Start PostgreSQL. The default datasource expects it on `localhost:55432`:

   ```bash
   docker compose up -d db
   ```

2. Optionally, start the WireMock stubs so upstream calls have something to answer them:

   ```bash
   docker compose -f docker/docker-compose.integration.yml up -d wiremock
   export CP_CLP_BASE_URL=http://localhost:8089
   ```

3. Run the service:

   ```bash
   ./gradlew bootRun
   ```

4. Check that it's up: <http://localhost:8082/courtlistpublishing-service/actuator/health>

Flyway applies the database migrations on startup. You can connect a client such as Postico with database, user and password all set to `courtlistpublishing`.

The root [`docker-compose.yml`](docker-compose.yml) and the integration stack both bind port `55432`. Stop one before starting the other.

## Configuration

All settings come from [`application.yaml`](src/main/resources/application.yaml) and the per-concern files it imports, such as `application-datasource.yml`, `application-azure.yml` and `application-authz.yml`. Every value can be overridden with an environment variable. Locally, put overrides in a `.env` file; [`.envrc`](.envrc) loads it through direnv:

```bash
brew install direnv
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc
direnv allow
```

Commonly used variables:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port. Can also be set with `./gradlew integration -Pserver.port=8080` |
| `CP_CLP_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local Postgres on `55432` / `courtlistpublishing` | Database |
| `CP_CLP_BASE_URL` | `http://localhost:8080` | Base URL of the Common Platform query APIs |
| `CATH_PUBLISHING_ENABLED` | `false` | Send lists to CaTH |
| `AZURE_STORAGE_ENABLED` | `false` | Enables blob storage, PDF generation and file download. Also needs `AZURE_STORAGE_ACCOUNT_NAME` and `AZURE_CLIENT_ID` |
| `AZURE_LOCAL_DTS_APIMURL`, `AZURE_LOCAL_DTS_*`, `AZURE_REMOTE_DTS_*` | — | APIM / Publishing Hub endpoint and Azure AD app registrations. Internal hostnames are not defaulted in the repo |
| `CP_CLP_AUTHZ_ENABLED`, `CP_CLP_AUDIT_ENABLED` | `false` | Authorisation (Drools) and audit filters |
| `PUBLISH_STATUS_CLEANUP_DAYS` | `90` | Retention period for publish records and files |

The full list is in the [Configuration](docs/court-list-publishing-service.md#9-configuration) section of the guide. In deployed environments, secrets are mounted at `/mnt/secrets/rpe/`.

## Static code analysis

PMD uses the ruleset in [`.github/pmd-ruleset.xml`](.github/pmd-ruleset.xml):

```bash
./gradlew pmdMain
```

To run it outside Gradle, install PMD with `brew install pmd`, then:

```bash
pmd check --dir src/main/java --rulesets .github/pmd-ruleset.xml --format html -r build/reports/pmd/pmd-report.html
```

CI also runs CodeQL, an OWASP ZAP baseline scan and a secrets scanner. See [CI/CD pipeline](docs/court-list-publishing-service.md#13-cicd-pipeline).

## Pact provider verification

To run Pact provider verification and publish the results to the Pact broker from your machine, add the following to `.env`, replacing the placeholders:

```bash
export PACT_PROVIDER_VERSION="0.1.0-local-YOUR-INITIALS"
export PACT_VERIFIER_PUBLISH_RESULTS=true
export PACT_PROVIDER_BRANCH="ANY_BRANCH_NAME_THAT_IS_NOT_A_DEFAULT_ONE"
export PACT_BROKER_TOKEN="YOUR_PACTFLOW_BROKER_TOKEN"
export PACT_BROKER_URL="https://hmcts-dts.pactflow.io"
export PACT_ENV="local"
```

Then run:

```bash
./gradlew pactVerificationTest
```

## Contributing

See [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md).

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
