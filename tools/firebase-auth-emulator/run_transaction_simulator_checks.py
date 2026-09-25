#!/usr/bin/env python3
"""Owns an ephemeral demo Auth+Firestore Emulator run. Never uses real credentials/data.

Runs the installed Debug app's same Compose sessions. Writes status-only reports and
screenshots outside the repository; test account secrets exist only in process memory.
"""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid

PROJECT = "demo-walletwise"
BUNDLE = "com.example.walletwise.ios"
AUTH = "http://127.0.0.1:9099"
FIRESTORE = "http://127.0.0.1:8080"
DOCS = "/v1/projects/demo-walletwise/databases/(default)/documents"
PRODUCTION = re.compile(r"firestore\.googleapis\.com|identitytoolkit\.googleapis\.com|securetoken\.googleapis\.com", re.I)
UNSAFE = re.compile(r"\[AuthIsolation\] FAIL|\[TransactionIntegration\] FAIL|-34018|Error loading saved user", re.I)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        raise RuntimeError("STOP: emulator redirect refused")


def request(endpoint, path, method="GET", data=None, owner=True):
    assert endpoint in (AUTH, FIRESTORE)
    headers = {"Content-Type": "application/json"}
    if owner: headers["Authorization"] = "Bearer owner"
    body = None if data is None else json.dumps(data).encode()
    with urllib.request.build_opener(NoRedirect).open(urllib.request.Request(endpoint + path, data=body, method=method, headers=headers), timeout=10) as response:
        value = response.read()
        return json.loads(value) if value else {}


