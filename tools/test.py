#!/usr/bin/env python3
"""Compile every production class against the official SDK and run focused JVM/contract tests."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from android_sdk import android_platform

ROOT = Path(__file__).resolve().parents[1]
sdk_root = Path(os.environ.get("ANDROID_HOME", os.environ.get(
    "ANDROID_SDK_ROOT", str(Path.home() / "android-sdk"))))
java_home = os.environ.get("JAVA_HOME")


def tool(name):
    return str(Path(java_home) / "bin" / name) if java_home else name


def main():
    platform = android_platform(sdk_root)
    sources = [*sorted((ROOT / "sdk/src").rglob("*.java")),
               *sorted((ROOT / "src").rglob("*.java")),
               *sorted((ROOT / "tests").rglob("*.java"))]
    with tempfile.TemporaryDirectory(prefix="live-notifications-test-") as directory:
        subprocess.run([tool("javac"), "--release", "8", "-Xlint:all",
                        "-cp", str(platform), "-d", directory, *map(str, sources)], check=True)
        for test in ("CoreTest", "ControllerTest", "AppearanceTest"):
            subprocess.run([tool("java"), "-ea", "-cp",
                            os.pathsep.join([directory, str(platform)]),
                            "com.shymoose.livenotifications." + test], check=True)
    subprocess.run([sys.executable, "-m", "unittest", "discover",
                    "-s", str(ROOT / "tests"), "-p", "test_*.py", "-v"], check=True)
    subprocess.run([sys.executable, "-m", "unittest", "discover",
                    "-s", str(ROOT / "tools"), "-p", "test_*.py", "-v"], check=True)


if __name__ == "__main__":
    main()
