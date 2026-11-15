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
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 닉네임 변경. 형식 → 욕설·개인정보 → 변경 주기 → 중복(비슷한 이름·버린 이름 포함) 순서로 막는다. */
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

    @Autowired
    JdbcTemplate jdbc;

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

    @ParameterizedTest
    @ValueSource(strings = {"별헤는 밤", "별헤는_밤", "별-헤는밤", "IIya", "Ilya", "B0B", "8OB"})
    @DisplayName("눈으로 구별되지 않는 이름(구분 기호, l·I·1, 0·O)은 같은 이름으로 보고 거절한다")
    void rejectsLookalikes(String nickname) {
        save("h1", "별헤는밤");
        save("h2", "llya");
        save("h3", "BOB");
        save("h4", "8ob");
        User me = save("h5", Nicknames.random());

        assertThatThrownBy(() -> userService.changeNickname(me.getId(), nickname))
                .isInstanceOf(EarthApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_TAKEN);
    }

    @Test
    @DisplayName("비교용 키는 V12 마이그레이션의 SQL 규칙과 같다")
    void keyMatchesMigrationSql() {
        for (String nickname : new String[]{"별헤는 밤_1", "Ill-0O", "서울시 교통정보", "지구인-2F2D96", "Hello World"}) {
            String sql = jdbc.queryForObject("select translate(lower(?), 'l10 _-', 'iio')", String.class, nickname);
            assertThat(Nicknames.key(nickname)).as(nickname).isEqualTo(sql);
        }
    }

    @Test
    @DisplayName("다른 이름으로는 30일에 한 번 바꿀 수 있고, 같은 이름을 다듬는 것(띄어쓰기·대소문자)은 언제든 된다")
    void cooldown() {
        User me = save("i", Nicknames.random());
        userService.changeNickname(me.getId(), "별헤는밤");

        assertThatThrownBy(() -> userService.changeNickname(me.getId(), "새벽별"))
                .isInstanceOf(EarthApiException.class)
                .hasMessageContaining("30일 뒤")
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_CHANGE_TOO_SOON);
        assertThat(userService.changeNickname(me.getId(), "별헤는 밤").nickname()).isEqualTo("별헤는 밤");

        jdbc.update("update users set nickname_changed_at = now() - interval '29 days' where id = ?", me.getId());
        assertThatThrownBy(() -> userService.changeNickname(me.getId(), "새벽별")).hasMessageContaining("1일 뒤");

        jdbc.update("update users set nickname_changed_at = now() - interval '31 days' where id = ?", me.getId());
        assertThat(userService.changeNickname(me.getId(), "새벽별").nickname()).isEqualTo("새벽별");
    }

    @Test
    @DisplayName("버린 이름은 30일 동안 다른 사람이 못 가져가고, 원래 주인은 되찾을 수 있다")
    void releasedNameIsHeld() {
        User owner = save("j", "별헤는밤");
        User other = save("k", Nicknames.random());
        userService.changeNickname(owner.getId(), "새벽별");

        assertThatThrownBy(() -> userService.changeNickname(other.getId(), "별헤는 밤"))
                .isInstanceOf(EarthApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_TAKEN);

        jdbc.update("update users set nickname_changed_at = now() - interval '31 days' where id = ?", owner.getId());
        assertThat(userService.changeNickname(owner.getId(), "별헤는밤").nickname()).isEqualTo("별헤는밤");

        // 주인이 다시 버리고 30일이 지나면 다른 사람이 가져갈 수 있다.
        jdbc.update("update users set nickname_changed_at = now() - interval '31 days' where id = ?", owner.getId());
        userService.changeNickname(owner.getId(), "새벽별");
        jdbc.update("update nickname_holds set released_at = now() - interval '31 days'");
        assertThat(userService.changeNickname(other.getId(), "별헤는밤").nickname()).isEqualTo("별헤는밤");
    }

    @Test
    @DisplayName("자동 닉네임에서 바꾼 것은 잡아 두지 않는다 — 무작위라 흉내 낼 가치가 없다")
    void autoNicknameIsNotHeld() {
        User me = save("l", Nicknames.random());
        userService.changeNickname(me.getId(), "별헤는밤");

        assertThat(jdbc.queryForObject("select count(*) from nickname_holds", Integer.class)).isZero();
    }

    @Test
    @DisplayName("API: 이미 쓰이는 닉네임이면 409, 바꾸면 200과 다음 변경 가능 시각, 30일 안에 또 바꾸면 429, 로그인하지 않으면 401")
    void api() throws Exception {
        save("f", "선점");
        User me = save("g", Nicknames.random());
        String token = jwtTokenProvider.createAccessToken(me.getId());

        // 변경 주기 검사가 중복 검사보다 먼저라, 409는 처음 바꾸기 전에 본다.
        assertThat(patch("{\"nickname\":\"선점\"}", token).statusCode()).isEqualTo(409);
        HttpResponse<String> ok = patch("{\"nickname\":\"새이름\"}", token);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"nickname\":\"새이름\"").doesNotContain("email");
        assertThat(ok.body()).doesNotContain("\"nicknameChangeableAt\":null");

        HttpResponse<String> tooSoon = patch("{\"nickname\":\"새이름3\"}", token);
        assertThat(tooSoon.statusCode()).isEqualTo(429);
        assertThat(tooSoon.body()).contains("30일 뒤");
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
