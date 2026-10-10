package com.klickit.cart.service;

import com.klickit.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled and programmatic maintenance service to purge abandoned shopping carts.
 * Removes carts whose last modification time exceeds the configured retention window (default: 30 days).
 */
@Service
@Slf4j
public class CartCleanupService {

    private final CartRepository cartRepository;
    private final Clock clock;

    @Value("${klickit.cart.retention-days:30}")
    private int defaultRetentionDays = 30;

    public CartCleanupService(
            CartRepository cartRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
        this.cartRepository = cartRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Nightly scheduled eviction job.
     */
    @Scheduled(cron = "${klickit.cart.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void scheduledAbandonedCartCleanup() {
        log.info("Starting scheduled abandoned cart cleanup (retention window: {} days)...", defaultRetentionDays);
        int deleted = purgeAbandonedCarts(defaultRetentionDays);
        log.info("Completed scheduled abandoned cart cleanup. Purged {} stale cart(s).", deleted);
    }

    /**
     * Purges abandoned carts untouched for longer than the specified number of days.
     *
     * @param retentionDays maximum inactive age in days before eviction
     * @return number of abandoned carts deleted
     */
    @Transactional
    public int purgeAbandonedCarts(int retentionDays) {
        int safeDays = Math.max(1, retentionDays);
        Instant cutoff = Instant.now(clock).minus(safeDays, ChronoUnit.DAYS);
        int deletedCount = cartRepository.deleteByUpdatedAtBefore(cutoff);
        log.info("Purged {} abandoned cart(s) older than cutoff timestamp: {}", deletedCount, cutoff);
        return deletedCount;
    }
}
