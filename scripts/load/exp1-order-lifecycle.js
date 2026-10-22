import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// exp1 order-lifecycle: 주문 생성 → 결제 → (확률 P) 취소 요청 / 그 외 (확률 Q) 부분 환불 요청.
// 요청마다 clientId·clientSeq(클라이언트별 단조 증가)를 헤더·본문에 싣고 Idempotency-Key = clientId-clientSeq.
// 성공(2xx) 주문은 "OLC ORDER_OK ..." 한 줄로 콘솔에 남긴다 → k6 run --console-output=<file> 로 받아 INV-8 계산.
//
// env
//   BASE_URL       기본 http://localhost:8000
//   RUN            런 이름(이메일·clientId 접두사). 기본 epoch ms
//   RPS_STAGES     단계별 rps, 쉼표 구분. 기본 2,5,10,20
//   STAGE_DURATION 단계 길이. 기본 2m
//   P              결제 후 취소 요청 확률. 기본 0.2
//   Q              취소하지 않은 주문의 부분 환불 요청 확률. 기본 0.1
//   DUP_PROB       같은 Idempotency-Key로 주문 생성을 즉시 재전송할 확률. 기본 0
//   BUYER_COUNT    가입시킬 구매자 수. 기본 20
//   PRODUCT_IDS    상품 ID 목록. 기본 1..25

const BASE = __ENV.BASE_URL || 'http://localhost:8000';
const RUN = __ENV.RUN || `${Date.now()}`;
const RPS_STAGES = (__ENV.RPS_STAGES || '2,5,10,20').split(',').map((v) => parseInt(v.trim(), 10)).filter((v) => v > 0);
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';
const P = parseFloat(__ENV.P || '0.2');
const Q = parseFloat(__ENV.Q || '0.1');
const DUP_PROB = parseFloat(__ENV.DUP_PROB || '0');
const BUYER_COUNT = parseInt(__ENV.BUYER_COUNT || '20', 10);
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || Array.from({ length: 25 }, (_, i) => i + 1).join(','))
  .split(',').map((v) => parseInt(v.trim(), 10));
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const orderOk = new Counter('olc_order_2xx');
const paymentOk = new Counter('olc_payment_2xx');
const cancelOk = new Counter('olc_cancel_2xx');
const refundOk = new Counter('olc_refund_2xx');
const dupSent = new Counter('olc_order_dup_sent');

function durationSeconds(d) {
  const m = /^(\d+)(ms|s|m|h)$/.exec(d);
  if (!m) throw new Error(`STAGE_DURATION 형식 오류: ${d}`);
  const n = parseInt(m[1], 10);
  return { ms: n / 1000, s: n, m: n * 60, h: n * 3600 }[m[2]];
}

const stageSeconds = durationSeconds(STAGE_DURATION);
const scenarios = {};
RPS_STAGES.forEach((rps, i) => {
  scenarios[`stage_${i + 1}_${rps}rps`] = {
    executor: 'constant-arrival-rate',
    rate: rps,
    timeUnit: '1s',
    duration: STAGE_DURATION,
    startTime: `${i * stageSeconds}s`,
    preAllocatedVUs: Math.max(5, rps * 3),
    maxVUs: Math.max(20, rps * 10),
    exec: 'lifecycle',
  };
});

export const options = {
  scenarios,
  setupTimeout: '180s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max', 'count'],
};

export function setup() {
  const tokens = [];
  for (let i = 1; i <= BUYER_COUNT; i++) {
    const email = `olc-${RUN}-${i}@test.com`;
    http.post(`${BASE}/api/v1/users/signup`,
      JSON.stringify({ email, password: 'password123', name: `olc${i}`, role: 'BUYER' }), { headers: JSON_HEADERS });
    const res = http.post(`${BASE}/api/v1/users/login`,
      JSON.stringify({ email, password: 'password123' }), { headers: JSON_HEADERS });
    const token = (res.json('data') || {}).access_token;
    if (token) tokens.push(token);
  }
  console.log(`OLC SETUP run=${RUN} buyers=${tokens.length}/${BUYER_COUNT} stages=${RPS_STAGES.join(',')} stage=${STAGE_DURATION} p=${P} q=${Q} dup=${DUP_PROB}`);
  return { tokens };
}

