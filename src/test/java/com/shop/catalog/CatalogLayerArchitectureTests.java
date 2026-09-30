package com.shop.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.shop.catalog.internal.controller.CatalogAdministrationController;
import com.shop.catalog.internal.image.controller.ProductImageController;
import com.shop.catalog.internal.repository.CategoryRepository;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.catalog.internal.repository.ProductVariantRepository;
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

class CatalogLayerArchitectureTests {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importClasses() {
        applicationClasses = new ClassFileImporter().importPackagesOf(ShopApplication.class);
    }

    @Test
    void catalogDoesNotDependOnOtherBusinessModules() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.catalog.internal..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.shop.identity..", "com.shop.inventory..", "com.shop.order..", "com.shop.payment..")
                .check(applicationClasses);
    }

    @Test
    void catalogEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.catalog.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .hasSize(4)
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("san_pham_");
                });
    }

    @Test
    void catalogAggregateRepositoriesDoNotExposePhysicalDeleteOperations() {
        List<Class<?>> repositories =
                List.of(CategoryRepository.class, ProductRepository.class, ProductVariantRepository.class);

        assertThat(repositories).isNotEmpty().allSatisfy(repository -> assertThat(Arrays.stream(repository.getMethods())
                        .map(Method::getName)
                        .filter(methodName -> methodName.startsWith("delete")))
                .isEmpty());
    }

    @Test
    void catalogControllersDoNotAccessEntitiesOrRepositoriesDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..catalog.internal..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..catalog.internal.entity..", "..catalog.internal.repository..")
                .check(applicationClasses);
    }

    @Test
    void catalogRepositoriesAreOnlyAccessedByCatalogServices() {
        classes()
                .that()
                .resideInAPackage("..catalog.internal..repository..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage("..catalog.internal..repository..", "..catalog.internal..service..")
                .check(applicationClasses);
    }

    @Test
    void catalogDtosDoNotExposePersistenceEntities() {
        noClasses()
                .that()
                .resideInAPackage("..catalog.internal..dto..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(applicationClasses);
    }

    @Test
    void catalogAdministrationEndpointsAuthorizeByPermission() {
        List<Class<?>> controllers = List.of(CatalogAdministrationController.class, ProductImageController.class);

        for (Class<?> controller : controllers) {
            List<Method> endpoints = Arrays.stream(controller.getDeclaredMethods())
                    .filter(CatalogLayerArchitectureTests::isEndpoint)
                    .toList();
            assertThat(endpoints).isNotEmpty();
            assertThat(endpoints).allSatisfy(method -> assertPermissionAuthorization(controller, method));
        }
    }

    @SafeVarargs
    private static boolean hasAnyAnnotation(Method method, Class<? extends Annotation>... annotationTypes) {
        return Arrays.stream(annotationTypes).anyMatch(method::isAnnotationPresent);
    }

    private static boolean isEndpoint(Method method) {
        return hasAnyAnnotation(
                method, GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);
    }

    private static void assertPermissionAuthorization(Class<?> controller, Method method) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization)
                .as("%s.%s must declare @PreAuthorize", controller.getSimpleName(), method.getName())
                .isNotNull();
        assertThat(authorization.value())
                .as("%s.%s must use a Catalog authority", controller.getSimpleName(), method.getName())
                .startsWith("hasAuthority('CATALOG_")
                .doesNotContain("hasRole");
    }
}
