package com.earth.domain.user;

import java.security.SecureRandom;
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
