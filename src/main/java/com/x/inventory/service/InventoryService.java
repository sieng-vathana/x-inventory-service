package com.x.inventory.service;

import com.x.inventory.dto.ExpiryBatchResponse;
import com.x.inventory.dto.ExpirySummaryResponse;
import com.x.inventory.dto.ExpiryBatchUpdateRequest;
import com.x.inventory.dto.ExpiryWriteOffRequest;
import com.x.inventory.dto.InventoryMovementResponse;
import com.x.inventory.dto.StockBalanceResponse;
import com.x.inventory.dto.StockChangeRequest;
import com.x.inventory.dto.StockReservationRequest;
import com.x.inventory.entity.InventoryBatch;
import com.x.inventory.entity.InventoryMovement;
import com.x.inventory.entity.InventoryMovementType;
import com.x.inventory.entity.ReservationStatus;
import com.x.inventory.entity.StockBalance;
import com.x.inventory.entity.StockReservation;
import com.x.inventory.entity.StockReservationBatchAllocation;
import com.x.inventory.repository.InventoryBatchRepository;
import com.x.inventory.repository.InventoryMovementRepository;
import com.x.inventory.repository.StockBalanceRepository;
import com.x.inventory.repository.StockReservationBatchAllocationRepository;
import com.x.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final String NEGATIVE_STOCK = "NEGATIVE_STOCK";

    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockReservationBatchAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;
    private final ProductClient productClient;

    @Transactional
    public StockBalanceResponse getBalance(Long storeId, Long variantId) {
        ensureBalanceFromCatalog(storeId, variantId);
        StockBalance balance = lockBalance(storeId, variantId);
        ensureBatchLedger(balance);
        return toResponse(balance);
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> findMovements(
            Long storeId,
            Long variantId,
            InventoryMovementType movementType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be before or equal to 'to'");
        }

        Specification<InventoryMovement> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("storeId"), storeId);
        if (variantId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("variantId"), variantId));
        }
        if (movementType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("movementType"), movementType));
        }
        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.<LocalDateTime>get("createdAt"), to));
        }

        return movementRepository.findAll(specification,
                        PageRequest.of(page, size, Sort.by(
                                Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::toMovementResponse);
    }

    /** The first movement starts an undated opening lot for any catalog stock already on hand. */
    @Transactional
    public StockBalanceResponse stockIn(StockChangeRequest request) {
        if (request.expiryDate() != null && request.expiryDate().isBefore(businessDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot receive a batch that has already expired");
        }
        ensureBalanceFromCatalog(request.storeId(), request.variantId());
        StockBalance balance = lockBalance(request.storeId(), request.variantId());
        ensureBatchLedger(balance);

        balance.setQuantityOnHand(balance.getQuantityOnHand() + request.quantity());
        stockBalanceRepository.save(balance);

        InventoryMovementType movementType = "ADJUSTMENT".equalsIgnoreCase(request.referenceType())
                ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.STOCK_IN;
        addReceivedQuantity(request, movementType);
        return toResponse(balance);
    }

    @Transactional
    public StockBalanceResponse stockOut(StockChangeRequest request) {
        ensureBalanceFromCatalog(request.storeId(), request.variantId());
        StockBalance balance = lockBalance(request.storeId(), request.variantId());
        ensureBatchLedger(balance);

        List<BatchQuantity> plan = planAvailableBatches(
                request.storeId(), request.variantId(), balance.getQuantityReserved());
        if (sum(plan) < request.quantity() || balance.getQuantityOnHand() - balance.getQuantityReserved() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient non-expired, available stock");
        }
        balance.setQuantityOnHand(balance.getQuantityOnHand() - request.quantity());
        stockBalanceRepository.save(balance);

        InventoryMovementType movementType = "ADJUSTMENT".equalsIgnoreCase(request.referenceType())
                ? InventoryMovementType.ADJUSTMENT_OUT : InventoryMovementType.STOCK_OUT;
        applyBatchDecrements(plan, request.quantity(), movementType, request.referenceType(),
                request.referenceId(), request.performedBy(), request.note());
        return toResponse(balance);
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

        ensureBalanceFromCatalog(request.storeId(), request.variantId());
        StockBalance balance = lockBalance(request.storeId(), request.variantId());
        ensureBatchLedger(balance);
        boolean allowNegative = Boolean.TRUE.equals(request.allowNegativeStock());
        List<BatchQuantity> plan = planAvailableBatches(
                request.storeId(), request.variantId(), balance.getQuantityReserved());
        if (sum(plan) < request.quantity() && !allowNegative) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient non-expired, available stock");
        }

        StockReservation reservation = reservationRepository.save(StockReservation.builder()
                .storeId(request.storeId()).variantId(request.variantId()).orderId(request.orderId())
                .quantity(request.quantity()).allowNegativeStock(allowNegative)
                .status(ReservationStatus.ACTIVE).expiresAt(request.expiresAt()).build());
        saveAllocations(reservation, plan, request.quantity());
        balance.setQuantityReserved(balance.getQuantityReserved() + request.quantity());
        stockBalanceRepository.save(balance);
        recordMovement(request.storeId(), request.variantId(), null, InventoryMovementType.RESERVATION, 0,
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
        if (reservation.getStatus() == ReservationStatus.CONSUMED) return toReservationResponse(reservation);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active reservation can be consumed");
        }

        StockBalance balance = lockBalance(reservation.getStoreId(), reservation.getVariantId());
        ensureBatchLedger(balance);
        List<StockReservationBatchAllocation> allocations =
                allocationRepository.findAllByReservationIdOrderById(reservation.getId());
        long previouslyAllocated = allocations.stream().mapToLong(StockReservationBatchAllocation::getQuantity).sum();
        long stillToAllocate = Math.max(0, reservation.getQuantity() - previouslyAllocated);
        if (stillToAllocate > 0) {
            // Legacy holds and stock received after reservation are assigned when the order is picked.
            List<BatchQuantity> plan = planAvailableBatches(reservation.getStoreId(), reservation.getVariantId(),
                    Math.max(0, balance.getQuantityReserved() - stillToAllocate));
            if (sum(plan) < stillToAllocate && !reservation.isAllowNegativeStock()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The reservation has no remaining, non-expired batch stock to consume");
            }
            saveAllocations(reservation, plan, stillToAllocate);
            allocations = allocationRepository.findAllByReservationIdOrderById(reservation.getId());
        }

        long allocated = allocations.stream().mapToLong(StockReservationBatchAllocation::getQuantity).sum();
        if (allocated > reservation.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation batch allocation is inconsistent");
        }
        List<InventoryBatch> consumedBatches = new ArrayList<>();
        for (StockReservationBatchAllocation allocation : allocations) {
            InventoryBatch batch = batchRepository.findForUpdateById(allocation.getBatchId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "A reserved stock batch is no longer available"));
            if (!batch.getStoreId().equals(reservation.getStoreId())
                    || !batch.getVariantId().equals(reservation.getVariantId())
                    || batch.getQuantityOnHand() < allocation.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved batch quantity is no longer available");
            }
            if (isExpired(batch, businessDate())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Reserved stock has expired. Release the reservation and review the order before fulfilling it");
            }
            batch.setQuantityOnHand(batch.getQuantityOnHand() - allocation.getQuantity());
            consumedBatches.add(batch);
        }

        long shortage = reservation.getQuantity() - allocated;
        InventoryBatch negativeBatch = null;
        if (shortage > 0) {
            if (!reservation.isAllowNegativeStock()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved stock cannot be consumed");
            }
            negativeBatch = getOrCreateNegativeBatch(reservation.getStoreId(), reservation.getVariantId());
            negativeBatch.setQuantityOnHand(negativeBatch.getQuantityOnHand() - shortage);
            batchRepository.save(negativeBatch);
        }
        consumedBatches.forEach(batchRepository::save);

        if (balance.getQuantityReserved() < reservation.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved stock cannot be consumed");
        }
        balance.setQuantityOnHand(balance.getQuantityOnHand() - reservation.getQuantity());
        balance.setQuantityReserved(balance.getQuantityReserved() - reservation.getQuantity());
        stockBalanceRepository.save(balance);
        reservation.setStatus(ReservationStatus.CONSUMED);
        reservationRepository.save(reservation);
        for (StockReservationBatchAllocation allocation : allocations) {
            recordMovement(reservation.getStoreId(), reservation.getVariantId(), allocation.getBatchId(),
                    InventoryMovementType.RESERVATION_CONSUMED, -allocation.getQuantity(), "ORDER",
                    reservation.getOrderId().toString(), null, "Reserved stock sold");
        }
        if (shortage > 0) {
            recordMovement(reservation.getStoreId(), reservation.getVariantId(), negativeBatch.getId(),
                    InventoryMovementType.RESERVATION_CONSUMED, -shortage, "ORDER",
                    reservation.getOrderId().toString(), null, "Order fulfilled against negative stock");
        }
        return toReservationResponse(reservation);
    }

    /** Restores consumed quantities to their original lots when an order completion is compensated. */
    @Transactional
    public com.x.inventory.dto.StockReservationResponse reopenConsumedReservation(Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock reservation not found"));
        if (reservation.getStatus() == ReservationStatus.ACTIVE) return toReservationResponse(reservation);
        if (reservation.getStatus() != ReservationStatus.CONSUMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a consumed reservation can be reopened");
        }

        StockBalance balance = lockBalance(reservation.getStoreId(), reservation.getVariantId());
        ensureBatchLedger(balance);
        List<StockReservationBatchAllocation> allocations =
                allocationRepository.findAllByReservationIdOrderById(reservation.getId());
        long restoredToLots = 0;
        for (StockReservationBatchAllocation allocation : allocations) {
            InventoryBatch batch = batchRepository.findForUpdateById(allocation.getBatchId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "A consumed stock batch is no longer available"));
            batch.setQuantityOnHand(batch.getQuantityOnHand() + allocation.getQuantity());
            batchRepository.save(batch);
            restoredToLots += allocation.getQuantity();
        }
        long unallocatedQuantity = reservation.getQuantity() - restoredToLots;
        if (unallocatedQuantity > 0) {
            long remaining = unallocatedQuantity;
            InventoryBatch negative = batchRepository.findNegativeStockBatchForUpdate(
                    reservation.getStoreId(), reservation.getVariantId()).orElse(null);
            if (negative != null && negative.getQuantityOnHand() < 0) {
                long offset = Math.min(remaining, -negative.getQuantityOnHand());
                negative.setQuantityOnHand(negative.getQuantityOnHand() + offset);
                batchRepository.save(negative);
                remaining -= offset;
            }
            if (remaining > 0) {
                InventoryBatch restored = createBatch(reservation.getStoreId(), reservation.getVariantId(), null,
                        null, "RESTORED_ORDER", reservation.getOrderId().toString(), remaining, remaining);
                batchRepository.save(restored);
            }
        }
        balance.setQuantityOnHand(balance.getQuantityOnHand() + reservation.getQuantity());
        balance.setQuantityReserved(balance.getQuantityReserved() + reservation.getQuantity());
        stockBalanceRepository.save(balance);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservationRepository.save(reservation);
        recordMovement(reservation.getStoreId(), reservation.getVariantId(), null,
                InventoryMovementType.RESERVATION_REOPENED, reservation.getQuantity(), "ORDER",
                reservation.getOrderId().toString(), null, "Order completion compensated");
        return toReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public Page<ExpiryBatchResponse> findExpiryBatches(Long storeId, String filter, int page, int size) {
        String normalized = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase();
        LocalDate today = businessDate();
        Specification<InventoryBatch> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("storeId"), storeId), cb.greaterThan(root.get("quantityOnHand"), 0L));
        specification = switch (normalized) {
            case "ALL" -> specification;
            case "EXPIRED" -> specification.and((root, query, cb) -> cb.lessThan(root.get("expiryDate"), today));
            case "WITHIN_7_DAYS" -> specification.and((root, query, cb) -> cb.between(
                    root.get("expiryDate"), today, today.plusDays(7)));
            case "WITHIN_30_DAYS" -> specification.and((root, query, cb) -> cb.between(
                    root.get("expiryDate"), today, today.plusDays(30)));
            case "NO_DATE" -> specification.and((root, query, cb) -> cb.isNull(root.get("expiryDate")));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown expiry filter");
        };
        specification = specification.and((root, query, cb) -> {
            // Spring Data JPA's nullsLast Sort metadata is unsupported for this Criteria query.
            // Use a CASE expression so dated lots sort first, then by earliest expiry.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(
                        cb.asc(cb.<Integer>selectCase().when(cb.isNull(root.get("expiryDate")), 1).otherwise(0)),
                        cb.asc(root.<LocalDate>get("expiryDate")),
                        cb.asc(root.<LocalDateTime>get("receivedAt")),
                        cb.asc(root.get("id")));
            }
            return cb.conjunction();
        });
        Page<InventoryBatch> batches = batchRepository.findAll(specification, PageRequest.of(page, size));
        Map<Long, Map<Long, Long>> reservedByVariant = new HashMap<>();
        return batches.map(batch -> {
            Map<Long, Long> reserved = reservedByVariant.computeIfAbsent(batch.getVariantId(), ignored -> {
                long quantityReserved = stockBalanceRepository.findByStoreIdAndVariantId(storeId, batch.getVariantId())
                        .map(StockBalance::getQuantityReserved).orElse(0L);
                List<InventoryBatch> allLots = batchRepository
                        .findByStoreIdAndVariantIdOrderByExpiryDateAscReceivedAtAscIdAsc(storeId, batch.getVariantId());
                return batchReservedQuantities(storeId, batch.getVariantId(), quantityReserved, allLots);
            });
            return toExpiryResponse(batch, reserved);
        });
    }

    @Transactional(readOnly = true)
    public ExpirySummaryResponse expirySummary(Long storeId) {
        LocalDate today = businessDate();
        Object[] values = batchRepository.summarizeExpiry(storeId, today, today.plusDays(7), today.plusDays(30));
        return new ExpirySummaryResponse(number(values, 0), number(values, 1), number(values, 2),
                number(values, 3), number(values, 4), number(values, 5), number(values, 6), number(values, 7));
    }

    @Transactional
    public StockBalanceResponse writeOffExpiredBatch(Long storeId, Long batchId, ExpiryWriteOffRequest request) {
        InventoryBatch initial = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        if (!initial.getStoreId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found");
        }
        StockBalance balance = lockBalance(initial.getStoreId(), initial.getVariantId());
        InventoryBatch batch = batchRepository.findForUpdateById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        if (!isExpired(batch, businessDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only expired stock can be written off");
        }
        ensureBatchLedger(balance);
        List<InventoryBatch> lots = batchRepository.findForUpdateByStoreAndVariant(batch.getStoreId(), batch.getVariantId());
        Map<Long, Long> reserved = batchReservedQuantities(batch.getStoreId(), batch.getVariantId(),
                balance.getQuantityReserved(), lots);
        long available = batch.getQuantityOnHand() - reserved.getOrDefault(batch.getId(), 0L);
        if (request.quantity() > available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only " + Math.max(0, available) + " unreserved units can be written off");
        }
        if (balance.getQuantityOnHand() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock balance is inconsistent with this batch");
        }
        batch.setQuantityOnHand(batch.getQuantityOnHand() - request.quantity());
        balance.setQuantityOnHand(balance.getQuantityOnHand() - request.quantity());
        batchRepository.save(batch);
        stockBalanceRepository.save(balance);
        String reason = request.reason().trim();
        recordMovement(batch.getStoreId(), batch.getVariantId(), batch.getId(), InventoryMovementType.EXPIRY_WRITE_OFF,
                -request.quantity(), "EXPIRY", batch.getId().toString(), request.performedBy(), reason);
        return toResponse(balance);
    }

    @Transactional
    public ExpiryBatchResponse updateBatchDetails(Long storeId, Long batchId, ExpiryBatchUpdateRequest request) {
        InventoryBatch initial = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        if (!initial.getStoreId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found");
        }
        lockBalance(initial.getStoreId(), initial.getVariantId());
        InventoryBatch batch = batchRepository.findForUpdateById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        String previous = "batch=" + (batch.getBatchCode() == null ? "—" : batch.getBatchCode())
                + ", expiry=" + (batch.getExpiryDate() == null ? "—" : batch.getExpiryDate());
        batch.setBatchCode(trimToNull(request.batchCode()));
        batch.setExpiryDate(request.expiryDate());
        batchRepository.save(batch);
        recordMovement(batch.getStoreId(), batch.getVariantId(), batch.getId(),
                InventoryMovementType.BATCH_DETAILS_UPDATED, 0, "EXPIRY", batch.getId().toString(),
                request.performedBy(), "Batch details changed from " + previous + " to batch="
                        + (batch.getBatchCode() == null ? "—" : batch.getBatchCode()) + ", expiry=" + batch.getExpiryDate());
        List<InventoryBatch> allLots = batchRepository.findByStoreIdAndVariantIdOrderByExpiryDateAscReceivedAtAscIdAsc(
                storeId, batch.getVariantId());
        long reserved = stockBalanceRepository.findByStoreIdAndVariantId(storeId, batch.getVariantId())
                .map(StockBalance::getQuantityReserved).orElse(0L);
        return toExpiryResponse(batch, batchReservedQuantities(storeId, batch.getVariantId(), reserved, allLots));
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

    private void addReceivedQuantity(StockChangeRequest request, InventoryMovementType movementType) {
        long remaining = request.quantity();
        InventoryBatch negative = batchRepository.findNegativeStockBatchForUpdate(request.storeId(), request.variantId())
                .orElse(null);
        if (negative != null && negative.getQuantityOnHand() < 0) {
            long offset = Math.min(remaining, -negative.getQuantityOnHand());
            negative.setQuantityOnHand(negative.getQuantityOnHand() + offset);
            batchRepository.save(negative);
            recordMovement(request.storeId(), request.variantId(), negative.getId(), movementType, offset,
                    request.referenceType(), request.referenceId(), request.performedBy(), request.note());
            remaining -= offset;
        }
        if (remaining > 0) {
            String sourceType = request.referenceType() == null || request.referenceType().isBlank()
                    ? "STOCK_IN" : request.referenceType().trim().toUpperCase();
            InventoryBatch batch = createBatch(request.storeId(), request.variantId(), request.batchCode(),
                    request.expiryDate(), sourceType, request.referenceId(), remaining, remaining);
            batchRepository.save(batch);
            recordMovement(request.storeId(), request.variantId(), batch.getId(), movementType, remaining,
                    request.referenceType(), request.referenceId(), request.performedBy(), request.note());
        }
    }

    private List<BatchQuantity> planAvailableBatches(Long storeId, Long variantId, long quantityReserved) {
        List<InventoryBatch> batches = batchRepository.findForUpdateByStoreAndVariant(storeId, variantId);
        Map<Long, Long> reserved = batchReservedQuantities(storeId, variantId, quantityReserved, batches);
        LocalDate today = businessDate();
        List<BatchQuantity> available = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            if (isExpired(batch, today)) continue;
            long free = Math.max(0, batch.getQuantityOnHand() - reserved.getOrDefault(batch.getId(), 0L));
            if (free > 0) available.add(new BatchQuantity(batch, free));
        }
        return available;
    }

    /** Active lot allocations are exact; legacy reservations are conservatively held against oldest lots. */
    private Map<Long, Long> batchReservedQuantities(Long storeId, Long variantId, long quantityReserved,
                                                    List<InventoryBatch> batches) {
        long activeAllocated = allocationRepository.sumActiveQuantityByStoreAndVariant(storeId, variantId);
        long legacyReserved = Math.max(0, quantityReserved - activeAllocated);
        Map<Long, Long> reserved = new HashMap<>();
        for (InventoryBatch batch : batches) {
            long allocated = allocationRepository.sumActiveQuantityByBatchId(batch.getId());
            long unallocatedStock = Math.max(0, batch.getQuantityOnHand() - allocated);
            long legacyHeld = Math.min(legacyReserved, unallocatedStock);
            legacyReserved -= legacyHeld;
            reserved.put(batch.getId(), allocated + legacyHeld);
        }
        return reserved;
    }

    private Map<Long, Long> batchReservedQuantities(Long storeId, Long variantId, long quantityReserved) {
        return batchReservedQuantities(storeId, variantId, quantityReserved,
                batchRepository.findForUpdateByStoreAndVariant(storeId, variantId));
    }

    private void saveAllocations(StockReservation reservation, List<BatchQuantity> plan, long requested) {
        long remaining = requested;
        Map<Long, StockReservationBatchAllocation> existing = new HashMap<>();
        allocationRepository.findAllByReservationIdOrderById(reservation.getId())
                .forEach(allocation -> existing.put(allocation.getBatchId(), allocation));
        List<StockReservationBatchAllocation> allocations = new ArrayList<>();
        for (BatchQuantity batchQuantity : plan) {
            long allocated = Math.min(remaining, batchQuantity.quantity());
            if (allocated <= 0) break;
            StockReservationBatchAllocation allocation = existing.get(batchQuantity.batch().getId());
            if (allocation == null) {
                allocation = StockReservationBatchAllocation.builder().reservationId(reservation.getId())
                        .batchId(batchQuantity.batch().getId()).quantity(allocated).build();
            } else {
                allocation.setQuantity(allocation.getQuantity() + allocated);
            }
            allocations.add(allocation);
            remaining -= allocated;
        }
        if (!allocations.isEmpty()) allocationRepository.saveAll(allocations);
    }

    private void applyBatchDecrements(List<BatchQuantity> plan, long quantity, InventoryMovementType type,
                                      String referenceType, String referenceId, Long performedBy, String note) {
        long remaining = quantity;
        for (BatchQuantity available : plan) {
            if (remaining <= 0) break;
            InventoryBatch batch = available.batch();
            long decrement = Math.min(remaining, available.quantity());
            batch.setQuantityOnHand(batch.getQuantityOnHand() - decrement);
            batchRepository.save(batch);
            recordMovement(batch.getStoreId(), batch.getVariantId(), batch.getId(), type, -decrement,
                    referenceType, referenceId, performedBy, note);
            remaining -= decrement;
        }
    }

    private void release(StockReservation reservation, ReservationStatus status, InventoryMovementType movementType) {
        StockBalance balance = lockBalance(reservation.getStoreId(), reservation.getVariantId());
        if (balance.getQuantityReserved() < reservation.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved stock cannot be released");
        }
        balance.setQuantityReserved(balance.getQuantityReserved() - reservation.getQuantity());
        stockBalanceRepository.save(balance);
        reservation.setStatus(status);
        reservationRepository.save(reservation);
        allocationRepository.deleteAllByReservationId(reservation.getId());
        recordMovement(reservation.getStoreId(), reservation.getVariantId(), null, movementType,
                0, "ORDER", reservation.getOrderId().toString(), null,
                status == ReservationStatus.EXPIRED ? "Stock reservation expired" : "Stock reservation released");
    }

    private StockBalance lockBalance(Long storeId, Long variantId) {
        return stockBalanceRepository.findForUpdateByStoreIdAndVariantId(storeId, variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock balance not found"));
    }

    private void ensureBalanceFromCatalog(Long storeId, Long variantId) {
        if (stockBalanceRepository.findByStoreIdAndVariantId(storeId, variantId).isPresent()) return;
        ProductClient.VariantQuantity variant = productClient.getVariantQuantity(variantId, storeId);
        if (variant == null || !storeId.equals(variant.storeId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Product variant does not belong to store " + storeId);
        }
        stockBalanceRepository.initializeIfMissing(storeId, variantId,
                Math.max(0, variant.quantity() == null ? 0 : variant.quantity()));
    }

    private void ensureBatchLedger(StockBalance balance) {
        if (batchRepository.existsByStoreIdAndVariantId(balance.getStoreId(), balance.getVariantId())) return;
        long quantity = balance.getQuantityOnHand();
        if (quantity == 0) return;
        boolean negative = quantity < 0;
        InventoryBatch opening = createBatch(balance.getStoreId(), balance.getVariantId(), null, null,
                negative ? NEGATIVE_STOCK : "OPENING_BALANCE", null,
                Math.max(0, quantity), quantity);
        batchRepository.save(opening);
    }

    private InventoryBatch getOrCreateNegativeBatch(Long storeId, Long variantId) {
        return batchRepository.findNegativeStockBatchForUpdate(storeId, variantId)
                .orElseGet(() -> batchRepository.save(createBatch(storeId, variantId, null, null,
                        NEGATIVE_STOCK, null, 0, 0)));
    }

    private InventoryBatch createBatch(Long storeId, Long variantId, String batchCode, LocalDate expiryDate,
                                       String sourceType, String sourceReference,
                                       long quantityReceived, long quantityOnHand) {
        return InventoryBatch.builder().storeId(storeId).variantId(variantId).batchCode(trimToNull(batchCode))
                .expiryDate(expiryDate).sourceType(sourceType).sourceReference(sourceReference)
                .quantityReceived(quantityReceived).quantityOnHand(quantityOnHand).build();
    }

    private StockBalanceResponse currentBalance(Long storeId, Long variantId) {
        return stockBalanceRepository.findByStoreIdAndVariantId(storeId, variantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock balance not found"));
    }

    private void recordMovement(Long storeId, Long variantId, Long batchId, InventoryMovementType type, long delta,
                                String referenceType, String referenceId, Long userId, String note) {
        movementRepository.save(InventoryMovement.builder().storeId(storeId).variantId(variantId).batchId(batchId)
                .movementType(type).quantityDelta(delta).referenceType(referenceType).referenceId(referenceId)
                .userId(userId).note(note).build());
    }

    private StockBalanceResponse toResponse(StockBalance balance) {
        Long sellable = batchRepository.sellableQuantity(balance.getStoreId(), balance.getVariantId(), businessDate());
        long sellableQuantity = sellable == null ? balance.getQuantityOnHand() : sellable;
        if (!batchRepository.existsByStoreIdAndVariantId(balance.getStoreId(), balance.getVariantId())) {
            sellableQuantity = balance.getQuantityOnHand();
        }
        return new StockBalanceResponse(balance.getId(), balance.getStoreId(), balance.getVariantId(),
                balance.getQuantityOnHand(), balance.getQuantityReserved(), sellableQuantity - balance.getQuantityReserved());
    }

    private ExpiryBatchResponse toExpiryResponse(InventoryBatch batch, Map<Long, Long> reserved) {
        return new ExpiryBatchResponse(batch.getId(), batch.getStoreId(), batch.getVariantId(), batch.getBatchCode(),
                batch.getExpiryDate(), batch.getQuantityReceived(), batch.getQuantityOnHand(),
                Math.max(0, batch.getQuantityOnHand() - reserved.getOrDefault(batch.getId(), 0L)),
                batch.getSourceType(), batch.getSourceReference(), batch.getReceivedAt());
    }

    private InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(), movement.getStoreId(), movement.getVariantId(),
                movement.getBatchId(), movement.getMovementType(), movement.getQuantityDelta(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getUserId(),
                movement.getNote(), movement.getCreatedAt());
    }

    private com.x.inventory.dto.StockReservationResponse toReservationResponse(StockReservation reservation) {
        return new com.x.inventory.dto.StockReservationResponse(
                reservation.getId(), reservation.getStoreId(), reservation.getVariantId(),
                reservation.getOrderId(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getExpiresAt(), currentBalance(reservation.getStoreId(), reservation.getVariantId()));
    }

    private static boolean isExpired(InventoryBatch batch, LocalDate today) {
        return batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
    }

    private static LocalDate businessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private static long sum(List<BatchQuantity> quantities) {
        return quantities.stream().mapToLong(BatchQuantity::quantity).sum();
    }

    private static long number(Object[] values, int index) {
        return values != null && values.length > index && values[index] instanceof Number value
                ? value.longValue() : 0;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record BatchQuantity(InventoryBatch batch, long quantity) { }
}
