package com.shop.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ShopApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IdentityLayerArchitectureTests {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importClasses() {
        applicationClasses = new ClassFileImporter().importPackagesOf(ShopApplication.class);
    }

    @Test
    void controllersDoNotAccessPersistenceDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..identity.internal..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..identity.internal..entity..", "..identity.internal..repository..")
                .check(applicationClasses);
    }

    @Test
    void repositoriesAreOnlyAccessedByIdentityServices() {
        classes()
                .that()
                .resideInAPackage("..identity.internal..repository..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage("..identity.internal..repository..", "..identity.internal..service..")
                .check(applicationClasses);
    }

    @Test
    void securityDoesNotAccessPersistenceDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..identity.internal..security..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..identity.internal..entity..", "..identity.internal..repository..")
                .check(applicationClasses);
    }

    @Test
    void identityConfigurationClassesResideInTheConfigurationPackage() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.identity.internal"))
                        .filter(javaClass -> javaClass.getSimpleName().endsWith("Configuration")
                                || javaClass.getSimpleName().endsWith("Properties"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(javaClass -> assertThat(javaClass.getPackageName()).endsWith(".configuration"));
    }

    @Test
    void captchaComponentsResideInTheCaptchaFeaturePackage() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.identity.internal"))
                        .filter(javaClass -> javaClass.getSimpleName().contains("Captcha")
                                || javaClass.getSimpleName().equals("LoginFailure"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(javaClass ->
                        assertThat(javaClass.getPackageName()).startsWith("com.shop.identity.internal.captcha"));
    }

    @Test
    void systemAdministrationComponentsResideInTheAdministrationFeaturePackage() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.identity.internal"))
                        .filter(javaClass -> javaClass.getSimpleName().contains("Administration")
                                || javaClass.getSimpleName().contains("Admin"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(javaClass ->
                        assertThat(javaClass.getPackageName()).startsWith("com.shop.identity.internal.administration"));
    }

    @Test
    void identityEntitiesUseExplicitVietnameseModulePrefixedTableNames() {
        assertThat(applicationClasses.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("com.shop.identity.internal"))
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .toList())
                .isNotEmpty()
                .allSatisfy(javaClass -> {
                    assertThat(javaClass.isAnnotatedWith(Table.class)).isTrue();
                    assertThat(javaClass.getAnnotationOfType(Table.class).name())
                            .startsWith("xac_thuc_");
                });
    }
}
