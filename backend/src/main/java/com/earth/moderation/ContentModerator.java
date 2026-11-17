package com.earth.moderation;

import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 사용자가 올리는 글(채팅, 플레어 제목·내용)을 저장 전에 검사한다. 걸리면 저장하지 않고 이유를 돌려준다.
 *
 * <p>규칙 기반이다. 욕설은 금칙어 목록, 개인정보는 형식이 정해진 것(휴대전화·주민등록·카드 번호, 이메일)만
 * 정규식으로 잡는다. 비방처럼 문맥을 봐야 하는 것은 규칙으로 가릴 수 없어 다루지 않는다.
 *
 * <p>금칙어 비교는 원문이 아니라 정규화한 글에 한다. 숫자·기호를 끼우거나("시1발") 한 글자씩 띄어 쓰는("시 발")
 * 흔한 우회를 막기 위해서다. 단 공백을 전부 지우지는 않는다. 지우면 "다시 발표"가 "다시발표"가 되어
 * 멀쩡한 문장이 막힌다. 그래서 한 글자짜리 토막이 이어질 때만 붙인다.
 */
@Component
public class ContentModerator {

    /** 개인정보 종류별 형식. 앞뒤가 숫자면 더 긴 숫자의 일부이므로 잡지 않는다. */
    private static final Map<String, Pattern> PERSONAL_INFO = Map.of(
            "휴대전화 번호", Pattern.compile("(?<!\\d)01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)"),
            "주민등록번호", Pattern.compile(
                    "(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\s?-?\\s?[1-8]\\d{6}(?!\\d)"),
            "카드 번호", Pattern.compile("(?<!\\d)\\d{4}(?:[-\\s]?\\d{4}){3}(?!\\d)"),
            "이메일 주소", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));

    /**
     * 집을 특정하는 동·호수(D-24). "101동 1203호", "1203호에서". 호선·호점·호기·호실·호차는 집이 아니므로 뺀다("2호선"은 한 자리라 애초에
     * 걸리지 않는다). 사건이 아니어도 막는다 — 호수는 그 자체로 한 가구를 가리킨다.
     */
    private static final List<Pattern> HOUSEHOLD = List.of(
            Pattern.compile("(?<!\\d)\\d{1,4}\\s?동\\s?\\d{1,4}\\s?호(?![선점기실차])"),
            Pattern.compile("(?<![\\d-])\\d{3,4}\\s?호(?=$|[\\s.,!?)~/]|에|의|는|가|로|앞|집)"));

    /**
     * 정확한 주소(D-24). 도로명("강남대로 123", "테헤란로 12-3")과 지번("역삼동 123-4", "123번지"). 뒤에 층·번(출구)·가처럼 다른 말이
     * 붙으면 주소가 아니다("역삼동 3번 출구", "을지로 3가"). 사건 글에서만 막는다 — 교통 통제 같은 공익 정보는 주소가 필요하다.
     */
    private static final List<Pattern> PRECISE_ADDRESS = List.of(
            Pattern.compile("[가-힣0-9]+(?:로|길)\\s?\\d{1,4}(?:-\\d{1,4})?(?![\\d가-힣])"),
            Pattern.compile("[가-힣]+(?:동|리|가)\\s?\\d{1,5}(?:-\\d{1,5})?(?:\\s?번지)?(?![\\d가-힣])"),
            Pattern.compile("\\d{1,5}(?:-\\d{1,5})?\\s?번지"));

    /**
     * 사람이 피해를 입었거나 입힌 사건을 가리키는 말. 정확한 주소와 함께 있을 때만 막는다(D-24). 언론이 범죄 보도에서 동 이름까지만
     * 쓰는 것과 같은 이유 — 피해자·가족·이웃이 특정되고 그 건물 주민 전체가 2차 피해를 입는다. 일상어와 겹치는 말(강도·방화)은 넣지 않는다.
     */
    private static final List<String> CRIME_WORDS = List.of(
            "살인", "살해", "폭행", "성폭행", "성폭력", "성추행", "강간", "성범죄", "납치", "감금", "흉기", "칼부림",
            "시신", "변사", "자살", "투신", "극단적 선택", "스토킹", "가정폭력", "학대", "마약", "불법촬영", "몰카", "절도", "도둑");

    /** 한글 음절·호환 자모, 영문 소문자, 공백만 남긴다. 숫자와 기호는 우회용 끼워 넣기로 보고 지운다. */
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\uAC00-\\uD7A3\\u3131-\\u318Ea-z\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final List<String> bannedWords;
    private final List<String> allowedWords;

    public ContentModerator() {
        this(readWords("moderation/banned-words.txt"), readWords("moderation/allowed-words.txt"));
    }

    ContentModerator(List<String> bannedWords, List<String> allowedWords) {
        this.bannedWords = bannedWords;
        this.allowedWords = allowedWords;
    }

    /** 하나라도 걸리면 {@link ErrorCode#CONTENT_NOT_ALLOWED}로 거절한다. null은 건너뛴다. */
    public void check(String... texts) {
        for (String text : texts) {
            if (text == null) continue;
            for (Map.Entry<String, Pattern> entry : PERSONAL_INFO.entrySet()) {
                if (entry.getValue().matcher(text).find()) {
                    throw new EarthApiException(ErrorCode.CONTENT_NOT_ALLOWED,
                            "개인정보(%s)로 보이는 내용은 올릴 수 없습니다. 올린 글은 다른 사람에게 그대로 보입니다."
                                    .formatted(entry.getKey()));
                }
            }
            if (HOUSEHOLD.stream().anyMatch(p -> p.matcher(text).find())) {
                throw new EarthApiException(ErrorCode.CONTENT_NOT_ALLOWED,
                        "아파트 동·호수처럼 집이 특정되는 정보는 올릴 수 없습니다. 동 이름이나 근처 큰 건물까지만 써 주세요.");
            }
            if (CRIME_WORDS.stream().anyMatch(text::contains)
                    && PRECISE_ADDRESS.stream().anyMatch(p -> p.matcher(text).find())) {
                throw new EarthApiException(ErrorCode.CONTENT_NOT_ALLOWED,
                        "범죄·사건 글에는 번지나 도로명 주소 같은 정확한 위치를 쓸 수 없습니다. 피해자나 주민이 특정될 수 있어요. 동 이름까지만 써 주세요.");
            }
            if (containsBannedWord(text)) {
                throw new EarthApiException(ErrorCode.CONTENT_NOT_ALLOWED,
                        "욕설이나 비속어로 보이는 표현이 있어 올릴 수 없습니다.");
            }
        }
    }

    boolean containsBannedWord(String text) {
        String normalized = normalize(text);
        for (String allowed : allowedWords) {
            normalized = normalized.replace(allowed, " ");
        }
        for (String banned : bannedWords) {
            if (normalized.contains(banned)) return true;
        }
        return false;
    }

    /** 소문자로 바꾸고 숫자·기호를 지운 뒤, 한 글자짜리 토막이 이어지면 붙인다("시 발" → "시발"). */
    static String normalize(String text) {
        String lettersOnly = NOT_LETTER.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        String[] tokens = WHITESPACE.split(lettersOnly.strip());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            boolean joinWithPrevious = i > 0 && tokens[i].length() == 1 && tokens[i - 1].length() == 1;
            if (i > 0 && !joinWithPrevious) out.append(' ');
            out.append(tokens[i]);
        }
        return out.toString();
    }

    private static List<String> readWords(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            List<String> words = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String word = line.strip().toLowerCase(Locale.ROOT);
                if (!word.isEmpty() && !word.startsWith("#")) words.add(word);
            }
            return List.copyOf(words);
        } catch (IOException e) {
            // 목록 없이 뜨면 검열이 조용히 꺼진다. 기동을 실패시킨다.
            throw new UncheckedIOException("검열 단어 목록을 읽지 못했습니다: " + path, e);
        }
    }
}
