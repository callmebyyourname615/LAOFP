package com.example.switching.iso.inquiry;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IsoInquiryController {

    private final IsoInquiryInboundService service;

    public IsoInquiryController(IsoInquiryInboundService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/api/iso20022/acmt023",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receiveAcmt023(
            @RequestHeader(value = "X-Bank-Code", required = false) String xBankCode,
            @RequestBody String xmlBody
    ) {
        return ResponseEntity.ok(service.handle(xBankCode, xmlBody));
    }
}