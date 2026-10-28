package com.earth.controller;

import com.earth.clouds.CloudMapStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/globe")
public class GlobeController {

    /** 브라우저가 관측 시각을 표시할 수 있게 응답 헤더로 준다. CORS에서 노출 헤더로 열어 둔다. */
    public static final String OBSERVED_AT_HEADER = "X-Clouds-Observed-At";

    private final CloudMapStore cloudMapStore;

    public GlobeController(CloudMapStore cloudMapStore) {
        this.cloudMapStore = cloudMapStore;
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
                        .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                        .lastModified(map.observedAt())
                        .header(OBSERVED_AT_HEADER, map.observedAt().toString())
                        .body(map.jpeg()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
