import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { vpaPayload } from '../lib/payloads.js';
export const options = { scenarios: { vpa: { executor: 'constant-vus', vus: Number(__ENV.VUS || 500), duration: __ENV.DURATION || '5m', gracefulStop: '30s' } }, thresholds: { ...commonThresholds, http_req_duration: ['p(95)<400', 'p(99)<800'] } };
export default function () {
  assertResponse(http.post(`${BASE_URL}/v1/lookup/resolve`, JSON.stringify(vpaPayload()), { headers: headers() }), [200, 404, 422]); sleep(0.05);
}
