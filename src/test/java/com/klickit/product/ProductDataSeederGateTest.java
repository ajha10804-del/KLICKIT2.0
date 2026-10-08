package com.klickit.product;

import com.klickit.product.config.ProductDataSeeder;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductDataSeederGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProductRepository.class, () -> Mockito.mock(ProductRepository.class))
            .withUserConfiguration(ProductDataSeeder.class);

    @Test
    @DisplayName("Case A: ProductDataSeeder is disabled by default when property is absent")
    void seederDisabledByDefaultWhenPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ProductDataSeeder.class);
            ProductRepository mockRepo = context.getBean(ProductRepository.class);
            Mockito.verifyNoInteractions(mockRepo);
        });
    }

    @Test
    @DisplayName("Case A: ProductDataSeeder is disabled when property is explicitly false")
    void seederDisabledWhenPropertyExplicitlyFalse() {
        contextRunner
                .withPropertyValues("klickit.seed.demo-products=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ProductDataSeeder.class);
                    ProductRepository mockRepo = context.getBean(ProductRepository.class);
                    Mockito.verifyNoInteractions(mockRepo);
                });
    }

    @Test
    @DisplayName("Case B: ProductDataSeeder becomes active when klickit.seed.demo-products is true")
    void seederActiveWhenPropertyExplicitlyTrue() {
        contextRunner
                .withPropertyValues("klickit.seed.demo-products=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ProductDataSeeder.class);
                    ProductDataSeeder seeder = context.getBean(ProductDataSeeder.class);
                    assertThat(seeder).isNotNull();
                    ProductRepository mockRepo = context.getBean(ProductRepository.class);
                    verify(mockRepo, never()).save(Mockito.any());
                });
    }
}
