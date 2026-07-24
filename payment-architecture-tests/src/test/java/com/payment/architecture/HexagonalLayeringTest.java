package com.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fitness functions for the hexagonal (Ports &amp; Adapters) layering.
 *
 * <p>These rules apply identically inside every bounded context: the domain must
 * stay free of framework and infrastructure code, and the dependency direction
 * must always point inward (adapter &rarr; application &rarr; domain, never the reverse).
 */
class HexagonalLayeringTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.payment");
    }

    @Test
    void domainDependsOnNothingInfrastructural() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.apache.kafka..",
                        "org.hibernate..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..")
                .as("the domain must not depend on adapters, application services, "
                        + "or any framework/infrastructure library")
                .because("the hexagon core stays pure: delete every adapter and it still compiles");

        rule.check(classes);
    }

    @Test
    void applicationDependsOnlyOnDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .as("application services must not depend on adapters")
                .because("use cases are agnostic to how they were triggered (REST, Kafka, ...)");

        rule.check(classes);
    }

    @Test
    void adaptersNeverDependOnEachOther() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.rest..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.kafka..", "..adapter.persistence..")
                .as("the REST adapter must not reach into the Kafka or persistence adapters")
                .because("adapters communicate through ports, not directly with one another");

        rule.check(classes);
    }

    @Test
    void jpaEntitiesStayInsideThePersistenceAdapter() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .should().resideInAPackage("..adapter.persistence.model..")
                .as("@Entity classes must live only in the persistence model package")
                .because("the JPA entity is an infrastructure detail and must never leak into the domain");

        rule.check(classes);
    }

    @Test
    void portsAreInterfaces() {
        /*
         * A port is a contract: the UseCase/Repository/Publisher/Consumer types.
         * Records that happen to live next to a port (e.g. the event DTO carried by
         * an outbound port) are data, not contracts, so they are excluded.
    	*/
        ArchRule rule = classes()
                .that().resideInAPackage("..port..")
                .and().areNotRecords()
                .should().beInterfaces()
                .as("port contracts must be interfaces")
                .because("ports are contracts; their implementations are adapters or application services");

        rule.check(classes);
    }
}
