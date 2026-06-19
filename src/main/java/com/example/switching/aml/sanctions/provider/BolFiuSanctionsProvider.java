package com.example.switching.aml.sanctions.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.switching.aml.config.AmlProperties;
import com.example.switching.aml.sanctions.SanctionsHashing;
import com.example.switching.aml.sanctions.model.SanctionsSnapshot;
import com.example.switching.aml.sanctions.parser.BolFiuJsonSanctionsParser;

@Component
public class BolFiuSanctionsProvider implements SanctionsProvider {

    private final AmlProperties properties;
    private final SanctionsHttpClient httpClient;
    private final BolFiuJsonSanctionsParser parser;

    public BolFiuSanctionsProvider(AmlProperties properties,
                                   SanctionsHttpClient httpClient,
                                   BolFiuJsonSanctionsParser parser) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public String providerCode() {
        return "BOL";
    }

    @Override
    public boolean enabled() {
        return properties.getSanctions().getBol().isEnabled();
    }

    @Override
    public int minimumRecords() {
        return properties.getSanctions().getBol().getMinimumRecords();
    }

    @Override
    public SanctionsSnapshot fetchSnapshot() {
        AmlProperties.Provider config = properties.getSanctions().getBol();
        Map<String, String> headers = new LinkedHashMap<>();
        String apiKey = config.getApiKey().isBlank() ? properties.getBolFiuApiKey() : config.getApiKey();
        headers.put(config.getApiKeyHeader(), apiKey);
        byte[] payload = httpClient.get(config.getUrl(), headers, config.isAllowInsecureHttp());
        String hash = SanctionsHashing.sha256(payload);
        String sourceReference = "BOL-" + LocalDate.now(ZoneOffset.UTC) + "-" + hash.substring(0, 12);
        return new SanctionsSnapshot(
                providerCode(), sourceReference, Instant.now(), hash,
                parser.parse(payload, sourceReference));
    }
}
