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
| 开机落点 | 旧桌面(需手动) | 开机约 1 分钟内自动回到 MiniHome(见下「HOME 保卫战」) |

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

### 4. MiniHome 极简桌面（v5 壁纸版）

```
┌──────────────────────────────────────────────┐
│  (相册壁纸 + 三层渐变蒙皮)        18:13      │
│                              10月24日 星期六  │
│  全部应用                    城市 多云 15°C   │
│  [夸克网盘][相册][文件][日历]  ┌───────────┐ │
│  [投屏] [音乐][天气][设置]    │ 启动Switch │ │
│  …(自动扫描全部已装应用)      ├───────────┤ │
│  [老系统](虚线磁贴)           │开启有线电视│ │
│                              └───────────┘ │
│ OK 启动 · 长按OK 隐藏/排序 · 长按老系统 管理隐藏│
└──────────────────────────────────────────────┘
```

- 纯 Android framework 代码，零依赖；全部顶层类（d8 编译器对内部类/匿名类有 NPE bug）
- **壁纸**：U 盘/本地相册照片打底（按「天数+开机次数」轮换，每次开机换一张），三层渐变蒙皮保证文字可读；解码失败自动回退本地照片池，30 秒巡检自愈（开机早期能存储未就绪也不怕）
- **应用墙**：`queryIntentActivities` 自动扫描全部可启动应用（真实图标+名称排序），4 列网格；长按 OK 弹「上移/下移/隐藏/管理」，顺序与隐藏清单存 SharedPreferences；长按「老系统」磁贴打开隐藏面板可恢复
- **两张操作卡**：启动 Switch（直切 HDMI2）/ 开启有线电视（直切 TV 调谐器，无信号面板）
- **焦点动效**：放大 1.06 + 橙色光圈 + 描边加粗，150ms；缩放作用在内层卡片上防越界裁剪；网格↔卡片列手动四向导航（原生 beam 焦点搜索在这种布局会死胡同）
- 天气走纯 HTTP（ip-api 定位失败兜底固定城市 + open-meteo，绕开 Android 5.1 证书问题），30 分钟重刷，失败不清空旧显示

构建（首次自动下载 SDK；电视不在线也能构建，图标用占位图）：

```bash
cd launcher
TV_IP=192.168.x.x ./build.sh   # 构建 + 免人值守安装(自动点掉 MIUI 安装确认框)
./build.sh --no-install        # 只构建
```

#### Switch 计时（v5 已默认移除）

点「启动 Switch」直接切 HDMI2，不再计时（按家庭需求移除，`TimerService` 源码保留可自行启用）。
保留了工作日晚间的「不玩 Switch」提醒（可改 `isSchoolNight()`）。
实测补充：这台电视的 MIUI **允许** `TYPE_SYSTEM_ALERT` 悬浮角标（此前"悬浮窗必被拦"是手机端 MIUI 的结论），
TimerService 的实时倒计时角标可用，横幅 Activity 作为兜底。

#### 照片屏保

桌面 10 分钟无操作自动进入照片屏保（任意键退出）：优先读 U 盘照片
（插上 USB 口即用，jpg/png，最多 200 张随机播放），无 U 盘时读电视本地 `/sdcard/屏保/`。
交叉淡入淡出 + 缓推（Ken Burns），右下角半透明小时钟。
**刻意不用系统 DreamService**：那需要重新打开 screensaver_enabled/熄屏定时，
正是为保住 adb 连接专门关掉的东西——内置屏保零系统改动。

#### 家庭文件（本地文件夹直达）

桌面上的「家庭文件」方块打开电视本地存储的 `/sdcard/家庭文件/`：
里面放几个文件夹就显示几个，可逐层进入；视频点击直接调系统播放器，图片直接看，断网可用。
内容通过电脑拷入：`adb push 本地目录/* 电视IP:/sdcard/家庭文件/`，或 U 盘。

