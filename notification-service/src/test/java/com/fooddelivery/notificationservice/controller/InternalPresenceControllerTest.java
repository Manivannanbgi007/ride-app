package com.fooddelivery.notificationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fooddelivery.notificationservice.TestTokens;
import com.fooddelivery.notificationservice.config.SecurityConfig;
import com.fooddelivery.notificationservice.web.LiveSessions;
import com.fooddelivery.notificationservice.web.SecurityProblemHandlers;
import com.fooddelivery.notificationservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: the internal presence endpoint and its input checks. */
@WebMvcTest(InternalPresenceController.class)
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class InternalPresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveSessions sessions;

    @Test
    void answersWhichOfTheUsersAreConnected() throws Exception {
        when(sessions.onlineAmong(Set.of(1L, 2L, 3L))).thenReturn(new java.util.TreeSet<>(Set.of(1L, 3L)));

        mockMvc.perform(get("/api/v1/internal/presence").with(TestTokens.serviceToken()).param("userIds", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online[0]").value(1))
                .andExpect(jsonPath("$.online[1]").value(3))
                .andExpect(jsonPath("$.online.length()").value(2));
    }

    @Test
    void nobodyConnectedIsAnEmptyList() throws Exception {
        when(sessions.onlineAmong(any())).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/internal/presence").with(TestTokens.serviceToken()).param("userIds", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online.length()").value(0));
    }

    @Test
    void badInputIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/internal/presence").with(TestTokens.serviceToken())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/internal/presence")
                        .with(TestTokens.serviceToken())
                        .param("userIds", ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/internal/presence")
                        .with(TestTokens.serviceToken())
                        .param("userIds", "1,abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/internal/presence")
                        .with(TestTokens.serviceToken())
                        .param("userIds", "1,,2"))
                .andExpect(status().isBadRequest());
        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/v1/internal/presence")
                        .with(TestTokens.serviceToken())
                        .param("userIds", tooMany))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneHundredIdsAreAllowed() throws Exception {
        when(sessions.onlineAmong(any())).thenReturn(Set.of());
        String hundred = LongStream.rangeClosed(1, 100).mapToObj(Long::toString).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/v1/internal/presence")
                        .with(TestTokens.serviceToken())
                        .param("userIds", hundred))
                .andExpect(status().isOk());
    }
}
