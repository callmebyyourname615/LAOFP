package com.example.switching.crossborder.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.crossborder.service.CrossBorderReconciliationService;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operator/crossborder/reconciliation")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.cross-border-adapters",
        name = "enabled",
        havingValue = "true")
public class CrossBorderReconciliationController {

    private final CrossBorderReconciliationService service;

    public CrossBorderReconciliationController(
            CrossBorderReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/{rail}/{statementDate}")
    public Map<String, Integer> reconcile(
            @PathVariable String rail,
            @PathVariable LocalDate statementDate,
            @RequestBody List<CrossBorderReconciliationService.StatementItem> items) {
        return service.reconcile(rail, statementDate, items);
    }
}
