package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "stock_transfer_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockTransferBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false)
    private StockTransferLine transferLine;

    @Column(name = "source_batch_id")
    private Long sourceBatchId;

    @Column(name = "batch_code", length = 100)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity_dispatched", nullable = false)
    private long quantityDispatched;

    @Column(name = "quantity_received", nullable = false)
    private long quantityReceived;

    @Column(name = "quantity_missing", nullable = false)
    private long quantityMissing;
}
