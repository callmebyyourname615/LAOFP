import { DESTINATION_BANK, SOURCE_BANK, unique } from './config.js';

export function inquiryPayload() {
  const id = unique('inq');
  return {
    clientInquiryId: id, idempotencyKey: id, sourceBank: SOURCE_BANK,
    destinationBank: DESTINATION_BANK, debtorAccount: `100${__VU}`,
    creditorAccount: `200${(__ITER % 10000) + 1}`,
    amount: 10000 + (__ITER % 5000), currency: 'LAK', reference: `k6-${id}`,
  };
}

export function vpaPayload() {
  return {
    vpaType: __ENV.VPA_TYPE || 'MSISDN',
    vpaValue: __ENV.VPA_VALUE || `85620${String((__ITER % 99999999) + 1).padStart(8, '0')}`,
  };
}

export function qrPayPayload() {
  return {
    qrId: __ENV.QR_ID || '',
    issuingPspId: SOURCE_BANK,
    amount: Number(__ENV.QR_AMOUNT || 10000),
  };
}
