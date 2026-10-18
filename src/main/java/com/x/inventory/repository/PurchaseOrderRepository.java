package com.x.inventory.repository;

import com.x.inventory.entity.PurchaseOrder;
import com.x.inventory.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseOrder> findByIdAndBusinessId(Long id, Long businessId);

    @Query("""
            select p from PurchaseOrder p
            where p.businessId = :businessId
              and (:storeId is null or p.storeId = :storeId)
              and (:status is null or p.status = :status)
            """)
    Page<PurchaseOrder> search(@Param("businessId") Long businessId,
                               @Param("storeId") Long storeId,
                               @Param("status") PurchaseOrderStatus status,
                               Pageable pageable);
}
