package com.fooddelivery.catalogservice.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.fooddelivery.catalogservice.config.CacheProperties;
import com.fooddelivery.catalogservice.dto.response.MenuResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MenuCacheTest {

    private static final MenuResponse MENU = new MenuResponse(10L, "Spice Hub", List.of());

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private MenuCache menuCache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        menuCache = new MenuCache(new RedisJsonCache(redis, JsonMapper.builder().build()),
                new CacheProperties(Duration.ofMinutes(10)));
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void redisDownMeansTheMenuIsReadFromMySql() {
        when(values.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThat(menuCache.menu(10L, () -> MENU)).isEqualTo(MENU);
        verify(values, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void afterAFailureRedisIsLeftAloneForAFewSeconds() {
        when(values.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("Unknown host redis"));

        menuCache.menu(10L, () -> MENU);
        menuCache.menu(10L, () -> MENU);
        menuCache.menu(11L, () -> MENU);

        // Only the first read paid for the failure; the others went straight to MySQL
        verify(values, times(1)).multiGet(anyList());
    }

    @Test
    void aMissIsLoadedOnceAndStoredUnderTheCurrentGenerations() {
        when(values.multiGet(anyList())).thenReturn(java.util.Arrays.asList("3", null));

        assertThat(menuCache.menu(10L, () -> MENU)).isEqualTo(MENU);

        verify(values).set(org.mockito.ArgumentMatchers.eq("catalog:v1:menu:10:3.0"), anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
    }

    @Test
    void aChangeIsAnnouncedOnlyWhenItsTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        menuCache.menuChanged(10L);
        verify(values, never()).increment(anyString());

        // What Spring does after a successful commit
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(values).increment("catalog:generation:menu:10");
    }
}
