package uk.gov.hmcts.cp.services.sanitization;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.AddressSchema;
import uk.gov.hmcts.cp.models.transformed.schema.Venue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourtListDocumentSanitizerTest {

    private final CourtListDocumentSanitizer sanitizer =
            new CourtListDocumentSanitizer(new HtmlStrippingSanitizer());

    @Test
    void returnsNullWhenDocumentIsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void stripsHtmlFromNestedStringFields() {
        CourtListDocument input = documentWithAddress(AddressSchema.builder()
                .town("Manchester <br/> City")
                .county("Greater Manchester")
                .postCode("M1<br />1AA")
                .build());

        AddressSchema cleaned = sanitizer.sanitize(input).getVenue().getVenueAddress();

        assertThat(cleaned.getTown()).isEqualTo("Manchester City");
        assertThat(cleaned.getCounty()).isEqualTo("Greater Manchester");
        assertThat(cleaned.getPostCode()).isEqualTo("M1 1AA");
    }

    @Test
    void stripsHtmlFromStringListEntries() {
        CourtListDocument input = documentWithAddress(AddressSchema.builder()
                .line(List.of(
                        "1 Court Street",
                        "Floor <br/> 2",
                        "Entity-encoded &lt;p&gt;wrapping&lt;/p&gt; text"))
                .build());

        AddressSchema cleaned = sanitizer.sanitize(input).getVenue().getVenueAddress();

        assertThat(cleaned.getLine()).containsExactly(
                "1 Court Street",
                "Floor 2",
                "Entity-encoded wrapping text");
    }

    @Test
    void preservesBareLessThanWhenNotTagShaped() {
        CourtListDocument input = documentWithAddress(AddressSchema.builder()
                .town("Aged < 18 and < 20")
                .build());

        AddressSchema cleaned = sanitizer.sanitize(input).getVenue().getVenueAddress();

        assertThat(cleaned.getTown()).isEqualTo("Aged < 18 and < 20");
    }

    @Test
    void dropsObjectFieldWhenSanitizationCollapsesToEmpty() {
        // postCode is HTML-only after stripping -> sanitizer returns null
        // -> walker removes the field -> JsonInclude.NON_NULL omits it from JSON
        // -> when deserialised back into the POJO the field is null
        CourtListDocument input = documentWithAddress(AddressSchema.builder()
                .town("Manchester")
                .postCode("<br /><br />")
                .build());

        AddressSchema cleaned = sanitizer.sanitize(input).getVenue().getVenueAddress();

        assertThat(cleaned.getTown()).isEqualTo("Manchester");
        assertThat(cleaned.getPostCode()).isNull();
    }

    @Test
    void replacesEmptyArrayEntryWithBlankToPreserveArrayLength() {
        // a list entry that is entirely HTML collapses to "" so callers can rely on
        // the original list size and index alignment
        CourtListDocument input = documentWithAddress(AddressSchema.builder()
                .line(List.of("1 Court Street", "<br />", "Floor 2"))
                .build());

        AddressSchema cleaned = sanitizer.sanitize(input).getVenue().getVenueAddress();

        assertThat(cleaned.getLine()).containsExactly("1 Court Street", "", "Floor 2");
    }

    @Test
    void leavesCleanDocumentUntouched() {
        AddressSchema original = AddressSchema.builder()
                .line(List.of("1 Court Street", "Floor 2"))
                .town("Manchester")
                .county("Greater Manchester")
                .postCode("M1 1AA")
                .build();
        CourtListDocument input = documentWithAddress(original);

        CourtListDocument cleaned = sanitizer.sanitize(input);

        assertThat(cleaned.getVenue().getVenueAddress())
                .usingRecursiveComparison()
                .isEqualTo(original);
    }

    private CourtListDocument documentWithAddress(AddressSchema address) {
        return CourtListDocument.builder()
                .venue(Venue.builder().venueAddress(address).build())
                .build();
    }
}
