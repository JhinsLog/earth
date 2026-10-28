package com.earth.domain.geo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 좌표가 속한 국가. 판정 규칙(국경 안 → 12해리 안 최근접 → 없음)은 DB 함수 country_code_at 하나에만 있다.
 * 등록 시점의 판정과 마이그레이션의 기존 데이터 보정이 같은 규칙을 쓰게 하기 위해서다. 규칙은 V6 참고.
 */
@Repository
public class CountryBoundaryRepository {

    private final JdbcTemplate jdbc;

    public CountryBoundaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 공해처럼 어느 나라에도 속하지 않으면 null. */
    public String countryCodeAt(double latitude, double longitude) {
        return jdbc.queryForObject("select country_code_at(?, ?)", String.class, latitude, longitude);
    }
}
