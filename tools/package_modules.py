import os
import subprocess
import sys
import zipfile

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODULES_DIR = os.path.join(BASE_DIR, "modules")

PACKAGES = [
    {
        "name": "PlayIntegrity-TrickyStore-AIO.zip",
        "src": os.path.join(MODULES_DIR, "playintegrity-trickystore-aio"),
    },
    {
        "name": "TrickyStore-StrongIntegrity.zip",
        "src": os.path.join(MODULES_DIR, "tricky-store-aio"),
    },
]

def zip_dir(src_dir: str, output_zip: str):
    print(f"Packaging {os.path.basename(output_zip)}...")
    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(src_dir):
            for file in files:
                abs_path = os.path.join(root, file)
                rel_path = os.path.relpath(abs_path, src_dir).replace("\\", "/")
                zipf.write(abs_path, rel_path)
    print(f"Created: {output_zip} ({os.path.getsize(output_zip)} bytes)")

def push_to_device(zip_path: str):
    targets = [
        f"/sdcard/Modules/{os.path.basename(zip_path)}",
        f"/sdcard/Download/{os.path.basename(zip_path)}",
    ]
    for target in targets:
        print(f"Pushing to device: {target}...")
        res = subprocess.run(["adb", "push", zip_path, target], capture_output=True, text=True)
        if res.returncode == 0:
            print(f"Pushed successfully: {target}")
        else:
            print(f"Failed to push {target}: {res.stderr}")

def main():
    for pkg in PACKAGES:
        src = pkg["src"]
        out = os.path.join(BASE_DIR, pkg["name"])
        if not os.path.isdir(src):
            print(f"Directory not found: {src}")
            continue
        zip_dir(src, out)
        push_to_device(out)

if __name__ == "__main__":
    main()
