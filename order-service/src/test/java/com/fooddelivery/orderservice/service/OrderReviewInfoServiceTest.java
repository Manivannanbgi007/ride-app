package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.orderservice.dto.response.ReviewInfoResponse;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderLine;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;

/** What review-service is told about an order. */
class OrderReviewInfoServiceTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderReviewInfoService service = new OrderReviewInfoService(repository);

    @Test
    void anOrderIsDescribedWithItsOwnerStatusAndDishes() {
        Order order = new Order();
        order.setId(5L);
        order.setCustomerId(7L);
        order.setCustomerName("Asha Rao");
        order.setRestaurantId(3L);
        order.setRestaurantName("Spice Hub");
        order.setStatus(OrderStatus.DELIVERED);
        order.setLines(List.of(
                new OrderLine(11L, "Chicken Biryani", "NON_VEG", new BigDecimal("250.00"), 2, new BigDecimal("500.00")),
                new OrderLine(12L, "Raita", "VEG", new BigDecimal("40.00"), 1, new BigDecimal("40.00"))));
        when(repository.findById(5L)).thenReturn(Optional.of(order));

        ReviewInfoResponse info = service.getReviewInfo(5L);

        assertThat(info.customerId()).isEqualTo(7L);
        assertThat(info.restaurantName()).isEqualTo("Spice Hub");
        assertThat(info.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(info.lines()).containsExactly(new ReviewInfoResponse.Line(11L, "Chicken Biryani"),
                new ReviewInfoResponse.Line(12L, "Raita"));
    }

    @Test
    void anUnknownOrderIsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReviewInfo(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
