import http from 'k6/http';
import { BASE_URL, assertResponse, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';
export const options = {
  scenarios: { burst_20k_tps: { executor: 'ramping-arrival-rate', startRate: Number(__ENV.START_TPS || 10000), timeUnit: '1s', preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 8000), maxVUs: Number(__ENV.MAX_VUS || 30000), stages: [{ target: Number(__ENV.TARGET_TPS || 20000), duration: __ENV.RAMP_DURATION || '30s' }, { target: Number(__ENV.TARGET_TPS || 20000), duration: __ENV.DURATION || '15m' }, { target: 0, duration: '30s' }], gracefulStop: '2m' } },
  thresholds: { http_req_failed: ['rate<0.005'], http_req_duration: ['p(95)<750'], dropped_iterations: ['count==0'], checks: ['rate>0.995'] },
};
export default function () { assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() })); }
