package com.example.switching.webhook.security;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates webhook destinations at registration and immediately before every delivery. */
@Component
public class WebhookEndpointPolicy {
    private final WebhookEndpointPolicyProperties properties;
    private final WebhookDnsResolver dnsResolver;

    @Autowired
    public WebhookEndpointPolicy(WebhookEndpointPolicyProperties properties) {
        this(properties, WebhookDnsResolver.system());
    }

    WebhookEndpointPolicy(WebhookEndpointPolicyProperties properties, WebhookDnsResolver dnsResolver) {
        this.properties = Objects.requireNonNull(properties);
        this.dnsResolver = Objects.requireNonNull(dnsResolver);
    }

    public URI validate(String rawUrl) {
        if (!properties.isEnabled()) return parse(rawUrl);
        URI uri = parse(rawUrl);
        validateStructure(uri);
        String host = normalizeHost(uri.getHost());
        validateAllowlist(host);
        List<InetAddress> addresses = resolveBounded(host);
        if (addresses.isEmpty()) reject("Webhook host did not resolve");
        for (InetAddress address : addresses) validateAddress(address);
        return uri;
    }

    private URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) reject("Webhook URL is required");
        try { return new URI(rawUrl.trim()).normalize(); }
        catch (URISyntaxException ex) { throw new WebhookEndpointRejectedException("Webhook URL is invalid", ex); }
    }

    private void validateStructure(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (properties.isRequireHttps() && !"https".equals(scheme)) reject("Webhook URL must use HTTPS");
        if (!"https".equals(scheme) && !"http".equals(scheme)) reject("Webhook URL scheme is not allowed");
        if (uri.getRawUserInfo() != null) reject("Webhook URL must not contain user information");
        if (uri.getHost() == null || uri.getHost().isBlank()) reject("Webhook URL must contain a valid host");
        if (uri.getRawFragment() != null) reject("Webhook URL must not contain a fragment");
        int port = uri.getPort() == -1 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        if (!properties.getAllowedPorts().isEmpty() && !properties.getAllowedPorts().contains(port)) {
            reject("Webhook URL port is not allowed");
        }
    }

    private String normalizeHost(String host) {
        try { return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException ex) { throw new WebhookEndpointRejectedException("Webhook host is invalid", ex); }
    }

    private void validateAllowlist(String host) {
        List<String> rules = properties.getAllowedHosts().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (rules.isEmpty()) {
            if (properties.isRequireAllowlist()) reject("Webhook host allowlist is required but empty");
            return;
        }
        boolean matched = rules.stream().anyMatch(rule -> hostMatches(host, rule));
        if (!matched) reject("Webhook host is not in the outbound allowlist");
    }

    static boolean hostMatches(String host, String rawRule) {
        String rule = rawRule.toLowerCase(Locale.ROOT);
        if (rule.startsWith("*.")) {
            String suffix = rule.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(rule);
    }

    private List<InetAddress> resolveBounded(String host) {
        int timeout = Math.max(100, properties.getDnsResolveTimeoutMillis());
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return dnsResolver.resolve(host); }
                catch (UnknownHostException ex) { throw new DnsResolutionRuntimeException(ex); }
            }).orTimeout(timeout, TimeUnit.MILLISECONDS).join();
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new WebhookEndpointRejectedException("Webhook host DNS resolution failed", cause);
        }
    }

    private void validateAddress(InetAddress address) {
        if (properties.isAllowPrivateAddresses()) return;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            reject("Webhook host resolved to a blocked network address");
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && isBlockedIpv4(bytes)) reject("Webhook host resolved to a reserved IPv4 address");
        if (address instanceof Inet6Address && isBlockedIpv6(bytes)) reject("Webhook host resolved to a reserved IPv6 address");
    }

    private boolean isBlockedIpv4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff;
        return a == 0 || a == 10 || a == 127 || (a == 100 && c >= 64 && c <= 127)
                || (a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)
                || (a == 192 && (c == 0 || c == 2 || c == 88 || c == 168))
                || (a == 198 && (c == 18 || c == 19 || c == 51))
                || (a == 203 && c == 0) || a >= 224;
    }

    private boolean isBlockedIpv6(byte[] b) {
        int first = b[0] & 0xff;
        return (first & 0xfe) == 0xfc || (first == 0xfe && ((b[1] & 0xc0) == 0x80));
    }

    private static void reject(String message) { throw new WebhookEndpointRejectedException(message); }
    private static final class DnsResolutionRuntimeException extends RuntimeException {
        DnsResolutionRuntimeException(Throwable cause) { super(cause); }
    }
}
