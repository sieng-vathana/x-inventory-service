package com.x.inventory.repository;

import com.x.inventory.entity.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long>, JpaSpecificationExecutor<InventoryBatch> {
    @Modifying
    @Query(value = """
            insert into inventory_batches
                (store_id, variant_id, batch_code, expiry_date, source_type, source_reference,
                 quantity_received, quantity_on_hand, received_at, version)
            select balance.store_id, balance.variant_id, null, null,
                   case when balance.quantity_on_hand < 0 then 'NEGATIVE_STOCK' else 'OPENING_BALANCE' end,
                   null,
                   case when balance.quantity_on_hand > 0 then balance.quantity_on_hand else 0 end,
                   balance.quantity_on_hand, current_timestamp, 0
              from stock_balances balance
              left join inventory_batches existing_batch
                on existing_batch.store_id = balance.store_id and existing_batch.variant_id = balance.variant_id
             where balance.quantity_on_hand <> 0 and existing_batch.id is null
            """, nativeQuery = true)
    int backfillMissingBalances();

    boolean existsByStoreIdAndVariantId(Long storeId, Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBatch b
             where b.storeId = :storeId and b.variantId = :variantId
             order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate, b.id
            """)
    List<InventoryBatch> findForUpdateByStoreAndVariant(
            @Param("storeId") Long storeId, @Param("variantId") Long variantId);

    List<InventoryBatch> findByStoreIdAndVariantIdOrderByExpiryDateAscReceivedAtAscIdAsc(
            Long storeId, Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.id = :id")
    Optional<InventoryBatch> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBatch b
             where b.storeId = :storeId and b.variantId = :variantId
               and b.sourceType = 'NEGATIVE_STOCK' and b.expiryDate is null
            """)
    Optional<InventoryBatch> findNegativeStockBatchForUpdate(
            @Param("storeId") Long storeId, @Param("variantId") Long variantId);

    @Query("""
            select coalesce(sum(b.quantityOnHand), 0)
              from InventoryBatch b
             where b.storeId = :storeId and b.variantId = :variantId
               and (b.expiryDate is null or b.expiryDate >= :today)
            """)
    Long sellableQuantity(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                          @Param("today") LocalDate today);

    Page<InventoryBatch> findByStoreIdAndExpiryDateIsNotNullAndQuantityOnHandGreaterThan(
            Long storeId, long quantityOnHand, Pageable pageable);

    @Query(value = """
            select
              coalesce(sum(case when expiry_date < :today then 1 else 0 end), 0) as expired_batch_count,
              coalesce(sum(case when expiry_date < :today then quantity_on_hand else 0 end), 0) as expired_units,
              coalesce(sum(case when expiry_date >= :today and expiry_date <= :sevenDays then 1 else 0 end), 0) as within7_days_batch_count,
              coalesce(sum(case when expiry_date >= :today and expiry_date <= :sevenDays then quantity_on_hand else 0 end), 0) as within7_days_units,
              coalesce(sum(case when expiry_date >= :today and expiry_date <= :thirtyDays then 1 else 0 end), 0) as within30_days_batch_count,
              coalesce(sum(case when expiry_date >= :today and expiry_date <= :thirtyDays then quantity_on_hand else 0 end), 0) as within30_days_units,
              coalesce(sum(case when expiry_date is null then 1 else 0 end), 0) as undated_batch_count,
              coalesce(sum(case when expiry_date is null then quantity_on_hand else 0 end), 0) as undated_units
            from inventory_batches
            where store_id = :storeId and expiry_date is not null and quantity_on_hand > 0
            """, nativeQuery = true)
    Object[] summarizeExpiry(@Param("storeId") Long storeId,
                             @Param("today") LocalDate today,
                             @Param("sevenDays") LocalDate sevenDays,
                             @Param("thirtyDays") LocalDate thirtyDays);
}
