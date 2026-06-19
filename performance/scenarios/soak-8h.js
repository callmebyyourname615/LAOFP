import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { inquiryPayload, vpaPayload } from '../lib/payloads.js';
export const options = { scenarios: { soak: { executor: 'constant-arrival-rate', rate: Number(__ENV.TARGET_TPS || 500), timeUnit: '1s', duration: __ENV.DURATION || '8h', preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 300), maxVUs: Number(__ENV.MAX_VUS || 2000), gracefulStop: '1m' } }, thresholds: { ...commonThresholds, dropped_iterations: ['count==0'], http_req_duration: ['p(95)<750', 'p(99)<1500'] } };
export default function () {
  if ((__ITER % 10) < 8) assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() }));
  else assertResponse(http.post(`${BASE_URL}/v1/lookup/resolve`, JSON.stringify(vpaPayload()), { headers: headers() }), [200, 404, 422]);
  sleep(0.01);
}
