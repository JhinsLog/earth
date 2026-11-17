package com.earth.moderation;

/** 신고·검열 조치가 일어났다. 커밋된 뒤에만 로그로 남긴다(ModerationLog). reason은 신고 사유 또는 운영자 메모. */
public record ModerationRecorded(String event, Long flareId, Long actorId, String reason, int reportCount) {
}
