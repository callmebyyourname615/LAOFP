package com.example.switching.aml.sanctions.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.switching.aml.config.AmlProperties;
import com.example.switching.aml.sanctions.SanctionsHashing;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.sanctions.parser.UnXmlSanctionsParser;

@Component
public class UnSanctionsProvider implements SanctionsProvider {

    private final AmlProperties properties;
    private final SanctionsHttpClient httpClient;
    private final UnXmlSanctionsParser parser;

    public UnSanctionsProvider(AmlProperties properties,
                               SanctionsHttpClient httpClient,
                               UnXmlSanctionsParser parser) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public String providerCode() {
        return "UN";
    }

    @Override
    public boolean enabled() {
        return properties.getSanctions().getUn().isEnabled();
    }

    @Override
    public int minimumRecords() {
        return properties.getSanctions().getUn().getMinimumRecords();
    }

    @Override
    public SanctionsSnapshot fetchSnapshot() {
        AmlProperties.Provider config = properties.getSanctions().getUn();
        byte[] payload = httpClient.get(config.getUrl(), Map.of(), config.isAllowInsecureHttp());
        String hash = SanctionsHashing.sha256(payload);
        String sourceReference = "UN-" + LocalDate.now(ZoneOffset.UTC) + "-" + hash.substring(0, 12);
        return new SanctionsSnapshot(
                providerCode(), sourceReference, Instant.now(), hash,
                parser.parse(payload, sourceReference));
    }
}