let clientSeq = 0;

function nextRequest(clientId) {
  clientSeq += 1;
  return { clientId, clientSeq, key: `${clientId}-${clientSeq}` };
}

function headers(token, req) {
  return {
    ...JSON_HEADERS,
    Authorization: `Bearer ${token}`,
    'Idempotency-Key': req.key,
    'X-Client-Id': req.clientId,
    'X-Client-Seq': `${req.clientSeq}`,
  };
}

function body(payload, req) {
  return JSON.stringify({ ...payload, client_id: req.clientId, client_seq: req.clientSeq });
}

function is2xx(res) {
  return res.status >= 200 && res.status < 300;
}

export function lifecycle(data) {
  if (data.tokens.length === 0) return;
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const clientId = `${RUN}-vu${__VU}`;
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];

  const cartReq = nextRequest(clientId);
  const cart = http.post(`${BASE}/api/v1/cart-items`, body({ product_id: productId, quantity: 1 }, cartReq),
    { headers: headers(token, cartReq), tags: { name: 'cart' } });
  const cartItemId = is2xx(cart) ? (cart.json('data') || {}).id : null;
  if (!cartItemId) return;

  const orderReq = nextRequest(clientId);
  const orderPayload = body({ cart_item_ids: [cartItemId] }, orderReq);
  const orderParams = { headers: headers(token, orderReq), tags: { name: 'order' } };
  const order = http.post(`${BASE}/api/v1/orders`, orderPayload, orderParams);
  if (Math.random() < DUP_PROB) {
    dupSent.add(1);
    const dup = http.post(`${BASE}/api/v1/orders`, orderPayload, { ...orderParams, tags: { name: 'order_dup' } });
    console.log(`OLC ORDER_DUP status=${dup.status} key=${orderReq.key} body_id=${is2xx(dup) ? (dup.json('data') || {}).id : ''}`);
  }
  if (!check(order, { 'order 2xx': is2xx })) {
    console.log(`OLC ORDER_FAIL status=${order.status} key=${orderReq.key}`);
    return;
  }
  const orderData = order.json('data') || {};
  orderOk.add(1);
  console.log(`OLC ORDER_OK order_id=${orderData.id} key=${orderReq.key} client=${clientId} seq=${orderReq.clientSeq} at=${Date.now()}`);

  const payReq = nextRequest(clientId);
  const payment = http.post(`${BASE}/api/v1/payments`,
    body({ order_id: orderData.id, amount: orderData.total_amount, method: 'CREDIT_CARD' }, payReq),
    { headers: headers(token, payReq), tags: { name: 'payment' } });
  if (!is2xx(payment)) {
    console.log(`OLC PAY_FAIL status=${payment.status} order_id=${orderData.id}`);
    return;
  }
  paymentOk.add(1);

  if (Math.random() < P) {
    const cancelReq = nextRequest(clientId);
    const cancel = http.patch(`${BASE}/api/v1/orders/${orderData.id}/cancel`, body({}, cancelReq),
      { headers: headers(token, cancelReq), tags: { name: 'cancel' } });
    if (is2xx(cancel)) cancelOk.add(1);
    console.log(`OLC CANCEL status=${cancel.status} order_id=${orderData.id}`);
  } else if (Math.random() < Q) {
    const item = (orderData.items || [])[0];
    if (!item) return;
    const refundReq = nextRequest(clientId);
    const refund = http.post(`${BASE}/api/v1/refunds`,
      body({ order_id: orderData.id, reason: 'CHANGE_OF_MIND', items: [{ order_item_id: item.id, quantity: 1 }] }, refundReq),
      { headers: headers(token, refundReq), tags: { name: 'refund' } });
    if (is2xx(refund)) refundOk.add(1);
    console.log(`OLC REFUND status=${refund.status} order_id=${orderData.id}`);
  }
}
