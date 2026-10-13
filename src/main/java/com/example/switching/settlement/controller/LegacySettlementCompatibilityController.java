package com.example.switching.settlement.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.settlement.service.LegacySettlementCompatibilityService;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.LegacyFile;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.LegacySettlementFormat;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.RtgsJournal;

@RestController
@PreAuthorize("hasAuthority('PERM_SETTLEMENT_VIEW')")
public class LegacySettlementCompatibilityController {

    private final LegacySettlementCompatibilityService service;

    public LegacySettlementCompatibilityController(LegacySettlementCompatibilityService service) {
        this.service = service;
    }

    @GetMapping(value = "/settlementfile", produces = "text/plain")
    public ResponseEntity<String> settlementFile(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.SETTLEMENT_FILE, actor(authentication)),
                "text/plain; charset=UTF-8");
    }

    @GetMapping(value = "/report/settlement", produces = "text/csv")
    public ResponseEntity<String> settlementReport(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.SETTLEMENT_REPORT, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping(value = "/report/settlement_lapnet", produces = "text/csv")
    public ResponseEntity<String> settlementLapnetReport(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.SETTLEMENT_REPORT_LAPNET, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping(value = "/report/settlement_v2", produces = "text/csv")
    public ResponseEntity<String> settlementV2Report(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.SETTLEMENT_REPORT_V2, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping(value = "/finlink/report/settlement", produces = "text/csv")
    public ResponseEntity<String> finlinkSettlementReport(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.FINLINK_SETTLEMENT_REPORT, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping(value = "/finlink/report/settlement_v2", produces = "text/csv")
    public ResponseEntity<String> finlinkSettlementV2Report(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.FINLINK_SETTLEMENT_REPORT_V2, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping(value = "/finlink/report/lapnet_settlement", produces = "text/csv")
    public ResponseEntity<String> finlinkLapnetSettlementReport(@RequestParam String cycleRef, Authentication authentication) {
        return file(service.exportSettlementFile(cycleRef, LegacySettlementFormat.FINLINK_LAPNET_SETTLEMENT_REPORT, actor(authentication)),
                "text/csv; charset=UTF-8");
    }

    @GetMapping("/finlink/getrtgsjournal")
    public ResponseEntity<RtgsJournal> getRtgsJournal(
            @RequestParam(required = false) String instructionRef,
            @RequestParam(required = false) String rtgsMsgId,
            Authentication authentication) {
        return ResponseEntity.ok(service.exportRtgsJournal(instructionRef, rtgsMsgId, actor(authentication)));
    }

    private ResponseEntity<String> file(LegacyFile file, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "SETTLEMENT";
    }
}
