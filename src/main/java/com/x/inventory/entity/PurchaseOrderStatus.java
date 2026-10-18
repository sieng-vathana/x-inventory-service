package com.x.inventory.entity;

/** DRAFT can be edited; ORDERED and PARTIAL can be received; RECEIVED, CLOSED and CANCELLED are finished. */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIAL,
    RECEIVED,
    CLOSED,
    CANCELLED
}
