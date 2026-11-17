package com.earth.dto;

import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.ReportReason;

import java.time.Instant;
import java.util.Map;

/** 운영자 검토 목록의 한 줄. 판단에 필요한 글 내용과 사유별 신고 수를 담는다. 운영자 API에서만 나간다. */
public record HiddenFlareResponse(Long id, String title, String content, FlareCategory category,
                                  double latitude, double longitude, Long authorId, String authorNickname,
                                  Instant createdAt, Instant hiddenAt, Instant expiresAt, int reportCount,
                                  Map<ReportReason, Long> reasons) {

    public static HiddenFlareResponse from(Flare f, Map<ReportReason, Long> reasons) {
        return new HiddenFlareResponse(f.getId(), f.getTitle(), f.getContent(), f.getCategory(),
                f.getLatitude(), f.getLongitude(), f.getAuthor().getId(), f.getAuthor().getNickname(),
                f.getCreatedAt(), f.getHiddenAt(), f.getExpiresAt(), f.getReportCount(), Map.copyOf(reasons));
    }
}
