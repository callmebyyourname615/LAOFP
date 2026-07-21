package com.example.switching.settlement.controller;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.settlement.dto.RtgsCallbackRequest;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.service.RtgsGatewayService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("${switching.api.v1-prefix}/settlement")
@ConditionalOnProperty(
        prefix = "switching.settlement.rtgs-callback",
        name = "enabled",
        havingValue = "true")
public class RtgsCallbackController {

    private final RtgsGatewayService rtgsGatewayService;
    private final Set<String> callbackIpWhitelist;
    private final String callbackToken;

    public RtgsCallbackController(
            RtgsGatewayService rtgsGatewayService,
            @Value("${switching.settlement.rtgs-callback-ip-whitelist}") String callbackIpWhitelist,
            @Value("${switching.settlement.rtgs-callback.authentication-token:}") String callbackToken) {
        this.rtgsGatewayService = rtgsGatewayService;
        this.callbackToken = callbackToken;
        this.callbackIpWhitelist = Arrays.stream(callbackIpWhitelist.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
    }

    @PostMapping("/rtgs-callback")
    public ResponseEntity<RtgsCallbackResponse> callback(
            @RequestBody RtgsCallbackRequest request,
            @RequestHeader(value = "X-RTGS-Callback-Token", required = false) String suppliedToken,
            HttpServletRequest httpRequest) {
        String sourceIp = clientIp(httpRequest);
        if (!callbackIpWhitelist.contains(sourceIp) || !validCallbackToken(suppliedToken)) {
            return ResponseEntity.status(403).build();
        }

        SettlementInstructionEntity instruction =
                rtgsGatewayService.applyRtgsCallback(request, sourceIp);
        return ResponseEntity.ok(new RtgsCallbackResponse(
                instruction.getInstructionRef(),
                instruction.getRtgsMsgId(),
                instruction.getStatus(),
                instruction.getConfirmedAt(),
                instruction.getLastError()));
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private boolean validCallbackToken(String suppliedToken) {
        if (callbackToken == null || callbackToken.length() < 32 || suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public record RtgsCallbackResponse(
            String instructionRef,
            String rtgsMsgId,
            String status,
            java.time.LocalDateTime confirmedAt,
            String lastError) {}
}
