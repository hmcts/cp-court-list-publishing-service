package uk.gov.hmcts.cp.services.courtlistdownload;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import java.util.Map;
import java.util.Set;

/**
 * Removes the fields of listing's crown daily list payload ({@code listing.search.daily.list.payload})
 * that no crown Docmosis template reads, so large lists send Docmosis a much smaller payload.
 *
 * <p>Used for the templates CrownDailyList, CrownFirmList and CrownOnlinePublicCourtList, and their Welsh
 * versions. {@link #TEMPLATE_FIELDS} is the union of every field name those templates reference.
 *
 * <p>Fields are kept by name at any depth, because Docmosis also resolves names from parent levels
 * ({@code $parent.x}). Array elements are never removed (even when they become empty objects), so
 * {@code size()}, {@code $idx} and {@code $itemnum} evaluate exactly as they do on the full payload.
 *
 * <p>The templates are owned by {@code cpp-context-system-doc-generator}, in
 * {@code systemdocgenerator-docmosis-service/src/main/resources/templates/courtlist/Crown*Template.docx}.
 * Whoever changes one of those templates must add any new payload field to {@link #TEMPLATE_FIELDS}. A field
 * missing from the set prints as a blank, with no error. Trimming can be switched off without a code change by
 * setting {@code CP_CLP_CROWN_PAYLOAD_TRIM_ENABLED=false} in the environment's Helm values (the pods then restart).
 *
 * <p>{@link #TEMPLATE_FIELDS} was checked against the six templates (English and Welsh) on doc-generator's
 * {@code main} branch at commit {@code c66d8be81}. Check against {@code main}, not a feature branch: the crest
 * short-name fields, for example, are only used by the {@code main} versions of the Daily and Firm templates.
 * These payload fields are deliberately dropped because none of those templates reference them:
 * {@code welshTitle}, {@code welshLabel}, {@code restrictFromCourtList} (no template condition uses it, so dropping
 * it cannot change which hearings are printed), {@code applicant}, {@code respondents} and {@code subject}.
 */
public final class CrownDailyListPayloadTrimmer {

    /* default */ static final Set<String> TEMPLATE_FIELDS = Set.of(
            // document header and footer
            "listDate", "courtCentreName", "draftNumber", "publishedAt",
            "documentType", "documentCounter",
            "sittingDate", "weekCommencingStartDate", "weekCommencingEndDate",
            // court lists and court site
            "courtLists", "crestCourtSite",
            // "CrestCourtFullName" (capital C) is not a duplicate: CrownDailyList(Welsh) and CrownFirmList(Welsh)
            // read $parent.crestCourtSite.CrestCourtFullName. Keep both spellings while the templates use both.
            "crestCourtFullName", "CrestCourtFullName", "welshCrestCourtFullName",
            "crestCourtShortName", "welshCrestCourtShortName",
            "courtCentreAddress1", "courtCentreAddress2",
            "welshCourtCentreAddress1", "welshCourtCentreAddress2",
            "phoneNumber",
            // sittings and judiciary
            "sittings", "courtRoomName", "welshCourtRoomName",
            "judiciary", "judiciaryName", "welshJudiciaryName",
            // hearings
            "hearings", "startTime", "hearingType", "description", "welshDescription",
            "publicListNote", "caseIdentifier", "caseReference", "applicationReference",
            "prosecutor", "organisationName", "welshOrganisationName",
            // defendants and offences
            "defendants", "firstName", "lastName", "defenceOrganisation",
            "offences", "statementOfOffence", "title",
            "reportingRestrictions", "label");

    private CrownDailyListPayloadTrimmer() {
    }

    public static JsonObject trim(final JsonObject payload) {
        return trimObject(payload);
    }

    private static JsonObject trimObject(final JsonObject object) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (final Map.Entry<String, JsonValue> entry : object.entrySet()) {
            if (TEMPLATE_FIELDS.contains(entry.getKey())) {
                builder.add(entry.getKey(), trimValue(entry.getValue()));
            }
        }
        return builder.build();
    }

    private static JsonArray trimArray(final JsonArray array) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (final JsonValue value : array) {
            builder.add(trimValue(value));
        }
        return builder.build();
    }

    private static JsonValue trimValue(final JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> trimObject(value.asJsonObject());
            case ARRAY -> trimArray(value.asJsonArray());
            default -> value;
        };
    }
}
