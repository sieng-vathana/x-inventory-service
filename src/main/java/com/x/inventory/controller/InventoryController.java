package com.x.inventory.controller;

import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.inventory.dto.ExpiryBatchResponse;
import com.x.inventory.dto.ExpirySummaryResponse;
import com.x.inventory.dto.ExpiryWriteOffRequest;
import com.x.inventory.dto.ExpiryBatchUpdateRequest;
import com.x.inventory.dto.InventoryMovementResponse;
import com.x.inventory.dto.StockBalanceResponse;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.StockReservationRequest;
import com.x.inventory.dto.StockReservationResponse;
import com.x.inventory.dto.CreateStockTransferRequest;
import com.x.inventory.dto.ReceiveStockTransferRequest;
import com.x.inventory.dto.StockTransferCommand;
import com.x.inventory.dto.StockTransferResponse;
import com.x.inventory.entity.InventoryMovementType;
import com.x.inventory.entity.StockTransferStatus;
import com.x.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> getBalance(
            @RequestParam @Positive Long storeId,
            @RequestParam @Positive Long variantId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                inventoryService.getBalance(storeId, variantId)));
    }

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getMovements(
            @RequestParam @Positive Long storeId,
            @RequestParam(required = false) @Positive Long variantId,
            @RequestParam(required = false) InventoryMovementType movementType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<InventoryMovementResponse> result = inventoryService.findMovements(
                storeId, variantId, movementType, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext())));
    }

    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<PageResponse<StockTransferResponse>>> getTransfers(
            @RequestParam @Positive Long storeId,
            @RequestParam(required = false) StockTransferStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<StockTransferResponse> result = inventoryService.findTransfers(storeId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext())));
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), inventoryService.getTransfer(id)));
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(
            @Valid @RequestBody CreateStockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED.value(),
                "Stock transfer requested", inventoryService.createTransfer(request)));
    }

    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock transfer approved",
                inventoryService.approveTransfer(id, request)));
    }

    @PostMapping("/transfers/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock transfer rejected",
                inventoryService.rejectTransfer(id, request)));
    }

    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancelTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock transfer cancelled",
                inventoryService.cancelTransfer(id, request)));
    }

    @PostMapping("/transfers/{id}/dispatch")
    public ResponseEntity<ApiResponse<StockTransferResponse>> dispatchTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock transfer dispatched",
                inventoryService.dispatchTransfer(id, request)));
    }

    @PostMapping("/transfers/{id}/receive")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receiveTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody ReceiveStockTransferRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock transfer receipt recorded",
                inventoryService.receiveTransfer(id, request)));
    }

    @PostMapping("/stock-in")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> stockIn(@Valid @RequestBody StockChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Stock received", inventoryService.stockIn(request)));
    }

    @PostMapping("/stock-out")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> stockOut(@Valid @RequestBody StockChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock removed", inventoryService.stockOut(request)));
    }

    @PostMapping("/reservations")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> reserve(@Valid @RequestBody StockReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Stock reserved", inventoryService.reserve(request)));
    }

    @PostMapping("/reservations/with-details")
    public ResponseEntity<ApiResponse<StockReservationResponse>> reserveWithDetails(
            @Valid @RequestBody StockReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED.value(), "Stock reserved", inventoryService.reserveWithDetails(request)));
    }

    @PostMapping("/reservations/{id}/consume")
    public ResponseEntity<ApiResponse<StockReservationResponse>> consumeReservation(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), "Stock reservation consumed", inventoryService.consumeReservation(id)));
    }

    @PostMapping("/reservations/{id}/reopen")
    public ResponseEntity<ApiResponse<StockReservationResponse>> reopenConsumedReservation(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock reservation reopened",
                inventoryService.reopenConsumedReservation(id)));
    }

    @GetMapping("/reports/low-stock")
    public ResponseEntity<ApiResponse<PageResponse<StockBalanceResponse>>> lowStock(
            @RequestParam @Positive Long storeId,
            @RequestParam(defaultValue = "5") @PositiveOrZero long threshold,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<StockBalanceResponse> result = inventoryService.lowStock(storeId, threshold, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext())));
    }

    @GetMapping("/expiry/batches")
    public ResponseEntity<ApiResponse<PageResponse<ExpiryBatchResponse>>> expiryBatches(
            @RequestParam @Positive Long storeId,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<ExpiryBatchResponse> result = inventoryService.findExpiryBatches(storeId, filter, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext())));
    }

    @GetMapping("/expiry/summary")
    public ResponseEntity<ApiResponse<ExpirySummaryResponse>> expirySummary(@RequestParam @Positive Long storeId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), inventoryService.expirySummary(storeId)));
    }

    @PostMapping("/expiry/batches/{batchId}/write-off")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> writeOffExpiredBatch(
            @PathVariable @Positive Long batchId,
            @RequestParam @Positive Long storeId,
            @Valid @RequestBody ExpiryWriteOffRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Expired stock written off",
                inventoryService.writeOffExpiredBatch(storeId, batchId, request)));
    }

    @org.springframework.web.bind.annotation.PutMapping("/expiry/batches/{batchId}")
    public ResponseEntity<ApiResponse<ExpiryBatchResponse>> updateExpiryBatch(
            @PathVariable @Positive Long batchId,
            @RequestParam @Positive Long storeId,
            @Valid @RequestBody ExpiryBatchUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock batch updated",
                inventoryService.updateBatchDetails(storeId, batchId, request)));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> releaseReservation(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock reservation released",
                inventoryService.releaseReservation(id)));
    }
}
