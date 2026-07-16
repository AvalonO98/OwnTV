<p align="center">
  <img src="extras/logo.png" alt="OwnTV" width="360">
</p>

<p align="center">
  <b>OwnTV 汉化版</b><br>
  <sub>适用于 Android TV 的 IPTV 播放器 · 完整简体中文界面</sub>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue">
  <img alt="Language" src="https://img.shields.io/badge/界面-简体中文-red">
</p>

---

## 关于本项目

这是 [OwnTV](https://github.com/AvalonO98/OwnTV) 的**简体中文汉化版本**。

OwnTV 是一款原生的 Android TV IPTV 播放器，基于 Kotlin + Jetpack Compose for TV 构建，支持双播放引擎（libmpv + ExoPlayer）。本项目对其界面进行了完整的简体中文翻译，方便中文用户使用。

> ⚠️ 本应用**不提供**任何频道、播放列表或媒体内容，用户需自行添加合法的播放源。

## 汉化内容

| 模块 | 汉化范围 |
|------|---------|
| 导航菜单 | 搜索、首页、直播、电影、剧集、下载、节目指南、设置 |
| 设置界面 | 全部设置项、搜索框、开关按钮、对话框 |
| 侧边栏 | 用户信息、切换用户、分类标签 |
| 主页 | 持续观看、最近频道、收藏频道、继续观看 |
| 退出/提示 | 退出确认、离线提示、缩放警告、回看时间 |
| 技术术语 | HDR、PIN、M3U、Xtream、XMLTV 等保持原样 |

## 版本号

版本号跟随上游原仓库，格式为 `原版本号-zh`。例如上游发布 `v4.1.2` → 汉化版为 `v4.1.2-zh`。

## 自动同步上游更新

每天自动检查 [ahXN00/OwnTV](https://github.com/ahXN00/OwnTV) 是否有新版本。发现更新后自动合并并编译新 APK，无需手动干预。

## 自动编译

每次推送代码后，GitHub Actions 自动编译 APK，可在 [Releases](https://github.com/AvalonO98/OwnTV/releases) 页面下载安装。

## 安装

从 [Releases](https://github.com/AvalonO98/OwnTV/releases) 下载最新 APK， sideload 到 Android TV 设备即可。

## 致谢

- 原项目：[AvalonO98/OwnTV](https://github.com/AvalonO98/OwnTV)
- 原作者：[Ashiq Hasan](https://github.com/ahXN00)

## 许可证

GNU General Public License v3.0 — 详见 [LICENSE](LICENSE)。

---

<sub>OwnTV 是一个仅播放的开源项目。本项目仅为其中文汉化版本，功能与原版一致。</sub>