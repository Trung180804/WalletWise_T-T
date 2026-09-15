#!/usr/bin/env python3
"""Checks the installed Debug app; requires a separately started demo Auth Emulator.

Status output deliberately excludes accounts, tokens, passwords, and reset codes.
Raw artifacts go only to a caller-supplied directory outside the repository.
"""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import urllib.request

PROJECT = "demo-walletwise"
ENDPOINT = "http://127.0.0.1:9099"
BUNDLE = "com.example.walletwise.ios"
PRODUCTION = re.compile(
    r"identitytoolkit\.googleapis\.com|securetoken\.googleapis\.com|"
    r"www\.googleapis\.com/identitytoolkit|identitytoolkit\.google\.com|"
    r"securetoken\.google\.com", re.I
)
UNSAFE = re.compile(r"\[AuthIsolation\] FAIL|\[AuthIntegration\] FAIL|"
                    r"SecItemCopyMatching.*-34018|Error loading saved user", re.I)


class NoAdminRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        raise RuntimeError("STOP: emulator admin redirect refused")


def admin(path, method="GET"):
    # 'owner' is the Emulator's public admin sentinel, never a real credential.
    request = urllib.request.Request(ENDPOINT + path, method=method,
                                     headers={"Authorization": "Bearer owner"})
    with urllib.request.build_opener(NoAdminRedirect).open(request, timeout=5) as response:
        return json.load(response)


def account_count():
    # This is a LOCAL Emulator routing path, not a production hostname connection.
    data = admin("/identitytoolkit.googleapis.com/v1/projects/" + PROJECT + "/accounts:batchGet")
    return len(data.get("users", []))


