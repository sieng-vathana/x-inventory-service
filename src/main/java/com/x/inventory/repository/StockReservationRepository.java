package com.x.inventory.repository;

import com.x.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    Optional<StockReservation> findByOrderIdAndVariantId(Long orderId, Long variantId);
    List<StockReservation> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            com.x.inventory.entity.ReservationStatus status, LocalDateTime expiresAt);
}
