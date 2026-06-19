import http from 'k6/http';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';
export const options = {
  scenarios: { sustained_2k_tps: { executor: 'constant-arrival-rate', rate: Number(__ENV.TARGET_TPS || 2000), timeUnit: '1s', duration: __ENV.DURATION || '300s', preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 1000), maxVUs: Number(__ENV.MAX_VUS || 5000), gracefulStop: '30s' } },
  thresholds: { ...commonThresholds, dropped_iterations: ['count==0'], http_req_duration: ['p(95)<500', 'p(99)<1000'] },
};
export default function () {
  assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() }));
}
