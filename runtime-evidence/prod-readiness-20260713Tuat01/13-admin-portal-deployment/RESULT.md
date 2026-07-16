# 13 Admin Portal Deployment Result

Status: PASS
Collected at: 20260716T015423Z
Target: http://175.11.0.200:18085

Evidence:
- Production Node SSR returns HTTP 200 for /connectors and /certificates.
- The deployed pages do not contain the default Nginx page or Vite dev-client marker.
- Same-origin /actuator/health proxy returns Switching status UP.
- UAT Docker and Nginx checks are recorded when SSH credentials are provided.
