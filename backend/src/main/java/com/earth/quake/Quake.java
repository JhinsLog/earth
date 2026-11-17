package com.earth.quake;

import java.time.Instant;

/**
 * 공식 관측기관이 발표한 지진 하나(기상청 통보 또는 해외 공식 기관). 값은 원문 그대로이고 우리가 계산하거나 판단한 것은 없다.
 *
 * <p>한반도 인근은 기상청 발표만 쓰고, 같은 지진을 기상청도 통보했으면 기상청 것을 쓴다({@link QuakeSources}, D-18).
 * 그래서 규모를 반올림하거나 "위험" 같은 등급을 붙이지 않는다. 화면이 점과 원의 크기를 정할 때 규모를 쓰는 것은
 * 표현일 뿐이고, 글로는 통보문의 값만 보여 준다.
 *
 * @param id          같은 지진을 가리키는 안정적인 키. 진앙시(초)와 좌표로 만든다 — 프론트가 새 지진인지 알아보는 데 쓴다.
 * @param occurredAt  진앙시.
 * @param announcedAt 이 내용의 발표 시각. 같은 지진의 통보가 여러 번 나오면 가장 늦은 것이 남는다.
 * @param magnitude   규모. 통보문에 없으면 null.
 * @param depthKm     깊이(km, 기상청 시범 운영 항목). 없으면 null.
 * @param intensity   진도 설명 원문(예: "최대진도 Ⅲ(경북)"). 없으면 빈 문자열.
 * @param location    진앙 위치 원문(예: "경북 경주시 남남서쪽 9km 지역").
 * @param remark      참고사항 원문.
 * @param noticeType  통보종류 코드(fcTp, 기상청만). 화면에는 쓰지 않고 운영 로그로 분포를 본다. 해외 기관은 0.
 * @param source      발표 기관(화면에 출처로 그대로 쓴다). 예: "기상청", "USGS".
 */
public record Quake(String id, Instant occurredAt, Instant announcedAt, double latitude, double longitude,
                    Double magnitude, Double depthKm, String intensity, String location, String remark,
                    int noticeType, String source) {

    public static final String KMA = "기상청";
    public static final String USGS = "USGS";
}
