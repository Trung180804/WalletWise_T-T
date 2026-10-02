// Loaded before Firebase SDK/CLI. No credentials and no non-loopback transport.
const http = require('node:http');
const https = require('node:https');
const net = require('node:net');
const fs = require('node:fs');
const ports = new Set([8080, 9099, 8787, 4400, 4500, 9150]);
const hosts = new Set(['127.0.0.1', 'localhost', '::1', '[::1]', '0.0.0.0']);
const audit = global.__supportNetworkAudit = { allowed: 0, denied: 0, production_endpoint_matches: 0, endpoints: {} };
function check(host, port) {
  host = String(host || 'localhost').toLowerCase();
  port = Number(port);
  if (!hosts.has(host) || !ports.has(port)) {
    audit.denied++;
    if (/googleapis\.com|firebaseio\.com|firebaseapp\.com/.test(host)) audit.production_endpoint_matches++;
    throw new Error('EMULATOR_ONLY_NETWORK_GUARD');
  }
  audit.allowed++;
  const key = host + ':' + port;
  audit.endpoints[key] = (audit.endpoints[key] || 0) + 1;
}
function requestTarget(arg, options, secure) {
  if (typeof arg === 'string' || arg instanceof URL) {
    const url = new URL(arg);
    return [options?.hostname || url.hostname, options?.port || url.port || (secure ? 443 : 80)];
  }
  const opts = arg || {};
  return [opts.hostname || opts.host || 'localhost', opts.port || (secure ? 443 : 80)];
}
for (const [module, secure] of [[http, false], [https, true]]) {
  const original = module.request;
  module.request = function(arg, options, callback) {
    check(...requestTarget(arg, typeof options === 'object' ? options : undefined, secure));
    return original.apply(this, arguments);
  };
  module.get = function() { const req = module.request.apply(this, arguments); req.end(); return req; };
}
const originalConnect = net.Socket.prototype.connect;
net.Socket.prototype.connect = function(...args) {
  const value = Array.isArray(args[0]) ? args[0][0] : args[0];
  if (value && typeof value === 'object' && value.path) {
    if (!String(value.path).startsWith('\\\\.\\pipe\\')) throw new Error('NON_EMULATOR_SOCKET');
  } else if (typeof value === 'object') check(value.host || 'localhost', value.port);
  else if (typeof value === 'number') check(typeof args[1] === 'string' ? args[1] : 'localhost', value);
  else if (typeof value === 'string' && !value.startsWith('\\\\.\\pipe\\')) throw new Error('NON_EMULATOR_SOCKET');
  return originalConnect.apply(this, args);
};
const originalFetch = global.fetch;
global.fetch = function(input, options) {
  const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
  check(url.hostname, url.port || (url.protocol === 'https:' ? 443 : 80));
  return originalFetch(input, options);
};
process.on('exit', () => {
  if (process.env.SUPPORT_AUDIT_FILE) fs.writeFileSync(process.env.SUPPORT_AUDIT_FILE, JSON.stringify(audit, null, 2));
});

setInterval(() => { if (process.env.SUPPORT_AUDIT_FILE) fs.writeFileSync(process.env.SUPPORT_AUDIT_FILE, JSON.stringify(audit, null, 2)); }, 1000).unref();
