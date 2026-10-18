package com.x.inventory.repository;

import com.x.inventory.entity.StockReservationBatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockReservationBatchAllocationRepository extends JpaRepository<StockReservationBatchAllocation, Long> {
    List<StockReservationBatchAllocation> findAllByReservationIdOrderById(Long reservationId);
    void deleteAllByReservationId(Long reservationId);

    @Query(value = """
            select coalesce(sum(a.quantity), 0)
              from stock_reservation_batch_allocations a
              join stock_reservations r on r.id = a.reservation_id
             where a.batch_id = :batchId and r.status = 'ACTIVE'
            """, nativeQuery = true)
    long sumActiveQuantityByBatchId(@Param("batchId") Long batchId);

    @Query(value = """
            select coalesce(sum(a.quantity), 0)
              from stock_reservation_batch_allocations a
              join stock_reservations r on r.id = a.reservation_id
              join inventory_batches b on b.id = a.batch_id
             where b.store_id = :storeId and b.variant_id = :variantId and r.status = 'ACTIVE'
            """, nativeQuery = true)
    long sumActiveQuantityByStoreAndVariant(@Param("storeId") Long storeId,
                                            @Param("variantId") Long variantId);
}
