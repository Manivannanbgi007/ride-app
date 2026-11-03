package com.fooddelivery.aimcpserver.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fooddelivery.aimcpserver.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which messages are clear change requests (the agent then insists on the prepare tool) and which are not. */
class ChangeRequestGuardTest {

    @ParameterizedTest(name = "{0}: \"{1}\" -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            RESTAURANT_ADMIN | Accept order 42                              | owner_prepare_order_step
            RESTAURANT_ADMIN | please reject #42, we are closing early      | owner_prepare_order_step
            RESTAURANT_ADMIN | Start preparing 42                           | owner_prepare_order_step
            RESTAURANT_ADMIN | order 42 is ready                            | owner_prepare_order_step
            RESTAURANT_ADMIN | Can you accept order 42?                     | owner_prepare_order_step
            RESTAURANT_ADMIN | Paneer Tikka is sold out                     | owner_prepare_item_availability
            RESTAURANT_ADMIN | we ran out of garlic naan                    | owner_prepare_item_availability
            RESTAURANT_ADMIN | Garlic Naan is available again               | owner_prepare_item_availability
            RESTAURANT_ADMIN | mark the butter chicken as unavailable       | owner_prepare_item_availability
            DELIVERY_PARTNER | accept it                                    | partner_prepare_delivery_step
            DELIVERY_PARTNER | I picked up the food                         | partner_prepare_delivery_step
            DELIVERY_PARTNER | delivered                                    | partner_prepare_delivery_step
            CUSTOMER         | cancel my order 42                           | prepare_cancel_order
            PLATFORM_ADMIN   | cancel order 42, the customer called support | admin_prepare_cancel_order
            PLATFORM_ADMIN   | reassign delivery 31 to Ravi                 | admin_prepare_reassign_delivery
            PLATFORM_ADMIN   | put delivery 31 back in the queue            | admin_prepare_reassign_delivery
            """)
    void aClearChangeRequestNamesItsPrepareTool(Role role, String message, String tool) {
        assertThat(ChangeRequestGuard.expectedTool(role, message)).contains(tool);
    }

    @ParameterizedTest(name = "{0}: \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            RESTAURANT_ADMIN | How many orders did I accept today?
            RESTAURANT_ADMIN | Is order 42 ready?
            RESTAURANT_ADMIN | Which dishes are sold out?
            RESTAURANT_ADMIN | show orders waiting to be accepted
            RESTAURANT_ADMIN | accept the order
            RESTAURANT_ADMIN | What are my sales this week?
            DELIVERY_PARTNER | How many deliveries have I delivered today?
            DELIVERY_PARTNER | where do I go next
            CUSTOMER         | what can I order for dinner?
            CUSTOMER         | how do I cancel an order?
            PLATFORM_ADMIN   | cancel it
            PLATFORM_ADMIN   | What were the cancelled orders yesterday?
            """)
    void questionsAndReportsAreNotChangeRequests(Role role, String message) {
        assertThat(ChangeRequestGuard.expectedTool(role, message)).isEmpty();
    }

    @Test
    void anEmptyMessageIsNotAChangeRequest() {
        assertThat(ChangeRequestGuard.expectedTool(Role.RESTAURANT_ADMIN, " ")).isEmpty();
        assertThat(ChangeRequestGuard.expectedTool(Role.RESTAURANT_ADMIN, null)).isEmpty();
    }

    @Test
    void theOrderNumberStepAndReasonAreReadFromTheOwnersWords() {
        assertThat(change(Role.RESTAURANT_ADMIN, "Reject order 42, we ran out of chicken."))
                .isEqualTo(new ChangeRequestGuard.ExpectedChange("owner_prepare_order_step",
                        Map.of("orderId", 42L, "step", "REJECT", "reason", "we ran out of chicken"), true));
        assertThat(change(Role.RESTAURANT_ADMIN, "please reject #42 because the kitchen is closed").arguments())
                .containsEntry("reason", "the kitchen is closed");
        assertThat(change(Role.RESTAURANT_ADMIN, "Accept order 42").arguments())
                .isEqualTo(Map.of("orderId", 42L, "step", "ACCEPT"));
        assertThat(change(Role.RESTAURANT_ADMIN, "start preparing 42").arguments()).containsEntry("step", "START_PREPARING");
        assertThat(change(Role.RESTAURANT_ADMIN, "Order 43 is ready").arguments())
                .isEqualTo(Map.of("orderId", 43L, "step", "MARK_READY"));
    }

