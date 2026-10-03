# 更新日志 / Changelog

## 1.1.0-dev — 未发布 / Unreleased

- 迁移到现代 libxposed API 102.0.0，最低与目标 Xposed API 均为 102；需要支持 API 102 的框架。
- 使用现代模块入口与静态作用域，移除旧 API 依赖、自身 Hook 和持久化激活标记。
- 通过框架服务连接检测激活状态，核对媒体服务中的 API 与模块版本；升级后需重启作用域进程，不启用热重载。
- 查询、插入、删除记录及下载目录 Hook 改用拦截链；保留原有规则、业务 Binder 和数据库，过滤失败时用原参数重试原方法，取消与原方法异常继续向调用方传递。
- 编译 SDK 升至 37，AGP 升至 9.1.1，Gradle 升至 9.5.1；目标 SDK 36、最低 Android 10 保持不变。
- 增加查询调用约定、反射兼容和激活状态测试，并验证 Debug / R8 Release 的实际 APK 内容。

Modern libxposed API 102 migration for the development branch. Requires an API 102 framework; restart the scoped processes after updating. Rules, the module's Binder interface, and its database are retained. The app no longer hooks itself or persists an activation flag. Compile SDK 37, AGP 9.1.1, and Gradle 9.5.1 are required; target SDK remains 36 and minimum Android remains 10.

## 1.0.0 — 2026-10-03

这是 **Mzdyl/Media-Provider-Manager 分支的首个正式版**。本次汇总相对上游的功能调整和稳定性修复，不仅包含上一版 `625` 之后的改动。

