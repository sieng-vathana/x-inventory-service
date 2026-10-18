package com.x.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Stock ordered from a supplier for one store; receiving it adds the stock. */
@Entity
@Table(name = "purchase_orders",
        indexes = @Index(name = "idx_purchase_order_business_store", columnList = "business_id, store_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_order_po_number", columnNames = "po_number"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** Supplier name when the order was made, so old orders keep it if the supplier is renamed. */
    @Column(name = "supplier_name", nullable = false, length = 160)
    private String supplierName;

    /** "PO-00012"; set from the id when the order is first saved. */
    @Column(name = "po_number", length = 32)
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseOrderStatus status;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    /** When stock was last received against this order. */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    // Lists load the lines of a whole page in one query.
    @BatchSize(size = 100)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public void replaceLines(List<PurchaseOrderLine> replacement) {
        lines.clear();
        replacement.forEach(line -> {
            line.setPurchaseOrder(this);
            lines.add(line);
        });
    }
}
