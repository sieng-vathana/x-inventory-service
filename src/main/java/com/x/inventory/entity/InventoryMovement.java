package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "variant_id", nullable = false) private Long variantId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 32)
    private InventoryMovementType movementType;
    @Column(name = "quantity_delta", nullable = false) private long quantityDelta;
    @Column(name = "reference_type", length = 40) private String referenceType;
    @Column(name = "reference_id", length = 80) private String referenceId;
    @Column(name = "user_id") private Long userId;
    @Column(length = 500) private String note;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
