package com.shop.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.shop.catalog.internal.repository.CategoryRepository;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.catalog.internal.repository.ProductVariantRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
                .resideInAPackage("..catalog..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..identity..", "..inventory..", "..order..", "..payment..")
                .check(applicationClasses);
    }

    @Test
    void catalogEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.catalog.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .hasSize(3)
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("san_pham_");
                });
    }

    @Test
    void catalogRepositoriesDoNotExposePhysicalDeleteOperations() {
        List<Class<?>> repositories =
                List.of(CategoryRepository.class, ProductRepository.class, ProductVariantRepository.class);

        assertThat(repositories).allSatisfy(repository -> assertThat(Arrays.stream(repository.getMethods())
                        .map(Method::getName)
                        .filter(methodName -> methodName.startsWith("delete")))
                .isEmpty());
    }
}
