# GuGuGu Mod详细文档

本MOD为多人游戏服务器提供多样化的辅助工具，显著改善玩家游戏体验，同时允许服务器管理员灵活地控制和配置相关功能。

---

## 功能详细介绍

### 一、假人玩家功能（FakePlayer）

基于知名的 Carpet MOD 的假人功能，并修复了原版存在的内存溢出、协议缺失等导致无法进入部分 mod 服务器的问题。

#### 假人指令详解（`/rifakeplayer` 或 `/fp`）

* **创建假人**

  ```
  /fp create 玩家名
  /fp create 玩家名 x y z
  /fp create 玩家名 x y z yaw pitch
  /fp create 玩家名 x y z in dimension
  ```

* **杀死假人**

  ```
  /fp kill 玩家名
  ```

* **打开假人背包**（需要权限等级4）

  ```
  /fp open 假人名 [true|false]
  /fp open 假人名 viewer 玩家名 [true|false]
  ```

* **自动登录设置**

  ```
  /fp autoLogin enable 假人名
  /fp autoLogin disable 假人名
  /fp autoLogin list
  ```

* **控制假人动作**

  ```
  /fp control 假人名 [use|attack|jump|drop|stopAll] [interval|continue]
  ```

#### 假人配置说明

配置文件位置：`config/gugugu/fakeplayer_config.json`

* 权限等级（`commandLevel`）
* 是否允许打开假人背包（`allowOpenInventory`）
* 是否允许非管理员与假人互动（`allowInventoryInteraction`）
* 是否无视NetworkRegistry.checkPacket对假人的检查（`allowFakeServerGamePacketListenerImpl`）
* 假人名称前后缀设置
* 假人自动登录列表

---

### 二、玩家消息处理功能（ChatEvent Components）

支持丰富的聊天消息富文本处理，包含以下消息处理器：

* **mention**：`@{玩家}`或`@all`，强调提示特定玩家。
* **link**：自动将有效的HTTP链接转换为可点击链接。
* **component**：自动组合消息中的特殊格式（不可禁用）。
* **mention\_notify**：为被提醒玩家提供额外的音效和屏幕提示。
* **teleport**：单独输入玩家名称实现点击传送。
* **hand\_item**：支持在聊天中展示玩家持有物品，示例：`[i]`主手、`[io]`副手。
* **xaero\_map\_util**：支持 Xaero 地图路径点点击添加。
* **journey\_map\_util**：支持 JourneyMap 路径点点击添加。

#### 禁用组件方法

在`config/gugugu/config.json`中，通过`disabledMessageHandlers`字段指定禁用的组件。

---

### 三、服务器性能诊断（ShowStats）

* 使用指令 `/showstats`，快速获取当前服务器性能数据。

---

### 四、玩家间传送（TPF）

* 允许非 OP 玩家快速传送到其他玩家。

  ```
  /tpf 目标玩家名称
  ```

---

### 五、服务器备份与区域热回档（GBackup）

提供高效的手动、定时备份与热回档。

#### 指令详解

* **增量备份**：

  ```
  /gbackup inc
  ```

* **全量备份**：

  ```
  /gbackup full
  ```

* **热回档**（单区块）：

  ```
  /gbackup rollback [hot inc|hot full] [坐标]
  ```

* **区域热回档**：

  ```
  /gbackup rollback area 坐标1 坐标2 [hot inc|hot full]
  ```

#### 配置说明

配置文件：`GBackups/world/gbackup.json`

* 自动备份时间、是否启用压缩、保留备份数量
* 热回档数据源设置（增量或全量）
* 备份指令白名单

---

## 核心配置总览

位置：`config/gugugu/config.json`

* 假人功能启用开关（`enableFakePlayer`）
* 消息处理启用开关（`enableMessageHandler`）
* 玩家传送功能开关（`enableTeleport`）
* 备份功能开关（`enableBackup`）
* 白名单UID检查禁用开关（`whiteListDisableUidCheck`）