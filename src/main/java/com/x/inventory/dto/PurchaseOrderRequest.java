package com.x.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A new or edited purchase order; placeOrder sends it to the supplier straight away instead of saving a draft. */
public record PurchaseOrderRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long supplierId,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        LocalDate expectedDate,
        @Size(max = 500) String note,
        @Positive Long createdBy,
        Boolean placeOrder,
        @NotEmpty @Size(max = 200) List<@Valid Line> lines) {

    public record Line(
            @NotNull @Positive Long variantId,
            @Positive long quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost) {
    }
}
