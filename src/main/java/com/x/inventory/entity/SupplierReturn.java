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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Stock sent back to a supplier; completing the return removes the stock from the store. */
@Entity
@Table(name = "supplier_returns",
        indexes = @Index(name = "idx_supplier_return_business_store", columnList = "business_id, store_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_supplier_return_number", columnNames = "return_number"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SupplierReturn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_name", nullable = false, length = 160)
    private String supplierName;

    /** "SR-00004"; set from the id when the return is first saved. */
    @Column(name = "return_number", length = 32)
    private String returnNumber;

    /** The purchase order the goods came on, when known. */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SupplierReturnStatus status;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    // Lists load the lines of a whole page in one query.
    @BatchSize(size = 100)
    @OrderBy("id")
    private List<SupplierReturnLine> lines = new ArrayList<>();

    public void replaceLines(List<SupplierReturnLine> replacement) {
        lines.clear();
        replacement.forEach(line -> {
            line.setSupplierReturn(this);
            lines.add(line);
        });
    }
}
