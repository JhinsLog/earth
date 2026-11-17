package com.earth.exp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpLedgerRepository extends JpaRepository<ExpLedgerEntry, Long> {

    List<ExpLedgerEntry> findByFlareId(Long flareId);

    List<ExpLedgerEntry> findByUserIdOrderByCreatedAtAsc(Long userId);
}
