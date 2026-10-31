package com.earth.clouds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GIBS 적외선 영상의 색 → 밝기온도(°C).
 *
 * <p>GIBS는 온도 구간마다 정해진 색을 칠하고 그 표(ColorMap XML)를 공개한다. 그런데 실제 영상의 색이 표와
 * 정확히 일치하는 픽셀은 60%대였다(2026-10-31 측정, 원본 해상도 타일도 같음). 축소본을 만들 때 이웃 색이
 * 섞이기 때문이다. 그래서 표에서 가장 가까운 색으로 대응시키고, 남는 점 잡음은 이웃 중앙값으로 걷어낸다
 * ({@link GeoRingFrames}).
 */
public final class IrColormap {

    private static final Pattern ENTRY = Pattern.compile("<ColorMapEntry\\s([^>]*)/>");
    private static final Pattern RGB = Pattern.compile("rgb=\"(\\d+),(\\d+),(\\d+)\"");
    private static final Pattern VALUE = Pattern.compile("\\svalue=\"([^\"]*)\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final int[] colors;
    private final float[] celsius;
    // 한 장에 나오는 서로 다른 색은 수만 개라 한 번 찾은 결과를 기억한다.
    private final Map<Integer, Float> nearest = new HashMap<>();

    private IrColormap(int[] colors, float[] celsius) {
        this.colors = colors;
        this.celsius = celsius;
    }

    /** 투명·결측 항목을 빼고 색마다 구간의 가운데 온도를 쓴다. 한쪽이 무한대인 끝 구간은 유한한 경계값. */
    public static IrColormap parse(String xml) {
        List<Integer> colors = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        Matcher entries = ENTRY.matcher(xml);
        while (entries.find()) {
            String attrs = entries.group(1);
            if (attrs.contains("transparent=\"true\"") || attrs.contains("nodata=\"true\"")) {
                continue;
            }
            Matcher rgb = RGB.matcher(attrs);
            Matcher value = VALUE.matcher(attrs);
            if (!rgb.find() || !value.find()) {
                continue;
            }
            Matcher numbers = NUMBER.matcher(value.group(1));
            double sum = 0;
            int count = 0;
            while (numbers.find()) {
                sum += Double.parseDouble(numbers.group());
                count++;
            }
            if (count == 0) {
                continue;
            }
            int color = (Integer.parseInt(rgb.group(1)) << 16) | (Integer.parseInt(rgb.group(2)) << 8)
                    | Integer.parseInt(rgb.group(3));
            if (!colors.contains(color)) {
                colors.add(color);
                values.add((float) (sum / count));
            }
        }
        if (colors.size() < 10) {
            throw new IllegalArgumentException("색상표 항목이 너무 적습니다: " + colors.size());
        }
        int[] c = colors.stream().mapToInt(Integer::intValue).toArray();
        float[] v = new float[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(i);
        }
        return new IrColormap(c, v);
    }

    public int size() {
        return colors.length;
    }

    /** RGB(0xRRGGBB)에 가장 가까운 표 색의 온도. */
    public float celsius(int rgb) {
        return nearest.computeIfAbsent(rgb & 0xFFFFFF, this::search);
    }

    private float search(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            int dr = r - ((colors[i] >> 16) & 0xFF);
            int dg = g - ((colors[i] >> 8) & 0xFF);
            int db = b - (colors[i] & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return celsius[best];
    }
}
