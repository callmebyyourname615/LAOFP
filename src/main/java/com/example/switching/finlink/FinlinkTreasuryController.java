package com.example.switching.finlink;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('PERM_SETTLEMENT_VIEW')")
public class FinlinkTreasuryController {

    private final FinlinkTreasuryService service;

    public FinlinkTreasuryController(FinlinkTreasuryService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/finlink/transit_fund_move")
    public ResponseEntity<FinlinkTreasuryService.FinlinkEventResponse> transitFundMove(
            @RequestBody FinlinkTreasuryService.TransitFundMoveRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.recordTransitFundMove(request, actor(authentication)));
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/finlink/inform_LIFMIS")
    public ResponseEntity<FinlinkTreasuryService.FinlinkEventResponse> informLifmis(
            @RequestBody FinlinkTreasuryService.LifmisInformRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.recordLifmisInform(request, actor(authentication)));
    }

    @GetMapping(value = "/finlink/settlementfileeasytax", produces = "text/plain")
    public ResponseEntity<String> easyTaxSettlementFile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            Authentication authentication) {
        return file(service.exportSettlementFile("EASYTAX_SETTLEMENT", businessDate, actor(authentication)));
    }

    @GetMapping(value = "/finlink/adjustment_settlementfileeasytax", produces = "text/plain")
    public ResponseEntity<String> easyTaxAdjustmentFile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            Authentication authentication) {
        return file(service.exportSettlementFile("EASYTAX_ADJUSTMENT", businessDate, actor(authentication)));
    }

    @GetMapping(value = "/finlink/roadtax/settlementfile", produces = "text/plain")
    public ResponseEntity<String> roadTaxSettlementFile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            Authentication authentication) {
        return file(service.exportSettlementFile("ROADTAX_SETTLEMENT", businessDate, actor(authentication)));
    }

    private ResponseEntity<String> file(FinlinkTreasuryService.FinlinkFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "FINLINK";
    }
}
