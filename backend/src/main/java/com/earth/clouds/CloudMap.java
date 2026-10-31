package com.earth.clouds;

import java.time.Instant;
import java.util.List;

/**
 * 지구본에 씌울 구름 이미지 한 장.
 *
 * @param jpeg       등장방형 흑백 JPEG. 밝을수록 구름이 짙다. 왼쪽 끝이 경도 -180°, 위쪽 끝이 북위 90°.
 * @param observedAt 이 이미지에 쓰인 위성 영상 중 가장 오래된 관측 시각. "적어도 이만큼은 새롭다"는 뜻이다.
 * @param sources    쓰인 자료의 제공 기관. 화면의 출처 표기에 그대로 쓴다.
 */
public record CloudMap(byte[] jpeg, Instant observedAt, List<String> sources) {
}
