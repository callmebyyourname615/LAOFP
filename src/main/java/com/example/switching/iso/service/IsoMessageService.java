package com.example.switching.iso.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.enums.IsoValidationStatus;
import com.example.switching.iso.mapper.Pacs008XmlBuilder;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.iso.security.IsoMessageCryptoService;
import com.example.switching.transfer.entity.TransferEntity;

@Service
public class IsoMessageService {

    private final IsoMessageRepository isoMessageRepository;
    private final Pacs008XmlBuilder pacs008XmlBuilder;
    private final IsoMessageCryptoService isoMessageCryptoService;
    private final JdbcTemplate jdbcTemplate;

    public IsoMessageService(IsoMessageRepository isoMessageRepository,
                             Pacs008XmlBuilder pacs008XmlBuilder,
                             IsoMessageCryptoService isoMessageCryptoService,
                             JdbcTemplate jdbcTemplate) {
        this.isoMessageRepository = isoMessageRepository;
        this.pacs008XmlBuilder = pacs008XmlBuilder;
        this.isoMessageCryptoService = isoMessageCryptoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IsoMessageEntity createOutboundPacs008(TransferEntity transfer) {
        String messageId = generateMessageId(transfer.getTransferRef());
        String endToEndId = generateEndToEndId(transfer.getTransferRef());
        String correlationRef = transfer.getTransferRef();

        String xml = pacs008XmlBuilder.build(
                transfer,
                messageId,
                endToEndId
        );

        IsoMessageEntity entity = new IsoMessageEntity();
        entity.setCorrelationRef(correlationRef);
        entity.setInquiryRef(transfer.getInquiryRef());
        entity.setTransferRef(transfer.getTransferRef());
        entity.setEndToEndId(endToEndId);
        entity.setMessageId(messageId);
        entity.setMessageType(IsoMessageType.PACS_008);
        entity.setDirection(IsoMessageDirection.OUTBOUND);
        entity.setPlainPayload(xml);
        entity.setEncryptedPayload(null);
        entity.setSecurityStatus(IsoSecurityStatus.PLAIN);
        entity.setValidationStatus(IsoValidationStatus.NOT_VALIDATED);
        entity.setErrorCode(null);
        entity.setErrorMessage(null);

        return isoMessageRepository.save(entity);
    }

    @Transactional
    public IsoMessageEntity createEncryptedOutboundPacs008(TransferEntity transfer) {
        String messageId = generateMessageId(transfer.getTransferRef());
        String endToEndId = generateEndToEndId(transfer.getTransferRef());
        String correlationRef = transfer.getTransferRef();

        String xml = pacs008XmlBuilder.build(
                transfer,
                messageId,
                endToEndId
        );

        String encryptedPayload = isoMessageCryptoService.encrypt(xml);

        IsoMessageEntity entity = new IsoMessageEntity();
        entity.setCorrelationRef(correlationRef);
        entity.setInquiryRef(transfer.getInquiryRef());
        entity.setTransferRef(transfer.getTransferRef());
        entity.setEndToEndId(endToEndId);
        entity.setMessageId(messageId);
        entity.setMessageType(IsoMessageType.PACS_008);
        entity.setDirection(IsoMessageDirection.OUTBOUND);

        // secure default: do not keep plain XML after encryption
        entity.setPlainPayload(null);
        entity.setEncryptedPayload(encryptedPayload);

        entity.setSecurityStatus(IsoSecurityStatus.ENCRYPTED);
        entity.setValidationStatus(IsoValidationStatus.NOT_VALIDATED);
        entity.setErrorCode(null);
        entity.setErrorMessage(null);

        IsoMessageEntity saved = isoMessageRepository.save(entity);

        // encryptedPayload is transient, so persist it separately to iso_message_payloads.
        persistPayload(saved.getId(), null, encryptedPayload);

        return saved;
    }

    private void persistPayload(Long isoMessageId, String plainPayload, String encryptedPayload) {
        int sizeBytes = encryptedPayload != null
                ? encryptedPayload.getBytes(StandardCharsets.UTF_8).length
                : (plainPayload != null ? plainPayload.getBytes(StandardCharsets.UTF_8).length : 0);

        jdbcTemplate.update(
                """
                INSERT INTO iso_message_payloads
                  (iso_message_id, payload_type, plain_payload, encrypted_payload,
                   payload_size_bytes, business_date)
                VALUES (?, 'REQUEST', ?, ?, ?, ?)
                """,
                isoMessageId,
                plainPayload,
                encryptedPayload,
                sizeBytes,
                LocalDate.now());
    }

    private String generateMessageId(String transferRef) {
        return "MSG-" + transferRef;
    }

    private String generateEndToEndId(String transferRef) {
        return "E2E-" + transferRef;
    }
}
