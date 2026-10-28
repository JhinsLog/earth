package com.earth.realtime;

import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.EventCreateRequest;
import com.earth.service.EventService;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비로그인 WebSocket 연결이 실시간으로 무엇을 받는지 실제 연결로 검증한다(A-10).
 *
 * <p>REST에서 상세를 막아도 실시간 피드가 그대로 흘러나가면 소용이 없다. 예전에는 비로그인 연결도
 * 전체 피드(/topic/events)를 그대로 구독할 수 있었다. 단위 테스트(TopicAccessPolicyTest)는 규칙만 보므로,
 * 여기서는 연결 → 구독 → 별 등록 → Redis 릴레이 → 수신까지 실제 경로 전체를 탄다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnonymousRealtimeVisibilityTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("한국에서 접속한 비로그인 연결: 공개 피드(위치만)와 한국 피드(상세)만 받는다")
    void anonymousReceivesOnlyPublicAndOwnCountry() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new JacksonJsonMessageConverter());

        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("CF-IPCountry", "KR");
        StompSession session = client
                .connectAsync("http://localhost:" + port + "/ws", handshake, new StompHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> all = subscribe(session, "/topic/events");
        BlockingQueue<Map<String, Object>> pub = subscribe(session, "/topic/events.public");
        BlockingQueue<Map<String, Object>> korea = subscribe(session, "/topic/events.country.KR");
        BlockingQueue<Map<String, Object>> japan = subscribe(session, "/topic/events.country.JP");
        BlockingQueue<Map<String, Object>> chat = subscribe(session, "/topic/chat.1");
        // 구독 프레임이 서버에 먼저 도착하도록 잠깐 기다린다(구독보다 발행이 빠르면 아무것도 못 받는다).
        Thread.sleep(500);

        User author = userRepository.save(new User(AuthProvider.GOOGLE, "rt", "rt@test.local", "작성자", null));
        eventService.create(author, new EventCreateRequest("서울 소식", "내용", EventCategory.ETC, 37.5665, 126.9780));
        eventService.create(author, new EventCreateRequest("도쿄 소식", "내용", EventCategory.ETC, 35.6762, 139.6503));

        Map<String, Object> first = pub.poll(10, TimeUnit.SECONDS);
        Map<String, Object> second = pub.poll(10, TimeUnit.SECONDS);
        assertThat(List.of(first, second)).allSatisfy(event -> {
            assertThat(event.get("restricted")).isEqualTo(true);
            assertThat(event.get("title")).isNull();
        });

        Map<String, Object> seoul = korea.poll(10, TimeUnit.SECONDS);
        assertThat(seoul).isNotNull();
        assertThat(seoul.get("title")).isEqualTo("서울 소식");
        assertThat(seoul.get("restricted")).isEqualTo(false);

        // 허용되지 않은 구독은 서버가 버린다. 위에서 이미 같은 소식이 도착했으니 여기에도 왔다면 이미 와 있다.
        assertThat(all.poll(1, TimeUnit.SECONDS)).as("전체 피드").isNull();
        assertThat(japan.poll(1, TimeUnit.SECONDS)).as("다른 나라 피드").isNull();
        assertThat(chat).as("채팅 토픽").isEmpty();
        // 버려진 구독 때문에 연결이 끊기지 않아야 한다(예외를 던지면 ERROR 프레임과 함께 끊긴다).
        assertThat(session.isConnected()).isTrue();

        session.disconnect();
        client.stop();
    }

    @SuppressWarnings("unchecked")
    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }
}