> 关于把**网盘（如夸克）云端的文件夹**直接铺到桌面：不可行。实测其 TV 版只是个
> 运行时壳（eskit/extscreen），所有页面运行时从服务器下发，APK 内无路由、无导出
> 内容接口、无公开 API——云端内容只能进它的应用看。本地化常用内容才是老电视的最优解。

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

## HOME 保卫战：MIUI 硬编码默认桌面的对抗记录（本项目最硬的一仗）

想让第三方桌面在开机时直接顶掉 2015 桌面，实测走通了这条路：

1. **MIUI 把 HOME 硬编码给系统桌面**。证据：`pm hide` 旧桌面后按 HOME 正确落到 MiniHome，
   `pm unhide` 一恢复立刻抢回；老桌面在前台时按 HOME 是它自己拉自己（uid=1000 发的显式 intent）。
2. **`addPreferredActivity` 无效**：`SET_PREFERRED_APPLICATIONS` 是签名级权限，普通应用安装时被拒发
   （logcat: `Not granting permission ... protectionLevel=2`）。
3. **系统选择器「始终」有效但会被覆盖安装作废**：
   `adb shell am start -n android/com.android.internal.app.ResolverActivity -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10000000`
   弹出选择器，选 MiniHome + 始终 → 写入 `mAlways=true`，重启保留；但**每次 install 覆盖本包都会作废**。
4. **开机广播全被自启动策略拦**：`BOOT_COMPLETED`、`MEDIA_MOUNTED` 都收不到（开机进程表里本包从未被拉起）。
5. **破局：账号周期同步自愈**。Android 的同步调度由系统框架在开机后自行恢复，**不走广播、不受 MIUI 自启动管制**：
   - 假账号（`AccountAuthenticator` stub）+ `SyncAdapter` + 空 `ContentProvider`，周期 60 秒
   - 双条件把桌面拉到前台（任一命中）：内核开机计时 <5 分钟（快速开关机也算）；或墙钟打卡间隔 >3 分钟（长时间断电）
   - 平时同步空转零打扰；每次 `install -r` 后还有 `MY_PACKAGE_REPLACED` 广播（这个能收到）自动把新桌面拉到前台
   - 副作用：系统账号列表里多一个不可见的 stub 账号

结果：**开机 →（旧桌面/信号源至多约 1 分钟）→ MiniHome 自动到前台**。旧桌面保持可见以便「老系统」磁贴直进，
返回键即可退出。附赠逃生口：MiniHome 同时注册 `CATEGORY_LAUNCHER`，旧桌面的应用列表里永远能手动点开它。

## 运维小抄（这台机器的隐性坑）

- **adb server 会僵死**：ping 通但 `adb connect` 报 `No route to host` → `adb kill-server && adb start-server`
- **`adb install` 长连接易断流假成功**：稳妥两步法 `adb push` + `pm install`；MIUI 的「安装确认」框卡住时
  adb 仍可用——`uiautomator dump` 拿坐标 + `input tap` 直接点掉（build.sh 已内置）
- **每次重启会自动关闭 ADB 调试开关**：需到 设置→账号与安全 重开（系统行为，改不掉）
- **别把 SDK 放 /tmp**：macOS 数天不访问即清空，构建环境一夜蒸发

## 网盘类应用「闪退」的真相（以夸克 TV 版为例）

现象：使用中时不时退回桌面。日志实锤**不是崩溃**——进程从不死亡、无 FATAL、无系统查杀；
是 eskit 壳在内存耗尽时**主动关页自保**（退页前 Java 堆 GC 抖到 "1% free"、路由关闭、播放器释放）。
1GB 内存的 2015 电视跑云端下发页面的重型应用，这是结构性矛盾。
缓解：用完按返回彻底退出（立刻还 ~160MB）；闪退后再进秒开不丢登录态。

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
├── launcher/           MiniHome 源码 + 一键构建脚本
│   └── res/xml/        同步自愈的 authenticator/syncadapter/device_admin 配置
```

## License

MIT（MiniHome 部分）；清理清单与文档以 CC-BY-4.0 提供。
信号源图标构建时从你自己设备的固件提取，仓库不分发小米版权资源。
