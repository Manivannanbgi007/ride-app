package com.fooddelivery.aimcpclient;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** The layering and security rules of the other services, as tests (main code only). */
@AnalyzeClasses(packages = "com.fooddelivery.aimcpclient", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotCallRemoteServices = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..client..")
            .because("controllers call services: no business logic and no remote calls in controllers");

    @ArchTest
    static final ArchRule servicesDoNotKnowControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("dependencies flow inward: controller -> service -> client");

    @ArchTest
    static final ArchRule dtosDoNotReachDown = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..client..")
            .because("DTOs carry data only");

    @ArchTest
    static final ArchRule identityIsAPlainValue = noClasses()
            .that().resideInAPackage("..identity..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..client..", "..web..")
            .because("the verified identity is a value object every layer may use");

    @ArchTest
    static final ArchRule onlyTheWebLayerReadsTheToken = noClasses()
            .that().resideOutsideOfPackages("..web..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security.oauth2..")
            .because("the verified token is read in one place (CallerIdentityResolver); the rest uses CallerIdentity");

    @ArchTest
    static final ArchRule onlyTheClientForwardsTheToken = noClasses()
            .that().resideOutsideOfPackages("..client..", "..identity..")
            .should().callMethod(CallerIdentity.class, "accessToken")
            .because("the token only travels on to ai-mcp-server: it is never stored, logged or shown");

    @ArchTest
    static final ArchRule restControllersInControllerPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("log instead of printing to the console");

    @ArchTest
    static final ArchRule constructorInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection makes dependencies explicit and the class testable");

    @ArchTest
    static final ArchRule slf4jOnly = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
