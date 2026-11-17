package com.earth.domain.flare;

import com.earth.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlareReportRepository extends JpaRepository<FlareReport, Long> {

    boolean existsByFlareAndReporter(Flare flare, User reporter);

    /** 운영자 검토용: 플레어별 사유 집계. [flareId, reason, count] */
    @Query("""
            select r.flare.id, r.reason, count(r) from FlareReport r
            where r.flare.id in :flareIds
            group by r.flare.id, r.reason
            """)
    List<Object[]> countByReason(@Param("flareIds") List<Long> flareIds);
}
