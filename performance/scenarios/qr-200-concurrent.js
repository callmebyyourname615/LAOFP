import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
import { qrPayPayload } from '../lib/payloads.js';
export const options = { scenarios: { qr: { executor: 'constant-vus', vus: Number(__ENV.VUS || 200), duration: __ENV.DURATION || '5m', gracefulStop: '30s' } }, thresholds: { ...commonThresholds, http_req_duration: ['p(95)<750', 'p(99)<1500'] } };
export function setup() { if (!__ENV.QR_ID) throw new Error('QR_ID is required and must reference a seeded active STATIC QR'); }
export default function () {
  assertResponse(http.post(`${BASE_URL}/v1/qr/pay`, JSON.stringify(qrPayPayload()), { headers: headers() }), [200, 201, 202, 409, 422]); sleep(0.05);
}
