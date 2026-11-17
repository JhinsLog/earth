package com.earth.service;

import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.flare.FlareStatus;
import com.earth.domain.flare.ModerationActionRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영자 API는 ADMIN 역할만 쓸 수 있고, 역할은 요청마다 DB에서 읽는지 HTTP로 본다(D-24). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminFlareApiTest extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired private FlareService flareService;
    @Autowired private FlareRepository flareRepository;
    @Autowired private ModerationActionRepository actionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        actionRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    private int post(String path, User as) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"note\":\"테스트\"}"));
        if (as != null) req.header("Authorization", "Bearer " + tokens.createAccessToken(as.getId()));
        return HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("일반 사용자는 403, 비로그인은 401, ADMIN은 가리기·복구를 할 수 있다")
    void onlyAdmins() throws Exception {
        User author = userRepository.save(new User(AuthProvider.GOOGLE, "author", "작성자"));
        User someone = userRepository.save(new User(AuthProvider.GOOGLE, "someone", "누군가"));
        User admin = userRepository.save(new User(AuthProvider.GOOGLE, "admin", "운영자"));
        jdbc.update("update users set role = 'ADMIN' where id = ?", admin.getId());
        long flareId = flareService.create(author,
                new FlareCreateRequest("제목", "내용", FlareCategory.ETC, 37.5, 127.0)).id();

        assertThat(post("/api/admin/flares/" + flareId + "/hide", null)).isEqualTo(401);
        assertThat(post("/api/admin/flares/" + flareId + "/hide", someone)).isEqualTo(403);
        assertThat(flareRepository.findById(flareId).orElseThrow().getStatus()).isEqualTo(FlareStatus.ACTIVE);

        assertThat(post("/api/admin/flares/" + flareId + "/hide", admin)).isEqualTo(200);
        assertThat(flareRepository.findById(flareId).orElseThrow().getStatus()).isEqualTo(FlareStatus.HIDDEN);
        assertThat(post("/api/admin/flares/" + flareId + "/restore", admin)).isEqualTo(200);
        assertThat(flareRepository.findById(flareId).orElseThrow().getStatus()).isEqualTo(FlareStatus.ACTIVE);
    }
}
