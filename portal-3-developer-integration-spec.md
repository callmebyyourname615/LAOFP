# Portal 3: Developer / Integration Portal

## Product Definition

Build a complete production-quality web application called:

**Switching Developer / Integration Portal**

This portal is for developers, integration engineers, banks, PSPs, and technical teams integrating with the Switching platform.

The portal must provide API documentation, API testing, sandbox tools, credential management, webhook tools, technical logs, troubleshooting, and integration support.

If a design-token file is attached separately, it is mandatory and must be used 100% across the application. Do not introduce an unrelated visual system. Define the tokens as CSS variables and reuse them across all pages and components.

## Target Users and Roles

Implement role-based access control for:

- `PARTICIPANT_DEVELOPER`
- `PARTICIPANT_ADMIN`
- `INTEGRATION_ENGINEER`
- `SUPPORT_ENGINEER`
- `READ_ONLY_DEVELOPER`

All users must be scoped to their own participant, bank, or PSP. Never expose another participant's credentials, API logs, transactions, reports, webhook events, or personal data.

## Global Application Shell

Create a responsive developer portal with:

- Developer-focused sidebar navigation
- Topbar with environment selector
- Sandbox / UAT / Production environment indicator
- Organization and participant display
- Global search
- Notification center
- User profile menu
- API status indicator
- Documentation search
- Breadcrumbs
- Page headers
- Keyboard-accessible navigation
- Responsive desktop, tablet, and mobile layouts
- Loading, empty, error, permission-denied, and success states

Use a consistent professional developer-tool visual language with readable code blocks, clear technical status indicators, searchable documentation, and compact but breathable layouts.

## 1. Developer Dashboard

Create a dashboard containing:

- API health
- API usage
- Request volume
- Error rate
- Average latency
- P95/P99 latency
- Rate limit status
- Credential status
- Certificate expiry status
- Webhook status
- Sandbox status
- Recent API errors
- Recent webhook delivery failures
- Recent integration activity
- System status and incident notices
- API version alerts
- Upcoming certificate expiry warnings

Charts and visualizations:

- Request volume over time
- Success/error rate over time
- Latency trend
- Rate-limit usage
- Webhook delivery success rate
- API usage by product
- API usage by environment

Every dashboard card must link to the related detail module.

## 2. API Catalog

Create an API catalog organized by product:

- Authentication
- Transfers
- QR Payment
- Request-to-Pay
- Bill Payment
- VPA
- Cross-border
- Settlement
- Dispute
- Risk Inquiry
- Reports
- Webhooks
- OAuth

Create these pages:

- API catalog overview
- Product API list
- API endpoint detail
- API version detail
- Changelog
- Deprecated API list
- Migration guide
- Search results
- Favorites / bookmarked APIs

Each API endpoint detail page must include:

- Endpoint name
- HTTP method
- URL path
- API version
- Description
- Required headers
- Authentication method
- Request schema
- Request field descriptions
- Required and optional fields
- Response schema
- Response field descriptions
- Success response example
- Error response examples
- Error code catalog
- Idempotency requirement
- Idempotency key example
- Retry behavior
- Timeout behavior
- Rate limit
- Pagination behavior where applicable
- Webhook behavior where applicable
- Required permissions/scopes
- SDK/code examples
- Generate cURL action
- Open in API Explorer action
- Copy endpoint action
- Copy request example action

Use syntax-highlighted code blocks and provide copy buttons.

## 3. API Explorer

Create an interactive API Explorer with:

- Environment selector
- Product selector
- API endpoint selector
- HTTP method display
- Authentication selector
- Header editor
- Query parameter editor
- Path parameter editor
- Request body editor
- JSON formatting
- JSON validation
- Execute API button
- Cancel request button
- Request history
- Saved requests
- Response viewer
- HTTP status display
- Response headers
- Response body
- Correlation ID
- Request ID
- Latency
- Error detail
- Retry action
- Clear request action
- Copy response action

Code generation:

- Generate cURL
- Generate JavaScript
- Generate Java
- Generate Postman collection
- Generate HTTPie if practical

Requirements:

- Clearly show the selected environment
- Display Production warnings before executing requests
- Do not expose secrets in generated code
- Mask sensitive values
- Validate request schema before execution
- Show loading, timeout, validation error, authentication error, rate-limit error, and server error states
- Save request history per user and environment

## 4. Sandbox

Create a complete Sandbox module with:

