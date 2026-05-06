package uk.gov.hmcts.cp.services.courtlistdownload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListDataService;

import java.time.LocalDate;

@Service
public class CourtListDownloadService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListDownloadService.class);

    private final CourtListDataService courtListDataService;

    public CourtListDownloadService(final CourtListDataService courtListDataService) {
        this.courtListDataService = courtListDataService;
    }

    public CourtListFileResult generateCourtListDownload(final CourtListType courtListType,
                                                         final String courtCentreId,
                                                         final String courtRoomId,
                                                         final LocalDate startDate,
                                                         final LocalDate endDate,
                                                         final String cjscppuid) {
        LOG.info("Fetching court list document for type={}, courtCentreId={}, startDate={}, endDate={}",
                courtListType, sanitizeForLog(courtCentreId), startDate, endDate);

        CourtListFileResult result = courtListDataService.getCourtListDocumentForDownload(
                courtListType, courtCentreId, courtRoomId, startDate, endDate, cjscppuid);

        LOG.info("Court list document fetched for type={}, courtCentreId={}, contentType={}, size={} bytes",
                courtListType, sanitizeForLog(courtCentreId), result.contentType(), result.content().length);
        return result;
    }

    private static String sanitizeForLog(final String value) {
        if (value == null) {
            return null;
        }
        String withoutCrLf = value.replace('\r', ' ').replace('\n', ' ');
        StringBuilder cleaned = new StringBuilder(withoutCrLf.length());
        for (int i = 0; i < withoutCrLf.length(); i++) {
            char c = withoutCrLf.charAt(i);
            if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }
}
