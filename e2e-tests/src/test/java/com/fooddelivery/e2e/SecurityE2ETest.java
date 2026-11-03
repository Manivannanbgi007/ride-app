package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;

/**
 * Sign-in sessions through the API Gateway (phase 18): a sign-in returns a short-lived access token and sets the
 * area's HttpOnly refresh cookie; a refresh renews both (the old cookie is then used up); signing out ends the
 * session. Each area has its own cookie, and an account of another kind gets no session at all.
 */
class SecurityE2ETest extends E2ETestBase {

    private static final String CUSTOMER_COOKIE = "fd_refresh_customer";

    @Test
    void aSessionIsRenewedWithItsCookieAndEndsWhenTheCustomerSignsOut() {
        String email = "session-" + Platform.unique() + "@example.com";
        Platform.registerCustomer("Session Tester", email);

        Response login = Api.anonymous().post("/api/v1/auth/login",
                Map.of("email", email, "password", Platform.CUSTOMER_PASSWORD, "role", Actor.CUSTOMER)).expect(200);
        assertThat(login.text("/tokenType")).isEqualTo("Bearer");
        assertThat(login.number("/expiresIn")).isEqualTo(900);
        assertThat(login.text("/accessToken").split("\\.")).as("a signed JWT: header.claims.signature").hasSize(3);
        assertThat(login.header("Cache-Control")).contains("no-store");
        String setCookie = setCookie(login);
        assertThat(setCookie).startsWith(CUSTOMER_COOKIE + "=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        String cookie = cookieOf(setCookie);

        Response refreshed = refresh(cookie).expect(200);
        assertThat(refreshed.number("/userId")).isEqualTo(login.number("/userId"));
        assertThat(refreshed.text("/accessToken")).isNotEqualTo(login.text("/accessToken"));
        String renewedCookie = cookieOf(setCookie(refreshed));
        assertThat(renewedCookie).isNotEqualTo(cookie);

        // The used cookie again a moment later: another tab renewing at the same time, told to try again
        assertThat(refresh(cookie).status()).isEqualTo(409);

        Api.anonymous().header("Cookie", renewedCookie)
                .post("/api/v1/auth/logout", Map.of("role", Actor.CUSTOMER)).expect(204);
        Response afterSignOut = refresh(renewedCookie);
        assertThat(afterSignOut.status()).isEqualTo(401);
        assertThat(setCookie(afterSignOut)).as("the useless cookie is removed").contains("Max-Age=0");
    }

    @Test
    void anAccountOfAnotherKindGetsNoSession() {
        String unique = Platform.unique();
        Platform.registerOwner(unique);

        Response refused = Api.anonymous().post("/api/v1/auth/login", Map.of("email", "owner-" + unique + "@example.com",
                "password", Platform.OWNER_PASSWORD, "role", Actor.CUSTOMER));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.text("/code")).isEqualTo("WRONG_ACCOUNT_TYPE");
        assertThat(refused.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void theApiCallsOfASignedInPersonCarryTheirToken() {
        Actor customer = Platform.registerCustomer("Token Tester", "token-" + Platform.unique() + "@example.com");

        // Not checked by the services yet (phase 18 continues), but sent and accepted with every call
        Response me = Api.as(customer).get("/api/v1/users/me").expect(200);
        assertThat(me.number("/id")).isEqualTo(customer.userId());
    }

    @Test
    void theGatewayNoLongerBelievesIdentityHeaders() {
        // Without a token, claiming to be the administrator gets nowhere
        Response spoofed = Api.anonymous().header("X-User-Id", "1").header("X-User-Role", Actor.PLATFORM_ADMIN)
                .get("/api/v1/admin/dashboard");
        assertThat(spoofed.status()).isEqualTo(401);

        // With a customer's token, the token says who it is, whatever the headers claim
        Actor customer = Platform.registerCustomer("Header Tester", "headers-" + Platform.unique() + "@example.com");
        Response me = Api.as(customer).header("X-User-Id", "1").header("X-User-Role", Actor.PLATFORM_ADMIN)
                .get("/api/v1/users/me").expect(200);
        assertThat(me.number("/id")).isEqualTo(customer.userId());
        assertThat(Api.as(customer).header("X-User-Role", Actor.PLATFORM_ADMIN).get("/api/v1/admin/dashboard").status())
                .isEqualTo(403);
    }

    private static Response refresh(String cookie) {
        return Api.anonymous().header("Cookie", cookie).post("/api/v1/auth/refresh", Map.of("role", Actor.CUSTOMER));
    }

    private static String setCookie(Response response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies).as("one Set-Cookie header").hasSize(1);
        return cookies.getFirst();
    }

    /** "name=value" of a Set-Cookie header, as a browser sends it back. */
    private static String cookieOf(String setCookie) {
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
