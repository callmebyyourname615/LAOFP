package com.example.switching.rtp.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rtp_authorisation")
@Getter @Setter @NoArgsConstructor
public class RtpAuthorisationEntity {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "authorisation_reference", nullable = false, unique = true, length = 64) private String authorisationReference;
    @Enumerated(EnumType.STRING) @Column(name = "mode", nullable = false, length = 24) private RtpAuthorisationMode mode;
    @Column(name = "authorised_amount", nullable = false, precision = 19, scale = 4) private BigDecimal authorisedAmount;
    @Column(name = "request_sha256", length = 64) private String requestSha256;
    @Column(name = "actor_participant_id", nullable = false, length = 64) private String actorParticipantId;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
}
