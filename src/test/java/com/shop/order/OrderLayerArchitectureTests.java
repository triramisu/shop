package com.shop.order;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.shop.order.internal.checkout.dto.request.CheckoutQuoteRequest;
import com.shop.order.internal.entity.OrderItemSnapshot;
import com.shop.order.internal.repository.CartRepository;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderLayerArchitectureTests {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importClasses() {
        applicationClasses = new ClassFileImporter().importPackagesOf(ShopApplication.class);
    }

    @Test
    void orderOnlyUsesThePublishedCatalogContract() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.order.internal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.shop.catalog.internal..")
                .check(applicationClasses);
    }

    @Test
    void orderOnlyUsesThePublishedInventoryContract() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.order.internal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.shop.inventory.internal..")
                .check(applicationClasses);
    }

    @Test
    void orderEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.order.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .hasSize(6)
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("don_hang_");
                });
    }

    @Test
    void orderControllersDoNotAccessEntitiesOrRepositoriesDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..order.internal..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..order.internal.entity..", "..order.internal.repository..")
                .check(applicationClasses);
    }

    @Test
    void orderRepositoriesAreOnlyAccessedByOrderServices() {
        classes()
                .that()
                .resideInAPackage("..order.internal..repository..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage("..order.internal..repository..", "..order.internal..service..")
                .check(applicationClasses);
    }

    @Test
    void orderDtosDoNotExposePersistenceEntities() {
        noClasses()
                .that()
                .resideInAPackage("..order.internal..dto..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(applicationClasses);
    }

    @Test
    void orderRepositoriesDoNotExposeAggregateDeletion() {
        assertThat(List.of(
                        CartRepository.class,
                        CustomerOrderRepository.class,
                        OrderInventoryOrchestrationRepository.class))
                .allSatisfy(repository -> assertThat(Arrays.stream(repository.getMethods())
                                .map(Method::getName)
                                .filter(methodName -> methodName.startsWith("delete")))
                        .isEmpty());
    }

    @Test
    void checkoutRequestNeverAcceptsClientOwnedPricingOrItems() {
        assertThat(Arrays.stream(CheckoutQuoteRequest.class.getDeclaredFields())
                        .filter(field -> !field.isSynthetic())
                        .map(java.lang.reflect.Field::getName))
                .containsExactly("expectedCartVersion");
    }

    @Test
    void persistedOrderItemSnapshotsAreMarkedImmutable() {
        assertThat(OrderItemSnapshot.class).hasAnnotation(Immutable.class);
        assertThat(Arrays.stream(OrderItemSnapshot.class.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(jakarta.persistence.Column.class))
                        .map(field -> field.getAnnotation(jakarta.persistence.Column.class))
                        .map(jakarta.persistence.Column::updatable))
                .containsOnly(false);
    }
}
