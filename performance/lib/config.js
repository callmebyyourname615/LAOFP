import { check } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:18080').replace(/\/$/, '');
export const API_KEY = __ENV.API_KEY || '';
export const PSP_ID = __ENV.PSP_ID || 'BANK001';
export const SOURCE_BANK = __ENV.SOURCE_BANK || 'BANK001';
export const DESTINATION_BANK = __ENV.DESTINATION_BANK || 'BANK002';

export function headers(extra = {}) {
  const h = { 'Content-Type': 'application/json', Accept: 'application/json', ...extra };
  if (API_KEY) h['X-API-Key'] = API_KEY;
  return h;
}

export function assertResponse(response, accepted = [200, 201, 202, 409]) {
  return check(response, {
    'status accepted': (r) => accepted.includes(r.status),
    'response under 2s': (r) => r.timings.duration < 2000,
  });
}

export function unique(prefix = 'perf') {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

export const commonThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  checks: ['rate>0.99'],
};
