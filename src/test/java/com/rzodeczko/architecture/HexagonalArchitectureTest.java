package com.rzodeczko.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
