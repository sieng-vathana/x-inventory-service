package com.x.inventory.entity;

/** A PENDING return has not left the store yet; completing it removes the stock. */
public enum SupplierReturnStatus {
    PENDING,
    COMPLETED,
    CANCELLED
}
