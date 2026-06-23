import http from 'k6/http';
import { BASE_URL, assertResponse, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';

export const options = {
  scenarios: {
    smoke_100_tps: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TARGET_TPS || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 1000),
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<200'],
    dropped_iterations: ['count==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  assertResponse(http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() }));
}
