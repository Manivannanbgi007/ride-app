package com.fooddelivery.searchservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * The requirements' "Production coding rules" and "Recommended dependency direction" as tests: a change that breaks
 * one fails the build. Every service has the same rules (main code only, tests are not checked).
 */
@AnalyzeClasses(packages = "com.fooddelivery.searchservice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---- Controllers stay thin (rules 1, 2, 3 and 17)

    @ArchTest
    static final ArchRule controllersDoNotAccessTheDatabase = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("controllers call services: no business logic and no database access in controllers (rules 1, 2)");

    @ArchTest
    static final ArchRule transactionsStartInServices = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should().beAnnotatedWith(Transactional.class)
            .because("transactions belong to the business boundary, the service (rule 17)");

    // ---- Dependencies flow inward: controller -> service -> repository / client / event publisher

    @ArchTest
    static final ArchRule servicesDoNotKnowControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("dependencies flow inward (recommended dependency direction)");

    @ArchTest
    static final ArchRule dtosDoNotReachDown = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..repository..")
            .because("DTOs carry data only: no DTO -> repository (recommended dependency direction)");

    // ---- Where things live

    @ArchTest
    static final ArchRule restControllersInControllerPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    // A listener is a method, or a class with @KafkaHandler methods; services that only publish have none
    @ArchTest
    static final ArchRule kafkaListenerMethodsInConsumerPackage = methods()
            .that().areAnnotatedWith(KafkaListener.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..kafka.consumer..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule kafkaListenerClassesInConsumerPackage = classes()
            .that().areAnnotatedWith(KafkaListener.class)
            .should().resideInAPackage("..kafka.consumer..")
            .allowEmptyShould(true);

    // ---- General coding rules (rule 7, constructor injection, one logging API)

    @ArchTest
    static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("log instead of printing to the console (rule 7)");

    @ArchTest
    static final ArchRule constructorInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection makes dependencies explicit and the class testable");

    @ArchTest
    static final ArchRule slf4jOnly = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
