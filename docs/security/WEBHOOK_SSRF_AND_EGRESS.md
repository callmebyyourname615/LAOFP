# Webhook SSRF and outbound egress controls

The application validates webhook URLs at registration and immediately before each delivery. Production requires HTTPS, an explicit hostname allowlist, approved ports, no user-info or fragments, and rejection of loopback, private, link-local, multicast, carrier-grade NAT, benchmark, documentation, metadata, and other reserved address ranges. Every DNS answer is inspected. Java HTTP redirects are disabled, so a 3xx response is recorded as a failed attempt and is never followed.

DNS validation in the application is defence in depth, not the sole DNS-rebinding control. Between validation and connection, another resolver could return a different answer. Therefore production and staging require `WEBHOOK_EGRESS_PROXY_ENABLED=true` and an organization-managed forward proxy that independently:

1. allows only approved PSP webhook FQDNs and TCP/443;
2. rejects private, loopback, link-local, metadata, and reserved destinations after every resolution;
3. does not permit arbitrary CONNECT destinations;
4. records destination, policy decision, release, and change-ticket metadata without logging payloads or signatures.

`WEBHOOK_ALLOWED_HOSTS` and the proxy policy must be updated through the same approved change. Direct application-pod egress to internet TCP/443 should be removed after the proxy address is known. `k8s/egress-proxy.yaml` is an integration contract, not a permissive proxy deployment.
