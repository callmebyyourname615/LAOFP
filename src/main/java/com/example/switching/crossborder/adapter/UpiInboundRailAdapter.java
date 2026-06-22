package com.example.switching.crossborder.adapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;import com.example.switching.crossborder.service.*;import com.fasterxml.jackson.databind.ObjectMapper;
@Component @ConditionalOnProperty(prefix="switching.phase-ii.cross-border-adapters.upi",name="enabled",havingValue="true")
public class UpiInboundRailAdapter extends AbstractJsonRailAdapter{
 public UpiInboundRailAdapter(Environment e,RailMessageJournalService j,RailHttpTransport t,RailSignatureVerifier s,ObjectMapper m,OAuth2ClientCredentialsTokenProvider o){super(e,j,t,s,m,o);}
 @Override public com.example.switching.crossborder.dto.RailTransactionRef submit(com.example.switching.crossborder.dto.RailInstruction instruction){throw new IllegalStateException("UPI outward flow is disabled pending NPCI accreditation");}
 public String rail(){return "UPI";} protected String prefix(){return "switching.phase-ii.cross-border-adapters.upi";}
}
