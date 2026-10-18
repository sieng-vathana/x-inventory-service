package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A received lot of stock. The balance table remains the aggregate; batches retain expiry traceability. */
@Entity
@Table(name = "inventory_batches", indexes = {
        @Index(name = "idx_inventory_batch_store_expiry", columnList = "store_id, expiry_date, quantity_on_hand"),
        @Index(name = "idx_inventory_batch_store_variant_expiry", columnList = "store_id, variant_id, expiry_date")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "variant_id", nullable = false) private Long variantId;
    @Column(name = "batch_code", length = 100) private String batchCode;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "source_reference", length = 100) private String sourceReference;
    @Column(name = "quantity_received", nullable = false) private long quantityReceived;
    @Column(name = "quantity_on_hand", nullable = false) private long quantityOnHand;
    @CreationTimestamp @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
    @Version private Long version;

    public long availableQuantity() {
        return quantityOnHand;
    }
}
