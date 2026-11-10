package com.earth.service;

import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 채팅방이 플레어와 수명을 같이하는지 검증한다.
 *
 * <p>예전에는 플레어가 존재하기만 하면 채팅이 써졌다. 만료·삭제되어 지도에서 사라진 플레어에도
 * 메시지가 쌓이고, 삭제된 플레어의 대화가 계속 열렸다.
 */
class ChatFlareLifetimeTest extends IntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FlareService flareService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    // 만료 시각을 과거로 옮기는 것은 운영 코드에 테스트용 setter를 두지 않고 SQL로 처리한다.
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("살아 있는 플레어에는 채팅을 쓰고 내역을 볼 수 있다")
    void chatWorksWhileFlareIsVisible() {
        User author = userRepository.save(user("author"));
        long flareId = flareService.create(author, request()).id();

        chatService.postMessage(flareId, author, "안녕하세요");

        assertThat(chatService.findRecent(flareId)).hasSize(1);
    }

    @Test
    @DisplayName("만료 시각이 지난 플레어에는 스케줄러가 돌기 전이라도 채팅을 쓸 수 없고 내역도 열리지 않는다")
    void expiredFlareRejectsChat() {
        User author = userRepository.save(user("author"));
        long flareId = flareService.create(author, request()).id();
        chatService.postMessage(flareId, author, "만료 전 메시지");

        // 상태는 ACTIVE 그대로 두고 시각만 지나게 한다. 스케줄러가 아직 돌지 않은 순간이다.
        jdbc.update("update flares set expires_at = now() - interval '1 second' where id = ?", flareId);

        assertRejected(() -> chatService.postMessage(flareId, author, "만료 후 메시지"));
        assertRejected(() -> chatService.findRecent(flareId));
        assertThat(chatMessageRepository.count()).as("만료 후 메시지는 저장되지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 플레어에는 채팅을 쓸 수 없고 내역도 열리지 않는다")
    void deletedFlareRejectsChat() {
        User author = userRepository.save(user("author"));
        long flareId = flareService.create(author, request()).id();
        flareService.delete(author, flareId);

        assertRejected(() -> chatService.postMessage(flareId, author, "삭제 후 메시지"));
        assertRejected(() -> chatService.findRecent(flareId));
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FLARE_NOT_FOUND);
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }

    private FlareCreateRequest request() {
        return new FlareCreateRequest("채팅 수명 테스트", "내용", FlareCategory.ETC, 37.5665, 126.9780);
    }
}
