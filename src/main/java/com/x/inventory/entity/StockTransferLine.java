package com.x.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfer_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_transfer_variant", columnNames = {"transfer_id", "variant_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockTransferLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "variant_name", length = 200)
    private String variantName;

    @Column(length = 100)
    private String sku;

    @Column(name = "quantity_requested", nullable = false)
    private long quantityRequested;

    @Column(name = "quantity_dispatched", nullable = false)
    private long quantityDispatched;

    @Column(name = "quantity_received", nullable = false)
    private long quantityReceived;

    @Column(name = "quantity_missing", nullable = false)
    private long quantityMissing;

    @Builder.Default
    @OneToMany(mappedBy = "transferLine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<StockTransferBatch> batches = new ArrayList<>();

    public void addBatch(StockTransferBatch batch) {
        batches.add(batch);
        batch.setTransferLine(this);
    }
}
