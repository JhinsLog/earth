package com.earth.events;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 파리 행사 하나에서 Earth가 쓰는 값만. 제목·설명·연락처·출연자는 받지도 않는다(특정 인물 정보 금지 원칙) —
 * 공연명에는 출연자 이름이 들어가는 일이 많다.
 *
 * @param venue       장소 이름(address_name). 시설 이름이다
 * @param tags        분류(qfap_tags, 프랑스어)
 * @param occurrences 회차(시작·끝, 파리 현지 오프셋 포함)
 */
public record ParisEvent(String id, String venue, double latitude, double longitude, List<String> tags,
                         List<Occurrence> occurrences) {

    public record Occurrence(OffsetDateTime start, OffsetDateTime end) {
    }
}
