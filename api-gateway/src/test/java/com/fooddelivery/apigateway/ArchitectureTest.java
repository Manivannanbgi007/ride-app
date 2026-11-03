package com.fooddelivery.apigateway;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The general coding rules of the requirements as tests (the gateway has no controllers, services or database:
 * the layering rules of the other services do not apply).
 */
@AnalyzeClasses(packages = "com.fooddelivery.apigateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("log instead of printing to the console (rule 7)");

    @ArchTest
    static final ArchRule constructorInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection makes dependencies explicit and the class testable");

    @ArchTest
    static final ArchRule slf4jOnly = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
