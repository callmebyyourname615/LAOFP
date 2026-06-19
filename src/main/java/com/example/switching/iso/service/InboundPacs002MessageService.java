package com.example.switching.iso.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.iso.dto.Pacs002ParseResult;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.enums.IsoValidationStatus;
import com.example.switching.iso.repository.IsoMessageRepository;

@Service
public class InboundPacs002MessageService {

    private final IsoMessageRepository isoMessageRepository;

    public InboundPacs002MessageService(IsoMessageRepository isoMessageRepository) {
        this.isoMessageRepository = isoMessageRepository;
    }

    public IsoMessageEntity saveInboundPacs002(
            IsoMessageEntity outboundPacs008,
            Pacs002ParseResult pacs002,
            String pacs002Xml
    ) {
        if (outboundPacs008 == null) {
            throw new IllegalArgumentException("outboundPacs008 is required");
        }

        if (pacs002 == null) {
            throw new IllegalArgumentException("pacs002 parse result is required");
        }

        if (!StringUtils.hasText(pacs002Xml)) {
            throw new IllegalArgumentException("pacs002Xml is required");
        }

        IsoMessageEntity entity = new IsoMessageEntity();

        entity.setCorrelationRef(outboundPacs008.getCorrelationRef());
        entity.setInquiryRef(outboundPacs008.getInquiryRef());
        entity.setTransferRef(outboundPacs008.getTransferRef());

        entity.setEndToEndId(firstText(
                pacs002.originalEndToEndId(),
                outboundPacs008.getEndToEndId()
        ));

        entity.setMessageId(firstText(
                pacs002.messageId(),
                "PACS002-" + outboundPacs008.getTransferRef()
        ));

        entity.setMessageType(IsoMessageType.PACS_002);
        entity.setDirection(IsoMessageDirection.INBOUND);

        entity.setPlainPayload(pacs002Xml);
        entity.setEncryptedPayload(null);

   
        entity.setSecurityStatus(IsoSecurityStatus.DECRYPTED);
        entity.setValidationStatus(IsoValidationStatus.NOT_VALIDATED);

        entity.setErrorCode(null);
        entity.setErrorMessage(null);
        entity.setCreatedAt(LocalDateTime.now());

        return isoMessageRepository.save(entity);
    }

    private String firstText(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return fallback;
    }
}