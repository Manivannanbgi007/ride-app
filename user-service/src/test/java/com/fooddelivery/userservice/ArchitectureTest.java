package com.fooddelivery.userservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.springframework.data.repository.Repository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * The requirements' "Production coding rules" and "Recommended dependency direction" as tests: a change that breaks
 * one fails the build. Every service has the same rules (main code only, tests are not checked).
 */
@AnalyzeClasses(packages = "com.fooddelivery.userservice", importOptions = ImportOption.DoNotIncludeTests.class)
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

    @ArchTest
    static final ArchRule noEntitiesInTheApi = noClasses()
            .that().resideInAnyPackage("..controller..", "..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("the REST API returns and accepts DTOs, never JPA entities (rules 3 and 14)");
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

    @ArchTest
    static final ArchRule repositoriesDoNotReachUp = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..client..", "..kafka..")
            .because("no repository -> controller (recommended dependency direction)");

    @ArchTest
    static final ArchRule entitiesDoNotReachOut = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..repository..", "..client..", "..kafka..", "..dto..")
            .because("no entity -> REST client: entities hold state and rules only (recommended dependency direction)");

    @ArchTest
    static final ArchRule relationshipsAreLazy = fields()
            .that().areAnnotatedWith(ManyToOne.class).or().areAnnotatedWith(OneToOne.class)
            .or().areAnnotatedWith(OneToMany.class).or().areAnnotatedWith(ManyToMany.class)
            .or().areAnnotatedWith(ElementCollection.class)
            .should(new ArchCondition<JavaField>("load lazily") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    FetchType fetch = fetchTypeOf(field);
                    if (fetch != FetchType.LAZY) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " is fetched " + fetch
                                + ": declare fetch = FetchType.LAZY"));
                    }
                }
            })
            .allowEmptyShould(true)
            .because("no EAGER relationships by default (rule 11)");

    @ArchTest
    static final ArchRule entitiesInEntityPackage = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule repositoriesInRepositoryPackage = classes()
            .that().areAssignableTo(Repository.class)
            .should().resideInAPackage("..repository..");

    /** The fetch type a relationship annotation declares (ManyToOne and OneToOne default to EAGER). */
    private static FetchType fetchTypeOf(JavaField field) {
        if (field.isAnnotatedWith(ManyToOne.class)) {
            return field.getAnnotationOfType(ManyToOne.class).fetch();
        }
        if (field.isAnnotatedWith(OneToOne.class)) {
            return field.getAnnotationOfType(OneToOne.class).fetch();
        }
        if (field.isAnnotatedWith(OneToMany.class)) {
            return field.getAnnotationOfType(OneToMany.class).fetch();
        }
        if (field.isAnnotatedWith(ManyToMany.class)) {
            return field.getAnnotationOfType(ManyToMany.class).fetch();
        }
        return field.getAnnotationOfType(ElementCollection.class).fetch();
    }
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
