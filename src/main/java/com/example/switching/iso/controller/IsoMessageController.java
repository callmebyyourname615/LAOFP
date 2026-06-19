package com.example.switching.iso.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.iso.dto.IsoMessageDetailResponse;
import com.example.switching.iso.dto.IsoMessageListResponse;
import com.example.switching.iso.service.IsoMessageQueryService;

@RestController
public class IsoMessageController {

    private final IsoMessageQueryService isoMessageQueryService;

    public IsoMessageController(IsoMessageQueryService isoMessageQueryService) {
        this.isoMessageQueryService = isoMessageQueryService;
    }

    @GetMapping("/api/iso-messages")
    public IsoMessageListResponse search(
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String correlationRef,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String transferRef,
            @RequestParam(required = false) String endToEndId,
            @RequestParam(required = false) Integer limit
    ) {
        return isoMessageQueryService.search(
                messageType,
                direction,
                correlationRef,
                inquiryRef,
                transferRef,
                endToEndId,
                limit
        );
    }

 
    @GetMapping("/api/iso-messages/{messageKey}")
    public IsoMessageDetailResponse getDetail(
            @PathVariable String messageKey
    ) {
        return isoMessageQueryService.getDetail(messageKey);
    }
}