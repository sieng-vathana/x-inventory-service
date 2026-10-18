package com.x.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReceiveStockTransferRequest(
        Long performedBy,
        @Size(max = 500) String note,
        @NotEmpty @Size(max = 100) List<@Valid StockTransferReceiveLineRequest> lines) {
}
