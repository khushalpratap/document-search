package com.document.search.document.repository;

import com.document.search.document.domain.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
    Optional<DocumentEntity> findByIdAndTenantId(String id, String tenantId);
}
