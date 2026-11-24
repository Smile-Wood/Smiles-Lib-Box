#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
构建 smile_libbox-<版本>+26.2.jar —— 官方名（named）命名空间的适配层。

26.2 起 Mojang 不再提供 intermediary，游戏 jar 里直接是 net.minecraft.* 官方名，
也没有 refmap。因此这个 jar 不能用 Loom / Stonecutter 产物，而是：

  * 以 1.20.1 jar 里的「纯 Java 部分」为底（api/**、入口点、图标）——它们不含任何 MC 符号；
  * 用 JDK 26 的 javac --release 25，直接对 26.2 客户端 jar + Fabric API 编译
    named26/src 下的 VersionedScreen / UiBackend / mixin 类，覆盖底包里的同名
    intermediary 版本；
  * 剔掉底包的 mixin 配置与中介名残留，改写 fabric.mod.json / libbox-target.txt，
    并附一份按 26.2 官方名写注入点的 client mixin 配置。

这样 26.2 不只是「能开配置界面」：上下文条接管、界面按键、全局鼠标事件三个
注入点都在这一个 jar 里，依赖它的 toolb 仍然是不含任何 MC 引用的单 jar。

用法：python tools/build_named26.py
可用环境变量覆盖：MC_HOME（.minecraft 目录）、JAVAC（javac.exe 路径）、MC_VERSION。
"""

import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MC_HOME = Path(os.environ.get("MC_HOME", "C:/Apps/PCL2CE/.minecraft"))
JAVAC = Path(os.environ.get("JAVAC", "D:/Program Files/Java/jdk-26/bin/javac.exe"))
VERSION = os.environ.get("MC_VERSION", "26.2")


def read_mod_version() -> str:
    """版本号只有 gradle.properties 一个来源，免得脚本里到处是写死的 1.0.x。"""
    props = ROOT / "gradle.properties"
    for line in props.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("mod.version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("mod.version not found in " + str(props))


MOD_VERSION = read_mod_version()

VERSION_DIR = MC_HOME / "versions" / VERSION
CLIENT_JAR = VERSION_DIR / (VERSION + ".jar")
VERSION_JSON = VERSION_DIR / (VERSION + ".json")
BASE_JAR = ROOT / "versions" / "1.20.1" / "build" / "libs" / (
    "smile_libbox-" + MOD_VERSION + "+1.20.1.jar")
SRC_DIR = ROOT / "named26" / "src"
MIXIN_CONFIG = ROOT / "named26" / "resources" / "smile_libbox.named26.mixins.json"
WORK = ROOT / "named26" / "build"
CLASSES = WORK / "classes"
NESTED = WORK / "nested"
ARGFILE = WORK / "javac.args"
OUT_JAR = ROOT / "build" / "libs" / ("smile_libbox-" + MOD_VERSION + "+" + VERSION + ".jar")

# 沙箱/杀软不允许脚本递归删目录时，用 SMILE_KEEP_WORK=1 原地覆盖编译，产物一致
KEEP_WORK = os.environ.get("SMILE_KEEP_WORK") == "1"

# 底包里这些条目属于 intermediary 世界，26.2 jar 一律不要
DROP_PREFIXES = (
    "smile_libbox/client/compat/",
    "smile_libbox/client/mixin/",
    "smile_libbox/client/LibBoxMixinPlugin.class",
)
DROP_EXACT = {
    "fabric.mod.json",
    "libbox-target.txt",
    "smile_libbox.client.mixins.json",
    "smile_libbox.mixins.json",
    "META-INF/MANIFEST.MF",
}


def fwd(path):
    """javac 的 argfile 只认正斜杠；混合反斜杠会导致 classpath 解析失败。"""
    return str(path).replace("\\", "/")


def windows_allows(rules):
    """按 26.2.json 的 rules 判断该 library 是否适用于 Windows。"""
    if not rules:
        return True
    allowed = not any(r.get("action") == "allow" for r in rules)
    for rule in rules:
        os_rule = rule.get("os") or {}
        name = os_rule.get("name")
        if name and name not in ("windows", "win"):
            continue
        allowed = rule.get("action") == "allow"
    return allowed


def library_jars():
    data = json.loads(VERSION_JSON.read_text(encoding="utf-8"))
    jars = []
    for lib in data.get("libraries", []):
        if not windows_allows(lib.get("rules")):
            continue
        artifact = (lib.get("downloads") or {}).get("artifact")
        if not artifact or not artifact.get("path"):
            continue
        path = MC_HOME / "libraries" / artifact["path"]
        if path.is_file():
            jars.append(path)
    return jars


def extract_nested():
    """Fabric API 26.2 把各子模块塞在 META-INF/jars/*.jar，javac 需要展开后才能见到。"""
    if NESTED.exists() and not KEEP_WORK:
        shutil.rmtree(NESTED)
    NESTED.mkdir(parents=True, exist_ok=True)
    found = []
    for mod in sorted((VERSION_DIR / "mods").glob("fabric-api-*.jar")):
        with zipfile.ZipFile(mod) as z:
            for name in z.namelist():
                if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                    target = NESTED / Path(name).name
                    target.write_bytes(z.read(name))
                    found.append(target)
    if not found:
        raise RuntimeError("没有从 fabric-api jar 里找到 META-INF/jars/*.jar")
    return found


def mixin_annotation_jars():
    """sponge-mixin 的注解 jar。

    26.2.json 里虽然列了 sponge-mixin，但启动器常把它的 download.artifact 留空，
    library_jars() 因此拿不到；而 named26/src 下的 mixin 类需要
    org.spongepowered.asm.mixin.* 才能编译。这里从 libraries 目录兜底取最高版本。
    """
    base = MC_HOME / "libraries" / "net" / "fabricmc" / "sponge-mixin"
    jars = sorted(base.glob("*/*.jar"), key=lambda p: p.name)
    return jars[-1:] if jars else []


