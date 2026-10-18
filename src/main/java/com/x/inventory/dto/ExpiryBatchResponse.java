package com.x.inventory.dto;

import com.x.inventory.entity.InventoryBatch;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpiryBatchResponse(
        Long id,
        Long storeId,
        Long variantId,
        String batchCode,
        LocalDate expiryDate,
        long quantityReceived,
        long quantityOnHand,
        long availableQuantity,
        String sourceType,
        String sourceReference,
        LocalDateTime receivedAt) {
    public static ExpiryBatchResponse from(InventoryBatch batch) {
        return new ExpiryBatchResponse(batch.getId(), batch.getStoreId(), batch.getVariantId(), batch.getBatchCode(),
                batch.getExpiryDate(), batch.getQuantityReceived(), batch.getQuantityOnHand(),
                batch.availableQuantity(), batch.getSourceType(), batch.getSourceReference(), batch.getReceivedAt());
    }
}
