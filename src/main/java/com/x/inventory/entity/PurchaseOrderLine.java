package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

import java.math.BigDecimal;

/** One product variant on a purchase order; its names are kept as they were when ordered. */
@Entity
@Table(name = "purchase_order_lines")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_order_lines_order"))
    private PurchaseOrder purchaseOrder;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "variant_name", length = 255)
    private String variantName;

    @Column(length = 100)
    private String sku;

    @Column(name = "qty_ordered", nullable = false)
    private long qtyOrdered;

    @Column(name = "qty_received", nullable = false)
    private long qtyReceived;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    public long remaining() {
        return Math.max(0, qtyOrdered - qtyReceived);
    }
}