对比基线为 [MaterialCleaner/Media-Provider-Manager 的 main 分支提交 `76254c0`](https://github.com/MaterialCleaner/Media-Provider-Manager/commit/76254c04c2643fedc2f65e3a3736fea8a297dbe5)（2024-05-15），也是本次发布准备时上游 main 的最新提交。上游最后一个公开 Release 为 `beta11`。感谢上游作者与贡献者，本分支继续遵循 Apache-2.0 协议。

### 界面与规则管理

- 将管理界面迁移到 Jetpack Compose，重新设计应用列表、应用详情、模板、设置和使用记录页面，延续 Material 3 风格并支持动态配色、深色模式。
- 改进应用搜索、系统应用筛选、规则数量展示及模板绑定操作。
- 模板编辑改为显式保存；退出前提示未保存的更改，保存失败时保留草稿，旋转屏幕后保留编辑状态。
- 修复新建、重命名和重复名称处理，编辑模板时保留已有应用绑定。
- 支持目录选择和手动编辑路径，将隐藏空格、格式控制字符显示为 `[U+XXXX]`，方便排查“路径看起来正确但规则不生效”的情况。
- 新增规则的剪贴板备份与恢复。
- 使用新的“媒体卡片 + 盾牌”图标，支持自适应桌面图标和 Android 单色主题图标。

### 媒体过滤与系统兼容性

- 更新 Android 16 的 MediaProvider 适配，兼容多种内部方法签名；最低系统版本仍为 Android 10（API 29），编译和目标 SDK 更新为 36。
- 在系统查询分页前应用过滤条件，修复过滤后分页出现空洞、后续媒体无法正常加载的问题，并保留调用方的查询列和排序等参数。
- 根据 files、images、video、audio 等不同媒体表选择过滤方式，避免向不支持的表注入无效字段；跳过不适用的聚合查询。
- 修复路径边界、尾部分隔符以及 SQL 通配符转义，避免将相似名称的其他目录一并过滤。
- 修复并发请求共享模板匹配状态的问题，并为规则缓存设置容量上限。
- 无法识别的系统接口、无法应用的查询过滤和损坏规则会记录日志并放行原操作，避免影响系统媒体服务。

### 稳定性与使用记录

- 修复未激活模块、服务尚未就绪或 Binder 断连时的界面异常，区分“未激活”“需要重启作用域”和“已激活”状态。
- 将 Binder 和数据库工作移出界面主线程，使用后台批量写入、有限长度队列和通知合并，减少阻塞与重复刷新。
- 为使用记录数据库增加索引和迁移，自动清理超过 90 天的记录，支持手动清空。
- 对管理接口进行调用方 UID 校验；规则文件使用原子写入并在写入前校验内容。
- 修复 R8 优化后的数据库实现和反射依赖保留问题，避免只在 Release 构建中出现的初始化异常。
- 增加规则匹配、查询条件、路径边界、数据转换和模板编辑测试，CI 执行单元测试、Android Lint 和 APK 构建。

### 与上游不同的行为

- 本分支专注规则管理与访问记录，移除了上游内置的媒体浏览器、图片查看器、视频播放器及 Playground。
- 单个模板的允许媒体类型“不选”或“全选”表示不增加类型限制；目录过滤仍然有效。多个绑定模板的有限类型列表按**并集**合并，例如一个允许图片、另一个允许视频，则允许图片和视频；不限制类型的模板不会取消其他模板的类型限制。任一模板命中的过滤目录仍会被过滤。升级后请核对原有多模板规则。
- 媒体类型无法可靠判断的查询会放行类型检查；downloads 查询保留目录过滤，不强行按媒体类型过滤。聚合查询及不支持的系统实现可能不应用过滤。

### 安装与升级

1. 安装附件 `Media-Provider-Manager-1.0.0.apk`。同包名、同签名的本分支版本可以覆盖升级；上游或其他来源的安装包可能因签名不同而无法直接覆盖。
2. 在 LSPosed 中启用模块，按推荐勾选媒体存储、下载管理器及模块自身作用域，然后重启相关作用域或设备。
3. 检查模块状态、模板绑定、目录路径和媒体类型设置。建议升级前备份规则；本分支可从设置页复制规则备份，请将其保存到持久位置。

主要兼容目标为 Android 10–16；已有 Samsung Android 16 的媒体查询与模板编辑实机回归记录，不代表所有系统版本和厂商 ROM 均已逐一验证。模块过滤的是 MediaStore 访问，不会清除相册的历史缓存、缩略图或云端副本，也不提供文件系统访问隔离。

[相对上游的完整差异](https://github.com/Mzdyl/Media-Provider-Manager/compare/76254c04c2643fedc2f65e3a3736fea8a297dbe5...v1.0.0) · [相对本分支上一版的差异](https://github.com/Mzdyl/Media-Provider-Manager/compare/625...v1.0.0)

### English summary

The first stable release of the **Mzdyl fork**, compared with upstream MaterialCleaner `main` at `76254c0` (2024-05-15), rather than only the previous fork release.

- Rebuilt management screens with Jetpack Compose, explicit template saving, recoverable drafts, invisible path-character hints, clipboard rule backup/restore, and adaptive/themed app icons.
- Improved Android 16 MediaProvider compatibility, moved filtering before pagination, preserved caller query behavior, and handled different media tables and path boundaries correctly.
- Improved Binder recovery, background record writes, bounded queues/caches, database migration/indexes, 90-day record retention, atomic rule writes, caller checks, and R8 release initialization.
- Removed the upstream media browser, image viewer, video player, and playground to focus on rule management and usage records.
- **Rule behavior change:** empty/all media-type selections add no type restriction; finite allowlists from multiple templates are combined as a union. Unrestricted templates do not override restricted ones. Matching directory exclusions still apply. Review existing multi-template rules after upgrading.
- Requires Android 10 or newer and an enabled Xposed/LSPosed environment. Android 10–16 is the compatibility target; device regression evidence currently covers Samsung Android 16, not every Android version or ROM. Unsupported operations fail open. Existing app caches, cloud copies, and direct filesystem access are outside the filtering scope.
- Install the attached release APK, enable the suggested scopes, and restart them or reboot. In-place upgrades require the same package name and signing certificate. Back up rules before upgrading.
