package com.earth.incident;

import com.earth.service.ExternalFlareSyncService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 서울시 돌발정보 → 플레어 초안. 무엇을 넣고 뺄지는 {@link SeoulIncidentTypes}. */
public final class SeoulIncidents {

    public static final String SOURCE = "SEOUL_TOPIS";
    public static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("seoul-topis", "서울시 교통정보");
    private static final int CONTENT_MAX = 1000;

    // 좌표가 서울 근처가 아니면 변환이나 원본이 잘못된 것이다. 엉뚱한 곳에 플레어를 찍지 않는다.
    private static final double MIN_LAT = 37.0;
    private static final double MAX_LAT = 38.0;
    private static final double MIN_LNG = 126.5;
    private static final double MAX_LNG = 127.5;

    public record Mapped(List<ExternalFlareSyncService.Draft> drafts, int skipped) {
    }

    private SeoulIncidents() {
    }

    public static Mapped toDrafts(List<SeoulIncident> incidents) {
        List<ExternalFlareSyncService.Draft> drafts = new ArrayList<>(incidents.size());
        int skipped = 0;
        for (SeoulIncident incident : incidents) {
            Optional<ExternalFlareSyncService.Draft> draft = toDraft(incident);
            if (draft.isPresent()) {
                drafts.add(draft.get());
            } else {
                skipped++;
            }
        }
        return new Mapped(drafts, skipped);
    }

    static Optional<ExternalFlareSyncService.Draft> toDraft(SeoulIncident incident) {
        Optional<SeoulIncidentTypes.Kind> kind = SeoulIncidentTypes.classify(incident);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        double[] latLng = KoreaTm.toLatLng(incident.tmX(), incident.tmY());
        if (latLng[0] < MIN_LAT || latLng[0] > MAX_LAT || latLng[1] < MIN_LNG || latLng[1] > MAX_LNG) {
            return Optional.empty();
        }
        return Optional.of(new ExternalFlareSyncService.Draft(incident.accId(), kind.get().title(),
                content(incident.info()), kind.get().category(), latLng[0], latLng[1], incident.expectedClearAt()));
    }

    /** 응답의 줄바꿈은 CR(&#13;)이다. 화면이 줄을 나눌 수 있게 LF로 맞춘다. */
    static String content(String info) {
        if (info == null) return null;
        String text = info.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) return null;
        return text.length() > CONTENT_MAX ? text.substring(0, CONTENT_MAX) : text;
    }
}
