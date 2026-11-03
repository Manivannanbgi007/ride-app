package com.fooddelivery.aimcpserver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.tool.AssistantTools;
import com.fooddelivery.aimcpserver.tool.ToolPolicy;
import com.fooddelivery.aimcpserver.tool.ToolResult;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.bind.annotation.RestController;

/**
 * The service's rules as tests (main code only). Besides the rules every service has, the ones that keep this service
 * safe: tools only reach the platform through the GatewayClient, every tool is guarded and role-bound, the proposal
 * (order) code cannot be reached from the model's side except through its one tool, and no tool can confirm a change.
 */
@AnalyzeClasses(packages = "com.fooddelivery.aimcpserver", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---- One gateway, no other outbound HTTP

    @ArchTest
    static final ArchRule onlyTheClientPackageMakesHttpCalls = noClasses()
            .that().resideOutsideOfPackages("..client..", "..config..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.http.HttpClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.URL")
            .because("tools and services call the platform only through GatewayClient (fixed hosts and paths)");

    // ---- Tools

    @ArchTest
    static final ArchRule toolMethodsAreGuardedAndReturnToolResults = methods()
            .that().areAnnotatedWith(Tool.class)
            .should().beAnnotatedWith(ToolPolicy.class)
            .andShould().haveRawReturnType(ToolResult.class)
            .because("every tool declares who may use it and returns a summary (plus card) the guard can handle");

    @ArchTest
    static final ArchRule toolClassesAreRegisteredAsAssistantTools = classes()
            .that().containAnyMethodsThat(DescribedPredicate.describe("are tools",
                    method -> method.isAnnotatedWith(Tool.class)))
            .should().implement(AssistantTools.class)
            .because("only AssistantTools beans are offered to the model and over MCP");

    @ArchTest
    static final ArchRule toolsDoNotKnowControllersOrTheAgent = noClasses()
            .that().resideInAPackage("..tool..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..agent..")
            .because("tools are called by the agent, not the other way round");

    @ArchTest
    static final ArchRule proposalsKnowNeitherToolsNorTheAgent = noClasses()
            .that().resideInAPackage("..proposal..")
            .should().dependOnClassesThat().resideInAnyPackage("..tool..", "..agent..", "..controller..")
            .because("the order flow is a service: prepare is reached only through prepare_order, confirm only by the button");

    @ArchTest
    static final ArchRule onlyOneToolClassUsesTheProposalService = noClasses()
            .that().resideInAPackage("..tool..").and().doNotHaveSimpleName("OrderProposalTools")
            .should().dependOnClassesThat().resideInAPackage("..proposal..")
            .because("the model can only prepare a proposal; it can never confirm one (no tool reaches ProposalService.confirm)");

    @ArchTest
    static final ArchRule agentDoesNotReachTheOrderFlow = noClasses()
            .that().resideInAPackage("..agent..")
            .should().dependOnClassesThat().resideInAPackage("..proposal..")
            .because("the model's loop has no way to place an order");

    @ArchTest
    static final ArchRule actionsKnowNeitherToolsNorTheAgent = noClasses()
            .that().resideInAPackage("..action..")
            .should().dependOnClassesThat().resideInAnyPackage("..tool..", "..agent..", "..controller..", "..proposal..")
            .because("the change flow is a service: propose is reached only through the prepare tools, confirm only by "
                    + "the button");

    @ArchTest
    static final ArchRule noToolConfirmsOrCancelsAnAction = noClasses()
            .that().resideInAPackage("..tool..")
            .should().callMethod(ActionService.class, "confirm", CallerContext.class, String.class)
            .orShould().callMethod(ActionService.class, "cancel", CallerContext.class, String.class)
            .because("the model can only prepare a change; only the user's button press (controller) makes it");

    @ArchTest
    static final ArchRule agentDoesNotReachTheActions = noClasses()
            .that().resideInAPackage("..agent..")
            .should().dependOnClassesThat().resideInAPackage("..action..")
            .because("the model's loop has no way to make a change");

    @ArchTest
    static final ArchRule securityAndSupportAreLowLevel = noClasses()
            .that().resideInAnyPackage("..security..", "..support..", "..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..agent..", "..proposal..", "..action..",
                    "..client..");

    @ArchTest
    static final ArchRule onlyTheVerifierReadsTokens = noClasses()
            .that().resideOutsideOfPackages("..security..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security.oauth2..")
            .because("the caller's token is checked in one place (CallerTokenVerifier); the rest uses CallerContext");

    @ArchTest
    static final ArchRule onlyTheGatewayClientForwardsTheToken = noClasses()
            .that().resideOutsideOfPackages("..security..", "..client..")
            .should().callMethod(CallerContext.class, "accessToken")
            .because("the token only travels on to the platform: it is never stored, logged or given to the model");

    // ---- Where things live

    @ArchTest
    static final ArchRule restControllersInControllerPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule controllersDoNotCallTheGatewayOrTools = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..tool..")
            .orShould().dependOnClassesThat().haveSimpleName("GatewayClient")
            .because("controllers are thin: they call services");

    // ---- General coding rules

    @ArchTest
    static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("log instead of printing to the console");

    @ArchTest
    static final ArchRule constructorInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection makes dependencies explicit and the class testable");

    @ArchTest
    static final ArchRule slf4jOnly = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
