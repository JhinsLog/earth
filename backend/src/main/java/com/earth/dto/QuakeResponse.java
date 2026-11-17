package com.earth.dto;

import com.earth.quake.Quake;

import java.time.Instant;

/** 지구본에 그릴 지진 하나. 글은 발표 기관의 원문이다. @param source 발표 기관(출처 표기) */
public record QuakeResponse(String id, Instant occurredAt, Instant announcedAt, double latitude, double longitude,
                            Double magnitude, Double depthKm, String intensity, String location, String remark,
                            String source) {

    public static QuakeResponse from(Quake q) {
        return new QuakeResponse(q.id(), q.occurredAt(), q.announcedAt(), q.latitude(), q.longitude(),
                q.magnitude(), q.depthKm(), q.intensity(), q.location(), q.remark(), q.source());
    }
}
