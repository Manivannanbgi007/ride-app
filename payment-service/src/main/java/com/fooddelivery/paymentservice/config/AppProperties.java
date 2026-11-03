package com.fooddelivery.paymentservice.config;

import java.time.Duration;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone time zone of "today" in the payment summary
 * @param orderService     order-service, asked about orders not known locally yet
 * @param refundRecovery   the job that sends refunds stuck before the provider call
 * @param payment          which payment gateway takes online payments, and its settings
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue RemoteService orderService,
                            @DefaultValue RefundRecovery refundRecovery,
                            @DefaultValue Payment payment) {

    /** Always set timeouts on remote calls, so a slow service cannot block request threads. */
    public record RemoteService(@DefaultValue("http://localhost:8084") String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("3s") Duration readTimeout) {
    }

    /**
     * @param enabled       run the job (switched off in tests that run it themselves)
     * @param checkInterval how often it looks for stuck refunds
     * @param stuckAfter    a REQUESTED refund older than this was not sent (normal ones are sent within a second)
     */
    public record RefundRecovery(@DefaultValue("true") boolean enabled,
                                 @DefaultValue("60s") Duration checkInterval,
                                 @DefaultValue("2m") Duration stuckAfter) {
    }

    /**
     * @param provider SIMULATED (built-in test gateway, the default) or RAZORPAY (Razorpay Checkout)
     * @param razorpay Razorpay's settings, used when the provider is RAZORPAY
     */
    public record Payment(@DefaultValue("SIMULATED") PaymentProvider provider,
                          @DefaultValue Razorpay razorpay) {

        public boolean razorpayEnabled() {
            return provider == PaymentProvider.RAZORPAY;
        }
    }

    /**
     * Razorpay (https://razorpay.com/docs/api/). The keys come from the environment (.env), never from the code.
     *
     * @param keyId          public key id ("rzp_test_..." in Test Mode): the browser needs it to open Checkout
     * @param keySecret      secret key: stays on the server; signs the API calls and checks payment signatures
     * @param webhookSecret  the secret entered for the webhook in the Razorpay Dashboard: checks webhook signatures
     * @param baseUrl        Razorpay's API
     * @param merchantName   the name shown at the top of Checkout
     * @param connectTimeout how long to wait for a connection to Razorpay
     * @param readTimeout    how long to wait for Razorpay's answer
     */
    public record Razorpay(String keyId,
                           String keySecret,
                           String webhookSecret,
                           @DefaultValue("https://api.razorpay.com") String baseUrl,
                           @DefaultValue("Front-App") String merchantName,
                           @DefaultValue("3s") Duration connectTimeout,
                           @DefaultValue("10s") Duration readTimeout) {

        /** Never prints the secrets (records print every field by default, and settings can end up in logs). */
        @Override
        public String toString() {
            return "Razorpay[keyId=" + keyId + ", keySecret=" + mask(keySecret) + ", webhookSecret="
                    + mask(webhookSecret) + ", baseUrl=" + baseUrl + ", merchantName=" + merchantName + "]";
        }

        private static String mask(String secret) {
            return secret == null || secret.isBlank() ? "(not set)" : "****";
        }
    }
}
