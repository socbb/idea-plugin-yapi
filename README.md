# YApi Doc Generator — IntelliJ IDEA 插件

[![Plugin Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/your-repo)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ_IDEA-2023.2%2B-brightgreen)](https://plugins.jetbrains.com)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin_DSL-purple)](https://gradle.org)

[**English**](./README.en.md) | **中文**

> **YApi Doc Generator** 是一款 IntelliJ IDEA 插件，能够**自动从 Spring MVC Controller 类和方法中解析并生成 YApi 接口文档**。
>
> 告别手动编写 API 文档的繁琐工作，一键将 Controller 代码转换为结构清晰的 YApi 文档。

---

## 目录

- [功能特性](#功能特性)
- [安装方式](#安装方式)
- [快速开始](#快速开始)
- [详细使用说明](#详细使用说明)
  - [配置 YApi 服务器](#1-配置-yapi-服务器)
  - [生成接口文档](#2-生成接口文档)
  - [服务器自动匹配机制](#3-服务器自动匹配机制)
- [功能展示](#功能展示)
- [技术架构](#技术架构)
- [开发指南](#开发指南)
- [许可证](#许可证)

---

## 功能特性

### Controller 智能识别
- 通过 `@RestController` / `@Controller` 注解或 `*Controller` 类名后缀自动识别 Controller 类

### 全面的 Spring MVC 注解支持
| 注解 | 支持情况 |
|------|---------|
| `@RequestMapping` | ✅ 完全支持 |
| `@GetMapping` | ✅ 完全支持 |
| `@PostMapping` | ✅ 完全支持 |
| `@PutMapping` | ✅ 完全支持 |
| `@DeleteMapping` | ✅ 完全支持 |
| `@PatchMapping` | ✅ 完全支持 |

### 请求参数深度解析
- `@RequestParam` — 查询参数解析
- `@PathVariable` — 路径变量解析
- `@RequestHeader` — 请求头解析
- `@RequestBody` — 请求体 DTO 递归解析

### DTO 深度递归解析
- 嵌套对象字段（递归深度最多 10 层）
- 泛型类型参数替换（如 `RestResult<T>` → `RestResult<User>`）
- 集合类型支持（`List`、`Set`、数组）
- 枚举类型智能解析
- 循环引用自动检测与终止
- 继承字段收集（包含父类所有字段）
- `@JsonIgnore` 注解字段自动跳过
- JSR-303 / Jakarta 验证注解识别（`@NotNull`、`@NotBlank`、`@NotEmpty`）

### 响应体自动解析
- 方法返回值类型自动解析为接口响应结构，复用 DTO 解析能力

### 文档标题智能提取
- 优先使用 Swagger 注解 `@ApiOperation` / `@Operation`
- 回退到 `Controller名称.方法名` 格式

### 分类自动管理
- 按 URL 路径第一段自动创建 YApi 分类（category）
- 自动创建不存在的分类

### 多 YApi 服务器支持
- 支持配置多个 YApi 服务器
- **模块名自动匹配**：根据模块根目录名自动模糊匹配对应的服务器
- 手动选择对话框（当有多个服务器时）

### 优秀的用户体验
- 后台异步上传，带进度指示
- 操作结果通过 Notification Balloon 通知
- 未配置服务器时友好提示
- 支持选中单方法或整个类批量生成
- 快捷键：`Ctrl+Alt+Y`

---

## 安装方式

### 方式一：从 JetBrains Marketplace 安装（推荐）
> **即将发布**，敬请期待。

### 方式二：本地磁盘安装（手动）
1. 从 [Releases](https://github.com/your-repo/releases) 页面下载最新的 `.jar` 或 `.zip` 插件包
2. 打开 IntelliJ IDEA，进入 `File → Settings → Plugins`
3. 点击齿轮按钮 → `Install Plugin from Disk...`
4. 选择下载的插件包
5. 重启 IDE

### 方式三：自行构建安装
```bash
# 克隆项目
git clone https://github.com/your-repo/yapi-idea-plugin.git

# 进入项目目录
cd idea-plugin

# 构建插件
./gradlew buildPlugin

# 构建产物位于 build/distributions/ 目录下
# 然后按"方式二"的步骤安装
```

---

## 快速开始

### 1. 配置 YApi 服务器

打开 `File → Settings → Tools → YApi Doc Generator`，点击 **+** 号添加服务器配置：

- **服务器名称**：自定义名称（如 "测试环境"、"生产环境"）
- **服务器地址**：YApi 服务地址（如 `https://yapi.example.com`）
- **Token**：YApi 项目的 Token（在 YApi 项目设置中获取）
- **项目 ID**：YApi 项目的 ID
- **设为默认**：勾选后作为默认服务器

![配置界面](./screenshots/settings-panel.png)

### 2. 生成接口文档

1. 在项目中打开一个 Spring MVC Controller 类
2. **生成单个接口**：在方法名上右键 → `Generate YApi Doc`
3. **生成整个类**：在类名上右键 → `Generate YApi Doc`
4. 或者选中后使用快捷键 `Ctrl+Alt+Y`
5. 如果有多个服务器配置，会弹出服务器选择对话框
6. 插件在后台自动解析并上传，完成后通过通知栏告知结果

![右键菜单](./screenshots/right-click-menu.png)

![上传成功通知](./screenshots/upload-success.png)

---

## 详细使用说明

### 1. 配置 YApi 服务器

#### 添加服务器
进入 `Settings → Tools → YApi Doc Generator`，通过表格管理多个服务器配置：

| 操作 | 说明 |
|------|------|
| **添加** | 点击 **+** 按钮，填写服务器信息 |
| **编辑** | 双击表格行或选中后点击编辑按钮 |
| **删除** | 选中行后点击 **-** 按钮 |

> **注意**：Token 在表格中会脱敏显示（`****`），编辑时可查看完整 Token。

![服务器配置对话框](./screenshots/server-config-dialog.png)

#### Token 获取方式
1. 登录 YApi 平台
2. 进入目标项目 → `设置 → Token 配置`
3. 复制 Token 填入插件配置

### 2. 生成接口文档

#### 单方法生成
在 Controller 中的某个方法上右键 → `Generate YApi Doc`，仅上传该方法对应的文档。

#### 整个类生成
在 Controller 类名上右键 → `Generate YApi Doc`，上传该类下所有被 Spring MVC 注解标记的方法。

#### 多服务器选择
当配置了多个服务器时，插件会弹出选择对话框：
- 如果某个服务器被设为 **默认**，会自动预选
- 如果模块名能模糊匹配到某个服务器，会自动预选
- 用户可以手动切换目标服务器


### 3. 服务器自动匹配机制

插件支持根据**当前代码所在模块的根目录名**自动匹配服务器，匹配规则：

- 忽略大小写差异（如 `E-Work` ↔ `ework-backend-enterprise`）
- 忽略分隔符差异（下划线、中划线、空格）
- 模糊包含匹配

**示例**：如果模块根目录名为 `e-work`，则会自动匹配到服务器名称包含 `ework` 的配置。

---

## 功能展示

> 📸 以下为截图占位符，您可以替换为实际截图。

| 场景 | 截图 |
|------|------|
| IDEA 右键菜单 | ![右键菜单](./screenshots/right-click-menu.png) |
| 服务器配置界面 | ![设置界面](./screenshots/settings-panel.png) |
| 服务器编辑对话框 | ![编辑对话框](./screenshots/server-config-dialog.png) |
| 上传成功通知 | ![上传成功](./screenshots/upload-success.png) |

---

## 技术架构

### 模块职责

```
┌─────────────────────────────────────────────────┐
│                    action 层                      │
│          GenerateYApiDocAction                   │
│     IDEA 右键菜单动作入口 & 快捷键绑定            │
├─────────────────────────────────────────────────┤
│                    parser 层                      │
│  ┌─────────────────┐  ┌───────────────────────┐  │
│  │SpringController │  │      DtoParser        │  │
│  │    Parser       │  │  DTO 类字段递归解析器 │  │
│  │ Controller 注解  │  │  嵌套对象·泛型·集合   │  │
│  │& 参数解析        │  │  枚举·继承·验证注解   │  │
│  └─────────────────┘  └───────────────────────┘  │
├─────────────────────────────────────────────────┤
│                    client 层                      │
│                YApiClient                        │
│    使用 Java HttpClient 调用 YApi 开放 API        │
├─────────────────────────────────────────────────┤
│                    config 层                      │
│  ┌─────────────┐  ┌──────────────────────────┐  │
│  │ YApiSettings │  │  YApiSettingsConfigurable │  │
│  │ 持久化配置    │  │  Settings 配置界面入口    │  │
│  └─────────────┘  └──────────────────────────┘  │
├─────────────────────────────────────────────────┤
│                     ui 层                        │
│             ServerSelectDialog                   │
│           服务器选择 UI 对话框                    │
└─────────────────────────────────────────────────┘
```

### 核心类说明

| 类名 | 包路径 | 职责 |
|------|--------|------|
| `GenerateYApiDocAction` | `com.yapi.plugin.action` | 入口 Action，处理右键菜单事件 |
| `SpringControllerParser` | `com.yapi.plugin.parser` | 解析 Spring MVC 注解和请求映射信息 |
| `DtoParser` | `com.yapi.plugin.parser` | 递归解析 DTO 类字段结构为 YApi 数据格式 |
| `YApiInterfaceInfo` | `com.yapi.plugin.parser.model` | YApi 接口数据模型 |
| `YApiClient` | `com.yapi.plugin.client` | YApi HTTP API 客户端，负责文档上传 |
| `YApiSettings` | `com.yapi.plugin.config` | 基于 PersistentStateComponent 的持久化设置 |
| `YApiSettingsConfigurable` | `com.yapi.plugin.config` | Settings 配置界面 |
| `YApiSettingsPanel` | `com.yapi.plugin.config` | 设置面板 UI |
| `YApiServerConfig` | `com.yapi.plugin.config` | 服务器配置数据模型 |
| `ServerSelectDialog` | `com.yapi.plugin.ui` | 服务器选择对话框 |

---

## 开发指南

### 技术栈

| 组件 | 版本 |
|------|------|
| Java | 17 |
| IntelliJ Platform | 2024.3 (IC) |
| Gradle | Kotlin DSL |
| 最低兼容版本 | IntelliJ 2023.2+ (since-build 232) |
| JSON 处理 | Google Gson 2.10.1 |

### 开发环境要求

- IntelliJ IDEA 2023.2+（建议使用 Ultimate 版本以获得完整 Spring 支持）
- JDK 17+
- Gradle（使用项目附带的 Gradle Wrapper）

### 常用命令

```bash
# 运行 IntelliJ 插件开发实例
./gradlew runIde

# 构建插件包（输出到 build/distributions/）
./gradlew buildPlugin

# 清理构建产物
./gradlew clean
```

### 项目结构

```
idea-plugin/
├── src/main/
│   ├── java/com/yapi/plugin/
│   │   ├── action/
│   │   │   └── GenerateYApiDocAction.java    # 右键菜单动作入口
│   │   ├── client/
│   │   │   └── YApiClient.java               # YApi HTTP 客户端
│   │   ├── config/
│   │   │   ├── YApiServerConfig.java         # 服务器配置模型
│   │   │   ├── YApiServerEditDialog.java     # 添加/编辑服务器对话框
│   │   │   ├── YApiSettings.java             # 持久化配置
│   │   │   ├── YApiSettingsConfigurable.java # 设置界面入口
│   │   │   └── YApiSettingsPanel.java        # 设置面板 UI
│   │   ├── parser/
│   │   │   ├── SpringControllerParser.java   # Controller 注解解析器
│   │   │   ├── DtoParser.java                # DTO 递归解析器
│   │   │   └── model/
│   │   │       └── YApiInterfaceInfo.java    # 接口数据模型
│   │   └── ui/
│   │       └── ServerSelectDialog.java       # 服务器选择对话框
│   └── resources/META-INF/
│       └── plugin.xml                        # 插件注册配置文件
├── screenshots/                              # 截图资源目录
├── build.gradle.kts                          # Gradle 构建脚本
├── settings.gradle.kts
├── README.md
└── README.en.md
```

### 调试技巧

1. 使用 `./gradlew runIde` 启动调试实例
2. 在插件代码中设置断点进行调试
3. 日志输出可在 IDE 的 `Event Log` 和对话框通知中查看
4. 修改 `plugin.xml` 中的 `<idea-version since-build="232"/>` 可调整最低兼容版本

---

## 许可证

```
Copyright (c) 2024

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

> **提示**：文档中的截图路径为占位符，请将实际截图放置于 `screenshots/` 目录下。
>
> 如果您在使用过程中遇到任何问题，欢迎提交 [Issue](https://github.com/your-repo/issues) 或 Pull Request。

[**English**](./README.en.md) | **中文**
