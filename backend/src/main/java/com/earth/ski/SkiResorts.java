package com.earth.ski;

import com.earth.domain.flare.FlareCategory;
import com.earth.service.ExternalFlareSyncService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 스키장 개장 정보 → 플레어 초안. 오늘 날짜(한국 시각)로 어느 스키장을 띄울지 정한다.
 *
 * <ul>
 *   <li>개장일 30일 전부터: "개장 예정". 개장 시즌을 미리 보여 주려는 것이다(처음엔 7일이었는데, 날짜가 알려진
 *       스키장도 시즌 직전까지 하나도 뜨지 않았다)</li>
 *   <li>개장일부터: "개장"</li>
 *   <li>폐장일이 있으면 그날까지, 아니면 파일의 validUntil까지. 날짜가 없는 스키장은 띄우지 않는다</li>
 * </ul>
 *
 * <p>글에는 시설 이름·날짜·출처 이름만 담는다. 특정 인물에 관한 내용은 넣지 않는다(자동 플레어 원칙).
 * 날짜 뒤에 근거(리조트 공지)를 붙인다. 눈·기온으로 개장이 미뤄질 수 있고, 우리가 그것까지 확인한 것은 아니기 때문이다.
 */
public final class SkiResorts {

    public static final String SOURCE = "SKI_SEASON";
    public static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("ski-season", "스키장 개장 정보");

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int ANNOUNCE_DAYS_BEFORE = 30;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");

    private SkiResorts() {
    }

    public static List<ExternalFlareSyncService.Draft> toDrafts(SkiSeason season, LocalDate today) {
        if (today.isAfter(season.validUntil())) {
            return List.of();
        }
        List<ExternalFlareSyncService.Draft> drafts = new ArrayList<>();
        for (SkiSeason.Resort resort : season.resorts()) {
            toDraft(season, resort, today).ifPresent(drafts::add);
        }
        return drafts;
    }

    static Optional<ExternalFlareSyncService.Draft> toDraft(SkiSeason season, SkiSeason.Resort resort, LocalDate today) {
        LocalDate opening = resort.opening();
        if (opening == null || today.isBefore(opening.minusDays(ANNOUNCE_DAYS_BEFORE))) {
            return Optional.empty();
        }
        LocalDate lastDay = resort.closing() != null && resort.closing().isBefore(season.validUntil())
                ? resort.closing() : season.validUntil();
        if (today.isAfter(lastDay)) {
            return Optional.empty();
        }
        boolean open = !today.isBefore(opening);
        String title = resort.name() + (open ? " 개장" : " 개장 예정");
        // 마지막 날이 끝나는 자정(한국 시각)에 내린다.
        Instant end = lastDay.plusDays(1).atStartOfDay(KST).toInstant();
        return Optional.of(new ExternalFlareSyncService.Draft(season.season() + ":" + resort.id(), title,
                content(season, resort), FlareCategory.LEISURE, resort.latitude(), resort.longitude(), end));
    }

    static String content(SkiSeason season, SkiSeason.Resort resort) {
        StringBuilder text = new StringBuilder()
                .append(season.season()).append(" 시즌\n")
                .append("개장 ").append(day(resort.opening())).append(" (리조트 공지 기준, 날씨에 따라 바뀔 수 있음)\n")
                .append("폐장 ").append(resort.closing() == null ? "발표 전" : day(resort.closing()))
                .append("\n\n")
                // 출처는 이름과 공지 날짜만 짧게 보인다. 주소와 확인한 날은 season.json에 남겨 두고 화면에는 싣지 않는다.
                .append("출처: ").append(resort.source().name());
        return text.toString();
    }

    private static String day(LocalDate date) {
        return date.format(DAY) + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
