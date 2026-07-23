package com.x.inventory.service;

import com.x.inventory.dto.StockBalanceResponse;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.StockReservationRequest;
import com.x.inventory.entity.InventoryMovement;
import com.x.inventory.entity.InventoryMovementType;
import com.x.inventory.entity.ReservationStatus;
import com.x.inventory.entity.StockBalance;
import com.x.inventory.entity.StockReservation;
import com.x.inventory.repository.InventoryMovementRepository;
import com.x.inventory.repository.StockBalanceRepository;
import com.x.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public StockBalanceResponse getBalance(Long storeId, Long variantId) {
        return stockBalanceRepository.findByStoreIdAndVariantId(storeId, variantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock balance not found"));
    }

    @Transactional
    public StockBalanceResponse stockIn(StockChangeRequest request) {
        return changeOnHand(request, InventoryMovementType.STOCK_IN, request.quantity());
    }

    @Transactional
    public StockBalanceResponse stockOut(StockChangeRequest request) {
        return changeOnHand(request, InventoryMovementType.STOCK_OUT, -request.quantity());
    }

    @Transactional
    public StockBalanceResponse reserve(StockReservationRequest request) {
        StockBalance balance = balanceFor(request.storeId(), request.variantId());
        if (balance.availableQuantity() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock");
        }
        balance.setQuantityReserved(balance.getQuantityReserved() + request.quantity());
        reservationRepository.save(StockReservation.builder()
                .storeId(request.storeId()).variantId(request.variantId()).orderId(request.orderId())
                .quantity(request.quantity()).status(ReservationStatus.ACTIVE).expiresAt(request.expiresAt()).build());
        recordMovement(request.storeId(), request.variantId(), InventoryMovementType.RESERVATION, 0,
                "ORDER", request.orderId().toString(), null, "Online order stock reserved");
        return toResponse(stockBalanceRepository.save(balance));
    }

    @Transactional
    public StockBalanceResponse releaseReservation(Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active reservation can be released");
        }
        StockBalance balance = balanceFor(reservation.getStoreId(), reservation.getVariantId());
        balance.setQuantityReserved(balance.getQuantityReserved() - reservation.getQuantity());
        reservation.setStatus(ReservationStatus.RELEASED);
        recordMovement(reservation.getStoreId(), reservation.getVariantId(), InventoryMovementType.RESERVATION_RELEASE,
                0, "ORDER", reservation.getOrderId().toString(), null, "Online order stock reservation released");
        reservationRepository.save(reservation);
        return toResponse(stockBalanceRepository.save(balance));
    }

    private StockBalanceResponse changeOnHand(StockChangeRequest request, InventoryMovementType type, long delta) {
        StockBalance balance = balanceFor(request.storeId(), request.variantId());
        long resultingOnHand = balance.getQuantityOnHand() + delta;
        if (resultingOnHand < balance.getQuantityReserved() || resultingOnHand < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock cannot be below reserved or zero");
        }
        balance.setQuantityOnHand(resultingOnHand);
        recordMovement(request.storeId(), request.variantId(), type, delta, request.referenceType(),
                request.referenceId(), request.performedBy(), request.note());
        return toResponse(stockBalanceRepository.save(balance));
    }

    private StockBalance balanceFor(Long storeId, Long variantId) {
        return stockBalanceRepository.findByStoreIdAndVariantId(storeId, variantId)
                .orElseGet(() -> StockBalance.builder().storeId(storeId).variantId(variantId)
                        .quantityOnHand(0).quantityReserved(0).build());
    }

    private void recordMovement(Long storeId, Long variantId, InventoryMovementType type, long delta,
                                String referenceType, String referenceId, Long userId, String note) {
        movementRepository.save(InventoryMovement.builder().storeId(storeId).variantId(variantId)
                .movementType(type).quantityDelta(delta).referenceType(referenceType).referenceId(referenceId)
                .userId(userId).note(note).build());
    }

    private StockBalanceResponse toResponse(StockBalance balance) {
        return new StockBalanceResponse(balance.getId(), balance.getStoreId(), balance.getVariantId(),
                balance.getQuantityOnHand(), balance.getQuantityReserved(), balance.availableQuantity());
    }
}
