package com.earth.moderation;

import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 검열기가 막아야 할 것은 막고, 막으면 안 되는 일상 문장은 통과시키는지 검증한다.
 *
 * <p>금칙어 검열은 놓치는 것보다 멀쩡한 글을 막는 쪽이 더 해롭다. 사용자는 왜 막혔는지 알 수 없고
 * 고칠 방법도 없다. 그래서 통과해야 하는 문장 목록을 막아야 하는 목록만큼 중요하게 둔다.
 */
class ContentModeratorTest {

    private final ContentModerator moderator = new ContentModerator();

    @ParameterizedTest
    @ValueSource(strings = {
            "시발 뭐야", "씨발", "시1발", "시.발", "시 발", "ㅅㅂ 진짜", "병신같네", "개새끼야", "존나 막히네",
            "지랄하네", "FUCK this", "what the shit",
    })
    @DisplayName("욕설과 흔한 우회(숫자·기호 끼우기, 한 글자씩 띄우기, 대문자)를 막는다")
    void blocksProfanity(String text) {
        assertRejected(text, "욕설");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "다시 발표한대요", "시발점에서 출발", "껌을 씹다", "보지 마세요", "자지 마", "시바견 산책 중",
            "미친 듯이 맛있다", "새끼 고양이 발견", "우리 개 새끼 낳았어요", "후쿠오카 fukuoka", "강남역 사고 2건",
            "10시 10분 출발", "교통 정체 1.5km",
    })
    @DisplayName("금칙어와 글자가 겹치는 일상 문장은 통과시킨다")
    void allowsEverydaySentences(String text) {
        assertThatCode(() -> moderator.check(text)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "연락주세요 010-1234-5678", "01012345678", "010 1234 5678", "900101-1234567", "9001011234567",
            "카드 1234-5678-9012-3456", "메일 someone@example.com",
    })
    @DisplayName("형식이 정해진 개인정보를 막는다")
    void blocksPersonalInfo(String text) {
        assertRejected(text, "개인정보");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026년 11월 12일", "버스 7016번", "02-123-4567 가게 문의", "인구 1,000,000명"})
    @DisplayName("개인정보 형식이 아닌 숫자는 통과시킨다")
    void allowsOrdinaryNumbers(String text) {
        assertThatCode(() -> moderator.check(text)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("여러 칸 중 하나만 걸려도 거절하고, 비어 있는 칸은 건너뛴다")
    void checksEveryField() {
        assertThatCode(() -> moderator.check("제목", null)).doesNotThrowAnyException();
        assertRejected(new String[]{"멀쩡한 제목", "내용에 씨발"}, "욕설");
    }

    @Test
    @DisplayName("정규화: 숫자·기호를 지우고 한 글자 토막만 붙인다")
    void normalize() {
        assertThat(ContentModerator.normalize("시 1 발  다시 발표!")).isEqualTo("시발 다시 발표");
    }

    private void assertRejected(String text, String reasonContains) {
        assertRejected(new String[]{text}, reasonContains);
    }

    private void assertRejected(String[] texts, String reasonContains) {
        assertThatThrownBy(() -> moderator.check(texts))
                .isInstanceOf(EarthApiException.class)
                .satisfies(e -> {
                    assertThat(((EarthApiException) e).getErrorCode()).isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
                    assertThat(e.getMessage()).contains(reasonContains);
                });
    }
}
