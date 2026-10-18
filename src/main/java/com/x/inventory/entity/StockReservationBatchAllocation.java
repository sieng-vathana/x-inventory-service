package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Preserves the source lots used by a completed reservation for safe compensation/reopen. */
@Entity
@Table(name = "stock_reservation_batch_allocations", indexes = {
        @Index(name = "idx_reservation_batch_allocation_reservation", columnList = "reservation_id"),
        @Index(name = "idx_reservation_batch_allocation_batch", columnList = "batch_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockReservationBatchAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false) private Long reservationId;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(nullable = false) private long quantity;
}
