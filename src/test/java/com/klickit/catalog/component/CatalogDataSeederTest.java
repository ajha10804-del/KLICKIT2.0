package com.klickit.catalog.component;

import com.klickit.catalog.dto.CatalogImportOptions;
import com.klickit.catalog.dto.CatalogImportResult;
import com.klickit.catalog.service.CatalogImportService;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogDataSeederTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CatalogImportService catalogImportService;

    private CatalogDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new CatalogDataSeeder(productRepository, catalogImportService);
    }

    @Test
    @DisplayName("CatalogDataSeeder invokes CatalogImportService with updatePrices=true and updateStock=false")
    void synchronizesCatalogWithCorrectOptions() {
        CatalogImportResult mockResult = CatalogImportResult.builder()
                .totalProcessed(246)
                .insertedCount(0)
                .metadataUpdatedCount(246)
                .pricesUpdatedCount(3)
                .stockUpdatedCount(0)
                .skippedCount(0)
                .build();

        when(catalogImportService.importFromCsv(any(InputStream.class), any(CatalogImportOptions.class)))
                .thenReturn(mockResult);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<CatalogImportOptions> optionsCaptor = ArgumentCaptor.forClass(CatalogImportOptions.class);
        verify(catalogImportService).importFromCsv(any(InputStream.class), optionsCaptor.capture());

        CatalogImportOptions options = optionsCaptor.getValue();
        assertThat(options.isUpdatePrices()).isTrue();
        assertThat(options.isUpdateStock()).isFalse(); // Protect live inventory
        assertThat(options.isActivateNewProducts()).isTrue();
    }

    @Test
    @DisplayName("Failure Case: When importFromCsv throws exception with sensitive DB details, logs only controlled metadata")
    void seeder_whenImportFails_logsSafeMetadataWithoutThrowableOrSensitiveDbMessage() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CatalogDataSeeder.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            when(catalogImportService.importFromCsv(any(InputStream.class), any(CatalogImportOptions.class)))
                    .thenThrow(new RuntimeException("Connection failed: postgresql://admin:SuperSecretSeederPass99@host:5432/klickit"));

            seeder.run(new DefaultApplicationArguments(new String[0]));

            assertThat(listAppender.list).isNotEmpty();
            ch.qos.logback.classic.spi.ILoggingEvent errorEvent = listAppender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR)
                    .findFirst()
                    .orElseThrow();

            assertThat(errorEvent.getThrowableProxy())
                    .as("Throwable must not be attached to log event to prevent stack trace leakage")
                    .isNull();

            String message = errorEvent.getFormattedMessage();
            assertThat(message).contains("Failed to synchronize product catalog from CSV: [type=java.lang.RuntimeException]");
            assertThat(message).doesNotContain("SuperSecretSeederPass99");
            assertThat(message).doesNotContain("postgresql://");
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}
