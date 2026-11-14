package com.earth.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareConfirmationRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.service.FlareService;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그 수집의 두 갈래를 실제 HTTP로 검증한다.
 * <ul>
 *   <li>브라우저 로그 API: 인증 없이 받되, 형식·크기·횟수 한도를 넘으면 로그에 닿기 전에 거절한다.</li>
 *   <li>서버 활동 로그: 로그인한 사용자의 쓰기 요청은 성공이든 실패든 경로 패턴·상태와 함께 남고, 조회는 남지 않는다.</li>
 * </ul>
 * Loki 전송 자체는 prod 프로파일 설정이라 여기서는 로거(earth.client·earth.activity)에 무엇이 실리는지를 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "earth.logs.client-events-per-minute=3",
        "earth.logs.max-body-bytes=4096"})
class ClientEventAndActivityLogTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    FlareService flareService;
    @Autowired
    FlareRepository flareRepository;
    @Autowired
    FlareConfirmationRepository confirmationRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ListAppender<ILoggingEvent> clientLogs = new ListAppender<>();
    private final ListAppender<ILoggingEvent> activityLogs = new ListAppender<>();

    @BeforeEach
    void attach() {
        attach("earth.client", clientLogs);
        attach("earth.activity", activityLogs);
    }

    @AfterEach
    void detach() {
        // 다른 테스트 클래스와 같은 DB를 쓴다. 남겨 두면 그쪽 정리(deleteAll)가 외래 키에 걸려 실패한다.
        confirmationRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
        ((Logger) LoggerFactory.getLogger("earth.client")).detachAppender(clientLogs);
        ((Logger) LoggerFactory.getLogger("earth.activity")).detachAppender(activityLogs);
    }

    private static void attach(String name, ListAppender<ILoggingEvent> appender) {
        appender.start();
        ((Logger) LoggerFactory.getLogger(name)).addAppender(appender);
    }

    @Test
    void 비로그인_묶음도_받아_오류에만_기기_정보를_붙이고_제어_문자를_지운다() throws Exception {
        String body = """
                {"sessionId":"s-1","client":{"userAgent":"Galaxy S23","gpu":"Adreno 740","viewport":"412x915","pixelRatio":3.5},
                 "events":[
                   {"kind":"ERROR","name":"webgl.context_lost","message":"lost\\u001b[31m","path":"/","at":1,"data":{"view":"map"}},
                   {"kind":"ACTION","name":"map.enter","at":2}
                 ]}""";

        assertThat(post(body, "10.0.0.1", null).statusCode()).isEqualTo(204);

        assertThat(clientLogs.list).hasSize(2);
        Map<String, Object> error = fields(clientLogs.list.get(0));
        assertThat(error).containsEntry("kind", "error").containsEntry("name", "webgl.context_lost")
                .containsEntry("gpu", "Adreno 740").containsEntry("data_view", "map")
                .containsEntry("errorMessage", "lost[31m")
                .doesNotContainKey("userId");
        assertThat(fields(clientLogs.list.get(1))).containsEntry("kind", "action").doesNotContainKey("gpu");
    }

    @Test
    void 이름_규칙을_벗어나면_400이고_아무것도_남지_않는다() throws Exception {
        String body = """
                {"sessionId":"s-2","events":[{"kind":"ACTION","name":"Bad Name\\n","at":1}]}""";

        assertThat(post(body, "10.0.0.2", null).statusCode()).isEqualTo(400);
        assertThat(clientLogs.list).isEmpty();
    }

    @Test
    void 본문이_한도를_넘으면_읽기_전에_413() throws Exception {
        String stack = "x".repeat(5000);
        String body = """
                {"sessionId":"s-3","events":[{"kind":"ERROR","name":"big","stack":"%s","at":1}]}""".formatted(stack);

        assertThat(post(body, "10.0.0.3", null).statusCode()).isEqualTo(413);
        assertThat(clientLogs.list).isEmpty();
    }

    @Test
    void 같은_접속지가_분당_한도를_넘으면_429이고_다른_접속지는_영향이_없다() throws Exception {
        String body = """
                {"sessionId":"s-4","events":[{"kind":"ACTION","name":"page.view","at":1}]}""";
        String ip = "10.0.4." + (int) (Math.random() * 200);

        for (int i = 0; i < 3; i++) {
            assertThat(post(body, ip, null).statusCode()).isEqualTo(204);
        }
        assertThat(post(body, ip, null).statusCode()).isEqualTo(429);
        assertThat(post(body, ip + "1", null).statusCode()).isEqualTo(204);
    }

    @Test
    void 로그인_사용자의_쓰기는_성공과_실패_모두_경로_패턴과_상태로_남고_조회는_남지_않는다() throws Exception {
        User author = userRepository.save(user("author"));
        User viewer = userRepository.save(user("viewer"));
        long flareId = flareService.create(author,
                new FlareCreateRequest("제목", "내용", FlareCategory.ETC, 37.5, 127.0)).id();
        activityLogs.list.clear();

        String viewerToken = jwtTokenProvider.createAccessToken(viewer.getId());
        String authorToken = jwtTokenProvider.createAccessToken(author.getId());

        assertThat(send("GET", "/api/flares/" + flareId, viewerToken).statusCode()).isEqualTo(200);
        assertThat(activityLogs.list).isEmpty();

        assertThat(send("POST", "/api/flares/" + flareId + "/confirm", viewerToken).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/flares/" + flareId + "/confirm", authorToken).statusCode()).isEqualTo(400);

        assertThat(activityLogs.list).hasSize(2);
        Map<String, Object> ok = fields(activityLogs.list.get(0));
        assertThat(ok).containsEntry("action", "POST /api/flares/{flareId}/confirm")
                .containsEntry("userId", viewer.getId()).containsEntry("status", 200)
                .containsEntry("flareId", String.valueOf(flareId)).containsKey("durationMs");
        assertThat(fields(activityLogs.list.get(1)))
                .containsEntry("userId", author.getId()).containsEntry("status", 400);
    }

    @Test
    void 로그인한_채_보낸_브라우저_로그에는_사용자가_붙는다() throws Exception {
        User user = userRepository.save(user("client"));
        String body = """
                {"sessionId":"s-6","events":[{"kind":"ACTION","name":"settings.open","at":1}]}""";

        assertThat(post(body, "10.0.0.6", jwtTokenProvider.createAccessToken(user.getId())).statusCode()).isEqualTo(204);
        assertThat(fields(clientLogs.list.get(0))).containsEntry("userId", user.getId());
        // 브라우저 로그 API 호출 자체는 활동 로그에 남기지 않는다(쓰기 요청이지만 사용자의 행동이 아니다).
        assertThat(activityLogs.list).isEmpty();
    }

    private static User user(String prefix) {
        String key = prefix + "-" + UUID.randomUUID();
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", prefix, null);
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> map = new HashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) pairs.forEach(pair -> map.put(pair.key, pair.value));
        return map;
    }

    private HttpResponse<String> post(String json, String ip, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/client-events"))
                .header("Content-Type", "application/json")
                .header("CF-Connecting-IP", ip)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
