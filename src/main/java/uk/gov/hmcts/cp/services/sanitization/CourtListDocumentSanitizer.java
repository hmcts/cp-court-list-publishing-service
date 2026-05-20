package uk.gov.hmcts.cp.services.sanitization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a CourtListDocument as a Jackson tree and runs every textual leaf
 * through both the {@link WafPatternSanitizer} (which strips substrings the
 * Azure WAF would reject — e.g. {@code ../}, {@code ..\}) and the
 * {@link HtmlStrippingSanitizer} (which removes HTML constructs and
 * normalises whitespace). Used to clean up upstream-provided free-text
 * fields (e.g. offence legislation narratives containing {@code <br />})
 * before the document is validated against the downstream JSON schema and
 * published to CaTH.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourtListDocumentSanitizer {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final WafPatternSanitizer wafPatternSanitizer;
    private final HtmlStrippingSanitizer stringSanitizer;

    private String sanitizeText(String original) {
        // WAF-blocked sequences (e.g. "../") are stripped first; the HTML
        // sanitiser then handles tag stripping plus whitespace normalisation
        // (so any gaps left by WAF substring removal are collapsed cleanly).
        String wafCleaned = wafPatternSanitizer.sanitize(original);
        return stringSanitizer.sanitize(wafCleaned);
    }

    public CourtListDocument sanitize(final CourtListDocument document) {
        if (document == null) {
            return null;
        }
        final JsonNode root = OBJECT_MAPPER.valueToTree(document);
        walk(root);
        return OBJECT_MAPPER.convertValue(root, CourtListDocument.class);
    }

    private void walk(final JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            final List<String> fieldsToRemove = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                final JsonNode child = objectNode.get(fieldName);
                if (child.isTextual()) {
                    final String original = child.asText();
                    final String cleaned = sanitizeText(original);
                    if (cleaned == null) {
                        // entirely-tag input collapsed to empty: drop the field so
                        // JsonInclude.NON_NULL omits it from the outgoing JSON
                        fieldsToRemove.add(fieldName);
                    } else if (!cleaned.equals(original)) {
                        objectNode.put(fieldName, cleaned);
                    }
                } else if (child.isContainerNode()) {
                    walk(child);
                }
            });
            fieldsToRemove.forEach(objectNode::remove);
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                final JsonNode child = arrayNode.get(i);
                if (child.isTextual()) {
                    final String original = child.asText();
                    final String cleaned = sanitizeText(original);
                    if (cleaned == null) {
                        // keep array length stable by replacing entirely-tag entries
                        // with an empty string rather than removing the slot
                        arrayNode.set(i, TextNode.valueOf(""));
                    } else if (!cleaned.equals(original)) {
                        arrayNode.set(i, TextNode.valueOf(cleaned));
                    }
                } else if (child.isContainerNode()) {
                    walk(child);
                }
            }
        }
    }
}
