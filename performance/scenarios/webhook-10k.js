import http from 'k6/http';
import { BASE_URL, assertResponse, commonThresholds, headers } from '../lib/config.js';
const WEBHOOK_ID = __ENV.WEBHOOK_ID || '';
export const options = { scenarios: { webhook: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 250), iterations: Number(__ENV.ITERATIONS || 10000), maxDuration: __ENV.MAX_DURATION || '10m' } }, thresholds: { ...commonThresholds, http_req_duration: ['p(95)<1000'] } };
export function setup() { if (!WEBHOOK_ID) throw new Error('WEBHOOK_ID is required'); }
export default function () {
  assertResponse(http.post(`${BASE_URL}/v1/webhooks/${WEBHOOK_ID}/test`, null, { headers: headers() }), [200, 202]);
}
