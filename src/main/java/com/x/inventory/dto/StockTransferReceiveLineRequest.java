package com.x.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record StockTransferReceiveLineRequest(
        @NotNull @Positive Long lineId,
        @PositiveOrZero long receivedQuantity,
        @PositiveOrZero long missingQuantity) {
}
