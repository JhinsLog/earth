package com.earth.clouds;

import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GMGSI NetCDF4 파일을 읽어 구름 이미지로 만든다. */
public final class GmgsiFiles {

    // 예: GLOBCOMPLIR_v3r0_blend_s202610280900000_e..._c....nc → 관측 시작 2026-10-28T09:00Z
    private static final Pattern OBSERVED_AT = Pattern.compile("_s(\\d{12})");
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private GmgsiFiles() {
    }

    public static Optional<Instant> observedAt(String key) {
        Matcher m = OBSERVED_AT.matcher(key);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(m.group(1), OBSERVED_AT_FORMAT).toInstant(ZoneOffset.UTC));
    }

    /**
     * 위도 범위는 위도 배열(lat)의 첫 행과 끝 행에서 읽는다. 전역 속성(geospatial_lat_max/min)에도 같은 값이
     * 있지만, 이 파일의 루트 속성에는 원본 위성 파일 목록(source)이 14KB 문자열로 들어 있어 jhdf 0.13.0이
     * 루트 속성을 읽다 실패한다(HdfException: Failed to load attributes for '//'). lat은 한 덩어리로 압축돼
     * 있어 두 값을 읽는 데 덩어리를 풀지만, 1시간에 한 번이라 감수한다.
     */
    /** 등장방형 흑백 픽셀(행 우선). {@link CloudMapRenderer#render} 참고. */
    public static byte[] renderGray(Path file, int width) {
        try (HdfFile hdf = new HdfFile(file)) {
            Dataset data = hdf.getDatasetByPath("/data");
            int[] dims = data.getDimensions(); // [time=1, rows, cols]
            if (dims.length != 3 || dims[0] != 1) {
                throw new IllegalStateException("예상하지 못한 GMGSI 격자 모양: " + java.util.Arrays.toString(dims));
            }
            int rows = dims[1];
            int cols = dims[2];
            Dataset lat = hdf.getDatasetByPath("/lat");
            double north = number(lat.getData(new long[]{0, 0}, new int[]{1, 1}));
            double south = number(lat.getData(new long[]{rows - 1, 0}, new int[]{1, 1}));
            if (!(north > south)) {
                throw new IllegalStateException("위도 범위가 이상합니다: " + north + " ~ " + south);
            }
            CloudMapRenderer.Source source = new CloudMapRenderer.Source(rows, cols, north, south,
                    (start, count) -> ((float[][][]) data.getData(new long[]{0, start, 0}, new int[]{1, count, cols}))[0]);
            return CloudMapRenderer.render(source, width);
        }
    }

    // 1×1 조각도 2차원 배열로 온다.
    private static double number(Object value) {
        Object v = value;
        while (v.getClass().isArray()) {
            v = Array.get(v, 0);
        }
        return ((Number) v).doubleValue();
    }
}
