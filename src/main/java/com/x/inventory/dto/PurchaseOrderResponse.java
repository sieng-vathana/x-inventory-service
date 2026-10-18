package com.x.inventory.dto;

import com.x.inventory.entity.PurchaseOrder;
import com.x.inventory.entity.PurchaseOrderLine;
import com.x.inventory.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id, Long businessId, Long storeId, Long supplierId, String supplierName, String poNumber,
        PurchaseOrderStatus status, String currencyCode, LocalDate orderDate, LocalDate expectedDate, String note,
        BigDecimal totalCost, long unitsOrdered, long unitsReceived,
        LocalDateTime createdAt, LocalDateTime orderedAt, LocalDateTime receivedAt, List<Line> lines) {

    public record Line(Long id, Long variantId, String productName, String variantName, String sku,
                       long qtyOrdered, long qtyReceived, BigDecimal unitCost, BigDecimal lineTotal) {

        static Line from(PurchaseOrderLine line) {
            return new Line(line.getId(), line.getVariantId(), line.getProductName(), line.getVariantName(), line.getSku(),
                    line.getQtyOrdered(), line.getQtyReceived(), line.getUnitCost(), totalOf(line));
        }
    }

    public static PurchaseOrderResponse from(PurchaseOrder order) {
        List<Line> lines = order.getLines().stream().map(Line::from).toList();
        return new PurchaseOrderResponse(
                order.getId(), order.getBusinessId(), order.getStoreId(), order.getSupplierId(), order.getSupplierName(),
                order.getPoNumber(), order.getStatus(), order.getCurrencyCode(), order.getOrderDate(),
                order.getExpectedDate(), order.getNote(),
                lines.stream().map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP),
                order.getLines().stream().mapToLong(PurchaseOrderLine::getQtyOrdered).sum(),
                order.getLines().stream().mapToLong(PurchaseOrderLine::getQtyReceived).sum(),
                order.getCreatedAt(), order.getOrderedAt(), order.getReceivedAt(), lines);
    }

    private static BigDecimal totalOf(PurchaseOrderLine line) {
        return line.getUnitCost().multiply(BigDecimal.valueOf(line.getQtyOrdered())).setScale(2, RoundingMode.HALF_UP);
    }
}
