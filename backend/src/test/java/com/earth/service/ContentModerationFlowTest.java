package com.earth.service;

import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareUpdateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 검열에 걸린 글이 채팅·플레어 어느 경로로도 저장되지 않는지 검증한다. */
class ContentModerationFlowTest extends IntegrationTest {

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

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("걸린 채팅은 저장되지 않는다")
    void rejectedChatIsNotSaved() {
        User sender = userRepository.save(user("chatter"));
        long flareId = flareService.create(sender, request("멀쩡한 제목", "멀쩡한 내용")).id();

        assertRejected(() -> chatService.postMessage(flareId, sender, "연락처 010-1234-5678"));
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("걸린 플레어는 등록되지 않고, 수정으로도 우회할 수 없다")
    void rejectedFlareIsNotSavedOrUpdated() {
        User author = userRepository.save(user("author"));

        assertRejected(() -> flareService.create(author, request("씨발 정체", "내용")));
        assertThat(flareRepository.count()).isZero();

        long flareId = flareService.create(author, request("정체", "내용")).id();
        assertRejected(() -> flareService.update(author, flareId,
                new FlareUpdateRequest("정체", "메일 someone@example.com", FlareCategory.ETC)));
        assertThat(flareRepository.findById(flareId).orElseThrow().getContent()).isEqualTo("내용");
    }

    private void assertRejected(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }

    private FlareCreateRequest request(String title, String content) {
        return new FlareCreateRequest(title, content, FlareCategory.ETC, 37.5665, 126.9780);
    }
}
