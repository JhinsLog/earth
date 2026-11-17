package com.earth.events;

import com.earth.domain.flare.FlareCategory;
import com.earth.service.ExternalFlareSyncService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 파리 행사 → 장소별 플레어 초안. <b>오늘(파리 날짜) 아직 끝나지 않은 회차가 있는 장소</b>만, 행사 수가 많은 순으로
 * 상한까지 띄운다.
 *
 * <p>행사마다가 아니라 장소마다 하나다. 행사 이름 대신 "장소 + 오늘의 행사 N건 + 분류"를 보여 준다 — 공연명에 출연자 이름이
 * 들어가는 일이 많아, 특정 인물 정보를 넣지 않는다는 원칙을 지키는 가장 단순한 방법이다. 같은 미술관의 전시 다섯 개가
 * 점 다섯 개로 겹치지도 않는다.
 *
 * <p>진행 중으로 표시된 행사도 대부분 매주 정해진 요일에만 열린다(예: 매주 화요일 저녁). 시작·끝 날짜로만 고르면 1,100건이
 * 넘고, 오늘 회차로 고르면 240건 안팎이다(2026-11-18).
 */
public final class ParisEvents {

    public static final String SOURCE = "PARIS_QFAP";
    public static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("paris-qfap", "파리시 행사 정보");
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");

    /** 원본 분류(프랑스어) → 화면 표기. 없는 분류는 보이지 않는다. 글에는 많이 나온 것 셋까지만 쓴다. */
    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("Expo", "전시"), Map.entry("Art contemporain", "현대미술"), Map.entry("Photo", "사진"),
            Map.entry("Peinture", "회화"), Map.entry("Sculpture", "조각"), Map.entry("Histoire", "역사"),
            Map.entry("Concert", "콘서트"), Map.entry("Spectacle musical", "뮤지컬"), Map.entry("Théâtre", "연극"),
            Map.entry("Danse", "무용"), Map.entry("Cirque", "서커스"), Map.entry("Humour", "코미디"),
            Map.entry("Ecrans", "영화·영상"), Map.entry("Cinéma", "영화"), Map.entry("Festival", "축제"),
            Map.entry("Atelier", "체험"), Map.entry("Enfants", "어린이"), Map.entry("Loisirs", "여가"),
            Map.entry("Sport", "스포츠"), Map.entry("Balade urbaine", "도시 산책"), Map.entry("Nature", "자연"),
            Map.entry("Littérature", "문학"), Map.entry("Conférence", "강연"), Map.entry("Innovation", "혁신"),
            Map.entry("Solidarité", "나눔"), Map.entry("Santé", "건강"));

    private ParisEvents() {
    }

    public static List<ExternalFlareSyncService.Draft> toDrafts(List<ParisEvent> events, Instant now, int maxVenues) {
        LocalDate today = now.atZone(PARIS).toLocalDate();
        Instant endOfToday = today.plusDays(1).atStartOfDay(PARIS).toInstant();

        Map<String, Venue> venues = new LinkedHashMap<>();
        for (ParisEvent event : events) {
            // 오늘(파리 날짜) 안에 시작하고 아직 끝나지 않은 회차. 회차는 하루 단위로 나뉘어 온다. 시작이 오늘보다 앞이어도
            // 걸쳐 있으면 넣는다 — 원본에 서머타임 오프셋(+02:00)이 겨울 날짜에 붙어 자정 회차가 전날로 읽히는 것이 있다.
            List<ParisEvent.Occurrence> todays = event.occurrences().stream()
                    .filter(o -> o.start().toInstant().isBefore(endOfToday) && o.end().toInstant().isAfter(now))
                    .toList();
            if (todays.isEmpty()) continue;
            String key = event.venue() + "|" + Math.round(event.latitude() * 1e4) + "|" + Math.round(event.longitude() * 1e4);
            venues.computeIfAbsent(key, k -> new Venue(k, event.venue(), event.latitude(), event.longitude()))
                    .add(event, todays);
        }
        return venues.values().stream()
                .sorted(Comparator.comparingInt(Venue::count).reversed().thenComparing(Venue::name))
                .limit(maxVenues)
                .map(v -> v.toDraft(today))
                .toList();
    }

    private static final class Venue {
        private final String key;
        private final String name;
        private final double latitude;
        private final double longitude;
        private int count;
        private final Map<String, Integer> tagCounts = new LinkedHashMap<>();
        private ZonedDateTime firstStart;
        private ZonedDateTime lastEnd;

        Venue(String key, String name, double latitude, double longitude) {
            this.key = key;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        void add(ParisEvent event, List<ParisEvent.Occurrence> todays) {
            count++;
            for (String tag : event.tags()) {
                String label = TAGS.get(tag);
                if (label != null) tagCounts.merge(label, 1, Integer::sum);
            }
            for (ParisEvent.Occurrence o : todays) {
                ZonedDateTime start = o.start().atZoneSameInstant(PARIS);
                ZonedDateTime end = o.end().atZoneSameInstant(PARIS);
                if (firstStart == null || start.isBefore(firstStart)) firstStart = start;
                if (lastEnd == null || end.isAfter(lastEnd)) lastEnd = end;
            }
        }

        int count() {
            return count;
        }

        String name() {
            return name;
        }

        ExternalFlareSyncService.Draft toDraft(LocalDate today) {
            String venue = name.length() > 60 ? name.substring(0, 60) : name;
            String tags = tagCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3).map(Map.Entry::getKey).collect(Collectors.joining(", "));
            StringBuilder content = new StringBuilder()
                    .append("오늘 열리는 행사 ").append(count).append("건")
                    .append(tags.isEmpty() ? "" : " · " + tags).append('\n')
                    .append(today.format(DAY)).append('(')
                    .append(today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN)).append(") 현지 시각 ")
                    .append(firstStart.format(TIME)).append(" ~ ")
                    // 자정을 넘겨 끝나면 날짜를 붙인다.
                    .append(lastEnd.toLocalDate().equals(today) ? lastEnd.format(TIME) : lastEnd.format(DAY) + " " + lastEnd.format(TIME))
                    .append("\n\n출처: 파리시 Que faire à Paris(ODbL)");
            return new ExternalFlareSyncService.Draft(externalId(key), venue + " 오늘의 행사", content.toString(),
                    FlareCategory.LEISURE, latitude, longitude, lastEnd.toInstant());
        }
    }

    /** 장소 이름이 길 수 있어(외부 ID는 64자) 해시로 줄인다. 같은 장소는 날이 바뀌어도 같은 ID라 플레어가 이어진다. */
    static String externalId(String venueKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(venueKey.getBytes(StandardCharsets.UTF_8));
            return "venue:" + HexFormat.of().formatHex(digest, 0, 20);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
