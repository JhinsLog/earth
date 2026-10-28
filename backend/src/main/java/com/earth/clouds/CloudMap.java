package com.earth.clouds;

import java.time.Instant;

/**
 * 지구본에 씌울 구름 이미지 한 장.
 *
 * @param jpeg       등장방형 흑백 JPEG. 밝을수록 구름이 짙다. 왼쪽 끝이 경도 -180°, 위쪽 끝이 북위 90°.
 * @param observedAt 위성 관측 시각(GMGSI 파일의 관측 시작 시각).
 * @param sourceKey  원본 파일 키. 같은 파일을 다시 받지 않는 데 쓴다.
 */
public record CloudMap(byte[] jpeg, Instant observedAt, String sourceKey) {
}
