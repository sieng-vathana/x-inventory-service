package com.x.inventory.entity;

public enum StockTransferStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    IN_TRANSIT,
    PARTIALLY_RECEIVED,
    RECEIVED,
    RECEIVED_WITH_VARIANCE,
    CANCELLED
}
