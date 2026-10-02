package com.shop.payment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaymentLayerArchitectureTests {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importClasses() {
        applicationClasses = new ClassFileImporter().importPackagesOf(ShopApplication.class);
    }

    @Test
    void paymentDoesNotDependOnOtherModuleInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.shop.payment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.shop.order.internal..",
                        "com.shop.inventory.internal..",
                        "com.shop.catalog.internal..",
                        "com.shop.identity.internal..")
                .check(applicationClasses);
    }

    @Test
    void paymentEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.payment.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .hasSize(1)
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("thanh_toan_");
                });
    }

    @Test
    void publishedProviderAndEventContractsDoNotExposePersistenceTypes() {
        noClasses()
                .that()
                .resideInAnyPackage("com.shop.payment.provider..", "com.shop.payment.event..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(applicationClasses);
    }

    @Test
    void paymentRepositoryDoesNotExposeAggregateDeletion() {
        assertThat(Arrays.stream(PaymentAttemptRepository.class.getMethods())
                        .map(Method::getName)
                        .filter(methodName -> methodName.startsWith("delete")))
                .isEmpty();
    }
}
