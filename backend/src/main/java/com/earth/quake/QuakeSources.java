package com.earth.quake;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 기상청 지진과 해외 공식 기관 지진을 합친다(D-18, 2026-11-18 결정).
 *
 * <ul>
 *   <li><b>한반도 인근은 기상청만.</b> 아래 구역 안의 해외 기관 지진은 버린다. 같은 지진을 기상청과 다른 규모로 보여 혼란을
 *       주는 것이 지진관측법 제16조(기상청장 외의 관측 결과 발표 제한)가 막으려는 일이다</li>
 *   <li><b>같은 지진이면 기상청.</b> 구역 밖이라도 기상청이 같은 지진(진앙시 2분·거리 100km 안 — 기상청 통보끼리 묶는 기준과
 *       같다)을 통보했으면 해외 기관 것은 버린다</li>
 *   <li>값은 각 기관 원문 그대로, 출처(기관 이름)를 붙인다. 예보·특보는 다루지 않는다(기상법 제17조)</li>
 * </ul>
 */
public final class QuakeSources {

    /**
     * 기상청 우선 구역. 한반도와 둘레 바다를 넉넉히 덮는 사각형이다(북위 32~41도, 동경 122~132도). 기상청이 "국내 지진"으로
     * 다루는 범위보다 일부러 넓게 잡았다 — 경계 근처에서 해외 기관 값이 끼어들지 않게.
     */
    static final double MIN_LAT = 32.0;
    static final double MAX_LAT = 41.0;
    static final double MIN_LON = 122.0;
    static final double MAX_LON = 132.0;

    private static final Duration SAME_QUAKE_WINDOW = Duration.ofMinutes(2);
    private static final double SAME_QUAKE_KM = 100;

    private QuakeSources() {
    }

    /** @return 기상청 지진 전부 + 규칙을 통과한 해외 지진. 진앙시 최신순. */
    public static List<Quake> combine(List<Quake> kma, List<Quake> overseas) {
        List<Quake> result = new ArrayList<>(kma);
        for (Quake q : overseas) {
            if (nearKorea(q)) continue;
            if (kma.stream().anyMatch(k -> sameQuake(k, q))) continue;
            result.add(q);
        }
        result.sort(Comparator.comparing(Quake::occurredAt).reversed());
        return result;
    }

    static boolean nearKorea(Quake q) {
        return q.latitude() >= MIN_LAT && q.latitude() <= MAX_LAT
                && q.longitude() >= MIN_LON && q.longitude() <= MAX_LON;
    }

    static boolean sameQuake(Quake a, Quake b) {
        return Duration.between(a.occurredAt(), b.occurredAt()).abs().compareTo(SAME_QUAKE_WINDOW) <= 0
                && distanceKm(a.latitude(), a.longitude(), b.latitude(), b.longitude()) <= SAME_QUAKE_KM;
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371 * Math.asin(Math.sqrt(h));
    }
}
