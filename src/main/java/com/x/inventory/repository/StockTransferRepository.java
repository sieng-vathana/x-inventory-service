package com.x.inventory.repository;

import com.x.inventory.entity.StockTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long>,
        JpaSpecificationExecutor<StockTransfer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from StockTransfer transfer where transfer.id = :id")
    Optional<StockTransfer> findForUpdateById(@Param("id") Long id);
}
