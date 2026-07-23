package com.x.inventory.repository;

import com.x.inventory.entity.StockBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {
    Optional<StockBalance> findByStoreIdAndVariantId(Long storeId, Long variantId);
}
