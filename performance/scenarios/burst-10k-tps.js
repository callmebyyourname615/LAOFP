import http from 'k6/http';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';
export const options = {
  scenarios: { burst_10k_tps: { executor: 'constant-arrival-rate', rate: Number(__ENV.TARGET_TPS || 10000), timeUnit: '1s', duration: __ENV.DURATION || '60s', preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 4000), maxVUs: Number(__ENV.MAX_VUS || 15000), gracefulStop: '30s' } },
  thresholds: { ...commonThresholds, dropped_iterations: ['count==0'], http_req_duration: ['p(95)<750', 'p(99)<1500'] },
};
export default function () {
  assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() }));
}
