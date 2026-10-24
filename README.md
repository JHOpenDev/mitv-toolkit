# mitv-toolkit — 老小米电视清理 + 自建极简桌面 全套工具

给 2014–2017 年老小米电视（MIUI TV 系统，Android 4.4–6，实测机型：小米电视 2S / Android 5.1.1）的一整套：

- **开机提速诊断**：用开机时间线日志精确定位慢在哪
- **广告/后台清理**：两档清单 + 可逆的 `pm hide`，附恢复脚本
- **开机广告根除**：降级回出厂桌面的完整打法（这代机器的开机广告长在新版桌面 APK 里）
- **MiniHome**：一个自研的极简桌面 APK（Android 5.1 原生编译，无任何依赖），带信号源一键切换、WiFi 状态、天气、大时钟

## 实测效果（小米电视 2S, Android 5.1.1）

| 指标 | 之前 | 之后 |
|---|---|---|
| 开机到桌面可操作（按电源起算） | ~52 s（其中约 20 s 为不可跳过开机广告） | ~35 s（无广告无开机视频，直接进桌面） |
| 开机自启进程 | 75 | 40 |
| 开机广告 | 20 秒不可跳过 | 无 |
| 后台内存压力(swap) | 接近满 | 基本为零 |
| 屏保/假待机随机断连 adb | 有 | 可关闭 |

## 为什么不用现成项目？

