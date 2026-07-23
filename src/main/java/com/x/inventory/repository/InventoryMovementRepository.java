package com.x.inventory.repository;

import com.x.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
}
