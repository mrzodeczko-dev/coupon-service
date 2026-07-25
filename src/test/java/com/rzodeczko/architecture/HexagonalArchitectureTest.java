package com.rzodeczko.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.rzodeczko");
    }

    @Nested
    @DisplayName("Naming conventions")
    class NamingConventions {

        @Test
        @DisplayName("Controllers should be suffixed with 'Controller'")
        void controllersShouldBeSuffixedWithController() {
            classes()
                    .that().resideInAPackage("..presentation.controller..")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Controller")
                    .check(classes);
        }

        @Test
        @DisplayName("Services should be suffixed with 'Service'")
        void servicesShouldBeSuffixedWithService() {
            classes()
                    .that().resideInAPackage("..application.service..")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Service")
                    .check(classes);
        }

        @Test
        @DisplayName("Adapters should be suffixed with 'Adapter'")
        void adaptersShouldBeSuffixedWithAdapter() {
            classes()
                    .that().resideInAPackage("..infrastructure.persistence.adapter..")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Adapter")
                    .check(classes);
        }

        @Test
        @DisplayName("Entities should be suffixed with 'Entity'")
        void entitiesShouldBeSuffixedWithEntity() {
            classes()
                    .that().resideInAPackage("..infrastructure.persistence.entity..")
                    .and().areNotInterfaces()
                    .and().areTopLevelClasses()
                    .should().haveSimpleNameEndingWith("Entity")
                    .check(classes);
        }

        @Test
        @DisplayName("Mappers should be suffixed with 'Mapper'")
        void mappersShouldBeSuffixedWithMapper() {
            classes()
                    .that().resideInAPackage("..infrastructure.persistence.mapper..")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Mapper")
                    .check(classes);
        }

        @Test
        @DisplayName("DTOs should be suffixed with 'Dto'")
        void dtosShouldBeSuffixedWithDto() {
            classes()
                    .that().resideInAPackage("..presentation.dto..")
                    .should().haveSimpleNameEndingWith("Dto")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain exceptions should be suffixed with 'Exception'")
        void domainExceptionsShouldBeSuffixedWithException() {
            classes()
                    .that().resideInAPackage("..domain.exception..")
                    .should().haveSimpleNameEndingWith("Exception")
                    .check(classes);
        }

        @Test
        @DisplayName("Application exceptions should be suffixed with 'Exception'")
        void applicationExceptionsShouldBeSuffixedWithException() {
            classes()
                    .that().resideInAPackage("..application.exception..")
                    .should().haveSimpleNameEndingWith("Exception")
                    .check(classes);
        }

        @Test
        @DisplayName("Configuration classes should be suffixed with 'Config' or 'Configuration'")
        void configurationClassesShouldBeSuffixedWithConfig() {
            classes()
                    .that().resideInAPackage("..infrastructure.configuration..")
                    .and().areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .should().haveSimpleNameEndingWith("Config")
                    .orShould().haveSimpleNameEndingWith("Configuration")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Annotation rules")
    class AnnotationRules {

        @Test
        @DisplayName("Controllers should be annotated with @RestController")
        void controllersShouldBeAnnotatedWithRestController() {
            classes()
                    .that().resideInAPackage("..presentation.controller..")
                    .and().areNotInterfaces()
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .check(classes);
        }

        @Test
        @DisplayName("Exception handlers should be annotated with @RestControllerAdvice")
        void exceptionHandlersShouldBeAnnotatedWithRestControllerAdvice() {
            classes()
                    .that().resideInAPackage("..presentation.exception..")
                    .and().haveSimpleNameEndingWith("Handler")
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class)
                    .check(classes);
        }

        @Test
        @DisplayName("Persistence entities should be annotated with @Entity")
        void entitiesShouldBeAnnotatedWithEntity() {
            classes()
                    .that().resideInAPackage("..infrastructure.persistence.entity..")
                    .and().areNotInterfaces()
                    .and().areTopLevelClasses()
                    .should().beAnnotatedWith(jakarta.persistence.Entity.class)
                    .check(classes);
        }

        @Test
        @DisplayName("Configuration classes should be annotated with @Configuration")
        void configurationClassesShouldBeAnnotated() {
            classes()
                    .that().resideInAPackage("..infrastructure.configuration..")
                    .and().areNotInterfaces()
                    .and().areNotRecords()
                    .and().haveSimpleNameEndingWith("Config").or().haveSimpleNameEndingWith("Configuration")
                    .should().beAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Service purity rules")
    class ServicePurityRules {

        @Test
        @DisplayName("Application services should not use Spring annotations")
        void applicationServicesShouldNotUseSpring() {
            noClasses()
                    .that().resideInAPackage("..application.service..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .check(classes);
        }

        @Test
        @DisplayName("Application services should not use JPA")
        void applicationServicesShouldNotUseJpa() {
            noClasses()
                    .that().resideInAPackage("..application.service..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("DTO rules")
    class DtoRules {

        @Test
        @DisplayName("DTOs should be records")
        void dtosShouldBeRecords() {
            classes()
                    .that().resideInAPackage("..presentation.dto..")
                    .should().beRecords()
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Exception rules")
    class ExceptionRules {

        @Test
        @DisplayName("Domain exceptions should extend CouponDomainException")
        void domainExceptionsShouldExtendBase() {
            classes()
                    .that().resideInAPackage("..domain.exception..")
                    .should().beAssignableTo(com.rzodeczko.domain.exception.CouponDomainException.class)
                    .because("all domain exceptions should extend CouponDomainException for uniform handling")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain exceptions should extend RuntimeException")
        void domainExceptionsShouldExtendRuntimeException() {
            classes()
                    .that().resideInAPackage("..domain.exception..")
                    .should().beAssignableTo(RuntimeException.class)
                    .check(classes);
        }

        @Test
        @DisplayName("Application exceptions should extend RuntimeException")
        void applicationExceptionsShouldExtendRuntimeException() {
            classes()
                    .that().resideInAPackage("..application.exception..")
                    .should().beAssignableTo(RuntimeException.class)
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Layer dependency rules")
    class LayerDependencyRules {

        @Test
        @DisplayName("Domain must not depend on application, infrastructure or presentation")
        void domainMustNotDependOnOuterLayers() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..infrastructure..",
                            "..presentation.."
                    )
                    .because("domain must remain the innermost layer with no outward dependencies")
                    .check(classes);
        }

        @Test
        @DisplayName("Application must not depend on infrastructure or presentation")
        void applicationMustNotDependOnOuterLayers() {
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..",
                            "..presentation.."
                    )
                    .because("application defines ports; adapters and transport are outer layers")
                    .check(classes);
        }

        @Test
        @DisplayName("Presentation must not depend on infrastructure")
        void presentationMustNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("presentation should talk to application ports, not to infrastructure adapters")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain must not depend on Spring")
        void domainMustNotDependOnSpring() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain must not depend on JPA / Jakarta persistence")
        void domainMustNotDependOnJpa() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "org.hibernate.."
                    )
                    .check(classes);
        }

        @Test
        @DisplayName("Domain must not depend on Jackson / Swagger / Web APIs")
        void domainMustNotDependOnWebOrSerialization() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.fasterxml.jackson..",
                            "io.swagger..",
                            "jakarta.servlet..",
                            "jakarta.validation.."
                    )
                    .check(classes);
        }

        @Test
        @DisplayName("Layered architecture: presentation → application → domain, infrastructure implements application ports")
        void layeredArchitecture() {
            Architectures.layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Infrastructure").definedBy("..infrastructure..")
                    .layer("Presentation").definedBy("..presentation..")

                    .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Package placement rules")
    class PackagePlacementRules {

        @Test
        @DisplayName("JPA entities must live only in infrastructure.persistence.entity")
        void jpaEntitiesOnlyInPersistenceEntityPackage() {
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..infrastructure.persistence.entity..")
                    .check(classes);
        }

        @Test
        @DisplayName("Spring stereotypes may not appear in application or domain")
        void springStereotypesOnlyOutsideCore() {
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..application..")
                    .should().beAnnotatedWith(org.springframework.stereotype.Component.class)
                    .orShould().beAnnotatedWith(org.springframework.stereotype.Service.class)
                    .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class)
                    .orShould().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Port rules")
    class PortRules {

        @Test
        @DisplayName("Use case interfaces should be suffixed with 'UseCase'")
        void useCaseInterfacesShouldBeSuffixedWithUseCase() {
            classes()
                    .that().resideInAPackage("..application.port.input..")
                    .and().areInterfaces()
                    .should().haveSimpleNameEndingWith("UseCase")
                    .check(classes);
        }

        @Test
        @DisplayName("Output ports should be interfaces")
        void outputPortsShouldBeInterfaces() {
            classes()
                    .that().resideInAPackage("..application.port.output..")
                    .should().beInterfaces()
                    .check(classes);
        }
    }
}
