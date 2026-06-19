package com.example.switching.iso.inbound;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iso20022")
public class IsoPacs008InboundController {

    private final IsoPacs008InboundService service;

    public IsoPacs008InboundController(IsoPacs008InboundService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/pacs008",
            consumes = {
                    MediaType.APPLICATION_XML_VALUE,
                    MediaType.TEXT_XML_VALUE,
                    "application/*+xml"
            },
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receivePacs008(
            @RequestBody(required = false) String xmlBody,
            @RequestHeader(value = "X-Bank-Code", required = false) String bankCode
    ) {
        String responseXml = service.handleInboundPacs008(xmlBody, bankCode);

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(responseXml);
    }
}