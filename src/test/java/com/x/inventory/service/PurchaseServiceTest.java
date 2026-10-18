package com.x.inventory.service;

import com.x.inventory.dto.PurchaseOrderRequest;
import com.x.inventory.dto.ReceivePurchaseOrderRequest;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.SupplierReturnRequest;
import com.x.inventory.entity.PurchaseOrder;
import com.x.inventory.entity.PurchaseOrderStatus;
import com.x.inventory.entity.SupplierReturn;
import com.x.inventory.entity.SupplierReturnStatus;
import com.x.inventory.repository.PurchaseOrderRepository;
import com.x.inventory.repository.SupplierReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseServiceTest {

    private PurchaseOrderRepository orderRepository;
    private SupplierReturnRepository returnRepository;
    private InventoryService inventoryService;
    private ProductClient productClient;
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(PurchaseOrderRepository.class);
        returnRepository = mock(SupplierReturnRepository.class);
        inventoryService = mock(InventoryService.class);
        productClient = mock(ProductClient.class);
        service = new PurchaseService(orderRepository, returnRepository, inventoryService, productClient);
        when(productClient.getSupplier(4L, 2L)).thenReturn(new ProductClient.SupplierSummary(4L, "Angkor Coffee Beans", "ACTIVE"));
        when(productClient.getVariantDetails(11L, 3L)).thenReturn(new ProductClient.VariantDetails(11L, 3L, "Coffee beans", "1 kg", "BEAN-1"));
        when(productClient.getVariantDetails(12L, 3L)).thenReturn(new ProductClient.VariantDetails(12L, 3L, "Fresh milk", "Case", "MILK-C"));
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(12L);
            long lineId = 100;
            for (var line : order.getLines()) line.setId(lineId++);
            return order;
        });
        when(returnRepository.save(any(SupplierReturn.class))).thenAnswer(invocation -> {
            SupplierReturn supplierReturn = invocation.getArgument(0);
            supplierReturn.setId(4L);
            return supplierReturn;
        });
    }

    @Test
    void placedOrderKeepsProductNamesAndTotals() {
        var order = service.createOrder(orderRequest(true));

        assertEquals("PO-00012", order.poNumber());
        assertEquals(PurchaseOrderStatus.ORDERED, order.status());
        assertEquals("Angkor Coffee Beans", order.supplierName());
        assertEquals("Coffee beans", order.lines().get(0).productName());
        assertEquals(new BigDecimal("460.00"), order.totalCost());
        assertEquals(70, order.unitsOrdered());
    }

    @Test
    void receivingAddsStockAndTracksWhatIsStillToCome() {
        PurchaseOrder order = orderOnFile();

        var partial = service.receive(2L, 12L, new ReceivePurchaseOrderRequest(
                List.of(new ReceivePurchaseOrderRequest.Line(100L, 40), new ReceivePurchaseOrderRequest.Line(101L, 10)), 7L, null));
        assertEquals(PurchaseOrderStatus.PARTIAL, partial.status());
        verify(inventoryService).stockIn(new StockChangeRequest(3L, 11L, 40, "PURCHASE_ORDER", "PO-00012", 7L,
                "Received from Angkor Coffee Beans"));

        var complete = service.receive(2L, 12L, new ReceivePurchaseOrderRequest(
                List.of(new ReceivePurchaseOrderRequest.Line(101L, 20)), 7L, "second delivery"));
        assertEquals(PurchaseOrderStatus.RECEIVED, complete.status());
        assertEquals(70, complete.unitsReceived());
        assertEquals(PurchaseOrderStatus.RECEIVED, order.getStatus());
    }

    @Test
    void cannotReceiveMoreThanWasOrdered() {
        orderOnFile();

        ResponseStatusException tooMany = assertThrows(ResponseStatusException.class, () -> service.receive(2L, 12L,
                new ReceivePurchaseOrderRequest(List.of(new ReceivePurchaseOrderRequest.Line(100L, 41)), 7L, null)));

        assertEquals("Only 40 of Coffee beans are still to receive", tooMany.getReason());
        verify(inventoryService, never()).stockIn(any());
    }

    @Test
    void anOrderWithReceivedGoodsIsClosedNotCancelled() {
        orderOnFile();
        service.receive(2L, 12L, new ReceivePurchaseOrderRequest(List.of(new ReceivePurchaseOrderRequest.Line(100L, 5)), 7L, null));

        assertThrows(ResponseStatusException.class, () -> service.cancelOrder(2L, 12L));
        assertEquals(PurchaseOrderStatus.CLOSED, service.closeOrder(2L, 12L).status());
    }

    @Test
    void completingAReturnRemovesStockAndExplainsAShortage() {
        var created = service.createReturn(new SupplierReturnRequest(2L, 3L, 4L, null, "USD", "Damaged packaging", null, 7L,
                List.of(new SupplierReturnRequest.Line(12L, 3, new BigDecimal("12.00")))));
        assertEquals("SR-00004", created.returnNumber());
        assertEquals(SupplierReturnStatus.PENDING, created.status());

        SupplierReturn saved = returnOnFile(created.returnNumber());
        when(inventoryService.stockOut(argThat(request -> request.variantId().equals(12L))))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock"));

        ResponseStatusException shortage = assertThrows(ResponseStatusException.class, () -> service.completeReturn(2L, 4L, 7L));
        assertEquals("Not enough Fresh milk in stock to return 3", shortage.getReason());
        assertEquals(SupplierReturnStatus.PENDING, saved.getStatus());
    }

    @Test
    void productsFromAnotherStoreAreRejected() {
        when(productClient.getVariantDetails(13L, 3L)).thenReturn(new ProductClient.VariantDetails(13L, 9L, "Tea", null, "TEA"));

        ResponseStatusException wrongStore = assertThrows(ResponseStatusException.class, () -> service.createOrder(
                new PurchaseOrderRequest(2L, 3L, 4L, "USD", null, null, 7L, false,
                        List.of(new PurchaseOrderRequest.Line(13L, 1, BigDecimal.ONE)))));

        assertEquals("Tea is not sold in this store", wrongStore.getReason());
    }

    private PurchaseOrderRequest orderRequest(boolean place) {
        return new PurchaseOrderRequest(2L, 3L, 4L, "usd", null, "Rush for the weekend", 7L, place, List.of(
                new PurchaseOrderRequest.Line(11L, 40, new BigDecimal("8.50")),
                new PurchaseOrderRequest.Line(12L, 30, new BigDecimal("4.00"))));
    }

    private PurchaseOrder orderOnFile() {
        service.createOrder(orderRequest(true));
        org.mockito.ArgumentCaptor<PurchaseOrder> saved = org.mockito.ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepository).save(saved.capture());
        when(orderRepository.findByIdAndBusinessId(12L, 2L)).thenReturn(Optional.of(saved.getValue()));
        return saved.getValue();
    }

    private SupplierReturn returnOnFile(String returnNumber) {
        org.mockito.ArgumentCaptor<SupplierReturn> saved = org.mockito.ArgumentCaptor.forClass(SupplierReturn.class);
        verify(returnRepository).save(saved.capture());
        assertEquals(returnNumber, saved.getValue().getReturnNumber());
        when(returnRepository.findByIdAndBusinessId(4L, 2L)).thenReturn(Optional.of(saved.getValue()));
        return saved.getValue();
    }
}
