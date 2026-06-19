package com.example.switching.operations.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsTransferItemResponse;
import com.example.switching.operations.dto.OperationsTransferListResponse;
import com.example.switching.operations.service.OperationsTransferQueryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsTransferQueryController {

    private final OperationsTransferQueryService transferQueryService;

    public OperationsTransferQueryController(OperationsTransferQueryService transferQueryService) {
        this.transferQueryService = transferQueryService;
    }

    @GetMapping("/transfers")
    public OperationsTransferListResponse searchTransfers(
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String sourceBank,
            @RequestParam(required = false) String destinationBank,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String transferRef,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String routeCode,
            @RequestParam(required = false) String connectorName,
            @RequestParam(required = false) String debtorAccount,
            @RequestParam(required = false) String creditorAccount,
            @RequestParam(required = false) String externalReference,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return transferQueryService.searchTransfers(
                bankCode,
                sourceBank,
                destinationBank,
                status,
                transferRef,
                inquiryRef,
                channelId,
                routeCode,
                connectorName,
                debtorAccount,
                creditorAccount,
                externalReference,
                fromDate,
                toDate,
                limit,
                offset
        );
    }

    @GetMapping("/transfers/{transferRef}")
    public ResponseEntity<OperationsTransferItemResponse> findTransfer(
            @PathVariable String transferRef
    ) {
        return transferQueryService.findByTransferRef(transferRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
