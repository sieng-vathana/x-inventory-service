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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

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
        stockBalanceRepository.addOnHand(request.storeId(), request.variantId(), request.quantity());
        recordMovement(request.storeId(), request.variantId(), InventoryMovementType.STOCK_IN, request.quantity(),
                request.referenceType(), request.referenceId(), request.performedBy(), request.note());
        return currentBalance(request.storeId(), request.variantId());
    }

    @Transactional
    public StockBalanceResponse stockOut(StockChangeRequest request) {
        int updated = stockBalanceRepository.removeAvailable(
                request.storeId(), request.variantId(), request.quantity());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock");
        }
        recordMovement(request.storeId(), request.variantId(), InventoryMovementType.STOCK_OUT, -request.quantity(),
                request.referenceType(), request.referenceId(), request.performedBy(), request.note());
        return currentBalance(request.storeId(), request.variantId());
    }

    @Transactional
    public StockBalanceResponse reserve(StockReservationRequest request) {
        return reserveWithDetails(request).balance();
    }

    @Transactional
    public com.x.inventory.dto.StockReservationResponse reserveWithDetails(StockReservationRequest request) {
        if (!request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation expiry must be in the future");
        }
        StockReservation existing = reservationRepository
                .findByOrderIdAndVariantId(request.orderId(), request.variantId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ReservationStatus.ACTIVE
                    && existing.getQuantity() == request.quantity()
                    && existing.getStoreId().equals(request.storeId())) {
                return toReservationResponse(existing);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A reservation already exists for this order and variant");
        }
        int updated = stockBalanceRepository.reserveAvailable(
                request.storeId(), request.variantId(), request.quantity());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock");
        }
        StockReservation reservation = reservationRepository.save(StockReservation.builder()
                .storeId(request.storeId()).variantId(request.variantId()).orderId(request.orderId())
                .quantity(request.quantity()).status(ReservationStatus.ACTIVE).expiresAt(request.expiresAt()).build());
        recordMovement(request.storeId(), request.variantId(), InventoryMovementType.RESERVATION, 0,
                "ORDER", request.orderId().toString(), null, "Order stock reserved");
        return toReservationResponse(reservation);
    }

    @Transactional
    public StockBalanceResponse releaseReservation(Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active reservation can be released");
        }
        release(reservation, ReservationStatus.RELEASED, InventoryMovementType.RESERVATION_RELEASE);
        return currentBalance(reservation.getStoreId(), reservation.getVariantId());
    }

    @Transactional
    public com.x.inventory.dto.StockReservationResponse consumeReservation(Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock reservation not found"));
        if (reservation.getStatus() == ReservationStatus.CONSUMED) {
            return toReservationResponse(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active reservation can be consumed");
        }
        int updated = stockBalanceRepository.consumeReserved(
                reservation.getStoreId(), reservation.getVariantId(), reservation.getQuantity());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved stock cannot be consumed");
        }
        reservation.setStatus(ReservationStatus.CONSUMED);
        reservationRepository.save(reservation);
        recordMovement(reservation.getStoreId(), reservation.getVariantId(),
                InventoryMovementType.RESERVATION_CONSUMED, -reservation.getQuantity(),
                "ORDER", reservation.getOrderId().toString(), null, "Reserved stock sold");
        return toReservationResponse(reservation);
    }

    /** Restores a consumed reservation only when order completion must be compensated. */
    @Transactional
    public com.x.inventory.dto.StockReservationResponse reopenConsumedReservation(Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock reservation not found"));
        if (reservation.getStatus() == ReservationStatus.ACTIVE) return toReservationResponse(reservation);
        if (reservation.getStatus() != ReservationStatus.CONSUMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a consumed reservation can be reopened");
        }
        int updated = stockBalanceRepository.reopenConsumedReservation(
                reservation.getStoreId(), reservation.getVariantId(), reservation.getQuantity());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock balance not found");
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservationRepository.save(reservation);
        recordMovement(reservation.getStoreId(), reservation.getVariantId(),
                InventoryMovementType.RESERVATION_REOPENED, reservation.getQuantity(),
                "ORDER", reservation.getOrderId().toString(), null, "Order completion compensated");
        return toReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public Page<StockBalanceResponse> lowStock(Long storeId, long threshold, int page, int size) {
        return stockBalanceRepository.findLowStock(storeId, threshold,
                        PageRequest.of(page, size, Sort.by("variantId").ascending()))
                .map(this::toResponse);
    }

    @Scheduled(fixedDelayString = "${inventory.reservations.expiry-scan-ms:60000}")
    @Transactional
    public void expireReservations() {
        var expired = reservationRepository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                ReservationStatus.ACTIVE, LocalDateTime.now());
        expired.forEach(reservation ->
                release(reservation, ReservationStatus.EXPIRED, InventoryMovementType.RESERVATION_EXPIRED));
    }

    private void release(StockReservation reservation, ReservationStatus status, InventoryMovementType movementType) {
        int updated = stockBalanceRepository.releaseReserved(
                reservation.getStoreId(), reservation.getVariantId(), reservation.getQuantity());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved stock cannot be released");
        }
        reservation.setStatus(status);
        reservationRepository.save(reservation);
        recordMovement(reservation.getStoreId(), reservation.getVariantId(), movementType,
                0, "ORDER", reservation.getOrderId().toString(), null,
                status == ReservationStatus.EXPIRED ? "Stock reservation expired" : "Stock reservation released");
    }

    private StockBalanceResponse currentBalance(Long storeId, Long variantId) {
        return stockBalanceRepository.findByStoreIdAndVariantId(storeId, variantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock balance not found"));
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

    private com.x.inventory.dto.StockReservationResponse toReservationResponse(StockReservation reservation) {
        return new com.x.inventory.dto.StockReservationResponse(
                reservation.getId(), reservation.getStoreId(), reservation.getVariantId(),
                reservation.getOrderId(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getExpiresAt(), currentBalance(reservation.getStoreId(), reservation.getVariantId()));
    }
}
