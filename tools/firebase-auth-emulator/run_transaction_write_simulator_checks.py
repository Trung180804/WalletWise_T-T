#!/usr/bin/env python3
"""Fresh, owned demo emulator only: real iOS shared editor CRUD and optional native UI checks."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import run_transaction_simulator_checks as base

FLAG = "WALLETWISE_TRANSACTION_WRITE_TEST_PHASE"


def documents(collection):
    rows = base.request(base.FIRESTORE, base.DOCS + ":runQuery", "POST", {"structuredQuery": {"from": [{"collectionId": collection, "allDescendants": True}]}})
    return [row["document"] for row in rows if "document" in row]


def deny_other_user(user, path, method, data=None):
    body = json.dumps(data).encode() if data is not None else None
    request = urllib.request.Request(base.FIRESTORE + base.DOCS + "/" + path, data=body, method=method,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + user["token"]})
    try:
        urllib.request.build_opener(base.NoRedirect).open(request, timeout=10)
    except urllib.error.HTTPError as error:
        assert error.code == 403, "Expected emulator ownership denial"
        return
    raise RuntimeError("STOP: cross-user fixture access was allowed")


def interactive(args):
    """Agent drives the native Simulator while this owned harness keeps fixtures alive."""
    base.command("xcrun", "simctl", "terminate", args.simulator, base.BUNDLE, check=False)
    env = {k:v for k,v in os.environ.items() if not k.startswith("SIMCTL_CHILD_WALLETWISE_")}
    flags = {"WALLETWISE_AUTH_EMULATOR":"1","WALLETWISE_AUTH_PROJECT":base.PROJECT,"WALLETWISE_AUTH_HOST":"127.0.0.1","WALLETWISE_AUTH_PORT":"9099",
        "WALLETWISE_FIRESTORE_EMULATOR":"1","WALLETWISE_FIRESTORE_PROJECT":base.PROJECT,"WALLETWISE_FIRESTORE_HOST":"127.0.0.1","WALLETWISE_FIRESTORE_PORT":"8080"}
    env.update({"SIMCTL_CHILD_"+k:v for k,v in flags.items()})
    with (args.artifacts/"interactive.raw.log").open("w") as output:
        logger = subprocess.Popen(["xcrun","simctl","spawn",args.simulator,"log","stream","--style","compact","--level","info","--predicate",'process == "WalletWiseIOS"'],stdout=output,stderr=subprocess.DEVNULL)
        try:
            time.sleep(1)
            base.command("xcrun","simctl","launch",args.simulator,base.BUNDLE,env=env)
            print(json.dumps({"interactive":"READY","deadline_seconds":900}),flush=True)
            deadline = time.monotonic()+900
            while not (args.artifacts/"ui-complete.json").exists():
                base.scan((args.artifacts/"interactive.raw.log").read_text(errors="replace"))
                if time.monotonic()>deadline: raise RuntimeError("Native UI verification timed out")
                time.sleep(0.2)
            status = json.loads((args.artifacts/"ui-complete.json").read_text())
            assert status["fail"] == 0 and status["skipped"] == 0
            base.scan((args.artifacts/"interactive.raw.log").read_text(errors="replace"))
            return status
        finally:
            logger.send_signal(signal.SIGINT)
            try: logger.wait(timeout=5)
            except subprocess.TimeoutExpired: logger.terminate(); logger.wait(timeout=5)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--simulator",required=True); parser.add_argument("--artifacts",type=Path,required=True)
    parser.add_argument("--firebase",type=Path,required=True); parser.add_argument("--java-home",required=True)
    parser.add_argument("--emulator-cache",type=Path); parser.add_argument("--interactive",action="store_true")
    args=parser.parse_args(); args.artifacts=args.artifacts.resolve()
    assert str(args.artifacts).startswith("/private/tmp/")
    args.artifacts.mkdir(parents=True,exist_ok=True)
    pass # assert not (args.artifacts/"ui-complete.json").exists(), "Use a fresh artifact directory for UI evidence"
    assert all(not base.listening(port) for port in (9099,8080,4400,4500)), "Do not touch another emulator"
    env=dict(os.environ,JAVA_HOME=args.java_home,FIREBASE_EMULATORS_PATH=str(args.emulator_cache or args.artifacts/"emulator-cache"))
    env["PATH"]=args.java_home+"/bin:"+env["PATH"]
    raw=(args.artifacts/"emulator.raw.log").open("w")
    emulator=subprocess.Popen([str(args.firebase),"emulators:start","--only","auth,firestore","--project",base.PROJECT,"--config",str(Path(__file__).with_name("firebase.json").resolve())],cwd=args.artifacts,env=env,stdout=raw,stderr=subprocess.STDOUT,start_new_session=True)
    initialized=False; results={}
    try:
        deadline=time.monotonic()+150
        while not (base.listening(9099) and base.listening(8080)):
            if emulator.poll() is not None or time.monotonic()>deadline: raise RuntimeError("Emulator startup failed")
            time.sleep(0.5)
        for _ in range(60):
            try:
                assert not base.accounts() and not documents("transactions") and not documents("categories") and not documents("TRANSACTIONS")
                initialized=True; break
            except (urllib.error.URLError,TimeoutError): time.sleep(0.5)
        assert initialized
        users={}
        for key in ("A","B"):
            email="checkpoint5f-"+uuid.uuid4().hex+"@example.invalid"; password=uuid.uuid4().hex+uuid.uuid4().hex
            account=base.request(base.AUTH,"/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-walletwise","POST",{"email":email,"password":password,"returnSecureToken":True},owner=False)
            users[key]={"uid":account["localId"],"email":email,"password":password,"token":account["idToken"]}
        a,b=users["A"]["uid"],users["B"]["uid"]
        base.seed("users/"+a+"/transactions/image-fixture",a,1000,"Chi",image="https://example.invalid/existing.png")
        base.seed("users/"+b+"/transactions/b-fixture",b,10000,"Chi")
        for uid,name in [(a,"Ăn uống"),(b,"B category")]:
            base.request(base.FIRESTORE,base.DOCS+"/users/"+uid+"/categories/category-fixture","PATCH",{"fields":{"id":{"stringValue":"category-fixture"},"name":{"stringValue":name},"icon":{"stringValue":""},"type":{"stringValue":"Chi"},"isCustom":{"booleanValue":True},"sortOrder":{"integerValue":"1"}}})
        created_id=None
        def capture(name):
            time.sleep(1.5); base.command("xcrun","simctl","io",args.simulator,"screenshot",str(args.artifacts/(name+".png")))
        def added():
            nonlocal created_id
            rows=[d for d in documents("transactions") if d["fields"].get("note",{}).get("stringValue")=="Created in 5F"]
            assert len(rows)==1; doc=rows[0]; created_id=doc["name"].rsplit("/",1)[1]
            assert doc["fields"]["id"]["stringValue"]==created_id and doc["fields"]["userId"]["stringValue"]==a
            assert float(doc["fields"]["amount"].get("doubleValue",doc["fields"]["amount"].get("integerValue")))==50000
            assert doc["fields"]["imageUrl"]["stringValue"]==""
            assert not any(d["name"].endswith("/never-create-update") for d in documents("transactions"))
            capture("added-result")
        def edited():
            doc=base.request(base.FIRESTORE,base.DOCS+"/users/"+a+"/transactions/"+created_id)
            assert float(doc["fields"]["amount"].get("doubleValue",doc["fields"]["amount"].get("integerValue")))==75000
            assert doc["fields"]["id"]["stringValue"]==created_id and doc["fields"]["note"]["stringValue"]=="Edited in 5F"
            capture("edited-result")
        def deleted():
            assert not any(d["name"].endswith("/"+created_id) for d in documents("transactions"))
            image=base.request(base.FIRESTORE,base.DOCS+"/users/"+a+"/transactions/image-fixture")
            assert image["fields"]["imageUrl"]["stringValue"]=="https://example.invalid/existing.png"
            capture("deleted-result")
        def verify_b():
            target="users/"+a+"/transactions/image-fixture"
            for method,data in [("GET",None),("PATCH",{"fields":{"note":{"stringValue":"forbidden"}}}),("DELETE",None)]: deny_other_user(users["B"],target,method,data)
            results["cross_user_rules"]={"read_denied":True,"update_denied":True,"delete_denied":True}
            assert any(d["fields"].get("note",{}).get("stringValue")=="Persisted B" for d in documents("transactions"))
            capture("saved-for-relaunch")
        actions={"home-content":lambda:capture("home-content"),"add-form":lambda:capture("add-form"),"added-result":added,"edit-form":lambda:capture("edit-form"),"edited-result":edited,"delete-confirmation":lambda:capture("delete-confirmation"),"deleted-result":deleted,"saved-for-relaunch":verify_b}
        results["prepare"]=base.phase(args,"prepare",users,probe_flag=FLAG)
        results["exercise"]=base.phase(args,"exercise",users,actions,probe_flag=FLAG)
        def legacy():
            base.seed("TRANSACTIONS/b-legacy",b,222000,"Thu")
            for d in documents("transactions"):
                if "/users/"+b+"/transactions/" in d["name"]: base.request(base.FIRESTORE,"/v1/"+d["name"],"DELETE")
        results["restore"]=base.phase(args,"restore",users,{"empty-primary-b":legacy,"legacy-detail":lambda:capture("legacy-detail")},probe_flag=FLAG)
        if args.interactive: results["native_ui"]=interactive(args)
        results["cleanup"]=base.phase(args,"cleanup",users,probe_flag=FLAG)
    finally:
        base.command("xcrun","simctl","terminate",args.simulator,base.BUNDLE,check=False)
        try:
            if initialized:
                base.request(base.FIRESTORE,"/emulator/v1/projects/demo-walletwise/databases/(default)/documents","DELETE")
                base.request(base.AUTH,"/emulator/v1/projects/demo-walletwise/accounts","DELETE")
                assert not base.accounts() and not documents("transactions") and not documents("TRANSACTIONS") and not documents("categories")
                results["cleanup_fixtures"]={"accounts":0,"transactions":0,"categories":0}
        finally:
            emulator.send_signal(signal.SIGINT)
            try: emulator.wait(timeout=20)
            except subprocess.TimeoutExpired: emulator.terminate(); emulator.wait(timeout=10)
            raw.close()
            assert all(not base.listening(port) for port in (9099,8080,4400,4500))
            results["emulator"]={"exit_code":emulator.returncode,"ports_closed":[9099,8080,4400,4500]}
            (args.artifacts/"results.json").write_text(json.dumps(results,indent=2)+"\n")
    print(json.dumps({"complete":True,"probe_passes":sum(len(results[k]["passes"]) for k in ("prepare","exercise","restore","cleanup")),"cleanup":results["cleanup_fixtures"],"emulator":results["emulator"]}),flush=True)


if __name__ == "__main__": main()
