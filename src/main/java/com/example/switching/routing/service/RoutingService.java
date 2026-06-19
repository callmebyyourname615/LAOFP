package com.example.switching.routing.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.service.ParticipantService;
import com.example.switching.routing.dto.RoutingResolveResponse;
import com.example.switching.routing.dto.RoutingRuleListResponse;
import com.example.switching.routing.dto.RoutingRuleResponse;
import com.example.switching.routing.entity.RoutingRuleEntity;
import com.example.switching.routing.exception.RoutingRuleNotFoundException;
import com.example.switching.routing.repository.RoutingRuleRepository;

@Service
public class RoutingService {

    private final RoutingRuleRepository routingRuleRepository;
    private final ParticipantService participantService;

    private final Map<String, RoutingResolveResponse> routeCache = new ConcurrentHashMap<>();

    public RoutingService(
            RoutingRuleRepository routingRuleRepository,
            ParticipantService participantService
    ) {
        this.routingRuleRepository = routingRuleRepository;
        this.participantService = participantService;
    }

    @Transactional(readOnly = true)
    public RoutingRuleListResponse list() {
        List<RoutingRuleResponse> items = routingRuleRepository.findAllOrdered()
                .stream()
                .map(RoutingRuleResponse::from)
                .collect(Collectors.toList());

        return new RoutingRuleListResponse(items);
    }

    @Transactional(readOnly = true)
    public RoutingResolveResponse resolve(
            String sourceBank,
            String destinationBank,
            String messageType
    ) {
        String normalizedSourceBank = normalizeBank(sourceBank);
        String normalizedDestinationBank = normalizeBank(destinationBank);
        IsoMessageType parsedMessageType = parseMessageType(messageType);

        String cacheKey = cacheKey(
                normalizedSourceBank,
                normalizedDestinationBank,
                parsedMessageType
        );

        RoutingResolveResponse cached = routeCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        RoutingResolveResponse resolved = resolveFromDatabase(
                normalizedSourceBank,
                normalizedDestinationBank,
                parsedMessageType
        );

        routeCache.put(cacheKey, resolved);

        return resolved;
    }

    public void clearCache() {
        routeCache.clear();
    }

    public int cacheSize() {
        return routeCache.size();
    }

    private RoutingResolveResponse resolveFromDatabase(
            String normalizedSourceBank,
            String normalizedDestinationBank,
            IsoMessageType parsedMessageType
    ) {
        ParticipantEntity sourceParticipant =
                participantService.requireActive(normalizedSourceBank);

        ParticipantEntity destinationParticipant =
                participantService.requireActive(normalizedDestinationBank);

        RoutingRuleEntity route = routingRuleRepository
                .findFirstBySourceBankAndDestinationBankAndMessageTypeAndEnabledTrueOrderByPriorityAsc(
                        normalizedSourceBank,
                        normalizedDestinationBank,
                        parsedMessageType
                )
                .orElseThrow(() -> new RoutingRuleNotFoundException(
                        normalizedSourceBank,
                        normalizedDestinationBank,
                        parsedMessageType.name()
                ));

        RoutingResolveResponse response = new RoutingResolveResponse();

        response.setSourceBank(route.getSourceBank());
        response.setDestinationBank(route.getDestinationBank());
        response.setMessageType(route.getMessageType() == null ? null : route.getMessageType().name());
        response.setRouteCode(route.getRouteCode());
        response.setConnectorName(route.getConnectorName());
        response.setPriority(route.getPriority());
        response.setEnabled(route.getEnabled());
        response.setSourceParticipantStatus(
                sourceParticipant.getStatus() == null ? null : sourceParticipant.getStatus().name()
        );
        response.setDestinationParticipantStatus(
                destinationParticipant.getStatus() == null ? null : destinationParticipant.getStatus().name()
        );

        return response;
    }

    private String cacheKey(
            String sourceBank,
            String destinationBank,
            IsoMessageType messageType
    ) {
        return sourceBank + "|" + destinationBank + "|" + messageType.name();
    }

    private String normalizeBank(String bankCode) {
        if (!StringUtils.hasText(bankCode)) {
            throw new IllegalArgumentException("bankCode is required");
        }

        return bankCode.trim().toUpperCase(Locale.ROOT);
    }

    private IsoMessageType parseMessageType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("messageType is required");
        }

        return IsoMessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}