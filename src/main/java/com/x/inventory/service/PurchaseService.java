package com.x.inventory.service;

import com.x.inventory.dto.PurchaseOrderRequest;
import com.x.inventory.dto.PurchaseOrderResponse;
import com.x.inventory.dto.ReceivePurchaseOrderRequest;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.SupplierReturnRequest;
import com.x.inventory.dto.SupplierReturnResponse;
import com.x.inventory.entity.PurchaseOrder;
import com.x.inventory.entity.PurchaseOrderLine;
import com.x.inventory.entity.PurchaseOrderStatus;
import com.x.inventory.entity.SupplierReturn;
import com.x.inventory.entity.SupplierReturnLine;
import com.x.inventory.entity.SupplierReturnStatus;
import com.x.inventory.repository.PurchaseOrderRepository;
import com.x.inventory.repository.SupplierReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Purchase orders and supplier returns. Receiving adds stock to the store; completing a return removes it. */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    static final String PURCHASE_ORDER_REFERENCE = "PURCHASE_ORDER";
    static final String SUPPLIER_RETURN_REFERENCE = "SUPPLIER_RETURN";
    private static final Set<PurchaseOrderStatus> RECEIVABLE = EnumSet.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIAL);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierReturnRepository supplierReturnRepository;
    private final InventoryService inventoryService;
    private final ProductClient productClient;

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listOrders(Long businessId, Long storeId, PurchaseOrderStatus status, int page, int size) {
        return purchaseOrderRepository.search(businessId, storeId, status,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(PurchaseOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getOrder(Long businessId, Long id) {
        return PurchaseOrderResponse.from(requireOrder(businessId, id));
    }

    @Transactional
    public PurchaseOrderResponse createOrder(PurchaseOrderRequest request) {
        PurchaseOrder order = PurchaseOrder.builder()
                .businessId(request.businessId())
                .orderDate(LocalDate.now())
                .createdBy(request.createdBy())
                .status(PurchaseOrderStatus.DRAFT)
                .build();
        applyOrderRequest(order, request);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        saved.setPoNumber(String.format("PO-%05d", saved.getId()));
        if (Boolean.TRUE.equals(request.placeOrder())) {
            markOrdered(saved);
        }
        return PurchaseOrderResponse.from(saved);
    }

    @Transactional
    public PurchaseOrderResponse updateOrder(Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = requireOrder(request.businessId(), id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft purchase order can be changed");
        }
        applyOrderRequest(order, request);
        if (Boolean.TRUE.equals(request.placeOrder())) {
            markOrdered(order);
        }
        return PurchaseOrderResponse.from(order);
    }

    /** Sends a draft to the supplier; from then on it can be received but not edited. */
    @Transactional
    public PurchaseOrderResponse placeOrder(Long businessId, Long id) {
        PurchaseOrder order = requireOrder(businessId, id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft purchase order can be placed");
        }
        markOrdered(order);
        return PurchaseOrderResponse.from(order);
    }

    /** Adds what arrived to the store's stock; the order is RECEIVED once every line is complete. */
    @Transactional
    public PurchaseOrderResponse receive(Long businessId, Long id, ReceivePurchaseOrderRequest request) {
        PurchaseOrder order = requireOrder(businessId, id);
        if (!RECEIVABLE.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an ordered purchase order can be received");
        }
        Map<Long, PurchaseOrderLine> linesById = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));
        String note = movementNote("Received from " + order.getSupplierName(), request.note());
        long received = 0;
        for (ReceivePurchaseOrderRequest.Line requested : request.lines()) {
            if (requested.quantity() == 0) {
                continue;
            }
            PurchaseOrderLine line = linesById.get(requested.lineId());
            if (line == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line " + requested.lineId() + " is not on this purchase order");
            }
            if (requested.quantity() > line.remaining()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only " + line.remaining() + " of " + line.getProductName() + " are still to receive");
            }
            line.setQtyReceived(line.getQtyReceived() + requested.quantity());
            inventoryService.stockIn(new StockChangeRequest(order.getStoreId(), line.getVariantId(), requested.quantity(),
                    PURCHASE_ORDER_REFERENCE, order.getPoNumber(), request.performedBy(), note,
                    requested.batchCode(), requested.expiryDate()));
            received += requested.quantity();
        }
        if (received == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a quantity to receive");
        }
        order.setReceivedAt(LocalDateTime.now());
        order.setStatus(order.getLines().stream().allMatch(line -> line.remaining() == 0)
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIAL);
        return PurchaseOrderResponse.from(order);
    }

    /** Stops waiting for the rest of an order; what was received stays in stock. */
    @Transactional
    public PurchaseOrderResponse closeOrder(Long businessId, Long id) {
        PurchaseOrder order = requireOrder(businessId, id);
        if (!RECEIVABLE.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an ordered purchase order can be closed");
        }
        order.setStatus(PurchaseOrderStatus.CLOSED);
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse cancelOrder(Long businessId, Long id) {
        PurchaseOrder order = requireOrder(businessId, id);
        boolean anythingReceived = order.getLines().stream().anyMatch(line -> line.getQtyReceived() > 0);
        if (anythingReceived || !(order.getStatus() == PurchaseOrderStatus.DRAFT || order.getStatus() == PurchaseOrderStatus.ORDERED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This purchase order has received goods or is finished; close it instead of cancelling");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return PurchaseOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<SupplierReturnResponse> listReturns(Long businessId, Long storeId, SupplierReturnStatus status, int page, int size) {
        return supplierReturnRepository.search(businessId, storeId, status,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(SupplierReturnResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierReturnResponse getReturn(Long businessId, Long id) {
        return SupplierReturnResponse.from(requireReturn(businessId, id));
    }

    @Transactional
    public SupplierReturnResponse createReturn(SupplierReturnRequest request) {
        if (request.purchaseOrderId() != null) {
            PurchaseOrder order = requireOrder(request.businessId(), request.purchaseOrderId());
            if (!order.getSupplierId().equals(request.supplierId()) || !order.getStoreId().equals(request.storeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The purchase order is for another supplier or store");
            }
        }
        SupplierReturn supplierReturn = SupplierReturn.builder()
                .businessId(request.businessId())
                .storeId(request.storeId())
                .supplierId(request.supplierId())
                .supplierName(requireSupplierName(request.supplierId(), request.businessId()))
                .purchaseOrderId(request.purchaseOrderId())
                .status(SupplierReturnStatus.PENDING)
                .currencyCode(normalizeCurrency(request.currencyCode()))
                .reason(request.reason().trim())
                .note(trim(request.note()))
                .createdBy(request.createdBy())
                .build();
        supplierReturn.replaceLines(request.lines().stream().map(requested -> {
            ProductClient.VariantDetails variant = requireVariant(requested.variantId(), request.storeId());
            return SupplierReturnLine.builder()
                    .variantId(requested.variantId())
                    .productName(variant.productName())
                    .variantName(variant.variantName())
                    .sku(variant.sku())
                    .quantity(requested.quantity())
                    .unitCost(cost(requested.unitCost()))
                    .build();
        }).toList());
        SupplierReturn saved = supplierReturnRepository.save(supplierReturn);
        saved.setReturnNumber(String.format("SR-%05d", saved.getId()));
        return SupplierReturnResponse.from(saved);
    }

    /** The goods have gone back to the supplier: removes them from the store's stock, all lines or none. */
    @Transactional
    public SupplierReturnResponse completeReturn(Long businessId, Long id, Long performedBy) {
        SupplierReturn supplierReturn = requireReturn(businessId, id);
        if (supplierReturn.getStatus() != SupplierReturnStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending return can be completed");
        }
        String note = movementNote("Returned to " + supplierReturn.getSupplierName(), supplierReturn.getReason());
        for (SupplierReturnLine line : supplierReturn.getLines()) {
            try {
                inventoryService.stockOut(new StockChangeRequest(supplierReturn.getStoreId(), line.getVariantId(),
                        line.getQuantity(), SUPPLIER_RETURN_REFERENCE, supplierReturn.getReturnNumber(), performedBy, note));
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Not enough " + line.getProductName() + " in stock to return " + line.getQuantity());
                }
                throw exception;
            }
        }
        supplierReturn.setStatus(SupplierReturnStatus.COMPLETED);
        supplierReturn.setCompletedAt(LocalDateTime.now());
        return SupplierReturnResponse.from(supplierReturn);
    }

    @Transactional
    public SupplierReturnResponse cancelReturn(Long businessId, Long id) {
        SupplierReturn supplierReturn = requireReturn(businessId, id);
        if (supplierReturn.getStatus() != SupplierReturnStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending return can be cancelled");
        }
        supplierReturn.setStatus(SupplierReturnStatus.CANCELLED);
        return SupplierReturnResponse.from(supplierReturn);
    }

    private void applyOrderRequest(PurchaseOrder order, PurchaseOrderRequest request) {
        if (!request.supplierId().equals(order.getSupplierId())) {
            order.setSupplierName(requireSupplierName(request.supplierId(), request.businessId()));
            order.setSupplierId(request.supplierId());
        }
        order.setStoreId(request.storeId());
        order.setCurrencyCode(normalizeCurrency(request.currencyCode()));
        order.setExpectedDate(request.expectedDate());
        order.setNote(trim(request.note()));
        List<PurchaseOrderLine> lines = request.lines().stream().map(requested -> {
            ProductClient.VariantDetails variant = requireVariant(requested.variantId(), request.storeId());
            return PurchaseOrderLine.builder()
                    .variantId(requested.variantId())
                    .productName(variant.productName())
                    .variantName(variant.variantName())
                    .sku(variant.sku())
                    .qtyOrdered(requested.quantity())
                    .qtyReceived(0)
                    .unitCost(cost(requested.unitCost()))
                    .build();
        }).toList();
        order.replaceLines(lines);
    }

    private void markOrdered(PurchaseOrder order) {
        order.setStatus(PurchaseOrderStatus.ORDERED);
        order.setOrderedAt(LocalDateTime.now());
    }

    private PurchaseOrder requireOrder(Long businessId, Long id) {
        return purchaseOrderRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
    }

    private SupplierReturn requireReturn(Long businessId, Long id) {
        return supplierReturnRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier return not found"));
    }

    private String requireSupplierName(Long supplierId, Long businessId) {
        try {
            return productClient.getSupplier(supplierId, businessId).supplierName();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found");
        }
    }

    private ProductClient.VariantDetails requireVariant(Long variantId, Long storeId) {
        ProductClient.VariantDetails variant;
        try {
            variant = productClient.getVariantDetails(variantId, storeId);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product variant " + variantId + " not found");
        }
        if (!storeId.equals(variant.storeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, variant.productName() + " is not sold in this store");
        }
        return variant;
    }

    private static BigDecimal cost(BigDecimal unitCost) {
        return unitCost.setScale(4, RoundingMode.HALF_UP);
    }

    private static String movementNote(String what, String detail) {
        String text = StringUtils.hasText(detail) ? what + ": " + detail.trim() : what;
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static String normalizeCurrency(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 4217 currency code");
        }
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