    @Test
    void aRejectionWithoutAReasonIsLeftToTheModelWhichAsksForIt() {
        ChangeRequestGuard.ExpectedChange change = change(Role.RESTAURANT_ADMIN, "reject order 42");

        assertThat(change.arguments()).doesNotContainKey("reason");
        assertThat(change.complete()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}, available={2}")
    @CsvSource(delimiter = '|', textBlock = """
            Paneer Tikka is sold out              | Paneer Tikka        | false
            the butter chicken is out of stock    | butter chicken      | false
            mark Garlic Naan as sold out          | Garlic Naan         | false
            mark the masala dosa as unavailable   | masala dosa         | false
            we ran out of chicken biryani         | chicken biryani     | false
            Paneer Tikka sold out                 | Paneer Tikka        | false
            Garlic Naan is available again        | Garlic Naan         | true
            Mango Lassi is back in stock          | Mango Lassi         | true
            """)
    void theDishAndItsAvailabilityAreReadFromTheOwnersWords(String message, String dish, boolean available) {
        ChangeRequestGuard.ExpectedChange change = change(Role.RESTAURANT_ADMIN, message);

        assertThat(change.tool()).isEqualTo("owner_prepare_item_availability");
        assertThat(change.arguments()).isEqualTo(Map.of("dish", dish, "available", available));
        assertThat(change.complete()).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            accept it                  | ACCEPT
            decline this one           | DECLINE
            I picked up the food       | PICKED_UP
            collected the order        | PICKED_UP
            delivered                  | DELIVERED
            I handed it over           | DELIVERED
            """)
    void thePartnersStepIsReadFromTheirWords(String message, String step) {
        assertThat(change(Role.DELIVERY_PARTNER, message).arguments()).isEqualTo(Map.of("step", step));
    }

    @Test
    void aCustomerCancellationNeedsTheOrderNumberToBePreparedWithoutTheModel() {
        assertThat(change(Role.CUSTOMER, "cancel my order 42"))
                .isEqualTo(new ChangeRequestGuard.ExpectedChange("prepare_cancel_order", Map.of("orderId", 42L), true));
        assertThat(change(Role.CUSTOMER, "cancel my last order").complete()).isFalse();
    }

    @Test
    void adminInterventionsAreAlwaysPreparedByTheModel() {
        assertThat(change(Role.PLATFORM_ADMIN, "cancel order 42, customer request").complete()).isFalse();
        assertThat(change(Role.PLATFORM_ADMIN, "reassign delivery 31 to Ravi").complete()).isFalse();
    }

    @Test
    void theDirectiveNamesTheToolTheDetailsAndSaysTheButtonIsTheConfirmation() {
        String directive = ChangeRequestGuard.directive(change(Role.RESTAURANT_ADMIN, "Reject order 42, no rider"));

        assertThat(directive).contains("call owner_prepare_order_step now", "orderId=42", "step=REJECT",
                "reason=no rider", "Do not ask whether they want it", "that button is the confirmation",
                "the tool tells you what to ask");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Order #43 is now marked as ready for pickup.  | true
            Order #42 has been accepted.                  | true
            I've rejected order 42.                       | true
            Done successfully!                            | true
            Order #42 is ready for you to accept.         | false
            I've prepared it: press Accept below.         | false
            """)
    void anAnswerThatCallsAPreparedChangeDoneIsRecognised(String answer, boolean claimsDone) {
        assertThat(ChangeRequestGuard.claimsDone(answer)).isEqualTo(claimsDone);
    }

    private static ChangeRequestGuard.ExpectedChange change(Role role, String message) {
        return ChangeRequestGuard.expectedChange(role, message).orElseThrow();
    }
}
