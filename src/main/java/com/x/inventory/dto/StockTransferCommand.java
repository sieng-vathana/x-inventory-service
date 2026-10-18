package com.x.inventory.dto;

import jakarta.validation.constraints.Size;

public record StockTransferCommand(Long performedBy, @Size(max = 500) String note) {
}
