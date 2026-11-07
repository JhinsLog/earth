package com.earth.incident;

import java.time.Instant;

/**
 * 서울시 실시간 돌발정보 한 건. 응답 원문에서 쓰는 것만 옮긴다.
 *
 * @param accId           돌발 아이디. 별의 external_id
 * @param type            돌발 유형 코드(A01 교통사고 …)
 * @param detailType      세부 유형 코드(01B04 차량화재 …)
 * @param occurredAt      발생 시각. 형식이 깨졌으면 null
 * @param expectedClearAt 종료(해제) 예정 시각. 없거나 깨졌으면 null
 * @param tmX             중부원점 TM x
 * @param tmY             중부원점 TM y
 * @param info            돌발 내용(자유 문장)
 */
public record SeoulIncident(String accId, String type, String detailType, Instant occurredAt, Instant expectedClearAt,
                            double tmX, double tmY, String info) {
}
