package com.earth.controller;

import com.earth.clouds.CloudMapStore;
import com.earth.dto.QuakeListResponse;
import com.earth.dto.QuakeResponse;
import com.earth.quake.Quake;
import com.earth.quake.QuakeSources;
import com.earth.quake.QuakeStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/globe")
public class GlobeController {

    /** 브라우저가 관측 시각을 표시할 수 있게 응답 헤더로 준다. CORS에서 노출 헤더로 열어 둔다. */
    public static final String OBSERVED_AT_HEADER = "X-Clouds-Observed-At";
    /** 이 이미지에 쓰인 자료의 제공 기관(쉼표 구분). 화면 출처 표기에 쓴다. */
    public static final String SOURCES_HEADER = "X-Clouds-Sources";

    /** 지진 자료의 출처. 지진마다 발표 기관(source)이 따로 붙고, 이 값은 목록 전체의 표기다(docs/data-sources.md). */
    static final String QUAKE_SOURCE = "기상청 · USGS";

    private final CloudMapStore cloudMapStore;
    private final QuakeStore quakeStore;

    public GlobeController(CloudMapStore cloudMapStore, QuakeStore quakeStore) {
        this.cloudMapStore = cloudMapStore;
        this.quakeStore = quakeStore;
    }

    /**
     * 최근 지진. 기상청 통보 전부에, 한반도 인근이 아니고 기상청이 통보하지 않은 해외 기관(USGS) 지진을 더한다
     * ({@link QuakeSources}). 서버가 1분마다 받으므로 브라우저도 1분마다 묻는다. 누구에게나 같은 목록이라 공개다.
     *
     * <p>구름 이미지와 달리 짧게(30초) 캐시한다 — 새 지진이 늦게 보이는 시간을 줄이려고.
     */
    @GetMapping("/quakes")
    public ResponseEntity<QuakeListResponse> quakes() {
        QuakeStore.Snapshot snapshot = quakeStore.current();
        List<Quake> quakes = QuakeSources.combine(snapshot.quakes(), quakeStore.currentOverseas().quakes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(new QuakeListResponse(QUAKE_SOURCE, snapshot.fetchedAt(),
                        quakes.stream().map(QuakeResponse::from).toList()));
    }

    /**
     * 실시간 구름 이미지. 아직 없으면 404이고 프론트는 정적 구름을 쓴다.
     *
     * <p>주소에 확장자(.jpg)를 붙이지 않는다. 붙이면 Cloudflare가 정적 파일로 보고 엣지에 캐시하는데,
     * Origin 없이 들어온 요청의 응답(CORS 헤더 없음)이 캐시되면 다른 사이트 출처 요청에도 그대로 나가
     * 브라우저가 텍스처를 거부한다. 이미지는 메모리에서 바로 나가므로 엣지 캐시 없이도 가볍다.
     */
    @GetMapping("/clouds")
    public ResponseEntity<byte[]> clouds() {
        return cloudMapStore.current()
                .map(map -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // 10분마다 바뀌므로 그 절반만 캐시해, 프론트가 10분마다 다시 물을 때 새 이미지를 받게 한다.
                        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                        .lastModified(map.observedAt())
                        .header(OBSERVED_AT_HEADER, map.observedAt().toString())
                        .header(SOURCES_HEADER, String.join(",", map.sources()))
                        .body(map.jpeg()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
