# Single Justice Procedure (SJP) support

The court list publishing service supports **Single Justice Procedure (SJP)** list types alongside STANDARD and ONLINE_PUBLIC.

## Behaviour

- **Publish / task flow**: When a publish request or task uses `courtListType` / `listId` **SJP**:
  - Progression is called with `listId=SJP` (same as other list types).
  - **CaTH**: List is sent with `listType=MAGISTRATES_SJP_LIST` and `sensitivity=PUBLIC`.
  - **PDF**: Generated using the **SjpCourtList** document generator template (no Welsh variant).
  - **Transformation**: Uses `StandardCourtListTransformationService` and **schema/sjp-court-list-schema.json** for validation.

- **Court list data**: `GET .../courtlistdata?listId=SJP&...` is supported (same as STANDARD/ONLINE_PUBLIC; only PRISON is forbidden).

## API dependency

SJP is recognised by **list type name** (`"SJP"`), so it works whether or not the **api-cp-crime-court-list-publisher** OpenAPI spec has added `CourtListType.SJP` yet:

- If the API spec **does not** define SJP, callers can still send `listId=SJP` (e.g. as a string in the request). The task reads `courtListType` from job data and uses `CourtListType.valueOf(...)` / `fromValue("SJP")` if the spec supports it.
- Once the API spec **does** include SJP (e.g. in a new version of the publisher API), set `apiSpecVersion` in **gradle.properties** to that version. The service will then accept SJP in the same way as other enum values.

## Configuration

- **Document generator**: Must provide a template named **SjpCourtList** (see `PdfGenerationService`).
- **CaTH**: Expects list type **MAGISTRATES_SJP_LIST**; adjust `CaTHService` if your CaTH instance uses a different value.
- **Schema**: SJP uses **schema/sjp-court-list-schema.json** (currently aligned with the standard list structure). It can be refined later for SJP-specific fields if needed.

## References

- [Single justice procedure cases listed online](https://www.gov.uk/government/news/single-justice-procedure-cases-to-be-listed-online)
- API spec: **api-cp-crime-court-list-publisher** (and related api-cp-crime-court-publisher changes for SJP).
