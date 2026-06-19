package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsIsoMessageListResponse;
import com.example.switching.operations.service.OperationsIsoMessageQueryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsIsoMessageQueryController {

    private final OperationsIsoMessageQueryService isoMessageQueryService;

    public OperationsIsoMessageQueryController(
            OperationsIsoMessageQueryService isoMessageQueryService
    ) {
        this.isoMessageQueryService = isoMessageQueryService;
    }

    @GetMapping("/iso-messages")
    public OperationsIsoMessageListResponse searchIsoMessages(
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String sourceBank,
            @RequestParam(required = false) String destinationBank,
            @RequestParam(required = false) String transferRef,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String correlationRef,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String endToEndId,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String securityStatus,
            @RequestParam(required = false) String validationStatus,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Boolean includePayload,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return isoMessageQueryService.searchIsoMessages(
                bankCode,
                sourceBank,
                destinationBank,
                transferRef,
                inquiryRef,
                correlationRef,
                messageId,
                endToEndId,
                messageType,
                direction,
                securityStatus,
                validationStatus,
                fromDate,
                toDate,
                includePayload,
                limit,
                offset
        );
    }
}