package com.example.switching.liquidity.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.switching.liquidity.dto.NetPositionsResponse;
import com.example.switching.liquidity.dto.PoolBalance;
import com.example.switching.liquidity.dto.PoolHistoryResponse;
import com.example.switching.liquidity.dto.PoolTopUpRequest;
import com.example.switching.liquidity.dto.PoolTopUpResponse;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementNetPositionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix}/settlement")
public class LiquidityController {

    private final PoolService poolService;
    private final SettlementCycleService cycleService;
    private final SettlementNetPositionService netPositionService;

    public LiquidityController(PoolService poolService,
                                SettlementCycleService cycleService,
                                SettlementNetPositionService netPositionService) {
        this.poolService = poolService;
        this.cycleService = cycleService;
        this.netPositionService = netPositionService;
    }

    @GetMapping("/balance")
    public ResponseEntity<PoolBalance> balance(
            @RequestParam(required = false) String pspId,
            @RequestHeader(name = "X-Override-Psp", required = false) String overridePsp,
            Authentication auth) {
        return ResponseEntity.ok(poolService.getAvailableBalance(resolvePspId(pspId, overridePsp, auth)));
    }

    @PostMapping("/liquidity/topup")
    public ResponseEntity<PoolTopUpResponse> topUp(
            @Valid @RequestBody PoolTopUpRequest request,
            @RequestParam(required = false) String pspId,
            @RequestHeader(name = "X-Override-Psp", required = false) String overridePsp,
            Authentication auth) {
        var result = poolService.topUp(
                resolvePspId(pspId, overridePsp, auth),
                request.getAmount(),
                request.getReference(),
                auth == null ? "SYSTEM" : auth.getName());
        return ResponseEntity.ok(new PoolTopUpResponse(
                result.topUpId(),
                result.reference(),
                result.status(),
                result.balance()));
    }

    @GetMapping("/pool-history")
    public ResponseEntity<PoolHistoryResponse> history(
            @RequestParam(required = false) String pspId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(name = "X-Override-Psp", required = false) String overridePsp,
            Authentication auth) {
        String resolvedPspId = resolvePspId(pspId, overridePsp, auth);
        return ResponseEntity.ok(new PoolHistoryResponse(
                resolvedPspId,
                poolService.history(resolvedPspId, limit)));
    }

    /**
     * Returns multilateral net positions for a settlement cycle.
     *
     * <p>If {@code cycleRef} is omitted, the most-recently-opened OPEN cycle is used.
     * If no OPEN cycle exists, a 404 is returned.
     *
     * <p>Accessible by OPS and ADMIN only (BoL operations staff).
     */
    @GetMapping("/positions")
    public ResponseEntity<?> positions(
            @RequestParam(required = false) String cycleRef) {

        // ── 1. Resolve cycleRef (auto-select latest OPEN if omitted) ──────────
        String resolvedRef = cycleRef;
        if (resolvedRef == null || resolvedRef.isBlank()) {
            List<SettlementCycleEntity> open = cycleService.listByStatus("OPEN");
            if (open.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            resolvedRef = open.get(0).getCycleRef();
        }

        // ── 2. Load cycle — 404 if the explicit ref is unknown ────────────────
        SettlementCycleEntity cycle;
        try {
            cycle = cycleService.requireCycle(resolvedRef);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        // ── 3. Map positions → DTO ────────────────────────────────────────────
        List<NetPositionsResponse.PositionEntry> entries =
                netPositionService.getPositions(resolvedRef).stream()
                        .map(p -> new NetPositionsResponse.PositionEntry(
                                p.getBankCode(),
                                p.getCurrency(),
                                p.getDebitAmount(),
                                p.getCreditAmount(),
                                p.getNetPosition(),
                                p.getTransactionCount(),
                                p.getStatus()))
                        .toList();

        return ResponseEntity.ok(new NetPositionsResponse(
                cycle.getCycleRef(),
                cycle.getStatus(),
                cycle.getSettlementDate(),
                entries));
    }

    private String resolvePspId(String requestedPspId, String overridePsp, Authentication auth) {
        boolean adminOrOps = hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_OPS");
        if (adminOrOps) {
            if (hasText(overridePsp)) {
                return overridePsp.trim();
            }
            if (hasText(requestedPspId)) {
                return requestedPspId.trim();
            }
        }
        if (hasRole(auth, "ROLE_BANK")) {
            String bankCode = bankCodeFromDetails(auth);
            if (hasText(bankCode)) {
                return bankCode;
            }
        }
        if (auth != null && hasText(auth.getName())) {
            return auth.getName().trim();
        }
        throw new IllegalArgumentException("pspId is required");
    }

    private String bankCodeFromDetails(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        Object bankCode = details.get("bankCode");
        return bankCode == null ? null : bankCode.toString();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority(role));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
