package com.fooddelivery.chatservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fooddelivery.chatservice.TestTokens;
import com.fooddelivery.chatservice.config.SecurityConfig;
import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.dto.response.OpenedConversation;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.dto.response.UnreadCountResponse;
import com.fooddelivery.chatservice.service.ParticipantChatService;
import com.fooddelivery.chatservice.service.StaffChatService;
import com.fooddelivery.chatservice.web.SecurityProblemHandlers;
import com.fooddelivery.chatservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({ChatController.class, AdminChatController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class ChatAccessControlTest {

    private static final String MESSAGE = """
            {"body": "Hello", "clientMessageId": "6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11"}
            """;
    private static final List<String> PARTICIPANT_ROLES = List.of("CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER");
    private static final List<String> NOT_PARTICIPANT_ROLES = List.of("PLATFORM_ADMIN");
    private static final List<String> NOT_ADMIN_ROLES = PARTICIPANT_ROLES;

    /** One endpoint: what to call, and the body it needs. */
    private record Endpoint(HttpMethod method, String path, String body) {
    }

    private static final List<Endpoint> PARTICIPANT_API = List.of(
            new Endpoint(HttpMethod.GET, "/api/v1/chat/conversation", null),
            new Endpoint(HttpMethod.GET, "/api/v1/chat/messages", null),
            new Endpoint(HttpMethod.POST, "/api/v1/chat/messages", MESSAGE),
            new Endpoint(HttpMethod.POST, "/api/v1/chat/read", null));

    private static final List<Endpoint> ADMIN_API = List.of(
            new Endpoint(HttpMethod.GET, "/api/v1/admin/chat/conversations", null),
            new Endpoint(HttpMethod.POST, "/api/v1/admin/chat/conversations", "{\"userId\": 7}"),
            new Endpoint(HttpMethod.GET, "/api/v1/admin/chat/conversations/5", null),
            new Endpoint(HttpMethod.GET, "/api/v1/admin/chat/conversations/5/messages", null),
            new Endpoint(HttpMethod.POST, "/api/v1/admin/chat/conversations/5/messages", MESSAGE),
            new Endpoint(HttpMethod.POST, "/api/v1/admin/chat/conversations/5/read", null),
            new Endpoint(HttpMethod.PUT, "/api/v1/admin/chat/conversations/5/pin", "{\"pinned\": true}"),
            new Endpoint(HttpMethod.PUT, "/api/v1/admin/chat/conversations/5/mute", "{\"muted\": true}"),
            new Endpoint(HttpMethod.GET, "/api/v1/admin/chat/unread-count", null));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipantChatService participantChatService;
    @MockitoBean
    private StaffChatService staffChatService;

    @BeforeEach
    void setUp() {
        when(participantChatService.send(any(), any())).thenReturn(new SentMessage(null, true));
        when(participantChatService.messages(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new MessagePageResponse(List.of(), false, null));
        when(staffChatService.open(any(), any())).thenReturn(new OpenedConversation(null, true));
        when(staffChatService.send(any(), any(), any())).thenReturn(new SentMessage(null, true));
        when(staffChatService.messages(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new MessagePageResponse(List.of(), false, null));
        when(staffChatService.unreadCount()).thenReturn(new UnreadCountResponse(0));
    }

    private int call(Endpoint endpoint, String role) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path());
        if (role != null) {
            request.with(TestTokens.as("7", role));
        }
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void everyParticipantEndpointAcceptsTheThreeParticipantRoles() throws Exception {
        for (Endpoint endpoint : PARTICIPANT_API) {
            for (String role : PARTICIPANT_ROLES) {
                assertThat(call(endpoint, role)).as("%s %s as %s", endpoint.method(), endpoint.path(), role)
                        .isBetween(200, 299);
            }
        }
    }

    @Test
    void everyParticipantEndpointRefusesAPlatformAdminAndAnonymousCallers() throws Exception {
        for (Endpoint endpoint : PARTICIPANT_API) {
            for (String role : NOT_PARTICIPANT_ROLES) {
                assertThat(call(endpoint, role)).as("%s %s as %s", endpoint.method(), endpoint.path(), role).isEqualTo(403);
            }
            assertThat(call(endpoint, null)).as("%s %s anonymous", endpoint.method(), endpoint.path()).isEqualTo(401);
        }
        verifyNoInteractions(participantChatService);
    }

    @Test
    void everyAdminEndpointAcceptsAPlatformAdmin() throws Exception {
        for (Endpoint endpoint : ADMIN_API) {
            assertThat(call(endpoint, "PLATFORM_ADMIN")).as("%s %s", endpoint.method(), endpoint.path())
                    .isBetween(200, 299);
        }
    }

    @Test
    void everyAdminEndpointRefusesTheOtherRolesAndAnonymousCallers() throws Exception {
        for (Endpoint endpoint : ADMIN_API) {
            for (String role : NOT_ADMIN_ROLES) {
                assertThat(call(endpoint, role)).as("%s %s as %s", endpoint.method(), endpoint.path(), role).isEqualTo(403);
            }
            assertThat(call(endpoint, null)).as("%s %s anonymous", endpoint.method(), endpoint.path()).isEqualTo(401);
        }
        verifyNoInteractions(staffChatService);
    }

    @Test
    void aCallerWithoutAUserIdIsNotIdentified() throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages").header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(participantChatService);
    }

    @Test
    void sendingReturns201ForANewMessageAnd200ForARepeat() throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
                .andExpect(status().isCreated());

        when(participantChatService.send(any(), any())).thenReturn(new SentMessage(null, false));

        mockMvc.perform(post("/api/v1/chat/messages").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidMessageIsRejectedBeforeTheService() throws Exception {
        for (String body : new String[] {
                "{\"body\": \"   \", \"clientMessageId\": \"6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11\"}",
                "{\"body\": \"Hello\", \"clientMessageId\": \"nope\"}",
                "{\"body\": \"Hello\"}",
                "{\"body\": \"" + "x".repeat(2001) + "\", \"clientMessageId\": \"6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11\"}"}) {
            mockMvc.perform(post("/api/v1/chat/messages").with(TestTokens.as("7", "CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/admin/chat/conversations/5/messages").with(TestTokens.as("1", "PLATFORM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(participantChatService, staffChatService);
    }

    @Test
    void thePageSizesAreChecked() throws Exception {
        for (String limit : new String[] {"0", "101", "-1", "abc"}) {
            mockMvc.perform(get("/api/v1/chat/messages").param("limit", limit)
                            .with(TestTokens.as("7", "CUSTOMER")))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/v1/admin/chat/conversations/5/messages").param("limit", limit)
                            .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/chat/messages").param("limit", "100")
                        .with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false));
        for (String size : new String[] {"0", "51"}) {
            mockMvc.perform(get("/api/v1/admin/chat/conversations").param("size", size)
                            .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(staffChatService);
    }

    @Test
    void theAdminRequestsAreValidated() throws Exception {
        for (String path : new String[] {"/api/v1/admin/chat/conversations"}) {
            mockMvc.perform(post(path).with(TestTokens.as("1", "PLATFORM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/v1/admin/chat/conversations/5/pin")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/chat/conversations/5/mute")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(staffChatService);
    }
}
