package com.example.switching.iso.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.iso.dto.IsoMessageSecurityPolicyResponse;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;

@Service
public class IsoMessageSecurityPolicyService {

    private final IsoMessageQueryService isoMessageQueryService;

    public IsoMessageSecurityPolicyService(IsoMessageQueryService isoMessageQueryService) {
        this.isoMessageQueryService = isoMessageQueryService;
    }

    @Transactional(readOnly = true)
    public IsoMessageSecurityPolicyResponse getPolicy(String messageKey) {
        IsoMessageEntity entity = isoMessageQueryService.findEntityByIdOrMessageId(messageKey);

        IsoMessageSecurityPolicyResponse response = new IsoMessageSecurityPolicyResponse();

        response.setId(entity.getId());
        response.setMessageId(entity.getMessageId());
        response.setTransferRef(entity.getTransferRef());
        response.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        response.setDirection(entity.getDirection() == null ? null : entity.getDirection().name());
        response.setCurrentSecurityStatus(entity.getSecurityStatus() == null ? null : entity.getSecurityStatus().name());

        applyPolicy(entity, response);

        return response;
    }

    private void applyPolicy(
            IsoMessageEntity entity,
            IsoMessageSecurityPolicyResponse response
    ) {
        IsoMessageType messageType = entity.getMessageType();
        IsoMessageDirection direction = entity.getDirection();
        IsoSecurityStatus currentStatus = entity.getSecurityStatus();

        if (messageType == IsoMessageType.PACS_008 && direction == IsoMessageDirection.OUTBOUND) {
            response.setRequiredSecurityStatus(IsoSecurityStatus.ENCRYPTED.name());
            response.setEncryptedPayloadRequired(true);
            response.setPlainPayloadAllowed(false);
            response.setValidationAllowed(currentStatus == IsoSecurityStatus.DECRYPTED);
            response.setCompliant(currentStatus == IsoSecurityStatus.ENCRYPTED || currentStatus == IsoSecurityStatus.DECRYPTED);
            response.setPolicyCode("ISO-POLICY-PACS008-OUTBOUND");
            response.setPolicyMessage(
                    "Outbound PACS.008 must be encrypted before dispatch. It may become DECRYPTED only after authorized manual decrypt for validation or investigation."
            );
            return;
        }

        if (messageType == IsoMessageType.PACS_002 && direction == IsoMessageDirection.INBOUND) {
            response.setRequiredSecurityStatus(IsoSecurityStatus.DECRYPTED.name());
            response.setEncryptedPayloadRequired(false);
            response.setPlainPayloadAllowed(true);
            response.setValidationAllowed(true);
            response.setCompliant(currentStatus == IsoSecurityStatus.DECRYPTED);
            response.setPolicyCode("ISO-POLICY-PACS002-INBOUND");
            response.setPolicyMessage(
                    "Inbound PACS.002 is stored as DECRYPTED in this demo so the system can validate TxSts and show response details in trace."
            );
            return;
        }

        if (messageType == IsoMessageType.PACS_004) {
            response.setRequiredSecurityStatus(
                    direction == IsoMessageDirection.OUTBOUND
                            ? IsoSecurityStatus.ENCRYPTED.name()
                            : IsoSecurityStatus.DECRYPTED.name()
            );
            response.setEncryptedPayloadRequired(direction == IsoMessageDirection.OUTBOUND);
            response.setPlainPayloadAllowed(direction == IsoMessageDirection.INBOUND);
            response.setValidationAllowed(currentStatus == IsoSecurityStatus.DECRYPTED);
            response.setCompliant(true);
            response.setPolicyCode("ISO-POLICY-PACS004-FUTURE");
            response.setPolicyMessage("PACS.004 return message support is prepared for future implementation.");
            return;
        }

        if (messageType == IsoMessageType.PACS_028) {
            response.setRequiredSecurityStatus(
                    direction == IsoMessageDirection.OUTBOUND
                            ? IsoSecurityStatus.ENCRYPTED.name()
                            : IsoSecurityStatus.DECRYPTED.name()
            );
            response.setEncryptedPayloadRequired(direction == IsoMessageDirection.OUTBOUND);
            response.setPlainPayloadAllowed(direction == IsoMessageDirection.INBOUND);
            response.setValidationAllowed(currentStatus == IsoSecurityStatus.DECRYPTED);
            response.setCompliant(true);
            response.setPolicyCode("ISO-POLICY-PACS028-FUTURE");
            response.setPolicyMessage("PACS.028 status inquiry message support is prepared for future implementation.");
            return;
        }

        response.setRequiredSecurityStatus(null);
        response.setEncryptedPayloadRequired(false);
        response.setPlainPayloadAllowed(false);
        response.setValidationAllowed(false);
        response.setCompliant(false);
        response.setPolicyCode("ISO-POLICY-UNKNOWN");
        response.setPolicyMessage("No security policy is defined for this ISO message.");
    }
}