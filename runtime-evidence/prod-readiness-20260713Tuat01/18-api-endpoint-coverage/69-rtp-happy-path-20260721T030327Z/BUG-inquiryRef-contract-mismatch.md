# BUG: AuthoriseRtpRequest.inquiryRef is optional in the DTO but mandatory in the service

- DTO: `src/main/java/com/example/switching/rtp/dto/AuthoriseRtpRequest.java`
  `inquiryRef` only has `@Size(max = 64)` — no `@NotBlank`/`@NotNull`, so Bean Validation lets a
  request through with it omitted or blank.
- Service: `src/main/java/com/example/switching/rtp/service/RtpAuthorisationService.java:89`
  `request.setSettlementInquiryRef(required(command.inquiryRef()));` — `required()` (line 227)
  throws `IllegalArgumentException("Required value is blank")` unconditionally when it's null/blank.

## Impact
Any caller following the DTO's own contract (or an OpenAPI spec generated from these annotations)
reasonably omits `inquiryRef` and gets a 400 with a generic, unhelpful message that doesn't name
the offending field — "Required value is blank" gives no clue which of the 5 request fields is at
fault. This is both a correctness gap (annotation contract lies) and a DX/API-quality gap
(unhelpful error message, doesn't match the field-level messages the rest of the API uses via
@Valid, e.g. "authorisationReference: must not be blank").

## Fix recommendation
Either mark `inquiryRef` `@NotBlank` in the DTO so it fails fast with a proper field-level message
consistent with the rest of the API, or make the service genuinely treat it as optional if there
are legitimate authorise flows without a prior inquiry.
