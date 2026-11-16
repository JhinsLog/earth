package com.earth.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param source    출처 표기. 화면에 그대로 쓴다.
 * @param fetchedAt 기상청에서 마지막으로 받은 시각. 아직 못 받았으면 null.
 */
public record QuakeListResponse(String source, Instant fetchedAt, List<QuakeResponse> quakes) {
}
