import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { inquiryPayload } from '../lib/payloads.js';
export const options = { vus: 2, iterations: 10, thresholds: commonThresholds };
export default function () {
  const r = http.post(`${BASE_URL}/api/inquiries`, JSON.stringify(inquiryPayload()), { headers: headers() });
  assertResponse(r); sleep(0.2);
}
