package uk.gov.hmcts.cp.dto.azure;

public record FileInfo(
        String name,
        String path,
        String url,
        long size
) {}