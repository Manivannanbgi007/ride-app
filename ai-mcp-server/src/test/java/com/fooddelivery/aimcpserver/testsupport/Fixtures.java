package com.fooddelivery.aimcpserver.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.action.ActionStore;
import com.fooddelivery.aimcpserver.agent.PromptContext;
import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.draft.OrderDraftService;
import com.fooddelivery.aimcpserver.proposal.ProposalService;
import com.fooddelivery.aimcpserver.proposal.ProposalStore;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.CallerTokenVerifier;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.tool.AdminActionTools;
import com.fooddelivery.aimcpserver.tool.AdminOperationsTools;
import com.fooddelivery.aimcpserver.tool.AdminOrderTools;
import com.fooddelivery.aimcpserver.tool.AssistantTools;
import com.fooddelivery.aimcpserver.tool.CallerResolver;
import com.fooddelivery.aimcpserver.tool.CustomerAccountTools;
import com.fooddelivery.aimcpserver.tool.CustomerActionTools;
import com.fooddelivery.aimcpserver.tool.CustomerSearchTools;
import com.fooddelivery.aimcpserver.tool.OfferTools;
import com.fooddelivery.aimcpserver.tool.OrderProposalTools;
import com.fooddelivery.aimcpserver.tool.OwnerActionTools;
import com.fooddelivery.aimcpserver.tool.OwnerTools;
import com.fooddelivery.aimcpserver.tool.PartnerActionTools;
import com.fooddelivery.aimcpserver.tool.PartnerTools;
import com.fooddelivery.aimcpserver.tool.ToolCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Builds the real tool stack against a StubGateway, without a Spring context (fast MOCKED unit tests). */
public final class Fixtures {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** 2026-10-09 11:30 in Asia/Kolkata. */
    public static final Instant NOW = Instant.parse("2026-10-09T06:00:00Z");
    public static final String TOKEN = "test-service-token";

    // Their tokens are stand-ins: the tools only pass them on to the (stubbed) platform, which is where tokens are
    // checked; CallerTokenVerifier, which reads real ones, is tested with real signed tokens
    public static final CallerContext ADMIN = new CallerContext(2, Role.PLATFORM_ADMIN, "req-admin", "token-of-user-2");
    public static final CallerContext OWNER = new CallerContext(3, Role.RESTAURANT_ADMIN, "req-owner", "token-of-user-3");
    public static final CallerContext CUSTOMER = new CallerContext(1, Role.CUSTOMER, "req-customer", "token-of-user-1");
    public static final CallerContext PARTNER = new CallerContext(8, Role.DELIVERY_PARTNER, "req-partner", "token-of-user-8");

    private Fixtures() {
    }

    /** Moves when told to: for expiry tests. */
    public static final class MutableClock extends Clock {
        private volatile Instant instant;

        public MutableClock(Instant instant) {
            this.instant = instant;
        }

        public void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    public static AppProperties properties(String gatewayUrl) {
        return new AppProperties(TOKEN, ZONE,
                new AppProperties.Gateway(gatewayUrl, Duration.ofSeconds(2), Duration.ofSeconds(4)),
                new AppProperties.Ollama("http://localhost:1", "test-model", Duration.ofSeconds(120), Duration.ofSeconds(3)),
                new AppProperties.Agent(6, Duration.ofSeconds(30), Duration.ofSeconds(5), 12, 6000, 6000),
                new AppProperties.Proposal(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofHours(24),
                        Duration.ofSeconds(60)));
    }

    public static GatewayClient gatewayClient(String gatewayUrl, JsonMapper mapper) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(4));
        return new GatewayClient(RestClient.builder().baseUrl(gatewayUrl).requestFactory(factory).build(), mapper);
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZONE);
    }

    /** Everything the tests need, wired like Spring would wire it. */
    public record Stack(AppProperties properties, JsonMapper mapper, GatewayClient gateway, Clock clock,
                        ExecutorService executor, CallerResolver resolver, ProposalStore store,
                        ProposalService proposals, ToolCatalog catalog, SimpleMeterRegistry registry,
                        AdminOrderTools adminOrders, AdminOperationsTools adminOps, OwnerTools owner,
                        OfferTools offers, CustomerSearchTools search, CustomerAccountTools account,
                        OrderProposalTools orderTools, InMemoryDraftStore draftStore, OrderDraftService drafts,
                        PromptContext promptContext, PartnerTools partner, ActionStore actionStore,
                        ActionService actions, CustomerActionTools customerActions, OwnerActionTools ownerActions,
                        PartnerActionTools partnerActions, AdminActionTools adminActions) {
    }

    public static Stack stack(String gatewayUrl, Clock clock, ProposalStore store) {
        return stack(gatewayUrl, clock, store, new InMemoryActionStore());
    }

    /**
     * The tools' caller resolver of the unit tests: they put the caller into the ToolContext themselves, so a token (an
     * MCP call) is never needed here and is always refused.
     */
    public static CallerResolver callerResolver() {
        return new CallerResolver(new CallerTokenVerifier(token -> {
            throw new BadJwtException("No access tokens in unit tests");
        }));
    }

    public static Stack stack(String gatewayUrl, Clock clock, ProposalStore store, ActionStore actionStore) {
        JsonMapper mapper = JsonMapper.builder().build();
        AppProperties properties = properties(gatewayUrl);
        GatewayClient gateway = gatewayClient(gatewayUrl, mapper);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CallerResolver resolver = callerResolver();
        ProposalService proposals = new ProposalService(gateway, store, properties, clock);
        AdminOrderTools adminOrders = new AdminOrderTools(gateway, resolver, clock, mapper);
        AdminOperationsTools adminOps = new AdminOperationsTools(gateway, resolver, clock, mapper);
        OwnerTools owner = new OwnerTools(gateway, resolver, clock, mapper);
        OfferTools offers = new OfferTools(gateway, resolver, clock, mapper);
        CustomerSearchTools search = new CustomerSearchTools(gateway, resolver, clock, mapper);
        CustomerAccountTools account = new CustomerAccountTools(gateway, resolver, clock, mapper);
        InMemoryDraftStore draftStore = new InMemoryDraftStore();
        OrderDraftService drafts = new OrderDraftService(draftStore, clock);
        PromptContext promptContext = new PromptContext(gateway, drafts);
        OrderProposalTools orderTools = new OrderProposalTools(gateway, resolver, clock, mapper, proposals, drafts);
        PartnerTools partner = new PartnerTools(gateway, resolver, clock, mapper);
        ActionService actions = new ActionService(gateway, actionStore, properties, clock);
        CustomerActionTools customerActions = new CustomerActionTools(gateway, resolver, clock, mapper, actions);
        OwnerActionTools ownerActions = new OwnerActionTools(gateway, resolver, clock, mapper, actions);
        PartnerActionTools partnerActions = new PartnerActionTools(gateway, resolver, clock, mapper, actions);
        AdminActionTools adminActions = new AdminActionTools(gateway, resolver, clock, mapper, actions);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<AssistantTools> all = List.of(adminOrders, adminOps, owner, offers, search, account, orderTools, partner,
                customerActions, ownerActions, partnerActions, adminActions);
        ToolCatalog catalog = new ToolCatalog(all, properties, executor, resolver, registry);
        return new Stack(properties, mapper, gateway, clock, executor, resolver, store, proposals, catalog, registry,
                adminOrders, adminOps, owner, offers, search, account, orderTools, draftStore, drafts, promptContext,
                partner, actionStore, actions, customerActions, ownerActions, partnerActions, adminActions);
    }
}
