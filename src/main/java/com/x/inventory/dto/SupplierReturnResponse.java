package com.x.inventory.dto;

import com.x.inventory.entity.SupplierReturn;
import com.x.inventory.entity.SupplierReturnLine;
import com.x.inventory.entity.SupplierReturnStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record SupplierReturnResponse(
        Long id, Long businessId, Long storeId, Long supplierId, String supplierName, String returnNumber,
        Long purchaseOrderId, SupplierReturnStatus status, String currencyCode, String reason, String note,
        BigDecimal totalCost, long units, LocalDateTime createdAt, LocalDateTime completedAt, List<Line> lines) {

    public record Line(Long id, Long variantId, String productName, String variantName, String sku,
                       long quantity, BigDecimal unitCost, BigDecimal lineTotal) {

        static Line from(SupplierReturnLine line) {
            return new Line(line.getId(), line.getVariantId(), line.getProductName(), line.getVariantName(), line.getSku(),
                    line.getQuantity(), line.getUnitCost(),
                    line.getUnitCost().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        }
    }

    public static SupplierReturnResponse from(SupplierReturn supplierReturn) {
        List<Line> lines = supplierReturn.getLines().stream().map(Line::from).toList();
        return new SupplierReturnResponse(
                supplierReturn.getId(), supplierReturn.getBusinessId(), supplierReturn.getStoreId(),
                supplierReturn.getSupplierId(), supplierReturn.getSupplierName(), supplierReturn.getReturnNumber(),
                supplierReturn.getPurchaseOrderId(), supplierReturn.getStatus(), supplierReturn.getCurrencyCode(),
                supplierReturn.getReason(), supplierReturn.getNote(),
                lines.stream().map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP),
                supplierReturn.getLines().stream().mapToLong(SupplierReturnLine::getQuantity).sum(),
                supplierReturn.getCreatedAt(), supplierReturn.getCompletedAt(), lines);
    }
}
