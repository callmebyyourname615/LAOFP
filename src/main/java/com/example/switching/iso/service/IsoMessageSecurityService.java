package com.example.switching.iso.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.iso.dto.IsoMessageSecurityActionResponse;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.exception.IsoMessageInvalidStateException;
import com.example.switching.iso.exception.IsoMessageNotFoundException;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.iso.security.IsoMessageCryptoService;

@Service
public class IsoMessageSecurityService {

    private final IsoMessageRepository isoMessageRepository;
    private final IsoMessageCryptoService isoMessageCryptoService;

    public IsoMessageSecurityService(IsoMessageRepository isoMessageRepository,
                                     IsoMessageCryptoService isoMessageCryptoService) {
        this.isoMessageRepository = isoMessageRepository;
        this.isoMessageCryptoService = isoMessageCryptoService;
    }

    @Transactional
    public IsoMessageSecurityActionResponse encrypt(Long isoMessageId) {
        IsoMessageEntity message = getIsoMessageOrThrow(isoMessageId);

        IsoSecurityStatus previousStatus = message.getSecurityStatus();

        if (!StringUtils.hasText(message.getPlainPayload())) {
            throw new IsoMessageInvalidStateException(
                    "ISO message plainPayload is empty and cannot be encrypted: " + isoMessageId
            );
        }

        String encryptedPayload = isoMessageCryptoService.encrypt(message.getPlainPayload());

        message.setEncryptedPayload(encryptedPayload);
        message.setSecurityStatus(IsoSecurityStatus.ENCRYPTED);

        IsoMessageEntity saved = isoMessageRepository.save(message);

        return toResponse(
                saved,
                previousStatus,
                IsoSecurityStatus.ENCRYPTED,
                "ISO message encrypted successfully"
        );
    }

    @Transactional
    public IsoMessageSecurityActionResponse decrypt(Long isoMessageId) {
        IsoMessageEntity message = getIsoMessageOrThrow(isoMessageId);

        IsoSecurityStatus previousStatus = message.getSecurityStatus();

        if (!StringUtils.hasText(message.getEncryptedPayload())) {
            throw new IsoMessageInvalidStateException(
                    "ISO message encryptedPayload is empty and cannot be decrypted: " + isoMessageId
            );
        }

        String plainPayload = isoMessageCryptoService.decrypt(message.getEncryptedPayload());

        message.setPlainPayload(plainPayload);
        message.setSecurityStatus(IsoSecurityStatus.DECRYPTED);

        IsoMessageEntity saved = isoMessageRepository.save(message);

        return toResponse(
                saved,
                previousStatus,
                IsoSecurityStatus.DECRYPTED,
                "ISO message decrypted successfully"
        );
    }

    private IsoMessageEntity getIsoMessageOrThrow(Long isoMessageId) {
        return isoMessageRepository.findById(isoMessageId)
                .orElseThrow(() -> new IsoMessageNotFoundException(
                        "ISO message not found: " + isoMessageId
                ));
    }

    private IsoMessageSecurityActionResponse toResponse(IsoMessageEntity message,
                                                        IsoSecurityStatus previousStatus,
                                                        IsoSecurityStatus newStatus,
                                                        String responseMessage) {
        return new IsoMessageSecurityActionResponse(
                message.getId(),
                message.getTransferRef(),
                message.getMessageType() == null ? null : message.getMessageType().name(),
                message.getDirection() == null ? null : message.getDirection().name(),
                previousStatus == null ? null : previousStatus.name(),
                newStatus.name(),
                StringUtils.hasText(message.getPlainPayload()),
                StringUtils.hasText(message.getEncryptedPayload()),
                responseMessage
        );
    }
}