package com.earth.domain.user;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 닉네임 규칙. 자동 닉네임을 만들고, 사용자가 고른 닉네임의 형식을 검사한다.
 *
 * <p>구글 계정 이름을 닉네임으로 쓰지 않는 이유: 대개 실명이고, 채팅·플레어에서 다른 사람에게 그대로 보인다.
 * 가입 즉시 쓸 수 있도록 자동 닉네임을 붙이고 설정에서 바꾸게 한다(docs/decisions.md D-16).
 */
public final class Nicknames {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 16;

    /** 닉네임은 이 기간에 한 번 바꿀 수 있고, 버린 닉네임은 이 기간 동안 다른 사람이 가져가지 못한다. */
    public static final Duration CHANGE_COOLDOWN = Duration.ofDays(30);

    /** 자동 닉네임 앞부분. 사용자가 이 형식으로 직접 정하지는 못하게 해 자동 닉네임과 겹치지 않게 한다. */
    static final String AUTO_PREFIX = "지구인-";

    // 헷갈리는 글자(0/O, 1/I/L)를 뺀 32자. 6자리면 약 10억 가지라 겹칠 일이 드물고, 겹치면 다시 뽑는다.
    private static final char[] AUTO_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int AUTO_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    // 한글 음절, 영문, 숫자, 그리고 사이에 끼는 공백·밑줄·하이픈. 앞뒤 공백은 저장 전에 지운다.
    private static final Pattern ALLOWED = Pattern.compile("[가-힣A-Za-z0-9](?:[가-힣A-Za-z0-9 _-]*[가-힣A-Za-z0-9])?");
    private static final Pattern DOUBLE_SPACE = Pattern.compile(" {2,}");
    private static final Pattern AUTO_FORMAT = Pattern.compile("^지구인\\s*-", Pattern.CASE_INSENSITIVE);

    private Nicknames() {
    }

    /**
     * 같은 이름인지 판정하는 비교용 키. 눈으로 구별되지 않는 이름을 같은 키로 모은다(사칭 방지, D-17).
     * 소문자로 바꾸고, 공백·밑줄·하이픈을 지우고, 생김새가 같은 글자(l·1 → i, 0 → o)를 하나로 모은다.
     *
     * <p>V12 마이그레이션의 translate(lower(nickname), 'l10 _-', 'iio')와 같은 규칙이다. 한쪽을 고치면 다른 쪽도 맞춘다.
     * 허용 문자가 한글·영문·숫자뿐이라 다른 나라 문자로 흉내 내는 경우(키릴 문자 а 등)는 형식 검사에서 이미 걸린다.
     */
    public static String key(String nickname) {
        String lower = nickname.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case ' ', '_', '-' -> { }
                case 'l', '1' -> sb.append('i');
                case '0' -> sb.append('o');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 가입 때 붙인 자동 닉네임인가. 사용자는 이 형식으로 정할 수 없으므로 형식만 보면 된다. */
    public static boolean isAuto(String nickname) {
        return AUTO_FORMAT.matcher(nickname).find();
    }

    public static String random() {
        StringBuilder sb = new StringBuilder(AUTO_PREFIX);
        for (int i = 0; i < AUTO_LENGTH; i++) {
            sb.append(AUTO_ALPHABET[RANDOM.nextInt(AUTO_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** 앞뒤 공백을 지운 값을 돌려준다. 규칙에 맞지 않으면 null. */
    public static String normalizeOrNull(String input) {
        if (input == null) return null;
        String nickname = input.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) return null;
        if (!ALLOWED.matcher(nickname).matches() || DOUBLE_SPACE.matcher(nickname).find()) return null;
        if (AUTO_FORMAT.matcher(nickname).find()) return null;
        return nickname;
    }
}
