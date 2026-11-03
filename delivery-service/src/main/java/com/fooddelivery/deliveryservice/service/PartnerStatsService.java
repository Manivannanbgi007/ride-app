package com.fooddelivery.deliveryservice.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.response.PartnerStatsResponse;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository.MissedOffers;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository.PartnerTotals;
import com.fooddelivery.deliveryservice.repository.PartnerStatusTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delivery partner numbers for the admin dashboard and the delivery report, counted in the database. */
@Service
@RequiredArgsConstructor
public class PartnerStatsService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final PartnerStatsRepository partnerStatsRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PartnerStatsResponse getStats(LocalDate fromDate, LocalDate toDate, int limit) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, ZoneId.of(appProperties.businessTimeZone()), clock);

        Map<PartnerStatus, Long> partnersNow = new EnumMap<>(PartnerStatus.class);
        for (PartnerStatus status : PartnerStatus.values()) {
            partnersNow.put(status, 0L);
        }
        for (PartnerStatusTotal row : deliveryPartnerRepository.countByStatus()) {
            partnersNow.put(row.getStatus(), row.getTotal());
        }

        List<PartnerTotals> top = partnerStatsRepository.topPartners(period.from(), period.before(), limit);
        Map<Long, Long> missed = top.isEmpty() ? Map.of()
                : partnerStatsRepository.countMissedOffers(period.from(), period.before(),
                        top.stream().map(PartnerTotals::getPartnerUserId).toList()).stream()
                .collect(Collectors.toMap(MissedOffers::getPartnerUserId, MissedOffers::getOffersMissed));

        List<PartnerStatsResponse.Partner> partners = top.stream()
                .map(row -> new PartnerStatsResponse.Partner(row.getPartnerUserId(), row.getPartnerName(),
                        row.getDelivered(),
                        row.getAverageMinutes() == null ? null
                                : row.getAverageMinutes().setScale(1, RoundingMode.HALF_UP).doubleValue(),
                        missed.getOrDefault(row.getPartnerUserId(), 0L)))
                .toList();
        return new PartnerStatsResponse(period.fromDate(), period.toDate(), partnersNow,
                partnersNow.get(PartnerStatus.AVAILABLE) + partnersNow.get(PartnerStatus.BUSY),
                partnerStatsRepository.countWaitingForPartner(), partners);
    }
}
