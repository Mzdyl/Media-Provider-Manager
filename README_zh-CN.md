# 媒体存储设备管理

防止媒体存储滥用的 Xposed 模块。

本分支基于 [MaterialCleaner/Media-Provider-Manager](https://github.com/MaterialCleaner/Media-Provider-Manager)，专注媒体访问规则与使用记录。相对上游的变化和升级说明见 [更新日志](CHANGELOG.md)。

[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/+rx5V9umZI4FjMWNl)
[![Stars](https://img.shields.io/github/stars/Mzdyl/Media-Provider-Manager?label=Stars)](https://github.com/Mzdyl/Media-Provider-Manager)
[![Download](https://img.shields.io/github/v/release/Mzdyl/Media-Provider-Manager?label=Download)](https://github.com/Mzdyl/Media-Provider-Manager/releases/latest)

## 截屏

<p><img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/about.jpg" height="400" alt="Screenshot"/>
<img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/record.jpg" height="400" alt="Screenshot"/>
<img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/template.jpg" height="400" alt="Screenshot"/></p>

## 什么是媒体存储

[媒体存储库][1]是安卓系统提供的媒体文件索引。当应用需要访问媒体文件时（如相册应用想显示设备中的全部图片），相比于遍历外部存储卷中的全部文件，[使用媒体存储][2]更加高效、方便。另外它还能减少应用可访问的文件，有利于保护用户隐私。

## 媒体存储如何被滥用

与原生存储空间一样，安卓系统没有提供媒体存储的精细管理方案。
- ~~应用只需要低危权限就可以访问全部媒体文件，用户无法限制读取范围~~
- 无需权限即可写入文件，应用随意写入文件会让存储空间和媒体库混乱，而且还能借此实现跨应用追踪

## 特性

- 按应用配置媒体访问规则，支持共享模板和剪贴板备份、恢复
- 过滤媒体存储返回的数据，保护隐私数据不被查询
- 防止应用通过媒体存储随意写入文件
- 提供历史记录功能，帮助您了解应用如何使用媒体存储
- 阻止 💩 ROM 的下载管理程序创建不规范文件
- 质感设计 3
- 开源

本分支不包含上游的媒体浏览器、图片查看器、视频播放器和 Playground。

## 配置规则与排查

在应用管理中选择目标应用，再添加已有模板或创建新模板。编辑完成后点击“保存”生效；直接返回会提示放弃未保存更改。模板可以由多个应用共用，修改其路径会影响所有已绑定的应用。

过滤路径必须与真实目录一致。编辑器会将隐藏空格、格式控制字符显示为 `[U+XXXX]`，请通过目录选择器或手动编辑核对；不会自动删除文件名中的合法空格。允许的媒体类型不选或全选时，表示不限制类型，目录过滤仍生效。

如果谷歌相册等应用仍显示被过滤内容，先检查应用是否已绑定模板、路径是否包含隐藏字符，再区分新的本地媒体查询与应用历史缓存、云端副本。模块过滤 MediaStore 查询，不会删除已有缓存或云端媒体，也不提供文件系统访问隔离。

## 源代码

[https://github.com/Mzdyl/Media-Provider-Manager](https://github.com/Mzdyl/Media-Provider-Manager)

## 发行版

[Github Release](https://github.com/Mzdyl/Media-Provider-Manager/releases/latest)

## 兼容性

- 最低支持 Android 10（API 29），当前以 Android 10–16 为主要兼容范围。
- 开发分支需要实现现代 Xposed API 102 的框架。勾选建议的媒体存储、下载管理器作用域；模块应用通过框架服务连接，无需勾选模块自身。已发布的 1.0.x 版本使用旧 API。
- 升级后请重启作用域进程或设备。管理端会核对注入端的 API 与模块版本；仅连接框架不代表当前 Hook 已生效。本版不启用热重载。
- MediaProvider 是系统组件，不同 ROM 的私有实现可能不同。模块在无法识别方法签名或内部 API 时会优先放行系统原操作，并在 Xposed 日志中记录原因。

## 构建与验证

使用 JDK 21、Android SDK 37（37.0）和 Build Tools 36.0.0；Wrapper 使用 Gradle 9.5.1，AGP 为 9.1.1。目标 SDK 仍为 36，最低支持 Android 10：

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

CI 会执行单元测试、Android Lint 和 Debug APK 构建。Debug 与 Release APK 均通过 `tools/verify_xposed_apk.py` 检查现代模块入口、构造方法保留、旧 API 引用清除和框架 API 的打包隔离。核心规则、路径边界和数据库类型转换均应通过单元测试后再合并。

## 数据与隐私

使用记录保存在 MediaProvider 的应用私有数据库中，只能由本模块通过受 UID 校验的 Binder 接口读取。记录队列有容量上限，超过 90 天的记录会自动清理，也可以在设置中手动清空。

规则文件使用原子写入；损坏或无法识别的规则会被禁用，而不会阻止系统 MediaProvider 启动。

## 协议

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

[1]: https://developer.android.com/reference/android/provider/MediaStore
[2]: https://developer.android.com/training/data-storage/use-cases?hl=zh-cn#handle-media-files
