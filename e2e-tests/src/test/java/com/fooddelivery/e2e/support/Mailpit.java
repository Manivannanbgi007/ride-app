package com.fooddelivery.e2e.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The e-mails the platform sent, as Mailpit (the development mail server) caught them. */
public final class Mailpit {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Mailpit() {
    }

    /** Subjects of the e-mails sent to the address. */
    public static List<String> subjectsTo(String address) {
        String query = URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(E2EConfig.mailpitUrl() + "/api/v1/search?query=" + query))
                .GET().build();
        try {
            String body = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return Api.parse(body).path("messages").valueStream()
                    .map(message -> message.path("Subject").asString())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Mailpit is not reachable at " + E2EConfig.mailpitUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while asking Mailpit", e);
        }
    }
}
