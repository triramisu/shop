package com.shop.inventory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.shop.inventory.internal.controller.InventoryAdministrationController;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class InventoryLayerArchitectureTests {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importClasses() {
        applicationClasses = new ClassFileImporter().importPackagesOf(ShopApplication.class);
    }

    @Test
    void inventoryOnlyUsesThePublishedCatalogContract() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.inventory.internal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.shop.catalog.internal..")
                .check(applicationClasses);
    }

    @Test
    void inventoryEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.inventory.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .hasSize(3)
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("ton_kho_");
                });
    }

    @Test
    void inventoryRepositoriesDoNotExposePhysicalDeleteOperations() {
        List<Class<?>> repositories =
                List.of(StockItemRepository.class, StockMovementRepository.class, StockReservationRepository.class);

        assertThat(repositories).allSatisfy(repository -> assertThat(Arrays.stream(repository.getMethods())
                        .map(Method::getName)
                        .filter(methodName -> methodName.startsWith("delete")))
                .isEmpty());
    }

    @Test
    void inventoryControllersDoNotAccessEntitiesOrRepositoriesDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..inventory.internal..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..inventory.internal.entity..", "..inventory.internal.repository..")
                .check(applicationClasses);
    }

    @Test
    void inventoryRepositoriesAreOnlyAccessedByInventoryServices() {
        classes()
                .that()
                .resideInAPackage("..inventory.internal..repository..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage("..inventory.internal..repository..", "..inventory.internal..service..")
                .check(applicationClasses);
    }

    @Test
    void inventoryDtosDoNotExposePersistenceEntities() {
        noClasses()
                .that()
                .resideInAPackage("..inventory.internal..dto..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(applicationClasses);
    }

    @Test
    void publicReservationContractDoesNotExposeInventoryInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.inventory.reservation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.shop.inventory.internal..")
                .check(applicationClasses);
    }

    @Test
    void publicInventoryEventsDoNotExposeInventoryInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.inventory.event..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.shop.inventory.internal..")
                .check(applicationClasses);
    }

    @Test
    void inventoryAdministrationEndpointsAuthorizeByPermission() {
        List<Method> endpoints = Arrays.stream(InventoryAdministrationController.class.getDeclaredMethods())
                .filter(InventoryLayerArchitectureTests::isEndpoint)
                .toList();

        assertThat(endpoints).isNotEmpty().allSatisfy(method -> {
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization)
                    .as("%s must declare @PreAuthorize", method.getName())
                    .isNotNull();
            assertThat(authorization.value())
                    .as("%s must use an Inventory authority", method.getName())
                    .startsWith("hasAuthority('INVENTORY_")
                    .doesNotContain("hasRole");
        });
    }

    @SafeVarargs
    private static boolean hasAnyAnnotation(Method method, Class<? extends Annotation>... annotationTypes) {
        return Arrays.stream(annotationTypes).anyMatch(method::isAnnotationPresent);
    }

    private static boolean isEndpoint(Method method) {
        return hasAnyAnnotation(
                method, GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);
    }
}
