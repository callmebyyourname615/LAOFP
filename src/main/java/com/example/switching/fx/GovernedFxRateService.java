package com.example.switching.fx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GovernedFxRateService {
    private final JdbcTemplate jdbc;
    public GovernedFxRateService(JdbcTemplate jdbc){this.jdbc=jdbc;}


    @Transactional
    public UUID recordObservation(String provider,String pair,BigDecimal rate,OffsetDateTime observedAt,byte[] signedPayload){
        if(rate==null||rate.signum()<=0) throw new IllegalArgumentException("FX rate must be positive");
        if(observedAt==null||observedAt.isAfter(OffsetDateTime.now().plusMinutes(1))) throw new IllegalArgumentException("invalid observation timestamp");
        Boolean enabled=jdbc.queryForObject("SELECT enabled FROM fx_rate_provider WHERE provider_code=?",Boolean.class,provider);
        if(!Boolean.TRUE.equals(enabled)) throw new IllegalStateException("FX provider is disabled");
        UUID id=UUID.randomUUID();
        if(signedPayload==null||signedPayload.length==0) throw new IllegalArgumentException("signed FX payload is required");
        String payloadHash=hashBytes(signedPayload);
        jdbc.update("INSERT INTO fx_rate_observation(id,provider_code,currency_pair,rate,observed_at,payload_hash) VALUES (?,?,?,?,?,?) ON CONFLICT(provider_code,currency_pair,observed_at) DO NOTHING",
                id,provider,pair,rate,observedAt,payloadHash);
        jdbc.update("UPDATE fx_rate_provider SET last_success_at=now() WHERE provider_code=?",provider);
        return id;
    }

    @Transactional
    public UUID propose(String pair,String actor){
        Map<String,Object> policy=jdbc.queryForMap("SELECT * FROM fx_governance_policy WHERE currency_pair=? AND enabled=true",pair);
        int quorum=(Integer)policy.get("minimum_quorum");
        int maxAge=(Integer)policy.get("maximum_age_seconds");
        BigDecimal maxDeviation=(BigDecimal)policy.get("maximum_deviation_basis_points");
        int ttl=(Integer)policy.get("quote_ttl_seconds");
        List<BigDecimal> rates=jdbc.query("""
            SELECT DISTINCT ON (o.provider_code) o.rate FROM fx_rate_observation o
              JOIN fx_rate_provider p ON p.provider_code=o.provider_code AND p.enabled=true
             WHERE o.currency_pair=? AND o.observed_at >= now()-(? * interval '1 second')
             ORDER BY o.provider_code,o.observed_at DESC
            """,(rs,n)->rs.getBigDecimal(1),pair,maxAge);
        if(rates.size()<quorum) throw new IllegalStateException("FX provider quorum not met");
        BigDecimal median=median(rates);
        for(BigDecimal rate:rates){
            BigDecimal deviation=rate.subtract(median).abs().multiply(new BigDecimal("10000")).divide(median,4,RoundingMode.HALF_UP);
            if(deviation.compareTo(maxDeviation)>0) throw new IllegalStateException("FX observation deviation exceeds policy");
        }
        UUID id=UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        String evidence=hash(pair+"|"+median.toPlainString()+"|"+rates.stream().map(BigDecimal::toPlainString).sorted().toList());
        jdbc.update("""
            INSERT INTO governed_fx_rate_publication(id,currency_pair,rate,provider_count,status,valid_from,valid_until,requested_by,evidence_hash)
            VALUES (?,?,?,?,'DRAFT',?,?,?,?)
            """, id,pair,median,rates.size(),now,now.plusSeconds(ttl),actor,evidence);
        return id;
    }

    @Transactional
    public void approve(UUID publicationId,String approver){
        Map<String,Object> row=jdbc.queryForMap("SELECT currency_pair,requested_by,status FROM governed_fx_rate_publication WHERE id=? FOR UPDATE",publicationId);
        if(approver.equals(row.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve FX rate");
        if(!"DRAFT".equals(row.get("status"))) throw new IllegalStateException("FX rate is not pending approval");
        jdbc.update("UPDATE governed_fx_rate_publication SET status='EXPIRED' WHERE currency_pair=? AND status='APPROVED'",row.get("currency_pair"));
        int changed=jdbc.update("UPDATE governed_fx_rate_publication SET status='APPROVED',approved_by=? WHERE id=? AND valid_until>now()",approver,publicationId);
        if(changed!=1) throw new IllegalStateException("FX rate expired before approval");
    }

    public static BigDecimal median(List<BigDecimal> input){
        if(input==null||input.isEmpty()) throw new IllegalArgumentException("rates are required");
        List<BigDecimal> sorted=new ArrayList<>(input); sorted.sort(Comparator.naturalOrder());
        int mid=sorted.size()/2;
        return sorted.size()%2==1?sorted.get(mid):sorted.get(mid-1).add(sorted.get(mid)).divide(new BigDecimal("2"),10,RoundingMode.HALF_UP);
    }
    private static String hash(String value){return hashBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static String hashBytes(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
