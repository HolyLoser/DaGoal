"""Build signed DaGoal distribution artifacts without exposing or copying signing secrets."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess

ROOT = Path(__file__).resolve().parents[1]
def run(args, env):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, env=env, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Release tool failed: " + (result.stderr or result.stdout)[-1800:])
    return result.stdout

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--skip-build",action="store_true");args=parser.parse_args()
    private=ROOT/".release-private"; props={}
    config=private/"signing.properties"
    if config.exists():
        props=dict(line.split("=",1) for line in config.read_text(encoding="utf-8").splitlines() if "=" in line)
    for key,name in {"storeFile":"DAGOAL_RELEASE_STORE_FILE","storePassword":"DAGOAL_RELEASE_STORE_PASSWORD","keyAlias":"DAGOAL_RELEASE_KEY_ALIAS","keyPassword":"DAGOAL_RELEASE_KEY_PASSWORD"}.items():
        if os.environ.get(name):props[key]=os.environ[name]
    if not all(props.get(k) for k in ("storeFile","storePassword","keyAlias","keyPassword")):
        raise SystemExit("Release signing is missing. Restore the existing identity; do not generate a replacement key.")
    env=os.environ.copy()
    if not env.get("JAVA_HOME") and Path(r"C:\Program Files\Android\Android Studio\jbr").exists():env["JAVA_HOME"]=r"C:\Program Files\Android\Android Studio\jbr"
    sdk=Path(env.get("ANDROID_SDK_ROOT") or env.get("ANDROID_HOME") or Path.home()/"AppData/Local/Android/Sdk")
    signers=list((sdk/"build-tools").glob("*/apksigner.bat"))
    if not signers:raise SystemExit("Android build-tools with apksigner are required.")
    signer=max(signers,key=lambda p:tuple(int(n) for n in re.findall(r"\d+",p.parent.name)))
    if not args.skip_build:run([ROOT/"gradlew.bat",":app:distributionRelease","--no-configuration-cache"],env)
    metadata=json.loads((ROOT/"app/build/outputs/apk/release/output-metadata.json").read_text())
    version=metadata["elements"][0]["versionName"]
    source=ROOT/"app/build/outputs/apk/release"/metadata["elements"][0]["outputFile"]
    lineage=ROOT/"docs/release/debug-to-release.lineage"
    if not lineage.exists():raise SystemExit("The original signing lineage is missing; restore it rather than creating a different history.")
    out=ROOT/"build/outputs/distribution";out.mkdir(parents=True,exist_ok=True)
    apk=out/("DaGoal-"+version+".apk");aab=out/("DaGoal-"+version+".aab")
    env["DAGOAL_SIGN_STORE_PASSWORD"]=props["storePassword"];env["DAGOAL_SIGN_KEY_PASSWORD"]=props["keyPassword"]
    run([signer,"sign","--ks",props["storeFile"],"--ks-key-alias",props["keyAlias"],"--ks-pass","env:DAGOAL_SIGN_STORE_PASSWORD","--key-pass","env:DAGOAL_SIGN_KEY_PASSWORD","--lineage",lineage,"--rotation-min-sdk-version","28","--v1-signing-enabled","false","--v2-signing-enabled","false","--out",apk,source],env)
    certificate=run([signer,"verify","--verbose","--print-certs","--min-sdk-version","29","--max-sdk-version","36",apk],env)
    (out/"apk-signature-verification.txt").write_text(certificate,encoding="utf-8")
    shutil.copy2(ROOT/"app/build/outputs/bundle/release/app-release.aab",aab)
    jarsigner=Path(env["JAVA_HOME"])/"bin/jarsigner.exe"
    verification=run([jarsigner,"-verify",aab],env)
    if "jar verified" not in verification:raise SystemExit("Release bundle signature could not be verified.")
    (out/"bundle-signature-verification.txt").write_text(verification,encoding="utf-8")
    hashes=[]
    for file in (apk,aab):hashes.append(hashlib.sha256(file.read_bytes()).hexdigest()+"  "+file.name)
    (out/"SHA256SUMS.txt").write_text("\n".join(hashes)+"\n",encoding="utf-8")
    print("Verified signed distribution artifacts: " + str(out))
    print("Only APK/AAB, public verification reports, and checksums are placed in this directory; no signing credentials.")
if __name__=="__main__":main()
