package com.example.switching.iso.inquiry;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iso-inquiries")
public class IsoInquiryQueryController {

    private final IsoInquiryQueryService service;

    public IsoInquiryQueryController(IsoInquiryQueryService service) {
        this.service = service;
    }

    @GetMapping("/{inquiryRef}")
    public ResponseEntity<IsoInquiryQueryResponse> findByInquiryRef(
            @PathVariable String inquiryRef
    ) {
        return service.findByInquiryRef(inquiryRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
