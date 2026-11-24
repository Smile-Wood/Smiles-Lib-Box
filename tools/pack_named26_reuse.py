#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
在**没有 26.2 客户端 jar** 的机器上重新打包 26.2 官方名 jar。

`build_named26.py` 需要 `versions/26.2/26.2.jar` 才能重新编译 named26/src 下的适配类。
但适配类只在「库的 26.2 适配层」变化时才需要重编：改了 `src/main`（纯 Java API）之后
它们一个字都没变，直接复用 `named26/build/classes` 里上次编好的 class，再用**新的**
1.20.1 底包重新封一次 jar 即可，产物与完整编译一致。

用法：
    SMILE_KEEP_WORK=1 python tools/build_named26.py     # 有客户端 jar 时的正规路线
    python tools/pack_named26_reuse.py                  # 没有 jar、只改了 api 时用这条
"""

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load_builder():
    spec = importlib.util.spec_from_file_location("build_named26", HERE / "build_named26.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    builder = load_builder()
    if not builder.CLASSES.exists():
        raise SystemExit(
            "没有可复用的 class（%s）。请先用完整路线编译一次：\n"
            "  SMILE_KEEP_WORK=1 python tools/build_named26.py" % builder.CLASSES)
    classes = sorted(builder.CLASSES.rglob("*.class"))
    if not classes:
        raise SystemExit("可复用目录是空的：" + str(builder.CLASSES))
    if not builder.BASE_JAR.exists():
        raise SystemExit("缺少底包：" + str(builder.BASE_JAR))
    print("复用 %d 个 named class（%s）" % (len(classes), builder.CLASSES))
    print("底包：%s" % builder.BASE_JAR.name)

    fab_json, target_txt = builder.metadata()
    builder.assemble(classes, fab_json, target_txt)
    builder.verify()
    print("产物：%s" % builder.OUT_JAR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
