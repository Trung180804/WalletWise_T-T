import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, getIdTokenResult } from 'firebase/auth';
import { initializeFirestore, connectFirestoreEmulator, collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc,
  query, orderBy, onSnapshot, runTransaction, writeBatch, serverTimestamp, terminate, setLogLevel } from 'firebase/firestore';

const PROJECT = 'demo-walletwise';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, '.artifacts/support-chat-emulator');
fs.mkdirSync(OUT, { recursive: true });
if (!global.__supportNetworkAudit) throw new Error('Network guard must be preloaded');
if (process.env.GOOGLE_APPLICATION_CREDENTIALS || process.env.FIREBASE_TOKEN) throw new Error('Real credentials forbidden');
setLogLevel('silent');
const clients = [];
const removers = new Set();
const results = [];
const stats = { customer_messages_seen: 0, staff_replies_sent: 0, listener_events: 0, errors: 0 };
const userText = 'Tôi cần hỗ trợ kiểm tra giao dịch';
const staffText = 'WalletWise đã nhận được yêu cầu của bạn';
let fixture;
let cleaned = false;
let phase = "setup";
const adminStatuses = [];

async function localAdmin(service, route, method = 'GET', body, authorization = 'owner') {
  const port = service === 'auth' ? 9099 : 8080;
  const response = await fetch('http://127.0.0.1:' + port + route, {
    method, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + authorization },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
  const payload = await response.json().catch(() => ({}));
  adminStatuses.push({ service, method, status: response.status, phase });
  return { ok: response.ok, status: response.status, payload };
}
async function provision(label) {
  const app = initializeApp({ projectId: PROJECT, apiKey: 'fake-support-emulator-key', appId: '1:1234567890:web:support-test' }, 'support-' + label + '-' + randomUUID());
  const auth = getAuth(app);
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = initializeFirestore(app, { experimentalForceLongPolling: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  const email = label + '-' + randomUUID() + '@example.test';
  const password = randomUUID() + 'Aa7!';
  const client = { app, auth, db, email, password };
  clients.push(client);
  phase = "provision-" + label;
  const credential = await createUserWithEmailAndPassword(auth, email, password);
  client.uid = credential.user.uid;
  return client;
}
async function setup() {
  const before = await localAdmin('auth', '/identitytoolkit.googleapis.com/v1/projects/' + PROJECT + '/accounts:batchGet?maxResults=1000');
  assert.ok(before.ok && (before.payload.users || []).length === 0, 'Requires isolated empty Auth Emulator');
  fixture = { a: await provision('customer-a'), b: await provision('staff-b'), c: await provision('customer-c'), fake: await provision('role-impostor') };
  phase = "staff-claim-admin";
  const claim = await localAdmin('auth', '/identitytoolkit.googleapis.com/v1/projects/' + PROJECT + '/accounts:update',
    'POST', { localId: fixture.b.uid, customAttributes: JSON.stringify({ supportAgent: true }) });
  assert.ok(claim.ok, 'Local staff claim assignment failed');
  phase = "staff-claim-refresh";
  assert.ok((await getIdTokenResult(fixture.b.auth.currentUser, true)).claims.supportAgent === true, 'Staff claim missing after refresh');
}
function parent(client, uid = client.uid) { return doc(client.db, 'supportConversations', uid); }
function messages(client, uid = client.uid) { return collection(client.db, 'supportConversations', uid, 'messages'); }
async function send(client, uid, role, id, content) {
  const parentRef = parent(client, uid);
  const messageRef = doc(messages(client, uid), id);
  return runTransaction(client.db, async transaction => {
    const existing = await transaction.get(messageRef);
    if (existing.exists()) return;
    const conversation = await transaction.get(parentRef);
    const data = {
      userId: uid, userEmail: conversation.exists() ? conversation.data().userEmail : client.email,
      status: role === 'user' ? 'waiting_staff' : 'waiting_user', updatedAt: serverTimestamp(),
      lastMessagePreview: content.trim().slice(0, 160), lastSenderRole: role
    };
    if (!conversation.exists()) data.createdAt = serverTimestamp();
    transaction.set(parentRef, data, { merge: true });
    transaction.set(messageRef, { id, senderId: client.uid, senderRole: role, content: content.trim(),
      createdAt: serverTimestamp(), clientRequestId: id, status: 'sent' });
  });
}
async function denied(action) {
  let rejected = false;
  try { await action(); } catch (error) { rejected = error.code === 'permission-denied'; }
  assert.ok(rejected, 'Expected permission-denied');
}
async function test(label, fn) {
  phase = label;
  try { await fn(); results.push({ test: label, status: 'PASS' }); console.log('PASS ' + label); }
  catch (error) { results.push({ test: label, status: 'FAIL', code: error.code || error.name }); console.log('FAIL ' + label); throw new Error('Test failed: ' + label); }
}
function waitSnapshot(client, uid, predicate) {
  let remove;
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { remove?.(); removers.delete(remove); reject(new Error('Realtime timeout')); }, 20000);
    remove = onSnapshot(query(messages(client, uid), orderBy('createdAt')), snapshot => {
      stats.listener_events++;
      if (predicate(snapshot)) { clearTimeout(timer); remove(); removers.delete(remove); resolve(snapshot); }
    }, () => { clearTimeout(timer); remove?.(); removers.delete(remove); reject(new Error('Realtime listener denied')); });
    removers.add(remove);
  });
  return promise;
}
async function invalidMessage(client, content, role = 'user') {
  const id = randomUUID();
  const batch = writeBatch(client.db);
  batch.update(parent(client), { status: role === 'user' ? 'waiting_staff' : 'waiting_user', lastSenderRole: role, updatedAt: serverTimestamp() });
  batch.set(doc(messages(client), id), { id, senderId: client.uid, senderRole: role, content, createdAt: serverTimestamp(), clientRequestId: id });
  await batch.commit();
}
async function suite() {
  const { a, b, c, fake } = fixture;
  await test('customer_user_message', () => send(a, a.uid, 'user', 'customer-request-1', userText));
  await test('staff_lists_and_reads_customer', async () => {
    const list = await getDocs(collection(b.db, 'supportConversations'));
    assert.ok(list.docs.some(d => d.id === a.uid));
    assert.ok((await getDoc(parent(b, a.uid))).data().userId === a.uid);
  });
  await test('staff_reply_and_customer_realtime', async () => {
    const received = waitSnapshot(a, a.uid, s => s.docs.some(d => d.data().senderRole === 'staff' && d.data().createdAt));
    await send(b, a.uid, 'staff', 'staff-reply-1', staffText);
    const snapshot = await received;
    assert.ok(snapshot.docs.some(d => d.data().content === staffText));
  });
  await test('server_timestamp_order', async () => {
    const list = await getDocs(query(messages(a), orderBy('createdAt')));
    assert.ok(list.size === 2 && list.docs[0].data().senderRole === 'user' && list.docs[1].data().senderRole === 'staff');
    assert.ok(list.docs[0].data().createdAt.toMillis() <= list.docs[1].data().createdAt.toMillis());
    assert.ok((await getDoc(parent(a))).data().status === 'waiting_user');
  });
  await test('stable_id_retry_preserves_message_and_timestamp', async () => {
    const before = await getDoc(doc(messages(a), 'customer-request-1'));
    const conversationBefore = await getDoc(parent(a));
    await send(a, a.uid, 'user', 'customer-request-1', userText);
    const after = await getDoc(doc(messages(a), 'customer-request-1'));
    assert.ok(before.data().createdAt.isEqual(after.data().createdAt) && before.data().content === after.data().content);
    assert.ok((await getDocs(messages(a))).size === 2);
    assert.ok(conversationBefore.data().updatedAt.isEqual((await getDoc(parent(a))).data().updatedAt));
  });
  const anonymous = initializeApp({ projectId: PROJECT, apiKey: 'fake-support-key' }, 'anonymous-' + randomUUID());
  const anonymousDb = initializeFirestore(anonymous, { experimentalForceLongPolling: true });
  connectFirestoreEmulator(anonymousDb, '127.0.0.1', 8080);
  clients.push({ app: anonymous, db: anonymousDb });
  await test('unauthenticated_read_denied', () => denied(() => getDoc(doc(anonymousDb, 'supportConversations', a.uid))));
  await test('unauthenticated_write_denied', () => denied(() => setDoc(doc(anonymousDb, 'supportConversations', 'anonymous'), { userId: 'anonymous' })));
  await test('unauthenticated_message_read_denied', () => denied(() => getDocs(collection(anonymousDb, 'supportConversations', a.uid, 'messages'))));
  await test('customer_a_reads_c_denied', () => denied(() => getDoc(parent(a, c.uid))));
  await test('customer_c_reads_a_denied', () => denied(() => getDoc(parent(c, a.uid))));
  await test('customer_cross_account_messages_denied', () => denied(() => getDocs(messages(c, a.uid))));
  await test('customer_conversation_listing_denied', () => denied(() => getDocs(collection(a.db, 'supportConversations'))));
  await test('customer_staff_message_denied', () => denied(() => invalidMessage(a, 'forged staff', 'staff')));
  await test('customer_spoofed_sender_denied', () => denied(async () => {
    const id = randomUUID();
    await setDoc(doc(messages(a), id), { id, senderId: b.uid, senderRole: 'user', content: 'spoof', createdAt: serverTimestamp(), clientRequestId: id });
  }));
  await test('customer_close_denied', () => denied(() => updateDoc(parent(a), { status: 'closed', updatedAt: serverTimestamp() })));
  await test('customer_staff_metadata_denied', () => denied(() => updateDoc(parent(a), { lastSenderRole: 'staff', status: 'waiting_user', updatedAt: serverTimestamp() })));
  await test('customer_claim_field_in_conversation_denied', () => denied(() => updateDoc(parent(a), { supportAgent: true, updatedAt: serverTimestamp() })));
  await test('customer_cannot_self_grant_auth_claim', async () => {
    const token = await a.auth.currentUser.getIdToken();
    await localAdmin('auth', '/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-support-emulator-key',
      'POST', { idToken: token, customAttributes: JSON.stringify({ supportAgent: true }) }, token);
    assert.ok((await getIdTokenResult(a.auth.currentUser, true)).claims.supportAgent !== true);
    await denied(() => getDocs(collection(a.db, 'supportConversations')));
  });
  await test('document_role_without_custom_claim_denied', async () => {
    await setDoc(doc(fake.db, 'users', fake.uid), { role: 'staff', supportAgent: true });
    await denied(() => getDoc(parent(fake, a.uid)));
    await denied(() => getDocs(collection(fake.db, 'supportConversations')));
  });
  await test('empty_message_denied', () => denied(() => invalidMessage(a, '')));
  await test('whitespace_message_denied', () => denied(() => invalidMessage(a, '  \n\t')));
  await test('oversized_message_denied', () => denied(() => invalidMessage(a, 'x'.repeat(4001))));
  await test('existing_message_update_denied', () => denied(() => updateDoc(doc(messages(a), 'customer-request-1'), { content: 'overwrite', createdAt: serverTimestamp() })));
  await test('existing_message_delete_denied', () => denied(() => deleteDoc(doc(messages(b, a.uid), 'customer-request-1'))));
  await test('staff_outside_support_write_denied', () => denied(() => setDoc(doc(b.db, 'users', b.uid), { role: 'staff' })));
  await test('staff_outside_support_delete_denied', () => denied(() => deleteDoc(doc(b.db, 'users', fake.uid))));
  await test('staff_updates_supported_statuses', async () => {
    for (const status of ['open', 'waiting_staff', 'waiting_user', 'closed']) await updateDoc(parent(b, a.uid), { status, updatedAt: serverTimestamp() });
    assert.ok((await getDoc(parent(b, a.uid))).data().status === 'closed');
  });
  await test('staff_invalid_status_denied', () => denied(() => updateDoc(parent(b, a.uid), { status: 'online', updatedAt: serverTimestamp() })));
  await test('staff_customer_identity_change_denied', () => denied(() => updateDoc(parent(b, a.uid), { userEmail: 'changed@example.test', updatedAt: serverTimestamp() })));
  await test('customer_cannot_reopen_closed_conversation', () => denied(() => send(a, a.uid, 'user', 'closed-request', userText)));
  await test('production_endpoint_matches_zero', async () => assert.ok(global.__supportNetworkAudit.production_endpoint_matches === 0 && global.__supportNetworkAudit.denied === 0));
}
async function cleanup() {
  if (cleaned) return;
  cleaned = true;
  for (const remove of removers) remove();
  removers.clear();
  await Promise.all(clients.map(async c => { await terminate(c.db).catch(() => {}); await deleteApp(c.app).catch(() => {}); }));
  phase = "cleanup-delete";
  const clearAuth = await localAdmin('auth', '/emulator/v1/projects/' + PROJECT + '/accounts', 'DELETE');
  const clearDb = await localAdmin('firestore', '/emulator/v1/projects/' + PROJECT + '/databases/(default)/documents', 'DELETE');
  assert.ok(clearAuth.ok && clearDb.ok, 'Fixture cleanup failed');
  phase = "cleanup-verify";
  const accounts = await localAdmin('auth', '/identitytoolkit.googleapis.com/v1/projects/' + PROJECT + '/accounts:batchGet?maxResults=1000');
  const conversations = await localAdmin('firestore', '/v1/projects/' + PROJECT + '/databases/(default)/documents/supportConversations');
  const messageQuery = await localAdmin('firestore', '/v1/projects/' + PROJECT + '/databases/(default)/documents:runQuery',
    'POST', { structuredQuery: { from: [{ collectionId: 'messages', allDescendants: true }] } });
  const cleanupResult = { test_accounts_remaining: (accounts.payload.users || []).length,
    test_conversations_remaining: (conversations.payload.documents || []).length,
    test_messages_remaining: Array.isArray(messageQuery.payload) ? messageQuery.payload.filter(item => item.document).length : -1 };
  assert.ok(accounts.ok && conversations.ok && messageQuery.ok && Object.values(cleanupResult).every(v => v === 0), 'Fixtures remain');
  fs.writeFileSync(path.join(OUT, process.argv[2] + '-cleanup.json'), JSON.stringify(cleanupResult, null, 2));
  // Redact only files owned by this checkpoint before exposing diagnostics.
  const secrets = clients.flatMap(c => [c.uid, c.email, c.password]).filter(Boolean);
  for (const name of ['firebase-out.log', 'firebase-err.log', 'firestore-debug.log', 'firebase-debug.log']) {
    const log = path.join(OUT, name);
    if (fs.existsSync(log)) {
      let content = fs.readFileSync(log, 'utf8');
      for (const secret of secrets) content = content.split(secret).join('[fixture-redacted]');
      content = content.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g, '[token-redacted]');
      fs.writeFileSync(log, content);
    }
  }
  console.log('Cleanup PASS accounts=0 conversations=0 messages=0');
}
async function serve() {
  const { a, b, c } = fixture;
  const seen = new Set();
  const remove = onSnapshot(query(messages(b, a.uid), orderBy('createdAt')), snapshot => {
    stats.listener_events++;
    for (const entry of snapshot.docs) {
      if (entry.data().senderRole !== 'user' || seen.has(entry.id)) continue;
      seen.add(entry.id); stats.customer_messages_seen++;
      send(b, a.uid, 'staff', 'staff-' + entry.id, staffText).then(() => stats.staff_replies_sent++).catch(() => stats.errors++);
    }
  }, () => stats.errors++);
  removers.add(remove);
  let finish;
  const finished = new Promise(resolve => { finish = resolve; });
  const server = http.createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    try {
      if (request.method === 'GET' && request.url === '/fixture') {
        response.end(JSON.stringify({ a: { email: a.email, password: a.password, uid: a.uid },
          c: { email: c.email, password: c.password, uid: c.uid }, project: PROJECT }));
      } else if (request.method === 'GET' && request.url === '/metrics') {
        response.end(JSON.stringify({ ...stats, network: global.__supportNetworkAudit }));
      } else if (request.method === 'POST' && request.url === '/late-reply') {
        await send(b, a.uid, 'staff', 'staff-late-reply', 'Phản hồi kiểm thử sau đăng xuất');
        stats.staff_replies_sent++;
        response.end(JSON.stringify({ ok: true }));
      } else if (request.method === 'POST' && request.url === '/finish') {
        response.end(JSON.stringify({ ok: true }));
        finish();
      } else { response.statusCode = 404; response.end('{}'); }
    } catch { stats.errors++; response.statusCode = 500; response.end('{"error":"fixture-operation-failed"}'); }
  });
  await new Promise(resolve => server.listen(8787, '127.0.0.1', resolve));
  console.log('Staff harness ready on loopback; credentials stay in memory');
  process.once('SIGINT', finish);
  await finished;
  await new Promise(resolve => { server.close(resolve); server.closeIdleConnections(); });
  fs.writeFileSync(path.join(OUT, 'mobile-staff-metrics.json'), JSON.stringify({ ...stats, network: global.__supportNetworkAudit }, null, 2));
  assert.ok(stats.customer_messages_seen >= 1 && stats.staff_replies_sent >= 1 && stats.errors === 0, 'Mobile staff exchange incomplete');
}
let failed = false;
try { await setup(); if (process.argv[2] === 'suite') await suite(); else if (process.argv[2] === 'serve') await serve(); else throw new Error('Unknown mode'); }
catch (error) { failed = true; console.log('Harness FAIL phase=' + phase + ' code=' + (error.code || error.name)); }
finally {
  try { await cleanup(); } catch { failed = true; console.log('Cleanup FAIL phase=' + phase); }
  fs.writeFileSync(path.join(OUT, process.argv[2] + '-report.json'), JSON.stringify({ passed: results.filter(r => r.status === 'PASS').length,
    failed: failed ? Math.max(1, results.filter(r => r.status === 'FAIL').length) : 0, phase, admin_statuses: adminStatuses, results, network: global.__supportNetworkAudit }, null, 2));
}
process.exitCode = failed ? 1 : 0;
