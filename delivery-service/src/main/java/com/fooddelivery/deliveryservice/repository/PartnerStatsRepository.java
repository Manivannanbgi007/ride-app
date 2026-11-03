package com.fooddelivery.deliveryservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.fooddelivery.deliveryservice.entity.Delivery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only statistics about deliveries and partners for the admin dashboard and the delivery report. */
public interface PartnerStatsRepository extends Repository<Delivery, Long> {

    /** Deliveries waiting for a partner right now (no one accepted them yet). */
    @Query(value = "SELECT COUNT(*) FROM deliveries WHERE status IN ('PENDING', 'ASSIGNED')", nativeQuery = true)
    long countWaitingForPartner();

    /** Partners with the most deliveries completed in a period, with their average minutes from "ready" to "delivered". */
    @Query(value = """
            SELECT partner_user_id AS partnerUserId, MAX(partner_name) AS partnerName, COUNT(*) AS delivered,
                   AVG(TIMESTAMPDIFF(SECOND, ready_at, delivered_at)) / 60 AS averageMinutes
            FROM deliveries
            WHERE status = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :before
            GROUP BY partner_user_id
            ORDER BY delivered DESC, averageMinutes ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PartnerTotals> topPartners(@Param("from") Instant from, @Param("before") Instant before,
                                    @Param("limit") int limit);

    /**
     * Partners who delivered the most of ONE restaurant's orders in a period, counted by delivery day. The name is
     * the one copied onto the deliveries, else the partner's registered name (both are stored here). Found by
     * idx_deliveries_restaurant_delivered (restaurant_id, status, delivered_at).
     */
    @Query(value = """
            SELECT d.partner_user_id AS partnerUserId, COALESCE(MAX(d.partner_name), MAX(p.full_name)) AS partnerName,
                   COUNT(*) AS delivered,
                   AVG(TIMESTAMPDIFF(SECOND, d.ready_at, d.delivered_at)) / 60 AS averageMinutes
            FROM deliveries d LEFT JOIN delivery_partners p ON p.user_id = d.partner_user_id
            WHERE d.restaurant_id = :restaurantId AND d.status = 'DELIVERED' AND d.partner_user_id IS NOT NULL
              AND d.delivered_at >= :from AND d.delivered_at < :before
            GROUP BY d.partner_user_id
            ORDER BY delivered DESC, averageMinutes ASC, d.partner_user_id
            LIMIT :limit
            """, nativeQuery = true)
    List<PartnerTotals> topPartnersOfRestaurant(@Param("restaurantId") long restaurantId,
                                                @Param("from") Instant from, @Param("before") Instant before,
                                                @Param("limit") int limit);

    /** Offers the given partners declined or let run out in a period. */
    @Query(value = """
            SELECT partner_user_id AS partnerUserId, COUNT(*) AS offersMissed
            FROM delivery_events
            WHERE event_type IN ('DECLINED', 'EXPIRED') AND created_at >= :from AND created_at < :before
              AND partner_user_id IN (:partnerIds)
            GROUP BY partner_user_id
            """, nativeQuery = true)
    List<MissedOffers> countMissedOffers(@Param("from") Instant from, @Param("before") Instant before,
                                         @Param("partnerIds") Collection<Long> partnerIds);

    interface PartnerTotals {
        Long getPartnerUserId();

        String getPartnerName();

        long getDelivered();

        BigDecimal getAverageMinutes();
    }

    interface MissedOffers {
        Long getPartnerUserId();

        long getOffersMissed();
    }
}
