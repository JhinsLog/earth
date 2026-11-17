package com.earth.ski;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 스키장 개장 정보 한 시즌치(resources/ski/season.json).
 *
 * <p><b>왜 API가 아니라 파일인가.</b> 스키장 개장일을 주는 공공 API가 없다. 날짜는 각 리조트 공지와 그것을 옮긴 기사에만
 * 있다. 기사를 자동으로 긁어 AI로 날짜를 뽑는 방식은 포털 검색 API 약관(AI 입력 금지)과 언론사 사이트 무단 수집 문제에
 * 걸린다(docs/data-sources.md). 그래서 사람이 공지를 확인해 날짜와 출처를 파일에 적고, 서버는 그 날짜에 맞춰 플레어를
 * 켜고 끄는 일만 자동으로 한다.
 *
 * <p><b>날짜는 리조트 공식 공지(confirmed) 또는 주요 언론이 보도한 리조트의 목표일(target)만 적고, 둘을 구분한다.</b>
 * 블로그·정리 글의 날짜는 서로 엇갈려(2026-11-17 확인: 같은 스키장이 11월 20일·26일·27일로 갈림) 넣지 않는다.
 * 처음에는 공식 공지만 넣었는데, 리조트는 개장 직전까지 확정 공지를 내지 않고 언론에 목표일만 밝혀 시즌 내내 아무것도
 * 뜨지 않았다. 목표일은 화면에 "목표 · 보도 기준"으로 따로 표시한다.
 * 날짜가 없는 스키장은 플레어를 만들지 않는다. 날짜를 적을 때는 출처와 날짜 종류(dateStatus)를 반드시 함께 적는다.
 *
 * <p>좌표는 OpenStreetMap(ODbL)의 스키장 영역 대표점이다. 출처 표기는 지도의 OSM 표기가 이미 하고 있다.
 *
 * @param season     시즌 이름(예: 2026-27). 플레어 외부 ID 앞에 붙어, 다음 시즌 파일과 겹치지 않게 한다
 * @param validUntil 이 파일을 믿을 수 있는 마지막 날. 폐장일이 발표되지 않은 스키장도 이 날이 지나면 내린다.
 *                   파일을 고치지 않고 해가 바뀌어도 지난 시즌 플레어가 남지 않게 하려는 것이다
 */
public record SkiSeason(String season, LocalDate validUntil, List<Resort> resorts) {

    /**
     * @param opening   개장일. 모르면 null(플레어를 만들지 않는다)
     * @param closing   폐장일. 모르면 null
     * @param confirmed 날짜가 리조트의 확정 공지인지(true), 언론에 밝힌 목표일인지(false)
     * @param source    날짜의 출처. 날짜가 있으면 반드시 있다
     */
    public record Resort(String id, String name, double latitude, double longitude,
                         LocalDate opening, LocalDate closing, boolean confirmed, Source source) {
    }

    /** @param checkedOn 사람이 출처를 확인한 날 */
    public record Source(String name, String url, LocalDate checkedOn) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // 국내 스키장만 다룬다. 좌표를 잘못 적어(위경도 뒤바뀜 등) 바다에 찍히는 것을 막는다.
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 38.7;
    private static final double MIN_LNG = 124.5;
    private static final double MAX_LNG = 131.0;

    /** 파일을 읽고 검사한다. 잘못 적힌 파일은 기동 단계에서 실패시킨다 — 조용히 일부만 쓰면 아무도 모른다. */
    public static SkiSeason read(InputStream in) throws IOException {
        JsonNode root = JSON.readTree(in);
        String season = text(root, "season");
        LocalDate validUntil = date(root, "validUntil");
        if (season == null || validUntil == null) {
            throw new IllegalArgumentException("season과 validUntil은 반드시 있어야 합니다.");
        }
        List<Resort> resorts = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : root.path("resorts")) {
            Resort resort = resort(node);
            if (!ids.add(resort.id())) {
                throw new IllegalArgumentException("스키장 id가 겹칩니다: " + resort.id());
            }
            resorts.add(resort);
        }
        return new SkiSeason(season, validUntil, List.copyOf(resorts));
    }

    private static Resort resort(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        if (id == null || name == null) {
            throw new IllegalArgumentException("스키장마다 id와 name이 있어야 합니다: " + node);
        }
        double lat = node.path("latitude").asDouble(Double.NaN);
        double lng = node.path("longitude").asDouble(Double.NaN);
        if (!(lat >= MIN_LAT && lat <= MAX_LAT && lng >= MIN_LNG && lng <= MAX_LNG)) {
            throw new IllegalArgumentException(id + ": 좌표가 국내 범위 밖입니다(" + lat + ", " + lng + ").");
        }
        LocalDate opening = date(node, "opening");
        LocalDate closing = date(node, "closing");
        if (opening != null && closing != null && closing.isBefore(opening)) {
            throw new IllegalArgumentException(id + ": 폐장일이 개장일보다 앞섭니다.");
        }
        Source source = null;
        JsonNode s = node.path("source");
        if (s.isObject()) {
            source = new Source(text(s, "name"), text(s, "url"), date(s, "checkedOn"));
        }
        if ((opening != null || closing != null)
                && (source == null || source.name() == null || source.url() == null || source.checkedOn() == null)) {
            throw new IllegalArgumentException(id + ": 날짜를 적으면 출처(name, url, checkedOn)도 적어야 합니다.");
        }
        String status = text(node, "dateStatus");
        if ((opening != null || closing != null) && !"confirmed".equals(status) && !"target".equals(status)) {
            throw new IllegalArgumentException(id + ": 날짜를 적으면 dateStatus(confirmed 또는 target)도 적어야 합니다.");
        }
        return new Resort(id, name, lat, lng, opening, closing, "confirmed".equals(status), source);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString().strip() : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
