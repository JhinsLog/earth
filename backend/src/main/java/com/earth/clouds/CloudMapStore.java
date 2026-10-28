package com.earth.clouds;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 가장 최근에 만든 구름 이미지. 메모리에만 둔다.
 *
 * <p>한 장이 수백 KB이고 1시간마다 바뀐다. 재시작하면 기동 직후 수집이 다시 채우고, 그 사이에는
 * 프론트가 정적 구름으로 대신한다. DB나 디스크에 둘 만큼 잃어서 아까운 데이터가 아니다.
 * 인스턴스가 여럿이면 각자 받아 각자 들고 있는다(원본이 같아 결과도 같다).
 */
@Component
public class CloudMapStore {

    private final AtomicReference<CloudMap> current = new AtomicReference<>();

    public Optional<CloudMap> current() {
        return Optional.ofNullable(current.get());
    }

    public void replace(CloudMap map) {
        current.set(map);
    }
}
