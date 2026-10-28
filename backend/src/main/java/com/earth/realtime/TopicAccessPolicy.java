package com.earth.realtime;

/**
 * 실시간 토픽 이름과, 누가 무엇을 구독할 수 있는지.
 *
 * <p>별 피드는 세 갈래로 나간다.
 * <ul>
 *   <li>{@link #EVENTS_ALL}: 전체 정보. 로그인 사용자만</li>
 *   <li>{@link #EVENTS_PUBLIC}: 위치와 존재만. 누구나</li>
 *   <li>{@link #EVENTS_COUNTRY_PREFIX}{@code KR}: 그 나라 별의 전체 정보. 비로그인은 자기 접속 국가 하나만</li>
 * </ul>
 * 예전에는 비로그인 연결도 전체 피드와 채팅 토픽을 그대로 구독할 수 있었다. REST에서만 상세를 막으면
 * 실시간으로 같은 정보가 그대로 흘러나가므로, 구독 시점에 여기서 함께 막는다.
 */
public final class TopicAccessPolicy {

    public static final String EVENTS_ALL = "/topic/events";
    public static final String EVENTS_PUBLIC = "/topic/events.public";
    public static final String EVENTS_COUNTRY_PREFIX = "/topic/events.country.";

    private TopicAccessPolicy() {
    }

    public static boolean canSubscribe(boolean authenticated, String viewerCountry, String destination) {
        if (destination == null) return false;
        if (authenticated) return true;
        if (destination.equals(EVENTS_PUBLIC)) return true;
        return viewerCountry != null && destination.equals(EVENTS_COUNTRY_PREFIX + viewerCountry);
    }
}
