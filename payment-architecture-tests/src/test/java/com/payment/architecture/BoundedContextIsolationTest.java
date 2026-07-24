package com.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fitness functions for bounded-context isolation.
 *
 * <p>The Maven module split already prevents {@code submit} and {@code process}
 * from referencing each other at compile time. These tests are a second, explicit
 * line of defense that also documents the intent: the two contexts share nothing
 * but the Kafka JSON contract, so neither may import the other's packages.
 *
 * <p>Keeping this rule green is what guarantees the duplicated value objects
 * (PaymentId, PaymentInstrument, ...) really are independent copies and not a
 * shortcut waiting to happen.
 */
class BoundedContextIsolationTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.payment");
    }

    @Test
    void submitDoesNotDependOnProcess() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.payment.submit..")
                .should().dependOnClassesThat().resideInAPackage("com.payment.process..")
                .as("the submit context must not depend on the process context")
                .because("the only bridge between contexts is the Kafka JSON message");

        rule.check(classes);
    }

    @Test
    void processDoesNotDependOnSubmit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.payment.process..")
                .should().dependOnClassesThat().resideInAPackage("com.payment.submit..")
                .as("the process context must not depend on the submit context")
                .because("the only bridge between contexts is the Kafka JSON message");

        rule.check(classes);
    }
}
