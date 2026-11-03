package com.fooddelivery.chatservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.chatservice.exception.DependencyUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** What the two clients make of the answers (and the silence) of user-service and notification-service. */
class ClientsTest {

    private final RestClient unreachable = RestClient.builder().baseUrl("http://localhost:1").build();

    @Test
    void aUserIsReadFromTheInternalApiIgnoringTheFieldsChatDoesNotNeed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://users");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://users/api/v1/internal/users/7")).andRespond(withSuccess("""
                {"id": 7, "fullName": "Asha Rao", "email": "asha@example.com", "phone": "999",
                 "role": "CUSTOMER", "status": "ACTIVE"}
                """, MediaType.APPLICATION_JSON));

        Optional<UserInfo> user = new UserServiceClient(builder.build()).findUser(7L);

        assertThat(user).contains(new UserInfo(7L, "Asha Rao", "CUSTOMER"));
        server.verify();
    }

    @Test
    void anUnknownUserIsAnAnswerNotAFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://users");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://users/api/v1/internal/users/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(new UserServiceClient(builder.build()).findUser(9L)).isEmpty();
    }

    @Test
    void userServiceBeingDownOrFailingIsAnUnavailableDependency() {
        assertThatThrownBy(() -> new UserServiceClient(unreachable).findUser(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageStartingWith("user-service is unavailable");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://users");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://users/api/v1/internal/users/1")).andRespond(withServerError());
        assertThatThrownBy(() -> new UserServiceClient(builder.build()).findUser(1L))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void presenceIsReadInOneCallForAllIds() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://notifications");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://notifications/api/v1/internal/presence?userIds=1%2C2%2C3"))
                .andRespond(withSuccess("{\"online\": [1, 3]}", MediaType.APPLICATION_JSON));

        Optional<Set<Long>> online = new NotificationServiceClient(builder.build()).findOnline(List.of(1L, 2L, 3L));

        assertThat(online).contains(Set.of(1L, 3L));
        server.verify();
    }

    @Test
    void presenceIsUnknownNotAnErrorWhenTheServiceFailsOrIsDown() {
        assertThat(new NotificationServiceClient(unreachable).findOnline(List.of(1L))).isEmpty();

        RestClient.Builder builder = RestClient.builder().baseUrl("http://notifications");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://notifications/api/v1/internal/presence?userIds=1")).andRespond(withServerError());
        assertThat(new NotificationServiceClient(builder.build()).findOnline(List.of(1L))).isEmpty();
    }

    @Test
    void noIdsNoCall() {
        assertThat(new NotificationServiceClient(unreachable).findOnline(List.of())).contains(Set.of());
    }
}