def command(*args, env=None, check=True):
    result = subprocess.run(args, env=env, capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError("Native command failed: " + args[0])
    return result.stdout


def listening(port):
    with socket.socket() as sock:
        sock.settimeout(0.2)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def accounts():
    return request(AUTH, "/identitytoolkit.googleapis.com/v1/projects/demo-walletwise/accounts:batchGet").get("users", [])


def transaction_count():
    total = 0
    for name in ("transactions", "TRANSACTIONS"):
        values = request(FIRESTORE, DOCS + ":runQuery", "POST", {"structuredQuery": {"from": [{"collectionId": name, "allDescendants": True}]}})
        total += sum("document" in value for value in values)
    return total


def seed(path, owner, amount, kind, timestamp=None, image=""):
    fields = {"userId": {"stringValue": owner}, "amount": {"doubleValue": amount} if isinstance(amount, float) else {"integerValue": str(amount)}, "type": {"stringValue": kind}, "category": {"stringValue": "Lương" if kind == "Thu" else "Sinh hoạt"}, "paymentMethod": {"stringValue": "Tiền mặt"}, "note": {"stringValue": "Emulator fixture"}, "timestamp": timestamp or {"integerValue": "1700000000000"}, "imageUrl": {"stringValue": image}}
    request(FIRESTORE, DOCS + "/" + path, "PATCH", {"fields": fields})


def scan(text):
    if PRODUCTION.search(text):
        raise RuntimeError("STOP: production endpoint appeared in app runtime audit")
    if UNSAFE.search(text):
        raise RuntimeError("STOP: integration/isolation/Keychain failure")
    if re.search(r"[A-Za-z0-9._+-]+@example\.invalid|Bearer\s+eyJ|password[=:]\s*\S+", text, re.I):
        raise RuntimeError("STOP: sensitive fixture data appeared in app log")


def phase(args, name, users, actions=None, probe_flag="WALLETWISE_TRANSACTION_TEST_PHASE"):
    command("xcrun", "simctl", "terminate", args.simulator, BUNDLE, check=False)
    log = args.artifacts / (name + ".raw.log")
    with log.open("w") as output, (args.artifacts / (name + ".stderr.log")).open("w") as errors:
        logger = subprocess.Popen(["xcrun", "simctl", "spawn", args.simulator, "log", "stream", "--style", "compact", "--level", "info", "--predicate", 'process == "WalletWiseIOS"'], stdout=output, stderr=errors)
        try:
            time.sleep(1)
            env = {k: v for k, v in os.environ.items() if not k.startswith("SIMCTL_CHILD_WALLETWISE_")}
            flags = {"WALLETWISE_AUTH_EMULATOR": "1", "WALLETWISE_AUTH_PROJECT": PROJECT, "WALLETWISE_AUTH_HOST": "127.0.0.1", "WALLETWISE_AUTH_PORT": "9099", "WALLETWISE_FIRESTORE_EMULATOR": "1", "WALLETWISE_FIRESTORE_PROJECT": PROJECT, "WALLETWISE_FIRESTORE_HOST": "127.0.0.1", "WALLETWISE_FIRESTORE_PORT": "8080", probe_flag: name}
            for key, user in users.items():
                flags["WALLETWISE_TEST_" + key + "_EMAIL"] = user["email"]
                flags["WALLETWISE_TEST_" + key + "_PASSWORD"] = user["password"]
            env.update({"SIMCTL_CHILD_" + k: v for k, v in flags.items()})
            launched = command("xcrun", "simctl", "launch", args.simulator, BUNDLE, env=env)
            pid = int(launched.strip().rsplit(":", 1)[1])
            done = set()
            sockets_checked = False
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                text = log.read_text(errors="replace")
                scan(text)
                try:
                    os.kill(pid, 0)
                except ProcessLookupError:
                    raise RuntimeError("Debug app exited before integration completion")
                for marker, action in (actions or {}).items():
                    if marker not in done and "[TransactionIntegration] READY " + marker in text:
                        action(); done.add(marker)
                if not sockets_checked and "[TransactionIntegration] READY home-content" in text:
                    sockets = command("lsof", "-nP", "-a", "-p", str(pid), "-iTCP", check=False)
                    (args.artifacts / (name + ".sockets.txt")).write_text(sockets)
                    endpoints = re.findall(r"->([^\s]+)", sockets)
                    if not any("127.0.0.1:8080" in endpoint for endpoint in endpoints):
                        raise RuntimeError("No actual SDK localhost Firestore socket evidence")
                    if any(not endpoint.startswith(("127.0.0.1:", "[::1]:")) for endpoint in endpoints):
                        raise RuntimeError("STOP: non-local TCP peer in app process")
                    sockets_checked = True
                if "[TransactionIntegration] COMPLETE " + name in text:
                    time.sleep(1)
                    text = log.read_text(errors="replace"); scan(text)
                    assert text.count("[AuthBootstrap] configured once") == 1
                    assert text.count("[FirestoreBootstrap] emulator configured before requests") == 1
                    assert command("ps", "-p", str(pid), "-o", "pid=", check=False).strip()
                    passes = re.findall(r"\[TransactionIntegration\] PASS ([a-z0-9-]+)", text)
                    if name == "exercise":
                        assert sockets_checked and len(done) == len(actions)
                    result = {"phase": name, "passes": passes, "fail": 0, "skipped": 0, "configure_count": 1, "firestore_local_audit_events": text.count("[FirestoreTransport]"), "auth_local_requests": text.count("[AuthTransport] request endpoint=127.0.0.1:9099"), "production_endpoint_matches": 0, "keychain_34018_matches": 0}
                    print(json.dumps(result), flush=True)
                    return result
                time.sleep(0.1)
            raise RuntimeError("Integration probe timeout: " + name)
        finally:
            logger.send_signal(signal.SIGINT)
            try:
                logger.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logger.terminate(); logger.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--simulator", required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--firebase", type=Path, required=True)
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    args.artifacts = args.artifacts.resolve()
    assert str(args.artifacts).startswith("/private/tmp/")
    args.artifacts.mkdir(parents=True, exist_ok=True)
    assert all(not listening(port) for port in (9099, 8080, 4400, 4500)), "Ports must be free: do not touch another emulator"
    config = Path(__file__).resolve().with_name("firebase.json")
    env = dict(os.environ, JAVA_HOME=args.java_home, FIREBASE_EMULATORS_PATH=str(args.artifacts / "emulator-cache"))
    env["PATH"] = args.java_home + "/bin:" + env["PATH"]
    emulator_log = (args.artifacts / "emulator.raw.log").open("w")
    emulator = subprocess.Popen([str(args.firebase), "emulators:start", "--only", "auth,firestore", "--project", PROJECT, "--config", str(config)], cwd=args.artifacts, env=env, stdout=emulator_log, stderr=subprocess.STDOUT, start_new_session=True)
    initialized = False
    result = {}
    try:
        deadline = time.monotonic() + 150
        while not (listening(9099) and listening(8080)):
            if emulator.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Demo emulator startup failed")
            time.sleep(0.5)
        deadline = time.monotonic() + 30
        while True:
            try:
                assert len(accounts()) == 0 and transaction_count() == 0
                initialized = True; break
            except (urllib.error.URLError, TimeoutError):
                if time.monotonic() > deadline: raise
                time.sleep(0.5)
        users = {}
        for key in ("A", "B"):
            email = "checkpoint5e-" + key.lower() + "-" + uuid.uuid4().hex + "@example.invalid"
            password = uuid.uuid4().hex + uuid.uuid4().hex
            value = request(AUTH, "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-walletwise", "POST", {"email": email, "password": password, "returnSecureToken": True}, owner=False)
            if key == "A":
                request(AUTH, "/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-walletwise", "POST", {"idToken": value["idToken"], "displayName": "Emulator A"}, owner=False)
            users[key] = {"uid": value["localId"], "email": email, "password": password}
        a, b = users["A"]["uid"], users["B"]["uid"]
        seed("users/" + a + "/transactions/a-income", a, 1_000_000, "Thu", {"timestampValue": "2023-11-14T22:13:20Z"})
        seed("users/" + a + "/transactions/a-food", a, 25_000.0, "Chi", {"doubleValue": 1700000000001.0})
        seed("users/" + a + "/transactions/a-rent", a, 300_000, "Chi", image="https://example.invalid/broken.png")
        seed("users/" + b + "/transactions/b-expense", b, 99_000, "Chi")
        seed("TRANSACTIONS/a-legacy-unused", a, 45_000_000, "Thu")
        assert len(accounts()) == 2 and transaction_count() == 5
        def screenshot(name):
            time.sleep(1.5)
            command("xcrun", "simctl", "io", args.simulator, "screenshot", str(args.artifacts / (name + ".png")))
        def empty_b():
            assert transaction_count() == 6, "Logout must not delete Firestore data"
            request(FIRESTORE, DOCS + "/users/" + b + "/transactions/b-expense", "DELETE")
        actions = {
            "home-content": lambda: screenshot("home-content"),
            "home-detail": lambda: screenshot("home-detail"),
            "add-a": lambda: seed("users/" + a + "/transactions/a-extra", a, 75_000, "Chi"),
            "equivalent-a": lambda: seed("users/" + a + "/transactions/a-extra", a, 75_000, "Chi"),
            "empty-b": empty_b,
            "home-empty": lambda: screenshot("home-empty"),
            "seed-legacy-b": lambda: seed("TRANSACTIONS/b-legacy", b, 222_000, "Thu"),
            "change-legacy-b": lambda: seed("TRANSACTIONS/b-legacy", b, 333_000, "Thu"),
        }
        # Clear any demo SDK Keychain session left by a previously interrupted probe.
        result["prepare"] = phase(args, "prepare", users)
        result["exercise"] = phase(args, "exercise", users, actions)
        result["restore"] = phase(args, "restore", users)
        result["cleanup"] = phase(args, "cleanup", users)
    finally:
        command("xcrun", "simctl", "terminate", args.simulator, BUNDLE, check=False)
        try:
            if initialized:
                request(FIRESTORE, "/emulator/v1/projects/demo-walletwise/databases/(default)/documents", "DELETE")
                request(AUTH, "/emulator/v1/projects/demo-walletwise/accounts", "DELETE")
                assert len(accounts()) == 0 and transaction_count() == 0
                result["cleanup_fixtures"] = {"accounts": 0, "transactions": 0}
        finally:
            emulator.send_signal(signal.SIGINT)
            try:
                emulator.wait(timeout=20)
            except subprocess.TimeoutExpired:
                emulator.terminate(); emulator.wait(timeout=10)
            emulator_log.close()
            assert all(not listening(port) for port in (9099, 8080, 4400, 4500))
            result["emulator"] = {"exit_code": emulator.returncode, "ports_closed": [9099, 8080, 4400, 4500]}
            (args.artifacts / "results.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"complete": True, "probe_passes": sum(len(result[key]["passes"]) for key in ("prepare", "exercise", "restore", "cleanup")), "cleanup": result["cleanup_fixtures"], "emulator": result["emulator"]}), flush=True)


if __name__ == "__main__":
    main()