def build_classpath():
    # BASE_JAR 必须排在最前：26.2 的 mods 目录里通常还躺着上一版 smile_libbox，
    # javac 会先看到里面的旧 api 契约，导致「方法不会覆盖超类型」这种莫名其妙的错。
    entries = [BASE_JAR, CLIENT_JAR]
    entries += library_jars()
    # 排除 .minecraft 里的自家 jar：编译要用的是刚构建出来的 BASE_JAR
    entries += [p for p in sorted((VERSION_DIR / "mods").glob("*.jar"))
                if not p.name.startswith("smile_libbox-")]
    entries += extract_nested()
    entries += mixin_annotation_jars()
    return [p for p in entries if p.exists()]


def check_no_stonecutter():
    """named26 是 javac 直接编译的，stonecutter 的 //? 条件分支只会变成块注释。

    这样写出来的代码「编译通过、逻辑全无」——曾经让密度附魔的读取整段消失，
    所有重锤都被当成 0 级致密，永远切到背包里靠前的那把。宁可构建报错。
    """
    offenders = []
    for path in sorted(SRC_DIR.rglob("*.java")):
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "//?" in line:
                offenders.append("%s:%d  %s" % (path.relative_to(SRC_DIR), no, line.strip()))
    if offenders:
        raise SystemExit(
            "named26/src 里不能出现 stonecutter 条件注释（//? ...），"
            "请把该版本真正要用的分支直接写出来：\n  " + "\n  ".join(offenders))


