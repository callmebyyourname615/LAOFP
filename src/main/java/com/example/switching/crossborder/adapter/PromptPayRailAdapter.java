package com.example.switching.crossborder.adapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;import com.example.switching.crossborder.service.*;import com.fasterxml.jackson.databind.ObjectMapper;
@Component @ConditionalOnProperty(prefix="switching.phase-ii.cross-border-adapters.promptpay",name="enabled",havingValue="true")
public class PromptPayRailAdapter extends AbstractJsonRailAdapter{
 public PromptPayRailAdapter(Environment e,RailMessageJournalService j,RailHttpTransport t,RailSignatureVerifier s,ObjectMapper m,OAuth2ClientCredentialsTokenProvider o){super(e,j,t,s,m,o);}
 public String rail(){return "PROMPTPAY";} protected String prefix(){return "switching.phase-ii.cross-border-adapters.promptpay";}
}
