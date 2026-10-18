package com.x.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Quantities that arrived for some lines of a purchase order; each is added to the store's stock. */
public record ReceivePurchaseOrderRequest(
        @NotEmpty List<@Valid Line> lines,
        @Positive Long performedBy,
        @Size(max = 500) String note) {

    public record Line(@NotNull @Positive Long lineId, @PositiveOrZero long quantity) {
    }
}
