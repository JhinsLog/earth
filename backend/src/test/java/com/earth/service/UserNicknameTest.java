package com.earth.service;

import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.Nicknames;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 닉네임 변경. 형식 → 욕설·개인정보 → 중복 순서로 막는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserNicknameTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("앞뒤 공백을 지우고 저장한다")
    void changesAndTrims() {
        User user = save("a", Nicknames.random());

        assertThat(userService.changeNickname(user.getId(), "  별헤는 밤_1  ").nickname()).isEqualTo("별헤는 밤_1");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("별헤는 밤_1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "가나다라마바사아자차카타파하가나다", "별!", "<script>", "별  밤", "-별밤", "지구인-ABC123", "지구인 -abc"})
    @DisplayName("길이(2~16자)·허용 문자·자동 닉네임 형식을 벗어나면 거절한다")
    void rejectsInvalidFormat(String nickname) {
        User user = save("b", Nicknames.random());

        assertThatThrownBy(() -> userService.changeNickname(user.getId(), nickname))
                .isInstanceOf(EarthApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("욕설이 들어간 닉네임은 거절한다")
    void rejectsBannedWord() {
        User user = save("c", Nicknames.random());

        assertThatThrownBy(() -> userService.changeNickname(user.getId(), "시발놈아"))
                .isInstanceOf(EarthApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("다른 사람이 쓰는 닉네임은 대소문자가 달라도 거절하고, 내 닉네임의 대소문자만 바꾸는 것은 된다")
    void rejectsTakenButAllowsOwnCaseChange() {
        save("d", "Earth");
        User me = save("e", "Moon");

        assertThatThrownBy(() -> userService.changeNickname(me.getId(), "EARTH"))
                .isInstanceOf(EarthApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_TAKEN);
        assertThat(userService.changeNickname(me.getId(), "MOON").nickname()).isEqualTo("MOON");
    }

    @Test
    @DisplayName("API: 바꾸면 200과 새 정보, 이미 쓰이는 닉네임이면 409, 로그인하지 않으면 401")
    void api() throws Exception {
        save("f", "선점");
        User me = save("g", Nicknames.random());
        String token = jwtTokenProvider.createAccessToken(me.getId());

        HttpResponse<String> ok = patch("{\"nickname\":\"새이름\"}", token);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"nickname\":\"새이름\"").doesNotContain("email");

        assertThat(patch("{\"nickname\":\"선점\"}", token).statusCode()).isEqualTo(409);
        assertThat(patch("{\"nickname\":\"새이름2\"}", null).statusCode()).isEqualTo(401);
    }

    private User save(String key, String nickname) {
        return userRepository.save(new User(AuthProvider.GOOGLE, "nick-" + key, nickname));
    }

    private HttpResponse<String> patch(String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/nickname"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