def compile_sources(cp):
    if CLASSES.exists() and not KEEP_WORK:
        shutil.rmtree(CLASSES)
    CLASSES.mkdir(parents=True, exist_ok=True)
    sources = sorted(SRC_DIR.rglob("*.java"))
    if not sources:
        raise RuntimeError("named26/src 下没有 java 源文件")
    cp_text = ";".join(fwd(p) for p in cp)
    ARGFILE.write_text(
        '--release 25\n'
        '-encoding UTF-8\n'
        '-nowarn\n'
        '-cp "' + cp_text + '"\n'
        '-d "' + fwd(CLASSES) + '"\n'
        + "\n".join('"' + fwd(s) + '"' for s in sources) + "\n",
        encoding="utf-8",
    )
    proc = subprocess.run(
        [fwd(JAVAC), "@" + fwd(ARGFILE)],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if proc.stdout.strip():
        print(proc.stdout.strip())
    if proc.returncode != 0:
        print(proc.stderr.strip(), file=sys.stderr)
        raise RuntimeError("javac 失败（exit %d）" % proc.returncode)
    classes = sorted(CLASSES.rglob("*.class"))
    print("javac OK：编译出 %d 个 class" % len(classes))
    return classes


def metadata():
    mod = {
        "schemaVersion": 1,
        "id": "smile_libbox",
        "version": MOD_VERSION + "+" + VERSION,
        "name": "Smile's Lib Box",
        "description": "按游戏版本分发的跨版本转译层；本 jar 为 " + VERSION
                       + " 官方名（named）命名空间编译，向上暴露固定 API。",
        "authors": ["Me!"],
        "license": "CC0-1.0",
        "icon": "assets/smile_libbox/icon.png",
        "environment": "*",
        "entrypoints": {
            "main": ["smile_libbox.SmileSLibBox"],
            "client": ["smile_libbox.client.SmileSLibBoxClient"],
        },
        "mixins": [
            {"config": "smile_libbox.named26.mixins.json", "environment": "client"}
        ],
        "depends": {
            "fabricloader": ">=0.20.1",
            "minecraft": "~" + VERSION,
            "java": ">=25",
            "fabric-api": "*",
        },
        "custom": {"libbox_target": VERSION},
    }
    return (
        json.dumps(mod, indent="\t", ensure_ascii=False) + "\n",
        VERSION + "\n",
    )


def assemble(classes, fab_json, target_txt):
    OUT_JAR.parent.mkdir(parents=True, exist_ok=True)
    if OUT_JAR.exists():
        try:
            OUT_JAR.unlink()
        except OSError:
            # 被占用时直接以 "w" 覆盖，内容一样
            pass
    with zipfile.ZipFile(BASE_JAR) as base, zipfile.ZipFile(
            OUT_JAR, "w", zipfile.ZIP_DEFLATED) as out:
        copied = 0
        for item in base.infolist():
            name = item.filename
            if name in DROP_EXACT or name.startswith(DROP_PREFIXES):
                continue
            if name.endswith("/"):
                continue
            out.writestr(name, base.read(name))
            copied += 1
        for cls in classes:
            rel = cls.relative_to(CLASSES).as_posix()
            out.writestr(rel, cls.read_bytes())
        out.writestr("fabric.mod.json", fab_json)
        out.writestr("libbox-target.txt", target_txt)
        mixin_cfg = MIXIN_CONFIG.read_text(encoding="utf-8")
        json.loads(mixin_cfg)  # 提前发现 JSON 语法错误，别等 Loader 报
        out.writestr(MIXIN_CONFIG.name, mixin_cfg)
        out.writestr("META-INF/MANIFEST.MF",
                     "Manifest-Version: 1.0\n"
                     "Implementation-Title: Smile's Lib Box\n"
                     "Implementation-Version: " + MOD_VERSION + "+" + VERSION + "\n\n")
    print("已生成 %s（复用底包 %d 个条目 + %d 个 named class）"
          % (OUT_JAR, copied, len(classes)))


def verify():
    import re
    pat = re.compile(rb"net/minecraft/class_[0-9]+|Lnet/minecraft/class_[0-9]+")
    bad = []
    required = {
        "fabric.mod.json",
        "libbox-target.txt",
        "smile_libbox.named26.mixins.json",
        "smile_libbox/client/mixin/LibContextualBarMixin.class",
        "smile_libbox/client/mixin/LibScreenKeyMixin.class",
        "smile_libbox/client/mixin/LibMouseMixin.class",
        "smile_libbox/client/mixin/LibChatDraftMixin.class",
        "smile_libbox/client/mixin/WorldPacketsMixin.class",
    }
    with zipfile.ZipFile(OUT_JAR) as z:
        names = set(z.namelist())
        for name in names:
            if name.endswith(".class") and pat.search(z.read(name)):
                bad.append(name)
        missing = required - names
        if missing:
            raise RuntimeError("jar 缺少条目：" + ", ".join(sorted(missing)))
        leftover = sorted(n for n in names
                          if n.startswith("smile_libbox/client/mixin/")
                          and n not in required)
        if leftover:
            print("注意：额外 mixin 类 " + ", ".join(leftover))
    if bad:
        raise RuntimeError("仍有 intermediary 残留：" + ", ".join(bad))
    print("校验通过：jar 内无 net/minecraft/class_* 中介名引用，mixin 配置与 %d 个注入类齐全" % len(required))


def main():
    for required in (CLIENT_JAR, VERSION_JSON, BASE_JAR, JAVAC, MIXIN_CONFIG):
        if not required.exists():
            raise RuntimeError("缺少必需文件：" + fwd(required))
    WORK.mkdir(parents=True, exist_ok=True)
    check_no_stonecutter()
    cp = build_classpath()
    print("classpath 条目：%d" % len(cp))
    classes = compile_sources(cp)
    fab_json, target_txt = metadata()
    assemble(classes, fab_json, target_txt)
    verify()


if __name__ == "__main__":
    main()
