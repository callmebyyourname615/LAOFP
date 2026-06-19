package com.example.switching.settlement.controller;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.settlement.dto.RtgsCallbackRequest;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.service.RtgsGatewayService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("${switching.api.v1-prefix}/settlement")
public class RtgsCallbackController {

    private final RtgsGatewayService rtgsGatewayService;
    private final Set<String> callbackIpWhitelist;

    public RtgsCallbackController(
            RtgsGatewayService rtgsGatewayService,
            @Value("${switching.settlement.rtgs-callback-ip-whitelist}") String callbackIpWhitelist) {
        this.rtgsGatewayService = rtgsGatewayService;
        this.callbackIpWhitelist = Arrays.stream(callbackIpWhitelist.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
    }

    @PostMapping("/rtgs-callback")
    public ResponseEntity<RtgsCallbackResponse> callback(
            @RequestBody RtgsCallbackRequest request,
            HttpServletRequest httpRequest) {
        String sourceIp = clientIp(httpRequest);
        if (!callbackIpWhitelist.contains(sourceIp)) {
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
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record RtgsCallbackResponse(
            String instructionRef,
            String rtgsMsgId,
            String status,
            java.time.LocalDateTime confirmedAt,
            String lastError) {}
}
