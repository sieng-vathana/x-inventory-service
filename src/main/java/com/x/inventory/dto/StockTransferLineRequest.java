package com.x.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockTransferLineRequest(
        @NotNull @Positive Long variantId,
        @Positive long quantity) {
}
