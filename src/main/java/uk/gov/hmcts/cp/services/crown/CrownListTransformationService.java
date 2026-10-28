package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the listing context's Crown court list payload into the JSON document CaTH expects for a Crown list type.
 */
@Service
public class CrownListTransformationService {

    private final Map<CrownListType, CrownListTransformer> transformers = new EnumMap<>(CrownListType.class);
    private final WafPatternSanitizer wafPatternSanitizer;

    public CrownListTransformationService(final List<CrownListTransformer> transformers,
                                         final WafPatternSanitizer wafPatternSanitizer) {
        transformers.forEach(transformer -> this.transformers.put(transformer.type(), transformer));
        this.wafPatternSanitizer = wafPatternSanitizer;
    }

    /**
     * @param courtCentre reference data for the court centre; may be null
     * @return the document as JSON text, not yet validated against its schema
     */
    public String transform(final CrownListType type, final JsonNode payload, final CourtCentreData courtCentre,
                            final LocalDate startDate, final LocalDate endDate) {
        final CrownListTransformer transformer = transformers.get(type);
        if (transformer == null) {
            throw new IllegalStateException("No transformer for Crown list type " + type);
        }
        final ObjectNode document = transformer.transform(new CrownTransformInput(payload, courtCentre, startDate, endDate));
        sanitize(document);
        try {
            return ObjectMapperConfig.getObjectMapper().writeValueAsString(document);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Could not write the " + type + " Crown list as JSON: " + e.getMessage(), e);
        }
    }

    /**
     * The document goes to CaTH through a web application firewall that rejects whole requests for some character
     * sequences, even inside free text such as list notes. Those sequences are removed from every text value.
     */
    private void sanitize(final JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(name -> {
                final JsonNode child = object.get(name);
                if (child.isTextual()) {
                    object.put(name, wafPatternSanitizer.sanitize(child.asText()));
                } else {
                    sanitize(child);
                }
            });
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                final JsonNode child = array.get(i);
                if (child.isTextual()) {
                    array.set(i, array.textNode(wafPatternSanitizer.sanitize(child.asText())));
                } else {
                    sanitize(child);
                }
            }
        }
    }
}
