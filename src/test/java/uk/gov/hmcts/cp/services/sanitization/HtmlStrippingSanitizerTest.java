package uk.gov.hmcts.cp.services.sanitization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlStrippingSanitizerTest {

    /**
     * Mirrors the strictest anti-HTML rule applied by the downstream schemas:
     * no substring may match {@code <[^>]+>}, in raw or entity-encoded form.
     */
    private static final Pattern FORBIDDEN_RAW = Pattern.compile("<[^>]+>");
    private static final Pattern FORBIDDEN_ENTITY = Pattern.compile("&lt;[^&]*&gt;");

    private final HtmlStrippingSanitizer sanitizer = new HtmlStrippingSanitizer();

    @Test
    void returnsNullWhenInputIsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void returnsNullWhenInputIsEmpty() {
        assertThat(sanitizer.sanitize("")).isNull();
    }

    @Test
    void returnsNullWhenInputIsWhitespaceOnly() {
        assertThat(sanitizer.sanitize("   ")).isNull();
        assertThat(sanitizer.sanitize("\n\t ")).isNull();
    }

    @Test
    void trimsSurroundingWhitespaceWhenNoHtmlPresent() {
        assertThat(sanitizer.sanitize("  hello  ")).isEqualTo("hello");
        assertThat(sanitizer.sanitize("\nhello\t")).isEqualTo("hello");
    }

    @Test
    void collapsesInternalWhitespaceRuns() {
        assertThat(sanitizer.sanitize("foo   bar")).isEqualTo("foo bar");
    }

    @Test
    void returnsSameInstanceWhenNoTagLikeContent() {
        String input = "Contrary to section 40CA(1) and (4) of the Prison Act 1952.";
        assertThat(sanitizer.sanitize(input)).isSameAs(input);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // raw HTML tags
            "Contrary to s.40.<br /><br /> | Contrary to s.40.",
            "<p>hello</p>                  | hello",
            "before<br/>after              | before after",
            "<div class=\"x\">text</div>   | text",
            // entity-encoded forms (schema patterns 6/7 forbid closing/self-closing)
            "&lt;br/&gt;hello              | hello",
            "before&lt;br/&gt;after        | before after",
            "&lt;/p&gt;trailing            | trailing",
            // mixed raw and entity-encoded
            "<br/>middle&lt;/p&gt;         | middle",
    })
    void stripsAllRawAndEntityEncodedTags(String input, String expected) {
        assertThat(sanitizer.sanitize(input)).isEqualTo(expected);
    }

    @Test
    void collapsesWhitespaceAcrossNewlinesAfterStripping() {
        String input = "line1<br/>\nline2";
        assertThat(sanitizer.sanitize(input)).isEqualTo("line1 line2");
    }

    @Test
    void returnsNullWhenInputIsEntirelyTagLike() {
        assertThat(sanitizer.sanitize("<br />")).isNull();
        assertThat(sanitizer.sanitize("<br/><br/>")).isNull();
        assertThat(sanitizer.sanitize("&lt;br/&gt;")).isNull();
        assertThat(sanitizer.sanitize("<p></p>")).isNull();
    }

    @Test
    void leavesBareLessThanIntactWhenNoMatchingGreaterThan() {
        // The schema regex requires a closing '>' to consider a substring tag-shaped,
        // so bare '<' or '>' on its own is left alone.
        String input = "defendant aged < 18 years and his sister is aged < 20";
        assertThat(sanitizer.sanitize(input)).isSameAs(input);
    }

    @Test
    void outputAlwaysSatisfiesSchemaRegex() {
        String[] inputs = {
                "Contrary to s.40.<br /><br />",
                "<p>hello</p>",
                "&lt;br/&gt;hello",
                "before<br/>middle&lt;/p&gt;after",
                "<a href=\"x\">link</a> and &lt;span&gt;more&lt;/span&gt;",
                "defendant aged < 18 years > old",
                "no tags here",
                "",
                "<br />",
        };
        for (String input : inputs) {
            String sanitized = sanitizer.sanitize(input);
            if (sanitized == null) {
                // null is the most-stripped possible output and trivially satisfies the schema
                continue;
            }
            assertThat(FORBIDDEN_RAW.matcher(sanitized).find())
                    .as("sanitized output must not contain raw tag-like substring: <%s>", sanitized)
                    .isFalse();
            assertThat(FORBIDDEN_ENTITY.matcher(sanitized).find())
                    .as("sanitized output must not contain entity-encoded tag-like substring: <%s>", sanitized)
                    .isFalse();
        }
    }
}
