package uk.gov.hmcts.cp.services;

import static java.util.Objects.nonNull;

import uk.gov.hmcts.cp.domain.TestHearingEntity;
import uk.gov.hmcts.cp.dto.HearingRequest;
import uk.gov.hmcts.cp.repositories.HearingRepository;

import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HearingService {

    private static final Logger LOG = LoggerFactory.getLogger(HearingService.class);
    private final HearingRepository hearingRepository;

    @Transactional
    public String getHearingById(final UUID hearingId) throws ResponseStatusException {
        if (!nonNull(hearingId)) {
            LOG.atWarn().log("No hearing id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hearing is required");
        }
        LOG.atDebug().log("Fetching hearing for ID: {}", hearingId);
        List<TestHearingEntity> hearings = hearingRepository.findAll();

        LOG.atDebug().log("Found " + hearings.size() + " hearings");
        final var hearing = hearingRepository.getByHearingId(hearingId);
        return hearing.getPayload();
    }

    @Transactional
    public String updateHearing(HearingRequest request) {

        var hearing = hearingRepository.getByHearingId(request.hearingId());

        if (hearing != null) {
            hearing.setPayload(request.payload());
        }else{
            hearing = new TestHearingEntity(request.hearingId(), request.payload());
        }
        hearingRepository.save(hearing);
        return hearing.getPayload();
    }
}
