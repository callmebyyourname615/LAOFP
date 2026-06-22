package com.example.switching.rtp.controller;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.example.switching.rtp.dto.*;
import com.example.switching.rtp.service.RtpActor;
import com.example.switching.rtp.service.RtpAuthorisationService;
import com.example.switching.rtp.service.RtpRequestService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/rtp/requests")
@ConditionalOnProperty(prefix = "switching.phase-ii.rtp", name = "enabled", havingValue = "true")
public class RequestToPayController {
    private final RtpRequestService requestService;
    private final RtpAuthorisationService authorisationService;
    public RequestToPayController(RtpRequestService requestService, RtpAuthorisationService authorisationService) {
        this.requestService = requestService; this.authorisationService = authorisationService;
    }
    @PostMapping public ResponseEntity<RtpRequestResponse> create(@Valid @RequestBody CreateRtpRequest request, Authentication authentication) {
        RtpCreateResult result = requestService.create(request, RtpActor.from(authentication));
        if (!result.created()) return ResponseEntity.ok(result.response());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(result.response().id()).toUri()).body(result.response());
    }
    @GetMapping("/{id}") public ResponseEntity<RtpRequestResponse> get(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(requestService.get(id, RtpActor.from(authentication)));
    }
    @PostMapping("/{id}/authorise") public ResponseEntity<RtpAuthorisationResponse> authorise(@PathVariable UUID id,
            @Valid @RequestBody AuthoriseRtpRequest request, Authentication authentication) {
        return ResponseEntity.ok(authorisationService.authorise(id, request, RtpActor.from(authentication)));
    }
    @PostMapping("/{id}/decline") public ResponseEntity<RtpAuthorisationResponse> decline(@PathVariable UUID id,
            @Valid @RequestBody DeclineRtpRequest request, Authentication authentication) {
        return ResponseEntity.ok(authorisationService.decline(id, request, RtpActor.from(authentication)));
    }
    @PostMapping("/{id}/settlements") public ResponseEntity<RtpAuthorisationResponse> confirmSettlement(@PathVariable UUID id,
            @Valid @RequestBody ConfirmRtpSettlementRequest request, Authentication authentication) {
        return ResponseEntity.ok(authorisationService.confirmSettlement(id, request, RtpActor.from(authentication)));
    }
    @PostMapping("/{id}/cancel") public ResponseEntity<RtpRequestResponse> cancel(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelRtpRequest request, Authentication authentication) {
        return ResponseEntity.ok(requestService.cancel(id, request, RtpActor.from(authentication)));
    }
}
