"""脱离 Minecraft 的纯 Java 回归：数字输入框实时校验 + 两种定义路线一致性。

用法：
    python tools/selftest-number/run_selftest.py

不启动游戏、不碰 gradle，只把 ConfigSpec / ConfigValues / ConfigLayout 编译一遍，
桩掉 Ui.tr 后跑断言。适合改完校验逻辑立刻回归。
"""

import glob
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIB = HERE.parent.parent
SRC = LIB / "src/main/java/smile_libbox/api/ui"
OUT = HERE / ".out"


def find_gson() -> Path:
    # Gradle 缓存结构：gson/<版本>/<sha1>/gson-<版本>.jar
    pattern = os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/*/*/gson-*.jar")
    jars = [j for j in glob.glob(pattern) if not j.endswith("-sources.jar")]
    if not jars:
        sys.exit("找不到 gson jar（Gradle 缓存里没有）；先跑一次 ./gradlew build")
    # 取最新下载的那个
    return Path(max(jars, key=os.path.getmtime))


def run(cmd) -> None:
    result = subprocess.run(cmd)
    if result.returncode != 0:
        sys.exit("命令失败：%s" % " ".join(str(c) for c in cmd))


def main() -> int:
    gson = find_gson()
    print("gson:", gson.name)

    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "stub").mkdir(parents=True)
    (OUT / "main").mkdir(parents=True)

    sep = os.pathsep
    run(["javac", "-nowarn", "-d", str(OUT / "stub"),
         str(HERE / "stub/smile_libbox/api/ui/Ui.java")])
    run(["javac", "-nowarn",
         "-cp", f"{OUT / 'stub'}{sep}{gson}",
         "-d", str(OUT / "main"),
         str(SRC / "ConfigSpec.java"),
         str(SRC / "ConfigValues.java"),
         str(SRC / "ConfigLayout.java"),
         str(HERE / "NumberCheck.java")])
    run(["java", "-Dfile.encoding=UTF-8",
         "-cp", f"{OUT / 'main'}{sep}{OUT / 'stub'}{sep}{gson}",
         "NumberCheck"])

    shutil.rmtree(OUT, ignore_errors=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit:
        raise
    finally:
        shutil.rmtree(OUT, ignore_errors=True)
