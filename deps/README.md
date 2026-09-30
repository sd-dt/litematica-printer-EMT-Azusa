# deps 目录说明

本仓库**不包含** Minecraft / Fabric / malilib / litematica 等第三方 jar（体积与许可原因）。
要本地重新构建，请把这些 jar 放到 `deps/mc-26.2/`（可再分子目录）：

- Minecraft 26.2 客户端 jar（官方名，即未混淆的 client jar）
- Fabric Loader + Fabric API 模块（`fabric-content-registries-v0` 等）
- malilib ≥ 0.29.6、litematica ≥ 0.28.8
- Mixin（sponge-mixin）、MixinExtras（`mixinextras-common`）、slf4j-api
- 其余编译期引用（本仓库作者的 deps 目录里共 118 个 jar）

另外需要 JDK 25，并在 `scripts/jdk-25.path` 里写上它的 bin 目录（或运行时用 `-Jdk` 参数指定）。
构建命令见根目录 README「从源码构建」。
