package com.example.switching.rtp.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.rtp.dto.AuthoriseRtpRequest;
import com.example.switching.rtp.dto.ConfirmRtpSettlementRequest;
import com.example.switching.rtp.dto.DeclineRtpRequest;
import com.example.switching.rtp.dto.RtpAuthorisationResponse;
import com.example.switching.rtp.entity.RtpAuthorisationEntity;
import com.example.switching.rtp.entity.RtpInstallmentScheduleEntity;
import com.example.switching.rtp.entity.RtpRequestEntity;
import com.example.switching.rtp.entity.RtpStateTransitionEntity;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import com.example.switching.rtp.enums.RtpInstallmentStatus;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.exception.RtpAccessDeniedException;
import com.example.switching.rtp.exception.RtpNotFoundException;
import com.example.switching.rtp.repository.RtpAuthorisationRepository;
import com.example.switching.rtp.repository.RtpInstallmentScheduleRepository;
import com.example.switching.rtp.repository.RtpRequestRepository;
import com.example.switching.rtp.repository.RtpStateTransitionRepository;

@Service
@ConditionalOnProperty(prefix = "switching.phase-ii.rtp", name = "enabled", havingValue = "true")
public class RtpAuthorisationService {
    private final RtpRequestRepository requests;
    private final RtpAuthorisationRepository authorisations;
    private final RtpInstallmentScheduleRepository installments;
    private final RtpStateTransitionRepository transitions;
    private final RtpStateMachine stateMachine;
    private final RtpSettlementGateway settlementGateway;
    private final RtpDomainEventPublisher events;
    private final Clock clock;

    public RtpAuthorisationService(RtpRequestRepository requests,
            RtpAuthorisationRepository authorisations,
            RtpInstallmentScheduleRepository installments,
            RtpStateTransitionRepository transitions,
            RtpStateMachine stateMachine,
            RtpSettlementGateway settlementGateway,
            RtpDomainEventPublisher events,
            @Qualifier("rtpClock") Clock clock) {
        this.requests = requests; this.authorisations = authorisations; this.installments = installments;
        this.transitions = transitions; this.stateMachine = stateMachine; this.settlementGateway = settlementGateway;
        this.events = events; this.clock = clock;
    }

    @Transactional
    public RtpAuthorisationResponse authorise(UUID requestId, AuthoriseRtpRequest command, RtpActor actor) {
        RtpRequestEntity request = lock(requestId);
        assertPayer(actor, request);
        if (request.getStatus() != RtpStatus.PENDING_AUTH) {
            throw new IllegalStateException("RTP request is not pending authorisation");
        }
        if (!request.getExpiresAt().isAfter(clock.instant())) {
            expireLocked(request, "Expired before authorisation");
            throw new IllegalStateException("RTP request has expired");
        }
        validateAmount(request, command);
        List<RtpInstallmentScheduleEntity> schedule = validateAndBuildSchedule(request, command);

        RtpAuthorisationEntity auth = new RtpAuthorisationEntity();
        auth.setId(UUID.randomUUID());
        auth.setRequestId(request.getId());
        auth.setAuthorisationReference(required(command.authorisationReference()));
        auth.setMode(command.mode());
        auth.setAuthorisedAmount(command.authorisedAmount());
        auth.setActorParticipantId(actor.participantId());
        auth.setRequestSha256(hash(command.authorisationReference()+"|"+command.mode()+"|"+command.authorisedAmount().toPlainString()));
        authorisations.save(auth);
        if (!schedule.isEmpty()) installments.saveAll(schedule);

        RtpStatus previous = request.getStatus();
        request.setAuthorisationMode(command.mode());
        request.setAuthorisedAmount(command.authorisedAmount());
        request.setSettlementInquiryRef(required(command.inquiryRef()));
        request.setAuthorisedAt(clock.instant());
        request.setStatus(RtpStatus.AUTHORISED);
        record(request.getId(), previous, RtpStatus.AUTHORISED, actor.actorId(), "RTP authorised");

        String transferRef = null;
        if (command.mode() != RtpAuthorisationMode.INSTALLMENT) {
            var submission = settlementGateway.submit(new RtpSettlementGateway.SettlementCommand(
                    request.getPayerParticipantId(), request.getPayeeParticipantId(),
                    request.getPayerAccount(), request.getPayeeAccount(), command.authorisedAmount(),
                    request.getCurrency(), command.inquiryRef(), "RTP-"+request.getId()+"-AUTH",
                    "RTP "+request.getRequestCorrelationId()));
            transferRef = submission.transactionReference();
            request.setTransferReference(transferRef);
            if ("SETTLED".equalsIgnoreCase(submission.status())) {
                transitionToSettlement(request, command.authorisedAmount(), transferRef, actor.actorId());
            }
        }
        RtpRequestEntity saved = requests.saveAndFlush(request);
        events.publish("rtp.authorised", saved, Map.of("mode", command.mode().name(),
                "authorisationReference", command.authorisationReference(),
                "transferReference", transferRef == null ? "" : transferRef));
        if (saved.getStatus() == RtpStatus.SETTLED) events.publish("rtp.settled", saved, Map.of());
        return response(saved, auth.getAuthorisationReference(), schedule);
    }

