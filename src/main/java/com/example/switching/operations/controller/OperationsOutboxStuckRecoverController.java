package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsOutboxStuckRecoverAllResponse;
import com.example.switching.operations.service.OperationsOutboxStuckRecoverService;

@RestController
@RequestMapping("/api/operations")
public class OperationsOutboxStuckRecoverController {

    private final OperationsOutboxStuckRecoverService recoverService;

    public OperationsOutboxStuckRecoverController(
            OperationsOutboxStuckRecoverService recoverService
    ) {
        this.recoverService = recoverService;
    }

    @PostMapping("/outbox-stuck/recover-all")
    public OperationsOutboxStuckRecoverAllResponse recoverAllStuckOutboxEvents(
            @RequestParam(required = false) Integer minutes,
            @RequestParam(required = false) Integer limit
    ) {
        return recoverService.recoverAllStuck(minutes, limit);
    }
}