package com.x.inventory.dto;

import com.x.inventory.entity.InventoryMovementType;

import java.time.LocalDateTime;

public record InventoryMovementResponse(
        Long id,
        Long storeId,
        Long variantId,
        InventoryMovementType movementType,
        long quantityDelta,
        String referenceType,
        String referenceId,
        Long performedBy,
        String note,
        LocalDateTime createdAt) {
}
