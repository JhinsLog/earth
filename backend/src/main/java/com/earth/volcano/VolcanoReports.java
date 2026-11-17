package com.earth.volcano;

import com.earth.domain.flare.FlareCategory;
import com.earth.quake.QuakeSources;
import com.earth.service.ExternalFlareSyncService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 주간 화산 보고 → 공공 정보 플레어 초안(D-23).
 *
 * <ul>
 *   <li><b>한반도 인근은 쓰지 않는다.</b> 화산도 기상청 우선 원칙의 대상이다. 지진과 같은 구역({@link QuakeSources#nearKorea})
 *       안의 화산은 버린다. 우리는 기상청 화산 정보를 받지 않으므로 그 구역은 비워 둔다</li>
 *   <li><b>보고가 오래되면 내린다.</b> 발행 뒤 {@code maxAge}가 지난 보고는 넣지 않는다. 보고가 끊겨도 한 달 전의 "분화 중"이
 *       지금의 사실처럼 남지 않게 한다. 본문에 발행일을 적어 언제의 보고인지 보이게 한다</li>
 *   <li>보고 본문(경보 단계·대피 권고)은 옮기지 않는다. 예보·특보는 다루지 않는다</li>
 * </ul>
 */
public final class VolcanoReports {

    public static final String SOURCE = "SI_GVP_WVAR";
    public static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("si-gvp-wvar", "Smithsonian·USGS 화산 보고");
    static final String ATTRIBUTION = "Smithsonian·USGS 주간 화산 활동 보고";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Seoul"));

    private VolcanoReports() {
    }

    public static List<ExternalFlareSyncService.Draft> toDrafts(List<VolcanoReport> reports, Instant now, Duration maxAge) {
        List<ExternalFlareSyncService.Draft> drafts = new ArrayList<>();
        for (VolcanoReport r : reports) {
            if (QuakeSources.nearKorea(r.latitude(), r.longitude())) continue;
            Instant end = r.publishedAt().plus(maxAge);
            if (!end.isAfter(now)) continue;
            drafts.add(new ExternalFlareSyncService.Draft(r.volcanoNumber(), title(r), content(r), FlareCategory.ETC,
                    r.latitude(), r.longitude(), end));
        }
        return drafts;
    }

    static String title(VolcanoReport r) {
        String title = "화산 분화 · " + r.name() + " (" + r.country() + ")";
        return title.length() <= 80 ? title : title.substring(0, 80);
    }

    static String content(VolcanoReport r) {
        return (r.newActivity() ? "새 분화 활동" : "분화 계속") + "\n"
                + "주간 보고 " + DATE.format(r.publishedAt()) + " 발행\n\n"
                + "출처: " + ATTRIBUTION;
    }
}