- Sandbox dashboard
- Sandbox credentials
- Test participant
- Test accounts
- Test QR codes
- Test transactions
- Test Request-to-Pay
- Test bill payment
- Test webhook
- Test report generation
- Sandbox request history
- Sandbox reset tools

Test scenarios:

- Successful transaction
- Failed transaction
- Timeout
- Connector unavailable
- Retry
- Duplicate request
- Idempotency conflict
- Risk block
- Invalid request
- Invalid authentication
- Rate-limit exceeded
- Webhook success
- Webhook failure
- Webhook retry
- Settlement pending
- Refund flow
- Dispute flow

Each scenario must include:

- Scenario description
- Required setup
- Test action
- Expected result
- Actual result
- Request payload
- Response payload
- Correlation ID
- Execution time
- Pass/fail status
- Reset or rerun action

## 5. Credential Management

Create these pages:

- Credential dashboard
- OAuth client list
- Create OAuth client
- OAuth client detail
- API key list
- Create API key
- API key detail
- mTLS certificate list
- Upload certificate
- Certificate detail
- Credential expiry page
- IP allowlist
- Environment separation
- Credential activity history

Support:

- OAuth client creation
- API key creation
- API key rotation
- API key revocation
- mTLS certificate upload
- Certificate validation
- Certificate rotation
- Credential expiry warnings
- Revoke credential
- IP allowlist management
- Separate Sandbox, UAT, and Production credentials
- Show credential last-used time
- Show credential status
- Show credential owner
- Show credential environment

Security requirements:

- Never display private keys
- Never display full API secrets after creation
- Mask secrets by default
- Require confirmation before revoke or rotate
- Require MFA for Production credential changes
- Show clear environment warnings
- Record every credential action in an audit history

## 6. Webhook Developer Tools

Create these pages:

- Webhook tools dashboard
- Event catalog
- Event detail
- Webhook endpoint setup
- Webhook endpoint list
- Webhook endpoint detail
- Signing secret management
- Sample payload viewer
- Delivery history
- Failed deliveries
- Retry simulation
- Signature verification
- Event replay in Sandbox
- Webhook error logs

Webhook endpoint setup must support:

- Endpoint URL
- Environment
- Event subscriptions
- Signing method
- Active/inactive state
- Test delivery
- Delivery timeout
- Retry configuration

Webhook detail must show:

- Event name
- Event ID
- Delivery status
- Attempt count
- HTTP status
- Response time
- Request timestamp
- Delivery timestamp
- Correlation ID
- Error message
- Payload preview
- Signature headers
- Retry action

Signature verification must include:

- Payload input
- Signature input
- Signing algorithm
- Timestamp input
- Verification result
- Example implementation

Never show full signing secrets. Provide rotation and masked display.

## 7. Logs & Troubleshooting

Create these pages:

- API request logs
- API response logs
- Correlation ID search
- Failed request queue
- Validation errors
- Authentication errors
- Timeout events
- Rate-limit events
- Webhook delivery errors
- Integration incident detail
- Request trace detail

Log search must support:

- Correlation ID
- Request ID
- Endpoint
- HTTP method
- HTTP status
- Error code
- Environment
- Date range
- Latency range
- Product
- API version
- Webhook event

Log detail must include:

- Timestamp
- Endpoint
- HTTP method
- Environment
- Request ID
- Correlation ID
- HTTP status
- Latency
- Request headers with sensitive values masked
- Request body with sensitive values masked
- Response headers with sensitive values masked
- Response body with sensitive values masked
- Error details
- Retry behavior
- Related webhook event
- Troubleshooting guidance

## 8. Documentation & Support

Create these pages:

- Documentation home
- API changelog
- Version compatibility
- Migration guide
- Error code catalog
- FAQ
- Integration checklist
- Go-live checklist
- Support ticket list
- Create support ticket
- Support ticket detail
- Incident notices
- System status
- Maintenance schedule

Documentation features:

- Full-text search
- Product navigation
- Version selector
- Environment selector
- Copy code examples
- Bookmark documentation
- Feedback action
- Related articles
- Previous/next article navigation
- Deprecated version warning
- Migration notices

Go-live checklist should cover:

- Credentials configured
- Certificate registered
- IP allowlist configured
- Webhook endpoint verified
- Signature verification passed
- Sandbox tests passed
- Error handling tested
- Retry behavior tested
- Idempotency tested
- Monitoring configured
- Production readiness confirmed

## Reusable Components

Create reusable components including:

