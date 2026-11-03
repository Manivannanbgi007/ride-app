package com.fooddelivery.paymentservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.paymentservice.TestTokens;
import com.fooddelivery.paymentservice.config.SecurityConfig;
import com.fooddelivery.paymentservice.service.AdminPaymentService;
import com.fooddelivery.paymentservice.service.PaymentService;
import com.fooddelivery.paymentservice.service.RazorpayPaymentService;
import com.fooddelivery.paymentservice.service.RazorpayWebhookService;
import com.fooddelivery.paymentservice.service.RefundService;
import com.fooddelivery.paymentservice.web.SecurityProblemHandlers;
import com.fooddelivery.paymentservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: roles, Idempotency-Key and validation. */
@WebMvcTest({CustomerPaymentController.class, AdminPaymentController.class, AdminRefundController.class,
        RazorpayPaymentController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class PaymentAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private RefundService refundService;
    @MockitoBean
    private AdminPaymentService adminPaymentService;
    @MockitoBean
    private RazorpayPaymentService razorpayPaymentService;
    @MockitoBean
    private RazorpayWebhookService razorpayWebhookService;

    @Test
    void restaurantOwnersCannotPay() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN"))
                        .header("Idempotency-Key", "pay-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 1, \"paymentMethod\": \"UPI\", \"upiId\": \"a@bank\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    @Test
    void customersCannotSeeTheAdminPaymentScreens() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/refunds").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentWithoutIdempotencyKeyIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 1, \"paymentMethod\": \"UPI\", \"upiId\": \"a@bank\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void refundNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/payments/900/refunds")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .header("Idempotency-Key", "refund-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists())
                .andExpect(jsonPath("$.errors.amount").exists());

        verifyNoInteractions(refundService);
    }

    @Test
    void onlyCustomersStartOrConfirmRazorpayPayments() throws Exception {
        mockMvc.perform(post("/api/v1/payments/razorpay/orders")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\": 1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/razorpay/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\": \"order_A1\", \"razorpayPaymentId\": \"pay_B2\", "
                                + "\"razorpaySignature\": \"abc123\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(razorpayPaymentService);
    }

    @Test
    void checkoutValuesThatAreNotRazorpayIdsAreRefused() throws Exception {
        mockMvc.perform(post("/api/v1/payments/razorpay/verify")
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\": \"42\", \"razorpayPaymentId\": \"pay_B2\", "
                                + "\"razorpaySignature\": \"not-hex!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.razorpayOrderId").exists())
                .andExpect(jsonPath("$.errors.razorpaySignature").exists());

        verifyNoInteractions(razorpayPaymentService);
    }
}
