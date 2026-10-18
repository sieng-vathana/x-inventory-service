package com.x.inventory.service;

import com.x.inventory.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds existing aggregate stock as undated lots after Hibernate has created the new tables. */
@Component
@RequiredArgsConstructor
public class InventoryBatchBackfillRunner implements ApplicationRunner {
    private final InventoryBatchRepository batchRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        batchRepository.backfillMissingBalances();
    }
}
