package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.CatalogLookupClient;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemDetails;
import com.fooddelivery.orderservice.dto.response.CustomerTopFoodsResponse;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.FoodTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerTopFoodsServiceTest {

    // Wednesday 2026-10-07 10:00 in Asia/Kolkata (the business zone of TestProperties)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T04:30:00Z"), ZoneOffset.UTC);

    @Mock
    private OrderStatsRepository orderStatsRepository;
    @Mock
    private CatalogLookupClient catalogLookupClient;

    private CustomerTopFoodsService service() {
        return new CustomerTopFoodsService(orderStatsRepository, catalogLookupClient, TestProperties.appProperties(), CLOCK);
    }

    @Test
    void keepsTheDatabaseRankingAndAddsImageAndCategory() {
        List<FoodTotals> rows = List.of(
                row(2L, "Biryani", 3, 5, "1495.00", "299.00"), row(1L, "Kulfi", 2, 2, "178.00", "89.00"));
        when(orderStatsRepository.topFoodsOfCustomer(eq(7L), any(), eq(5))).thenReturn(rows);
        when(catalogLookupClient.findDetails(anyCollection())).thenReturn(Map.of(
                2L, new MenuItemDetails(2L, "Biryani", "http://img/2.jpg", "Mains")));

        CustomerTopFoodsResponse response = service().getTopFoods(7L, TopFoodsPeriod.MONTHLY, 5);

        assertThat(response.items()).extracting(CustomerTopFoodsResponse.Item::menuItemId).containsExactly(2L, 1L);
        CustomerTopFoodsResponse.Item first = response.items().getFirst();
        assertThat(first.quantity()).isEqualTo(5);
        assertThat(first.orders()).isEqualTo(3);
        assertThat(first.totalSpent()).isEqualByComparingTo("1495.00");
        assertThat(first.unitPrice()).isEqualByComparingTo("299.00");
        assertThat(first.imageUrl()).isEqualTo("http://img/2.jpg");
        assertThat(first.categoryName()).isEqualTo("Mains");
        // A dish catalog does not know stays in the list without image and category
        assertThat(response.items().get(1).imageUrl()).isNull();
        assertThat(response.items().get(1).categoryName()).isNull();
        verify(catalogLookupClient).findDetails(List.of(2L, 1L));
    }

    @Test
    void windowsAreThirtyDaysSevenDaysAndSinceBusinessMidnight() {
        when(orderStatsRepository.topFoodsOfCustomer(eq(7L), any(), eq(5))).thenReturn(List.of());

        CustomerTopFoodsResponse monthly = service().getTopFoods(7L, TopFoodsPeriod.MONTHLY, 5);
        CustomerTopFoodsResponse weekly = service().getTopFoods(7L, TopFoodsPeriod.WEEKLY, 5);
        CustomerTopFoodsResponse daily = service().getTopFoods(7L, TopFoodsPeriod.DAILY, 5);

        assertThat(monthly.from()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(weekly.from()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(daily.from()).isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(daily.period()).isEqualTo("DAILY");
        verify(orderStatsRepository).topFoodsOfCustomer(7L, Instant.parse("2026-09-07T04:30:00Z"), 5);
        verify(orderStatsRepository).topFoodsOfCustomer(7L, Instant.parse("2026-09-30T04:30:00Z"), 5);
        // Business midnight of 7 Oct in Kolkata (UTC+5:30) is 6 Oct 18:30 UTC
        verify(orderStatsRepository).topFoodsOfCustomer(7L, Instant.parse("2026-10-06T18:30:00Z"), 5);
    }

    @Test
    void aCatalogOutageDoesNotFailTheRequest() {
        List<FoodTotals> rows = List.of(
                row(2L, "Biryani", 1, 1, "299.00", "299.00"));
        when(orderStatsRepository.topFoodsOfCustomer(eq(7L), any(), eq(5))).thenReturn(rows);
        // What the client returns when catalog-service is down
        when(catalogLookupClient.findDetails(anyCollection())).thenReturn(Map.of());

        CustomerTopFoodsResponse response = service().getTopFoods(7L, TopFoodsPeriod.MONTHLY, 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("Biryani");
        assertThat(response.items().getFirst().imageUrl()).isNull();
    }

    private static FoodTotals row(long id, String name, long orders, long quantity, String spent, String unitPrice) {
        FoodTotals row = mock(FoodTotals.class);
        when(row.getMenuItemId()).thenReturn(id);
        when(row.getName()).thenReturn(name);
        when(row.getOrderCount()).thenReturn(orders);
        when(row.getQuantity()).thenReturn(quantity);
        when(row.getTotalSpent()).thenReturn(new BigDecimal(spent));
        when(row.getUnitPrice()).thenReturn(new BigDecimal(unitPrice));
        when(row.getLastOrderedAt()).thenReturn(Instant.parse("2026-10-01T10:00:00Z"));
        return row;
    }
}
