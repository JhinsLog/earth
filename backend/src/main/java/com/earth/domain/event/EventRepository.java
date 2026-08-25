package com.earth.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt > :now
              and e.latitude between :southLat and :northLat
              and e.longitude between :westLng and :eastLng
            order by e.createdAt desc
            """)
    List<Event> findVisibleWithinBoundingBox(
            @Param("now") Instant now,
            @Param("southLat") double southLat,
            @Param("northLat") double northLat,
            @Param("westLng") double westLng,
            @Param("eastLng") double eastLng);

    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt > :now
            order by e.createdAt desc
            limit 500
            """)
    List<Event> findVisibleLatest(@Param("now") Instant now);

    /** 만료 시각이 지났는데 아직 ACTIVE로 남아 있는 별. 스케줄러가 정리한다. */
    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt <= :now
            """)
    List<Event> findDueForExpiration(@Param("now") Instant now);
}
