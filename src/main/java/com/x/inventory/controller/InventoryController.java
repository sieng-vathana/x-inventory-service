package com.x.inventory.controller;

import com.sharedlib.response.ApiResponse;
import com.x.inventory.dto.StockBalanceResponse;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.StockReservationRequest;
import com.x.inventory.dto.StockReservationResponse;
import com.x.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import com.sharedlib.response.PageResponse;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> getBalance(
            @RequestParam @Positive Long storeId,
            @RequestParam @Positive Long variantId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                inventoryService.getBalance(storeId, variantId, authorization)));
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

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> releaseReservation(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock reservation released",
                inventoryService.releaseReservation(id)));
    }
}
