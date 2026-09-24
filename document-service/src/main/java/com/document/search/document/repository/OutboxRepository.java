package com.document.search.document.repository;

import com.document.search.document.domain.OutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    // Row locks make concurrent publishers wait instead of double-sending,
    // and id order keeps per-document events in the order they were written.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEntity o order by o.id")
    List<OutboxEntity> findBatchForPublishing(Pageable pageable);
}
