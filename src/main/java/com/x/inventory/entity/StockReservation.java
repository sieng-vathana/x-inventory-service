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

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_reservations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "variant_id", nullable = false) private Long variantId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(nullable = false) private long quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ReservationStatus status;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
}