def command(*arguments, environment=None, check=True):
    result = subprocess.run(arguments, env=environment, capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError("native command failed: " + arguments[0])
    return result.stdout


def scan(text):
    if PRODUCTION.search(text):
        raise RuntimeError("STOP: production Auth endpoint found in runtime log")
    if UNSAFE.search(text):
        raise RuntimeError("STOP: isolation/integration/Keychain failure in runtime log")


def phase(simulator, artifacts, name):
    command("xcrun", "simctl", "terminate", simulator, BUNDLE, check=False)
    log = artifacts / (name + ".raw.log")
    stderr = artifacts / (name + ".log-collection.stderr.log")
    with log.open("w") as output, stderr.open("w") as errors:
        logger = subprocess.Popen([
            "xcrun", "simctl", "spawn", simulator, "log", "stream", "--style", "compact",
            "--level", "info", "--predicate", 'process == "WalletWiseIOS"'
        ], stdout=output, stderr=errors)
        try:
            time.sleep(1)
            environment = dict(os.environ)
            environment.update({
                "SIMCTL_CHILD_WALLETWISE_AUTH_EMULATOR": "1",
                "SIMCTL_CHILD_WALLETWISE_AUTH_PROJECT": PROJECT,
                "SIMCTL_CHILD_WALLETWISE_AUTH_HOST": "127.0.0.1",
                "SIMCTL_CHILD_WALLETWISE_AUTH_PORT": "9099",
                "SIMCTL_CHILD_WALLETWISE_AUTH_TEST_PHASE": name,
            })
            launched = command("xcrun", "simctl", "launch", simulator, BUNDLE, environment=environment)
            pid = int(launched.strip().rsplit(":", 1)[1])
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                text = log.read_text(errors="replace")
                scan(text)
                if "[AuthIntegration] COMPLETE " + name in text:
                    time.sleep(2)
                    text = log.read_text(errors="replace")
                    scan(text)
                    if not command("ps", "-p", str(pid), "-o", "pid=", check=False).strip():
                        raise RuntimeError("app process disappeared after probe")
                    if text.count("[AuthBootstrap] configured once") != 1:
                        raise RuntimeError("bootstrap marker count must be exactly one per process")
                    if name in ("exercise", "signed-out"):
                        command("xcrun", "simctl", "io", simulator, "screenshot", str(artifacts / (name + ".png")))
                    passes = re.findall(r"\[AuthIntegration\] PASS ([a-z0-9-]+)", text)
                    endpoint_count = text.count("[AuthTransport] request endpoint=127.0.0.1:9099")
                    if name in ("exercise", "restore-reset-logout") and endpoint_count == 0:
                        raise RuntimeError("no Firebase SDK emulator transport audit evidence")
                    result = {"phase": name, "pid": pid, "alive": True, "passes": passes,
                              "failures": 0, "production_endpoint_matches": 0,
                              "keychain_34018_matches": 0, "configure_count": 1,
                              "emulator_transport_requests": endpoint_count}
                    print(json.dumps(result), flush=True)
                    return result
                time.sleep(0.1)
            raise RuntimeError("probe did not complete: " + name)
        finally:
            logger.send_signal(signal.SIGINT)
            try:
                logger.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logger.terminate()
                logger.wait(timeout=5)


def guard_phase(simulator, artifacts, name, flags, blocked):
    command("xcrun", "simctl", "terminate", simulator, BUNDLE, check=False)
    log = artifacts / (name + ".raw.log")
    with log.open("w") as output, (artifacts / (name + ".stderr.log")).open("w") as errors:
        logger = subprocess.Popen([
            "xcrun", "simctl", "spawn", simulator, "log", "stream", "--style", "compact",
            "--level", "info", "--predicate", 'process == "WalletWiseIOS"'
        ], stdout=output, stderr=errors)
        try:
            time.sleep(1)
            environment = {key: value for key, value in os.environ.items()
                           if not key.startswith("SIMCTL_CHILD_WALLETWISE_AUTH_")}
            environment.update({"SIMCTL_CHILD_" + key: value for key, value in flags.items()})
            launched = command("xcrun", "simctl", "launch", simulator, BUNDLE, environment=environment)
            pid = int(launched.strip().rsplit(":", 1)[1])
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                text = log.read_text(errors="replace")
                scan(text)
                if "[AuthBootstrap] configured once; Auth adapter disabled" in text:
                    time.sleep(2)
                    text = log.read_text(errors="replace")
                    scan(text)
                    if ("emulator guard rejected configuration" in text) != blocked:
                        raise RuntimeError("unexpected emulator guard decision")
                    if "[AuthTransport]" in text or "[AuthIntegration]" in text:
                        raise RuntimeError("guard allowed an Auth request/probe")
                    if name.startswith("release-") and "127.0.0.1:9099" in text:
                        raise RuntimeError("Release log contains an emulator endpoint")
                    if text.count("[AuthBootstrap] configured once") != 1:
                        raise RuntimeError("duplicate bootstrap")
                    if not command("ps", "-p", str(pid), "-o", "pid=", check=False).strip():
                        raise RuntimeError("guard-check app process disappeared")
                    if name == "release-no-emulator-flag":
                        command("xcrun", "simctl", "io", simulator, "screenshot", str(artifacts / "release-login.png"))
                    result = {"guard_check": name, "pass": True, "alive": True,
                              "adapter_disabled": True, "emulator_requests": 0,
                              "production_endpoint_matches": 0, "configure_count": 1}
                    print(json.dumps(result), flush=True)
                    return result
                time.sleep(0.1)
            raise RuntimeError("guard check timed out")
        finally:
            logger.send_signal(signal.SIGINT)
            try:
                logger.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logger.terminate()
                logger.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--simulator", required=True)
    parser.add_argument("--app", required=True, type=Path)
    parser.add_argument("--artifacts", required=True, type=Path)
    parser.add_argument("--guard-mode", choices=("debug", "release"))
    args = parser.parse_args()
    artifacts = args.artifacts.resolve()
    repository = Path(__file__).resolve().parents[2]
    if repository == artifacts or repository in artifacts.parents:
        raise RuntimeError("artifacts must be outside the repository")
    artifacts.mkdir(parents=True, exist_ok=True)
    if args.guard_mode:
        command("xcrun", "simctl", "install", args.simulator, str(args.app))
        valid = {"WALLETWISE_AUTH_EMULATOR": "1", "WALLETWISE_AUTH_PROJECT": PROJECT,
                 "WALLETWISE_AUTH_HOST": "127.0.0.1", "WALLETWISE_AUTH_PORT": "9099"}
        results = []
        if args.guard_mode == "release":
            # Normal Release smoke launch has no emulator flags or credentials.
            results.append(guard_phase(args.simulator, artifacts, "release-no-emulator-flag", {}, False))
        else:
            results.append(guard_phase(args.simulator, artifacts, "debug-no-emulator-flag", {}, False))
            for key, wrong in [("WALLETWISE_AUTH_PROJECT", "other-project"),
                               ("WALLETWISE_AUTH_HOST", "localhost"), ("WALLETWISE_AUTH_PORT", "9100")]:
                flags = dict(valid)
                flags[key] = wrong
                results.append(guard_phase(args.simulator, artifacts, "debug-rejected-" + key.lower(), flags, True))
        command("xcrun", "simctl", "terminate", args.simulator, BUNDLE, check=False)
        (artifacts / "guard-results.json").write_text(json.dumps(results, indent=2) + "\n")
        return
    results = []
    started = False
    try:
        if account_count() != 0:
            raise RuntimeError("start with a fresh, empty demo Auth Emulator")
        started = True
        command("xcrun", "simctl", "install", args.simulator, str(args.app))
        # Clear only our named demo app's fixture/Keychain session from an interrupted run.
        phase(args.simulator, artifacts, "cleanup")
        results.append(phase(args.simulator, artifacts, "exercise"))
        if account_count() != 1:
            raise RuntimeError("double submit must create exactly one emulator account")
        print(json.dumps({"check": "exactly-one-emulator-account", "pass": True}), flush=True)
        results.append(phase(args.simulator, artifacts, "restore-reset-logout"))
        codes = admin("/emulator/v1/projects/" + PROJECT + "/oobCodes").get("oobCodes", [])
        if len(codes) != 1 or codes[0].get("requestType") != "PASSWORD_RESET":
            raise RuntimeError("expected exactly one Emulator password-reset OOB request")
        print(json.dumps({"check": "one-emulator-password-reset-code-no-email-delivery", "pass": True}), flush=True)
        results.append(phase(args.simulator, artifacts, "signed-out"))
    finally:
        if started:
            command("xcrun", "simctl", "terminate", args.simulator, BUNDLE, check=False)
            admin("/emulator/v1/projects/" + PROJECT + "/accounts", "DELETE")
            count = account_count()
            print(json.dumps({"cleanup_emulator_accounts_remaining": count}), flush=True)
            if count:
                raise RuntimeError("Emulator account cleanup failed")
    (artifacts / "integration-results.json").write_text(json.dumps(results, indent=2) + "\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Only our status messages, not response bodies or secret-bearing native output.
        print("FAIL: " + str(error), flush=True)
        raise SystemExit(1)
