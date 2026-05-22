package uk.gov.hmcts.cp.services.sanitization;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredStringFieldsRegistryTest {

    private final RequiredStringFieldsRegistry registry = new RequiredStringFieldsRegistry();

    /**
     * Locks in the exact set of entries we expect the registry to produce from
     * the current schemas. If a schema adds, renames, removes or re-types a
     * required string field, this test will fail with a clear diff — that's a
     * deliberate prompt to confirm the change is intentional rather than an
     * accident.
     */
    @Test
    void extractsEverySchemaDeclaredRequiredStringField() {
        Map<String, Set<String>> expected = Map.of(
                "document", Set.of("publicationDate"),
                "venueAddress", Set.of("postCode"),
                "courtRoom", Set.of("courtRoomName"),
                "sittings", Set.of("sittingStart"),
                "case", Set.of("caseUrn"),
                "application", Set.of("applicationReference"),
                "organisationDetails", Set.of("organisationName")
        );

        assertThat(registry.asMap()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void returnsEmptySetForUnknownParentField() {
        assertThat(registry.requiredStringFieldsOf("somethingNeverDeclared")).isEmpty();
    }

    @Test
    void returnsExpectedFieldsForKnownParent() {
        assertThat(registry.requiredStringFieldsOf("venueAddress"))
                .containsExactly("postCode");
        assertThat(registry.requiredStringFieldsOf("courtRoom"))
                .containsExactly("courtRoomName");
    }

    @Test
    void doesNotIncludeContainerRequiredFields() {
        // The schemas declare e.g. session (array), hearing (array), line (array),
        // courtHouse (object), courtRoom (array) as required, but the registry
        // is for string-typed required fields only. Container-required fields
        // are out of scope and should be absent.
        assertThat(registry.asMap()).allSatisfy((parent, requiredFields) -> {
            assertThat(requiredFields)
                    .as("parent=%s", parent)
                    .doesNotContain("session", "hearing", "line", "courtHouse", "courtRoom",
                            "sittings", "case", "application", "party");
        });
    }

    @Test
    void asMapIsImmutable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> registry.asMap().put("x", Set.of("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
