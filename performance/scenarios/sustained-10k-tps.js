import http from 'k6/http';
import { BASE_URL, assertResponse, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';
export const options = {
  scenarios: { sustained_10k_tps: { executor: 'constant-arrival-rate', rate: Number(__ENV.TARGET_TPS || 10000), timeUnit: '1s', duration: __ENV.DURATION || '60m', preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 5000), maxVUs: Number(__ENV.MAX_VUS || 20000), gracefulStop: '2m' } },
  thresholds: { http_req_failed: ['rate<0.001'], http_req_duration: ['p(95)<500'], dropped_iterations: ['count==0'], checks: ['rate>0.999'] },
};
export default function () { assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() })); }
