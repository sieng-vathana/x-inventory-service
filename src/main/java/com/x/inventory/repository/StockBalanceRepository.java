package com.x.inventory.repository;

import com.x.inventory.entity.StockBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {
    Optional<StockBalance> findByStoreIdAndVariantId(Long storeId, Long variantId);

    @Modifying
    @Query(value = """
            INSERT INTO stock_balances
                (store_id, variant_id, quantity_on_hand, quantity_reserved, version)
            VALUES (:storeId, :variantId, :quantity, 0, 0)
            ON DUPLICATE KEY UPDATE
                quantity_on_hand = quantity_on_hand + VALUES(quantity_on_hand),
                version = version + 1
            """, nativeQuery = true)
    int addOnHand(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                  @Param("quantity") long quantity);

    @Modifying
    @Query(value = """
            INSERT INTO stock_balances
                (store_id, variant_id, quantity_on_hand, quantity_reserved, version)
            VALUES (:storeId, :variantId, :quantity, 0, 0)
            ON DUPLICATE KEY UPDATE
                store_id = store_id
            """, nativeQuery = true)
    int initializeIfMissing(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                            @Param("quantity") long quantity);

    @Modifying
    @Query("""
            update StockBalance b
               set b.quantityOnHand = b.quantityOnHand - :quantity,
                   b.version = b.version + 1
             where b.storeId = :storeId
               and b.variantId = :variantId
               and b.quantityOnHand - b.quantityReserved >= :quantity
            """)
    int removeAvailable(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                        @Param("quantity") long quantity);

    @Modifying
    @Query("""
            update StockBalance b
               set b.quantityReserved = b.quantityReserved + :quantity,
                   b.version = b.version + 1
             where b.storeId = :storeId
               and b.variantId = :variantId
               and (:allowNegativeStock = true or b.quantityOnHand - b.quantityReserved >= :quantity)
            """)
    int reserveAvailable(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                         @Param("quantity") long quantity,
                         @Param("allowNegativeStock") boolean allowNegativeStock);

    @Modifying
    @Query("""
            update StockBalance b
               set b.quantityReserved = b.quantityReserved - :quantity,
                   b.version = b.version + 1
             where b.storeId = :storeId
               and b.variantId = :variantId
               and b.quantityReserved >= :quantity
            """)
    int releaseReserved(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                        @Param("quantity") long quantity);

    @Modifying
    @Query("""
            update StockBalance b
               set b.quantityOnHand = b.quantityOnHand - :quantity,
                   b.quantityReserved = b.quantityReserved - :quantity,
                   b.version = b.version + 1
             where b.storeId = :storeId
               and b.variantId = :variantId
               and b.quantityReserved >= :quantity
            """)
    int consumeReserved(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                        @Param("quantity") long quantity);

    @Modifying
    @Query("""
            update StockBalance b
               set b.quantityOnHand = b.quantityOnHand + :quantity,
                   b.quantityReserved = b.quantityReserved + :quantity,
                   b.version = b.version + 1
             where b.storeId = :storeId and b.variantId = :variantId
            """)
    int reopenConsumedReservation(@Param("storeId") Long storeId, @Param("variantId") Long variantId,
                                  @Param("quantity") long quantity);

    @Query("""
            select b from StockBalance b
             where b.storeId = :storeId
               and b.quantityOnHand - b.quantityReserved <= :threshold
             order by (b.quantityOnHand - b.quantityReserved) asc
            """)
    Page<StockBalance> findLowStock(@Param("storeId") Long storeId,
                                    @Param("threshold") long threshold,
                                    Pageable pageable);
}