    @Transactional
    public RtpAuthorisationResponse decline(UUID requestId, DeclineRtpRequest command, RtpActor actor) {
        RtpRequestEntity request = lock(requestId);
        assertPayer(actor, request);
        stateMachine.assertTransition(request.getStatus(), RtpStatus.DECLINED);
        RtpStatus previous = request.getStatus();
        request.setStatus(RtpStatus.DECLINED);
        request.setDeclineReason(required(command.reason()));
        request.setDeclinedAt(clock.instant());
        requests.saveAndFlush(request);
        record(requestId, previous, RtpStatus.DECLINED, actor.actorId(), command.reason());
        events.publish("rtp.declined", request, Map.of("reason", command.reason()));
        return response(request, null, installments.findByRequestIdOrderByInstallmentNumberAsc(requestId));
    }

    @Transactional
    public RtpAuthorisationResponse confirmSettlement(UUID requestId, ConfirmRtpSettlementRequest command, RtpActor actor) {
        if (actor == null || !actor.privileged()) throw new RtpAccessDeniedException();
        RtpRequestEntity request = lock(requestId);
        if (stateMachine.isTerminal(request.getStatus())) {
            if (request.getStatus() == RtpStatus.SETTLED && command.settlementReference().equals(request.getSettlementReference())) {
                return response(request, null, installments.findByRequestIdOrderByInstallmentNumberAsc(requestId));
            }
            throw new IllegalStateException("Terminal RTP request cannot accept settlement");
        }
        if (command.installmentNumber() != null) {
            RtpInstallmentScheduleEntity item = installments.findByRequestIdOrderByInstallmentNumberAsc(requestId).stream()
                    .filter(i -> i.getInstallmentNumber() == command.installmentNumber()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Installment not found"));
            if (item.getStatus() == RtpInstallmentStatus.SETTLED) {
                return response(request, null, installments.findByRequestIdOrderByInstallmentNumberAsc(requestId));
            }
            item.setStatus(RtpInstallmentStatus.SETTLED);
            item.setSettledAt(clock.instant());
            item.setTransactionReference(command.settlementReference());
            installments.save(item);
        }
        BigDecimal total = request.getSettledAmount().add(command.settledAmount());
        if (total.compareTo(request.getAuthorisedAmount()) > 0) throw new IllegalArgumentException("Settlement exceeds authorised amount");
        RtpStatus previous = request.getStatus();
        request.setSettledAmount(total);
        request.setSettlementReference(command.settlementReference());
        RtpStatus target = total.compareTo(request.getAuthorisedAmount()) == 0
                ? RtpStatus.SETTLED
                : request.getAuthorisationMode() == RtpAuthorisationMode.INSTALLMENT
                    ? RtpStatus.INSTALMENT_IN_PROGRESS : RtpStatus.PARTIALLY_SETTLED;
        if (previous != target) stateMachine.assertTransition(previous, target);
        request.setStatus(target);
        if (target == RtpStatus.SETTLED) request.setSettledAt(clock.instant());
        requests.saveAndFlush(request);
        record(requestId, previous, target, actor.actorId(), "Settlement confirmed");
        events.publish(target == RtpStatus.SETTLED ? "rtp.settled" : "rtp.partially_settled", request,
                Map.of("settlementReference", command.settlementReference()));
        return response(request, null, installments.findByRequestIdOrderByInstallmentNumberAsc(requestId));
    }

    private void transitionToSettlement(RtpRequestEntity request, BigDecimal amount, String ref, String actor) {
        RtpStatus previous = request.getStatus();
        stateMachine.assertTransition(previous, RtpStatus.SETTLED);
        request.setSettledAmount(amount); request.setSettlementReference(ref); request.setSettledAt(clock.instant());
        request.setStatus(RtpStatus.SETTLED); record(request.getId(), previous, RtpStatus.SETTLED, actor, "Transfer rail settled");
    }

    private List<RtpInstallmentScheduleEntity> validateAndBuildSchedule(RtpRequestEntity request, AuthoriseRtpRequest command) {
        if (command.mode() != RtpAuthorisationMode.INSTALLMENT) {
            if (command.installments() != null && !command.installments().isEmpty()) throw new IllegalArgumentException("Installments are only valid for INSTALLMENT mode");
            return List.of();
        }
        if (command.installments() == null || command.installments().isEmpty()) throw new IllegalArgumentException("Installment schedule is required");
        if (command.installments().size() > 36) throw new IllegalArgumentException("Installment count exceeds maximum 36");
        BigDecimal sum = command.installments().stream().map(i -> i.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(command.authorisedAmount()) != 0) throw new IllegalArgumentException("Installment total must equal authorised amount");
        int expected = 1; Instant previousDue = clock.instant(); List<RtpInstallmentScheduleEntity> result = new ArrayList<>();
        for (var item : command.installments()) {
            if (item.installmentNumber() != expected++) throw new IllegalArgumentException("Installment numbers must be contiguous starting at 1");
            if (!item.dueAt().isAfter(previousDue)) throw new IllegalArgumentException("Installment due dates must be strictly increasing and in the future");
            RtpInstallmentScheduleEntity entity = new RtpInstallmentScheduleEntity();
            entity.setId(UUID.randomUUID()); entity.setRequestId(request.getId()); entity.setInstallmentNumber(item.installmentNumber());
            entity.setDueAt(item.dueAt()); entity.setAmount(item.amount()); entity.setStatus(RtpInstallmentStatus.SCHEDULED);
            result.add(entity); previousDue = item.dueAt();
        }
        return result;
    }

    private void validateAmount(RtpRequestEntity request, AuthoriseRtpRequest command) {
        BigDecimal amount = command.authorisedAmount();
        if (amount.signum() <= 0 || amount.compareTo(request.getRequestedAmount()) > 0) throw new IllegalArgumentException("Invalid authorised amount");
        if (command.mode() == RtpAuthorisationMode.FULL && amount.compareTo(request.getRequestedAmount()) != 0) throw new IllegalArgumentException("FULL mode must authorise requested amount");
        if (command.mode() == RtpAuthorisationMode.PARTIAL && amount.compareTo(request.getRequestedAmount()) >= 0) throw new IllegalArgumentException("PARTIAL mode must be less than requested amount");
    }

    private RtpRequestEntity lock(UUID id) { return requests.findByIdForUpdate(id).orElseThrow(() -> new RtpNotFoundException(id)); }
    private void assertPayer(RtpActor actor, RtpRequestEntity request) {
        if (actor != null && actor.privileged()) return;
        if (actor == null || actor.participantId() == null || !actor.participantId().equals(request.getPayerParticipantId())) throw new RtpAccessDeniedException();
    }
    private void expireLocked(RtpRequestEntity request, String reason) {
        if (request.getStatus() != RtpStatus.PENDING_AUTH) return;
        request.setStatus(RtpStatus.EXPIRED); requests.save(request);
        record(request.getId(), RtpStatus.PENDING_AUTH, RtpStatus.EXPIRED, "system", reason);
        events.publish("rtp.expired", request, Map.of("reason", reason));
    }
    private void record(UUID id, RtpStatus from, RtpStatus to, String actor, String reason) {
        RtpStateTransitionEntity t = new RtpStateTransitionEntity(); t.setId(UUID.randomUUID()); t.setRequestId(id);
        t.setFromStatus(from); t.setToStatus(to); t.setActorId(actor == null ? "unknown" : actor); t.setReason(reason); transitions.save(t);
    }
    private RtpAuthorisationResponse response(RtpRequestEntity request, String authRef, List<RtpInstallmentScheduleEntity> schedule) {
        return new RtpAuthorisationResponse(request.getId(), authRef, request.getAuthorisationMode(), request.getAuthorisedAmount(),
                request.getTransferReference(), request.getStatus(), schedule.stream().map(i -> new RtpAuthorisationResponse.RtpInstallmentView(
                    i.getInstallmentNumber(), i.getDueAt(), i.getAmount(), i.getStatus().name(), i.getTransactionReference())).toList());
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is blank"); return value.trim(); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
