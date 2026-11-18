# Universal Recipe Patterns 1.0.31

通用样板首次在 GitHub 发布。一个 AE2 空白样板即可合成。手持右键编辑，应用配置后可用一张卡在兼容的供应器或样板总成中发布多条已注册的 GT 配方。

- 支持按工艺、材料和规则筛选配方，并为每张卡保留草稿。
- 支持为不消耗原料配置供应方式，以及在编辑器中查看配方样例。
- 支持 AE2 普通供应器、ExtendedAE 扩展供应器与兼容的 GTL 通配符和配方样板总成。

## 下载与安装

下载 `universal-recipe-patterns-1.0.31.jar`，放入兼容实例的 `mods` 文件夹。升级时将旧版通用样板 JAR 移出 `mods`（旧文件名可能为 `gtl-universal-patterns-*.jar`）。联机时客户端和服务端需同步更新。

目前仅支持 GregTech Leisure。核对环境为 GregTech Leisure 1.4.5.4、Minecraft 1.20.1、Forge 47.x、Java 21、GTCEu 1.4.4、AE2 15.4.10 与 ExtendedAE 1.4.19。

本版本要求 [官方 GTLCore 1.2.3.3-fix4](https://github.com/GNSW/GTLCore/releases/tag/version-1.2.3.3-fix4)。如果使用 CurseForge 整合包自带的 `1.2.3.3`，需要先更新 GTLCore，否则 Forge 会拒绝加载附属模组。官方 fix4 已通过全量编译、关键 Mixin 目标及配方样板总成工厂的离线检查。完整游戏内流程待验证。

SHA-256：`fd8c7f3c2cc7b0e15aeef1461573723b52b2baf45638678852515df9c43dde70`
