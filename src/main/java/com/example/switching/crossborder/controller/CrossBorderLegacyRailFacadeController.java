package com.example.switching.crossborder.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.crossborder.service.CrossBorderLegacyRailFacadeService;
import com.example.switching.crossborder.service.CrossBorderLegacyRailFacadeService.LegacyRailFile;

@RestController
@PreAuthorize("hasAuthority('PERM_SETTLEMENT_VIEW')")
public class CrossBorderLegacyRailFacadeController {

    private final CrossBorderLegacyRailFacadeService service;

    public CrossBorderLegacyRailFacadeController(CrossBorderLegacyRailFacadeService service) {
        this.service = service;
    }

    @GetMapping(value = "/crossborder/settlementfile", produces = "text/plain")
    public ResponseEntity<String> crossBorderSettlementFile(@RequestParam(defaultValue = "PROMPTPAY") String rail,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile(rail, "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/crossborder_report", produces = "text/csv")
    public ResponseEntity<String> crossBorderReport(@RequestParam(defaultValue = "PROMPTPAY") String rail,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile(rail, "CROSSBORDER_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/report/transactions", produces = "text/csv")
    public ResponseEntity<String> crossBorderTransactionReport(@RequestParam(defaultValue = "PROMPTPAY") String rail,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile(rail, "TRANSACTION_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/itmx/settlementfile", produces = "text/plain")
    public ResponseEntity<String> itmxSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ITMX", "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/itmx/feesettlementfile", produces = "text/plain")
    public ResponseEntity<String> itmxFeeSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ITMX", "FEE_SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/itmx/report", produces = "text/csv")
    public ResponseEntity<String> itmxReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ITMX", "REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/itmx/thaiscan_settlementfile", produces = "text/plain")
    public ResponseEntity<String> thaiScanSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("THAISCAN", "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/itmx/thaiscanlao_fee_settlementfile", produces = "text/plain")
    public ResponseEntity<String> thaiScanLaoFeeSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("THAISCAN_LAO", "FEE_SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/napas/settlementfile", produces = "text/plain")
    public ResponseEntity<String> napasSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("NAPAS", "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/napas/settlementreport", produces = "text/csv")
    public ResponseEntity<String> napasSettlementReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("NAPAS", "SETTLEMENT_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/napas/settlementbankreport", produces = "text/csv")
    public ResponseEntity<String> napasSettlementBankReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("NAPAS", "SETTLEMENT_BANK_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/upi/settlementlakfile", produces = "text/plain")
    public ResponseEntity<String> upiLakSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("UPI", "SETTLEMENT_LAK_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/upi/report", produces = "text/csv")
    public ResponseEntity<String> upiReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("UPI", "REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/upi/summary", produces = "text/csv")
    public ResponseEntity<String> upiSummary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("UPI", "SUMMARY_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/ecny/settlementfile", produces = "text/plain")
    public ResponseEntity<String> ecnySettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ECNY", "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/crossborder/ecny/settlementreport", produces = "text/csv")
    public ResponseEntity<String> ecnySettlementReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ECNY", "SETTLEMENT_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/crossborder/ecny/settlementbankreport", produces = "text/csv")
    public ResponseEntity<String> ecnySettlementBankReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("ECNY", "SETTLEMENT_BANK_REPORT", statementDate, actor(authentication)), true);
    }

    @GetMapping(value = "/lfx/settlementfile", produces = "text/plain")
    public ResponseEntity<String> lfxSettlementFile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("LFX", "SETTLEMENT_FILE", statementDate, actor(authentication)), false);
    }

    @GetMapping(value = "/lfx/report", produces = "text/csv")
    public ResponseEntity<String> lfxReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementDate, Authentication authentication) {
        return file(service.exportFile("LFX", "REPORT", statementDate, actor(authentication)), true);
    }

    private ResponseEntity<String> file(LegacyRailFile file, boolean csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, csv ? "text/csv; charset=UTF-8" : "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "CROSS_BORDER";
    }
}
