package com.earth.volcano;

import java.time.Instant;

/**
 * 주간 보고의 화산 한 곳. 보고 본문은 담지 않는다 — 각국 화산 관측소의 경보 단계와 대피 권고가 섞여 있어 옮기면 특보를 전하는
 * 셈이 되고(예보·특보는 다루지 않는다), 문장 자체는 저작물이다. 화산 이름·위치·분화 구분·발행 시각이라는 사실만 남긴다.
 *
 * @param volcanoNumber Smithsonian 화산 번호(guid의 vn_). 주가 바뀌어도 같은 화산은 같은 번호라 플레어 하나를 이어 쓴다
 * @param newActivity   true면 "New Eruptive Activity"(이번 주 새로 시작), false면 "Continuing Eruptive Activity"
 */
public record VolcanoReport(String volcanoNumber, String name, String country, boolean newActivity,
                            double latitude, double longitude, Instant publishedAt) {
}
