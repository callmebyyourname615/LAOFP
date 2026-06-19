package com.example.switching.outbox.deadletter.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.outbox.deadletter.dto.DeadLetterResponse;
import com.example.switching.outbox.deadletter.entity.DeadLetterStatus;
import com.example.switching.outbox.deadletter.service.OutboxDeadLetterService;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operations/dead-letters")
public class OutboxDeadLetterController {

    private final OutboxDeadLetterService service;

    public OutboxDeadLetterController(OutboxDeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeadLetterResponse> list(@RequestParam(defaultValue = "QUARANTINED") DeadLetterStatus status,
                                         @RequestParam(defaultValue = "50") int limit) {
        return service.list(status, limit);
    }

    @PostMapping("/{id}/request-replay")
    public DeadLetterResponse requestReplay(@PathVariable Long id, Principal principal) {
        return service.requestReplay(id, principal.getName());
    }

    @PostMapping("/{id}/approve-replay")
    public DeadLetterResponse approveReplay(@PathVariable Long id, Principal principal) {
        return service.approveReplay(id, principal.getName());
    }

    @PostMapping("/{id}/execute-replay")
    public DeadLetterResponse executeReplay(@PathVariable Long id, Principal principal) {
        return service.executeReplay(id, principal.getName());
    }

    @PostMapping("/{id}/discard")
    public DeadLetterResponse discard(@PathVariable Long id, Principal principal) {
        return service.discard(id, principal.getName());
    }
}
