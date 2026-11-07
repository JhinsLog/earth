package com.earth.incident;

import com.earth.domain.event.EventCategory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 서울시 돌발 유형 중 무엇을 별로 만들지, 어떤 분류·제목으로 만들지.
 *
 * <p>목록에 없는 유형은 만들지 않는다. 유형 코드 전체 목록은 아직 받지 못했고(샘플키로는 5건만 보인다), 모르는 유형을
 * 기본 분류로 띄우면 의도하지 않은 별이 생긴다. 확인한 것만 넣는다(2026-11-07 샘플 응답).
 *
 * <ul>
 *   <li>교통사고·차량고장·보행사고·낙하물 — 그대로 넣는다. 차량화재(01B04)는 화재 분류.</li>
 *   <li>공사 — 하루 안에 끝나는 것만. 몇 년짜리 장기 통제(실제로 2030년까지인 건이 있었다)는 사건이 아니라 풍경이다.</li>
 *   <li>행사 통제 — 세부 유형 10B01(행사)만. 같은 A10 아래 10B02는 실제 응답에서 집회·행진이었고, 정치적으로 민감한
 *       내용을 자동으로 별로 띄우지 않는다.</li>
 * </ul>
 */
final class SeoulIncidentTypes {

    record Kind(String title, EventCategory category) {
    }

    private static final Map<String, Kind> BY_TYPE = Map.of(
            "A01", new Kind("교통사고", EventCategory.ACCIDENT),
            "A02", new Kind("차량고장", EventCategory.ACCIDENT),
            "A03", new Kind("보행사고", EventCategory.ACCIDENT),
            "A04", new Kind("도로공사", EventCategory.ETC),
            "A05", new Kind("낙하물", EventCategory.ACCIDENT),
            "A10", new Kind("행사 교통통제", EventCategory.CROWD));

    static final Duration MAX_CONSTRUCTION = Duration.ofHours(24);

    private SeoulIncidentTypes() {
    }

    static Optional<Kind> classify(SeoulIncident incident) {
        Kind kind = BY_TYPE.get(incident.type());
        if (kind == null) {
            return Optional.empty();
        }
        if ("01B04".equals(incident.detailType())) {
            return Optional.of(new Kind("차량화재", EventCategory.FIRE));
        }
        if ("A04".equals(incident.type())) {
            if (incident.occurredAt() == null || incident.expectedClearAt() == null
                    || Duration.between(incident.occurredAt(), incident.expectedClearAt()).compareTo(MAX_CONSTRUCTION) > 0) {
                return Optional.empty();
            }
        }
        if ("A10".equals(incident.type()) && !"10B01".equals(incident.detailType())) {
            return Optional.empty();
        }
        return Optional.of(kind);
    }
}