- DeveloperAppShell
- Sidebar
- Topbar
- EnvironmentSwitcher
- OrganizationBadge
- APIStatusIndicator
- SearchCommandPalette
- DocumentationSidebar
- APIProductCard
- EndpointSummary
- MethodBadge
- VersionBadge
- CodeBlock
- CopyButton
- SchemaViewer
- RequestBuilder
- ResponseViewer
- HeaderEditor
- ParameterEditor
- JSONEditor
- APIExecutionPanel
- RequestHistory
- SavedRequestList
- LatencyBadge
- RateLimitMeter
- CredentialCard
- CertificateCard
- SecretMaskedField
- WebhookEventCard
- DeliveryTimeline
- LogTable
- LogDetailPanel
- ErrorCodeCard
- Checklist
- IncidentBanner
- SupportTicketForm
- EmptyState
- ErrorState
- LoadingSkeleton
- ConfirmationDialog
- CredentialRotationDialog
- CredentialRevokeDialog
- WebhookRetryDialog
- ProductionWarningDialog
- PermissionGuard
- EnvironmentGuard

## API Service Layer

Create separate typed service modules:

- developerDashboardService
- apiCatalogService
- apiExplorerService
- sandboxService
- credentialService
- oauthService
- certificateService
- webhookService
- integrationLogService
- documentationService
- supportService
- systemStatusService

Requirements:

- Keep API calls outside page components
- Centralize endpoint constants
- Use typed request and response models
- Support authentication token handling
- Support refresh token handling
- Support environment-specific base URLs
- Support correlation IDs
- Support request timeout
- Support standard error handling
- Support retry behavior
- Support rate-limit responses
- Use realistic mock data if backend is unavailable
- Keep mock data behind the service layer
- Make the services ready for real REST integration

## Security and Privacy

Do not expose sensitive information unnecessarily.

Never display:

- Full account numbers
- Full API secrets
- Private keys
- Full OAuth secrets
- Full webhook signing secrets
- Unmasked personal information
- Another participant's data

Implement:

- Sensitive data masking
- Permission checks
- Environment-specific access controls
- Production action warnings
- MFA for sensitive credential actions
- Audit history for credential changes
- Session timeout warning
- Permission-denied state
- Secure copy behavior for secrets
- Confirmation before revoke, rotate, replay, or production execution

## Routing Requirements

Create working routes for all modules and pages. Use clear route groups such as:

- `/dashboard`
- `/api-catalog`
- `/api-catalog/:product`
- `/api-catalog/:product/:endpoint`
- `/api-explorer`
- `/sandbox`
- `/sandbox/scenarios`
- `/credentials`
- `/credentials/oauth`
- `/credentials/api-keys`
- `/credentials/certificates`
- `/webhooks`
- `/webhooks/events`
- `/webhooks/deliveries`
- `/logs`
- `/logs/:id`
- `/documentation`
- `/support`
- `/status`

Every navigation item must lead to a real working page.

## Responsive and Accessibility Requirements

Test and optimize for:

- 1440px desktop
- 1200px desktop
- 1024px tablet
- 768px tablet
- 390px mobile

Fix:

- Horizontal overflow
- Code block overflow
- Table overflow
- Modal overflow
- Drawer overflow
- Sidebar overlap
- Broken charts
- Button collisions
- Text wrapping
- Keyboard focus states
- Color contrast

## Final QA Checklist

Before completing the implementation:

- Verify every module exists
- Verify every page has a working route
- Verify every navigation item works
- Verify every API product has endpoint detail pages
- Verify every endpoint page includes request/response schemas and examples
- Verify API Explorer executes mock requests
- Verify code generation works
- Verify Sandbox scenarios work
- Verify credential actions have confirmation and masking
- Verify webhook testing and retry simulation work
- Verify logs support filtering and correlation ID search
- Verify documentation search works
- Verify support and system status pages work
- Verify role-based permissions
- Verify environment separation
- Verify participant data isolation
- Verify no secrets or private keys are exposed
- Verify loading, empty, error, timeout, and permission states
- Verify responsive layouts
- Verify no console errors
- Verify no broken imports
- Verify no placeholder pages
- Verify all buttons have meaningful behavior

At the end, provide an implementation report containing:

- Completed modules
- Completed pages
- Routes created
- Reusable components created
- Roles and permissions implemented
- API services created
- Sandbox scenarios implemented
- Credential security controls implemented
- Webhook tools implemented
- Documentation pages implemented
- Backend integration points
- Remaining limitations
