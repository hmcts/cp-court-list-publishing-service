package uk.gov.hmcts.cp.services.sanitization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WafPatternSanitizerTest {

    private static final String DEFAULT_PATTERNS = "..\\,../";

    private final WafPatternSanitizer sanitizer = new WafPatternSanitizer(DEFAULT_PATTERNS);

    @Test
    void returnsNullWhenInputIsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void returnsEmptyWhenInputIsEmpty() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void returnsSameInstanceWhenNoBlockedPatternPresent() {
        String input = "completely fine offence wording";
        assertThat(sanitizer.sanitize(input)).isSameAs(input);
    }

    @Test
    void replacesForwardSlashPathTraversalWithSpace() {
        assertThat(sanitizer.sanitize("see ../parent/file"))
                .isEqualTo("see  parent/file");
    }

    @Test
    void replacesBackslashPathTraversalWithSpace() {
        assertThat(sanitizer.sanitize("see ..\\parent\\file"))
                .isEqualTo("see  parent\\file");
    }

    @Test
    void replacesMultipleOccurrencesOnEveryMatch() {
        assertThat(sanitizer.sanitize("a/../b/../c"))
                .isEqualTo("a/ b/ c");
    }

    @Test
    void appliesEveryConfiguredPattern() {
        WafPatternSanitizer custom = new WafPatternSanitizer("foo,bar");
        assertThat(custom.sanitize("xfooxbarx")).isEqualTo("x x x");
    }

    @Test
    void quotesRegexMetaCharactersInConfiguredPatterns() {
        // '.' and '\\' are regex meta-characters; if the sanitiser quoted them
        // correctly, only literal "..\\" matches, not arbitrary chars.
        WafPatternSanitizer custom = new WafPatternSanitizer("..\\");
        assertThat(custom.sanitize("abc..\\xyz")).isEqualTo("abc xyz");
        assertThat(custom.sanitize("abcXXXxyz")).isEqualTo("abcXXXxyz"); // '.' did not match arbitrary chars
    }

    @Test
    void isNoOpWhenConfigIsEmpty() {
        WafPatternSanitizer empty = new WafPatternSanitizer("");
        String input = "anything ../ at all";
        assertThat(empty.sanitize(input)).isSameAs(input);
    }

    @Test
    void isNoOpWhenConfigIsNull() {
        WafPatternSanitizer nullCfg = new WafPatternSanitizer(null);
        String input = "anything ../ at all";
        assertThat(nullCfg.sanitize(input)).isSameAs(input);
    }

    @Test
    void ignoresEmptyEntriesInCsv() {
        // Trailing/duplicate commas should not produce a regex that matches "" everywhere.
        WafPatternSanitizer custom = new WafPatternSanitizer(",foo,,bar,");
        assertThat(custom.sanitize("xfooxbarx")).isEqualTo("x x x");
        assertThat(custom.sanitize("clean")).isSameAs("clean");
    }
}
