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
import java.util.List;

public record SupplierReturnRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long supplierId,
        @Positive Long purchaseOrderId,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 500) String note,
        @Positive Long createdBy,
        @NotEmpty @Size(max = 200) List<@Valid Line> lines) {

    public record Line(
            @NotNull @Positive Long variantId,
            @Positive long quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost) {
    }
}
