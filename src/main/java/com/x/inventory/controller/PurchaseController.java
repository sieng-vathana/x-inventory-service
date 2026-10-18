package com.x.inventory.controller;

import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.inventory.dto.PurchaseOrderRequest;
import com.x.inventory.dto.PurchaseOrderResponse;
import com.x.inventory.dto.ReceivePurchaseOrderRequest;
import com.x.inventory.dto.SupplierReturnRequest;
import com.x.inventory.dto.SupplierReturnResponse;
import com.x.inventory.entity.PurchaseOrderStatus;
import com.x.inventory.entity.SupplierReturnStatus;
import com.x.inventory.service.PurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Validated
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> listOrders(
            @RequestParam @Positive Long businessId,
            @RequestParam(required = false) @Positive Long storeId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ok(page(purchaseService.listOrders(businessId, storeId, status, page, size)));
    }

    @GetMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getOrder(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.getOrder(businessId, id));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED.value(), "Purchase order saved", purchaseService.createOrder(request)));
    }

    @PutMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> updateOrder(
            @PathVariable @Positive Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return ok(purchaseService.updateOrder(id, request));
    }

    @PostMapping("/purchase-orders/{id}/place")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> placeOrder(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.placeOrder(businessId, id));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receive(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long businessId,
            @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return ok(purchaseService.receive(businessId, id, request));
    }

    @PostMapping("/purchase-orders/{id}/close")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closeOrder(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.closeOrder(businessId, id));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelOrder(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.cancelOrder(businessId, id));
    }

    @GetMapping("/supplier-returns")
    public ResponseEntity<ApiResponse<PageResponse<SupplierReturnResponse>>> listReturns(
            @RequestParam @Positive Long businessId,
            @RequestParam(required = false) @Positive Long storeId,
            @RequestParam(required = false) SupplierReturnStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ok(page(purchaseService.listReturns(businessId, storeId, status, page, size)));
    }

    @GetMapping("/supplier-returns/{id}")
    public ResponseEntity<ApiResponse<SupplierReturnResponse>> getReturn(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.getReturn(businessId, id));
    }

    @PostMapping("/supplier-returns")
    public ResponseEntity<ApiResponse<SupplierReturnResponse>> createReturn(@Valid @RequestBody SupplierReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED.value(), "Supplier return saved", purchaseService.createReturn(request)));
    }

    @PostMapping("/supplier-returns/{id}/complete")
    public ResponseEntity<ApiResponse<SupplierReturnResponse>> completeReturn(
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long businessId,
            @RequestParam(required = false) @Positive Long performedBy) {
        return ok(purchaseService.completeReturn(businessId, id, performedBy));
    }

    @PostMapping("/supplier-returns/{id}/cancel")
    public ResponseEntity<ApiResponse<SupplierReturnResponse>> cancelReturn(
            @PathVariable @Positive Long id, @RequestParam @Positive Long businessId) {
        return ok(purchaseService.cancelReturn(businessId, id));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data));
    }

    private static <T> PageResponse<T> page(Page<T> result) {
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
}
