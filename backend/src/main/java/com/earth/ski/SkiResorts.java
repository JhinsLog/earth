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
 *   <li>개장일 7일 전부터: "개장 예정". 개장 소식은 보통 1~2주 전에 나와, 미리 보여야 쓸모가 있다</li>
 *   <li>개장일부터: "개장". 폐장일이 발표돼 있으면 그날까지, 아니면 파일의 validUntil까지</li>
 *   <li>날짜가 없는(공식 발표 전) 스키장은 띄우지 않는다</li>
 * </ul>
 *
 * <p>글에는 시설 이름·날짜·출처만 담는다. 특정 인물에 관한 내용은 넣지 않는다(자동 플레어 원칙).
 * 날짜 앞에 "발표 기준"을 붙인다. 눈·기온으로 개장이 미뤄질 수 있고, 우리가 그것까지 확인한 것은 아니기 때문이다.
 */
public final class SkiResorts {

    public static final String SOURCE = "SKI_SEASON";
    public static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("ski-season", "스키장 개장 정보");

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int ANNOUNCE_DAYS_BEFORE = 7;
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
                .append("개장 ").append(day(resort.opening())).append(" (발표 기준)\n")
                .append("폐장 ").append(resort.closing() == null ? "발표 전" : day(resort.closing()) + " (발표 기준)")
                .append("\n\n")
                .append("출처: ").append(resort.source().name())
                .append(" (").append(resort.source().checkedOn()).append(" 확인)\n")
                .append(resort.source().url());
        return text.toString();
    }

    private static String day(LocalDate date) {
        return date.format(DAY) + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
