package com.x.inventory.repository;

import com.x.inventory.entity.SupplierReturn;
import com.x.inventory.entity.SupplierReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, Long> {
    @EntityGraph(attributePaths = "lines")
    Optional<SupplierReturn> findByIdAndBusinessId(Long id, Long businessId);

    @Query("""
            select r from SupplierReturn r
            where r.businessId = :businessId
              and (:storeId is null or r.storeId = :storeId)
              and (:status is null or r.status = :status)
            """)
    Page<SupplierReturn> search(@Param("businessId") Long businessId,
                                @Param("storeId") Long storeId,
                                @Param("status") SupplierReturnStatus status,
                                Pageable pageable);
}
