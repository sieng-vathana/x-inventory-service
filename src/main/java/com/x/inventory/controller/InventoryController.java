package com.x.inventory.controller;

import com.sharedlib.response.ApiResponse;
import com.x.inventory.dto.StockBalanceResponse;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.StockReservationRequest;
import com.x.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> getBalance(
            @RequestParam @Positive Long storeId, @RequestParam @Positive Long variantId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), inventoryService.getBalance(storeId, variantId)));
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

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> releaseReservation(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock reservation released",
                inventoryService.releaseReservation(id)));
    }
}
