# GTL Universal Patterns · 通用样板

为 Minecraft 整合包 GregTech Leisure 提供可复用的 AE2 加工样板。一张卡可以按配方类型、材料和筛选规则收录多条已注册的配方，并保留每张卡的编辑草稿。

- 手持样板对空气右键打开编辑器。点击「应用」后，已配置的配方才会参与自动合成。
- 支持 AE2 普通样板供应器、ExtendedAE 扩展样板供应器，以及兼容的 GTL 通配符和配方样板总成。
- 可为催化剂等不消耗原料选择供应方式。选择「目标容器」时，机器仍需要预放所需物品或流体。

## 安装 1.0.31

| 组件 | 版本 |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.x |
| Java | 21 |
| GTCEu | 1.4.4 |
| GTLCore | 1.2.3.3-fix4 兼容分支。开发实例使用 `gtlcore-1.2.3.3-fix4-chain-ae-ui-recipes-v3.jar` |
| Applied Energistics 2 | 开发实例为 15.4.10 |
| ExtendedAE | 开发实例为 1.4.19，用于扩展样板供应器接入 |

1. 从 GitHub Releases 下载 `gtl-universal-patterns-1.0.31.jar`。
2. 退出游戏和服务端，把旧的 `gtl-universal-patterns-*.jar` 移出 `mods`，放入新版本。联机时两端使用相同版本。
3. 用一个 AE2 空白样板无序合成通用样板，在 JEI 中可搜索「通用样板」。

SHA-256：`c7dc8f585384247ef3a4830d96a8f0b6c8373fd4f427a14326209d65730e89a1`

## 源码与构建

源码和资源位于 [`universal-patterns/src`](universal-patterns/src)。当前脚本沿用 Windows 与 Prism Launcher 的本地构建环境。构建需要 JDK 22（目标字节码为 Java 21）、Python 3、GregTech Leisure 1.4.5.4 的模组与库、兼容的 GTLCore 基线 JAR，以及 Forge/Minecraft 的 Mojmap 构建输入。

在仓库根目录准备以下文件：

- `build/gtlcore-wireless/forge-mojmap.jar`
- `build/gtlcore-wireless/client-mojmap.jar`
- `build/gtlcore-wireless/srg-mojmap.tsrg`
- `build/annotations.jar`，或通过 `GTL_ANNOTATIONS_JAR` 指定 JetBrains 注解库。
- `dist/gtlcore-1.2.3.3-fix4-wireless-me-clusters.jar`，供编译和重映射使用。

随后运行 `python universal-patterns/build.py`。完整的离线测试夹具位于 [`universal-patterns/tests`](universal-patterns/tests)，其中部分契约测试需要同一套实例、其他版本的构建产物或 GTLCore 源码。

## 第三方许可

本项目基于 GregTech Leisure 的 GTLCore 工作。现有 LGPL-3.0、GPL-3.0 和 MIT 许可文本及致谢保存在 [`META-INF/licenses`](universal-patterns/src/main/resources/META-INF/licenses)。

- [GregTech Leisure](https://www.curseforge.com/minecraft/modpacks/gregtech-leisure/files/9322925)
- [GTLCore 上游项目](https://github.com/GNSW/GTLCore)
