# Universal Recipe Patterns · 通用样板

一张 AE2 通用样板可以同时发布多条加工配方。用工艺、材料或输入与输出样本筛选真实注册的配方，选择要收录的变体，再交给样板供应器或样板总成自动加工。**目前仅支持 GregTech Leisure。**

## 下载与安装

1. 使用 [GregTech Leisure 1.4.5.4](https://www.curseforge.com/minecraft/modpacks/gregtech-leisure/files/9322925)（Minecraft 1.20.1、Forge 47.x、GTCEu 1.4.4、AE2 15.4.10）。通用样板需要 Java 21 或更高版本。
2. 安装 [官方 GTLCore 1.2.3.3-fix4](https://github.com/GNSW/GTLCore/releases/tag/version-1.2.3.3-fix4)。按上游发布说明更新，并在 `mods` 中只保留一个 GTLCore JAR。
3. 下载 [universal-recipe-patterns-1.0.31.jar](https://github.com/winterd77/Universal-Recipe-Patterns/releases/download/v1.0.31/universal-recipe-patterns-1.0.31.jar) 放入 `mods`。升级时移出旧版通用样板 JAR（旧文件名可能以 `gtl-universal-patterns-` 开头）。联机时客户端和服务端都要安装。

### GTLCore 兼容性

| GTLCore | 通用样板 1.0.31 |
| --- | --- |
| CurseForge 整合包自带 `1.2.3.3` | Forge 会拒绝加载。此发布版要求精确版本 `1.2.3.3-fix4`。 |
| 上游官方 `1.2.3.3-fix4` | 已通过全量编译、关键 Mixin 目标和机器工厂检查。完整游戏内流程待验证。 |
| 基于 `1.2.3.3-fix4` 的自定义版 | 兼容性取决于改动。开发时使用了自定义构建，官方 fix4 的离线验证已独立完成。 |

## 使用

1. 用一个 AE2 空白样板无序合成通用样板。
2. 手持样板对空气右键，选择工艺，再按材料、规则或输入与输出样本筛选配方。关闭界面会保存草稿。点击「应用」才会更新向网络发布的配方。
3. 将样板放入受支持的 AE2 普通样板供应器、ExtendedAE 扩展样板供应器、GTL 通配符样板总成或配方样板总成。使用「目标容器」供给不消耗原料时，提前在机器端备好所需物品或流体。

## 从源码构建

`universal-patterns/src` 包含源码和资源。构建脚本使用 Windows、Python 3、JDK 22 和 Prism Launcher 的 GregTech Leisure 1.4.5.4 实例及库。编译目标为 Java 21。

- 把官方 `gtlcore-1.2.3.3-fix4.jar` 放到 `build/`，或用 `GTLCORE_JAR` 指定路径。
- 把 JetBrains 注解 JAR 放到 `build/annotations.jar`，或用 `GTL_ANNOTATIONS_JAR` 指定路径。
- 在 `build/gtlcore-wireless/` 准备对应版本的 `forge-mojmap.jar`、`client-mojmap.jar` 和 `srg-mojmap.tsrg`。
- 在仓库根目录运行 `python universal-patterns/build.py`，输出到 `dist/universal-recipe-patterns-1.0.31.jar`。

## 第三方许可

本项目基于 GregTech Leisure 的 GTLCore。LGPL-3.0、GPL-3.0 与 MIT 许可文本和致谢见 [`META-INF/licenses`](universal-patterns/src/main/resources/META-INF/licenses)。
