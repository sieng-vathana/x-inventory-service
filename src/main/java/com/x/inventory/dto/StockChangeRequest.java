package com.x.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StockChangeRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long variantId,
        @Positive long quantity,
        String referenceType,
        String referenceId,
        Long performedBy,
        String note,
        @Size(max = 100) String batchCode,
        LocalDate expiryDate) {

    public StockChangeRequest(Long storeId, Long variantId, long quantity, String referenceType,
                              String referenceId, Long performedBy, String note) {
        this(storeId, variantId, quantity, referenceType, referenceId, performedBy, note, null, null);
    }
}
