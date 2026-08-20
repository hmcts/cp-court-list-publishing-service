package uk.gov.hmcts.cp.services.sjp;

import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.util.Locale;
import java.util.Map;

/**
 * Maps CaTH's {@code SjpListType} wire vocabulary plus a language onto the fused
 * {@link CourtListType} used as the publish-status row key — SJP is national and all eight
 * daily publishes share a publish date, so the fused type is the only row-key discriminator.
 *
 * <p>Unknown input is rejected, never defaulted — a past bug silently treated any unrecognised
 * list type as public, letting the bogus value {@code SJP_PUBLISH_LIST} through unnoticed.
 */
public final class SjpStatusListTypeMapper {

    private static final String ENGLISH = "ENGLISH";
    private static final String WELSH = "WELSH";

    private static final Map<String, Map<String, CourtListType>> MAPPINGS = Map.of(
            "SJP_PUBLIC_LIST", Map.of(
                    ENGLISH, CourtListType.SJP_PUBLIC_FULL_ENGLISH,
                    WELSH, CourtListType.SJP_PUBLIC_FULL_WELSH),
            "SJP_DELTA_PUBLIC_LIST", Map.of(
                    ENGLISH, CourtListType.SJP_PUBLIC_DELTA_ENGLISH,
                    WELSH, CourtListType.SJP_PUBLIC_DELTA_WELSH),
            "SJP_PRESS_LIST", Map.of(
                    ENGLISH, CourtListType.SJP_PRESS_FULL_ENGLISH,
                    WELSH, CourtListType.SJP_PRESS_FULL_WELSH),
            "SJP_DELTA_PRESS_LIST", Map.of(
                    ENGLISH, CourtListType.SJP_PRESS_DELTA_ENGLISH,
                    WELSH, CourtListType.SJP_PRESS_DELTA_WELSH));

    private SjpStatusListTypeMapper() {
    }

    /**
     * @param sjpListType one of the four SjpListType values
     * @param language    ENGLISH or WELSH (case-insensitive)
     * @return the fused CourtListType used to key the publish-status row
     * @throws IllegalArgumentException if either argument is unrecognised
     */
    public static CourtListType toCourtListType(final String sjpListType, final String language) {
        final Map<String, CourtListType> byLanguage = MAPPINGS.get(normalise(sjpListType));
        if (byLanguage == null) {
            throw new IllegalArgumentException("Unknown SJP list type: " + sjpListType);
        }
        final CourtListType courtListType = byLanguage.get(normalise(language));
        if (courtListType == null) {
            throw new IllegalArgumentException("Unknown SJP language: " + language);
        }
        return courtListType;
    }

    private static String normalise(final String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
