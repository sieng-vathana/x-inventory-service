package com.x.inventory.dto;

import com.x.inventory.entity.ReservationStatus;

import java.time.LocalDateTime;

public record StockReservationResponse(
        Long id,
        Long storeId,
        Long variantId,
        Long orderId,
        long quantity,
        ReservationStatus status,
        LocalDateTime expiresAt,
        StockBalanceResponse balance) {
}
