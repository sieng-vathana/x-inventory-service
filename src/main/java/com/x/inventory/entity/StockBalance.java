package com.x.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_balances", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_balance_store_variant", columnNames = {"store_id", "variant_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "quantity_on_hand", nullable = false)
    private long quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private long quantityReserved;

    @Version
    private Long version;

    public long availableQuantity() {
        return quantityOnHand - quantityReserved;
    }
}
