package com.earth.quake;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 최근 받은 지진 목록. 메모리에만 둔다.
 *
 * <p>원본(기상청)이 며칠치를 언제든 다시 주므로 DB에 쌓을 이유가 없다. 재시작하면 기동 직후 수집이 다시 채운다.
 * 인스턴스가 여럿이면 각자 받아 각자 들고 있는다(원본이 같아 결과도 같다) — 구름 이미지(CloudMapStore)와 같은 판단이다.
 * 받기에 실패하면 바꾸지 않으므로 마지막으로 성공한 목록이 남는다.
 */
@Component
public class QuakeStore {

    /** @param fetchedAt 마지막으로 받기에 성공한 시각. 아직 한 번도 못 받았으면 null. */
    public record Snapshot(List<Quake> quakes, Instant fetchedAt) {
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(List.of(), null));

    public Snapshot current() {
        return current.get();
    }

    /** @return 목록이 바뀌었으면 true(로그를 바뀔 때만 남기려고). */
    public boolean replace(List<Quake> quakes, Instant fetchedAt) {
        Snapshot previous = current.getAndSet(new Snapshot(List.copyOf(quakes), fetchedAt));
        return !previous.quakes().equals(quakes);
    }
}