- [tv-detox](https://github.com/Ansub/tv-detox)：面向 Android TV 9+ / Google TV，包名单全是 TCL/OnePlus/Google
- [cleanyourtv](https://github.com/Gathin23/cleanyourtv)：很棒（本项目的 pm hide 打法与它一致），但它的极简桌面要求 Android 8+，且小米机型清单不含 2015 这代

这代机器还得额外面对：`pm disable-user` 被 MIUI 拒绝、`setprop persist.*` 被锁、Android 5.1 编译/HTTPS 的一堆老坑——全部记录在下面的「本代机器的坑」。

---

## 快速开始

### 0. 打开 ADB（这代固件没有「版本号」可点）

```
设置 → 关于 → 产品型号   遥控器连按「确定」7 次
设置 → 账号与安全 → ADB调试 → 开
```

```bash
export TV_IP=192.168.x.x        # 你的电视 IP
adb connect $TV_IP:5555
```

### 1. 只读体检（不动任何东西）

```bash
scripts/audit.sh                 # 版本/存储/内存/自启进程/已隐藏清单
scripts/boot-timeline.sh         # 重启后立刻跑:本次开机时间线
```

开机慢的判断方法：`boot_progress` 时间线里 `pms_data_scan_start → pms_scan_end` 这段是扫 /data 应用，在这代机器的存储速度下 ~13 秒是物理下限，**只有真卸载应用才能缩短**（`pm hide` 不会，见下）。

### 2. 清理（可逆）

```bash
scripts/debloat.sh debloat/tier1-ads-telemetry.txt    # 广告/遥测/推送,放心关
scripts/debloat.sh debloat/tier2-optional.txt         # 可选,逐项自己决定
scripts/restore.sh  debloat/tier1-ads-telemetry.txt   # 一键恢复
```

原理：这代 MIUI 拒绝 shell 执行 `pm disable-user`（SecurityException），等效且可逆的是
`adb shell pm hide <包名>` / `pm unhide`。已隐藏的包不运行、不收广播，但**仍参与开机包扫描**。

### 3. 开机广告根除

这代机器的开机广告**不是独立应用**，是新版桌面 `com.mitv.tvhome` 内嵌的小米广告 SDK 开机向 `api.ad.xiaomi.com` 拉取、由桌面的 BootActivity 播完才放行。本机开关 `persist.sys.adswitch` 被 SELinux 锁死、缓存在 `/data/data` 进不去——所以正确解法是**把桌面降级回出厂版**（2015 出厂桌面没有广告 SDK）：

```bash
adb shell pm path com.mitv.tvhome                  # 确认有 /data 更新版
adb pull /system/vendor/app/TvHome/TvHome.apk .    # 出厂版(每个机型路径可能不同)
adb install -r -d TvHome.apk                       # -d 允许降级;同签名系统包不弹确认框
```

装上第 4 步的 MiniHome 并把旧桌面 `pm hide` 后，开机直接进 MiniHome，广告载体整个消失。
**反悔**：`adb install -r <你备份的新版桌面.apk> && adb shell pm unhide com.mitv.tvhome`。

### 4. MiniHome 极简桌面

```
┌────────────────────────────────────────────────┐
│ 极简桌面    📶 城市 多云 15°C   2026年10月24日 星期六 18:13:36 │
├────────────────────────────────────────────────┤
│     [有线电视]   [Switch]   [夸克网盘]             │
│     [儿童思维]   [拼音乐园]  [识字乐园]             │
│     [设置]                                     │
└────────────────────────────────────────────────┘
```

- 纯 Android framework 代码，零依赖，~20 KB
- 信号源一键直切（extras 逆向结论见下）、WiFi 强度、IP 定位天气（纯 HTTP，绕开 Android 5.1 的证书问题）、时钟到秒
- 全屏沉浸（压掉 MIUI 系统状态栏）
- 应用图标运行时读系统；信号源图标构建时从你自己电视的出厂桌面 APK 提取（版权原因仓库不带）

构建（会自动下载 SDK 组件并从电视提取图标，需要电视 adb 在线）：

```bash
cd launcher
./build.sh                 # 产出 out/MiniHome.apk 并安装
adb shell pm hide com.mitv.tvhome     # 把旧桌面藏起,HOME 键归 MiniHome
```

改常用应用：编辑 `src/com/minihome/tv/MainActivity.java` 里的 `buildGrid()`。

### 信号源切换（逆向结论）

切源接口：`com.xiaomi.mitv.tvplayer/.ExternalSourceActivity`

```bash
adb shell am start -n com.xiaomi.mitv.tvplayer/.ExternalSourceActivity \
    --ei input 24 --ez no_launch_panel true
```

- extra 键是 **`input`**（不是 source / inputsource——猜错则静默无效）
- `no_launch_panel=true` 直切不弹选择面板
- 编号来自固件 `TVSdkImpl.smali` 的 sparse-switch（2S 实测）：HDMI1=23，HDMI2=24，HDMI3=25，AV=2，TV 调谐器=1。**不同机型可能不同**，挖法：baksmali 反编译你电视上的 `MiTVTVPlayer.apk` 后搜 `"HDMI1"`
- 验证：logcat 出现 `set source done` / `onCurrentSourceChanged <n>` 即成功

---

## 本代机器的坑（全部实测，写在这里省你几小时）

| 现象 | 真相 | 对策 |
|---|---|---|
| adb 连一会就 5555 拒绝 | 电视「假待机」（6095 端口还在），adbd 随待机关 | 遥控器按键唤醒即恢复；重启后 ADB 也自动恢复，无需重开开关 |
| `pm disable-user` → SecurityException | MIUI 拒 shell 改 uid=1000 包 | `pm hide` / `pm unhide` |
| `setprop persist.*` → could not set property | SELinux | 无解 |
| 改电视 DNS（ndc/net.dns/静态IP） | 全部被 netd/IPv6 RA 压回 | 想断广告域名只能路由器侧；或直接去掉广告载体（第 3 步） |
| adb 装第三方 APK 挂起不返回 | 电视弹了「允许安装」确认框 | 遥控器按允许；系统签名包不弹 |
| `pm uninstall com.mitv.tvhome` | 挂起，PackageManager 死锁 | 用 `install -r -d` 降级代替 |
| 隐藏的旧桌面显式启动 → Error type 3 | 隐藏包不可启动；解除隐藏会抢 HOME 键，Android 5.1 无 `cmd`/set-home 命令 | 两个桌面无法共存切换，用 hide/unhide 一键互换 |
| `uiautomator dump` 报 FATAL | 工具自身崩 | 忽略，dump 文件仍可用 |
| Android 5.1 访问现代 HTTPS | 根证书过期 | 只用纯 HTTP 接口（天气：ip-api.com + open-meteo.com） |
| 编译时 d8 NPE：`Cannot invoke "String.length()"` | d8 处理**内部类/匿名类**必崩 | 所有类写成顶层独立 .java 文件 |

## 安全网

- 改动前后各存一份 `adb shell dumpsys package > 存档`
- 桌面 APK 动手前先 `adb pull` 备份（新版 + 出厂版都留在电脑上）
- 一切包操作用 `pm hide`（可逆），保持 `pm uninstall` 只用于你明确要删的第三方应用
- 系统桌面出问题的恢复路径：adb 一直在线（不受桌面崩溃影响），`install -r 备份.apk` 或 `pm unhide` 即可救回

## 目录

```
mitv-toolkit/
├── README.md
├── debloat/            两档清理清单 + 永不触碰清单
├── scripts/            audit / boot-timeline / debloat / restore
└── launcher/           MiniHome 源码 + 一键构建脚本
```

## License

MIT（MiniHome 部分）；清理清单与文档以 CC-BY-4.0 提供。
信号源图标构建时从你自己设备的固件提取，仓库不分发小米版权资源。
