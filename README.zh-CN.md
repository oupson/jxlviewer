# JxlViewer

> 语言: [English](README.md) | **简体中文**

[![](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/fr.oupson.jxlviewer)](https://apt.izzysoft.de/fdroid/index/apk/fr.oupson.jxlviewer)
[![GitHub release (latest by date)](https://img.shields.io/github/downloads/oupson/jxlviewer/latest/total)](https://github.com/oupson/jxlviewer/releases/latest)
[![Support me](https://img.shields.io/liberapay/patrons/oupson.svg?logo=liberapay)](https://liberapay.com/oupson/)
[![Maven Central Version](https://img.shields.io/maven-central/v/fr.oupson/libjxl)](https://central.sonatype.com/artifact/fr.oupson/libjxl/overview)

一个使用 [libjxl](https://github.com/libjxl/libjxl) 的 Android Jpeg XL 查看器。

## 安装
- 到 [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/fr.oupson.jxlviewer) 获取！
- 或从 [latest release](https://github.com/oupson/jxlviewer/releases/) 安装 apk（不确定 ABI 就装 universal）。

## 更新日志
[见此](app/CHANGELOG.md)

## HDR（硬件）实现说明

关于 HDR JXL 图像（Ultra HDR，`intensity_target > 0`）如何利用设备的**硬件 HDR 管线**显示，
以及随之而来的加载耗时与缩放优化，这里是一份开发总结（不是用户手册——对 SDR 图像行为完全不变）。

### 目标与约束

- 在 HDR 面板（540 nit 级别设备）上按**绝对亮度**显示 HDR JXL，保留高光。
- SDR 路径的行为必须保持原样：任何 HDR 环节失败都**降级**到普通 SDR 路线，绝不中断解码。

最终方案确定前，有两条架构路线被实测否决：

1. **解码器内 tone-map（A1 路线）**：能生成观感正确的 SDR 图，但**不会触发硬件 HDR 管线**——面板不提升亮度。
2. **display-referred F16 走 Compose/Canvas 路径**：渲染容器会把 `RGBA_F16` + PQ 位图
   tone-map 进 8-bit sRGB 的窗口 buffer，高光在显示栈看到之前就被压平了。
   SurfaceFlinger 实测：窗口层是 `V0_SCRGB`、无 HDR metadata，即使 `hdrSdrRatio` 已是 5x。
   结论：display-referred HDR 必须让**窗口本身**跑在 HDR 管线里。

### 最终数据形态

| 环节 | 格式 |
|---|---|
| JXL 源 | scene-referred，BT.2100，PQ，intensity_target ≈ 1000 nit |
| 解码器输出 | **BT.2020 primaries + PQ 传输函数**，`SetDesiredIntensityTarget(10000)` |
| 位图 | `RGBA_F16`，存 **PQ 码值**（1.0 = 10000 nit） |
| 位图色空间 | `ColorSpace.Named.BT2020_PQ`（ICC parametric，10000 nit 参考），**创建时即打上** |
| 窗口 | API 31+ 用 `colorMode = ENHANCED`（值 2），更低版本用 `WIDE_COLOR_GAMUT` |

Mac 上用同版本 libjxl 0.12.0 做过数值验证：1000 nit 峰值的源图，解码回读
≈ 1125 nit（p50 = 41.8 nit，p999 = 1015 nit）——动态范围在往返中保持。

### 代码路径

1. **`JXL_DEC_COLOR_ENCODING`**（`Decoder.cpp`）：对 HDR + F16 输出，通过
   `JxlDecoderSetDesiredIntensityTarget` + `JxlDecoderSetCms` +
   `JxlDecoderSetOutputColorProfile` 请求 BT.2020 + PQ（10000 nit 参考），
   并打开 image-out 回调里的 **HDR passthrough** 标志。
2. **image-out 回调**（`ImageOutCallbackData.h`）：passthrough 模式把 F16 码值
   **逐字节原样**拷进位图，补 alpha = 1.0f。这里**绝不能走 `skcms_Transform`**，
   否则码值会被重新编码。
3. **位图创建**：`createBitmap(w, h, RGBA_F16, false, Named.BT2020_PQ)`
   （API 28+ 重载），JNI 构造函数里一次性解析并持全局引用。
4. **窗口**（`ViewerScreen.kt`）：Android 13+ 设 `ENHANCED` 色模式，
   让 SurfaceFlinger 把 F16 buffer 按 HDR 管线合成。
5. **降级**：每个 JNI 解析步骤都清挂起异常、落到 SDR 路线。API 缺失、方法查找失败、
   位图创建异常——任何一种都不会中断解码。

### 踩坑记录（全部实测得出）

- `Bitmap.setColorSpace()` 拒绝把已有的 sRGB 位图换成 BT.2020_PQ
  （报错 "cannot increase the minimum value for any of the components"）——
  色空间必须在**创建时**给。
- 自定义 `ColorSpace.Rgb`（线性 lambda OETF）**同样被拒**——`setColorSpace()`
  只收 ICC parametric 空间。`Named.BT2020_PQ` 是平台自带的锚点，
  正好匹配解码器 10000 nit 参考。
- JNI 挂起异常不清掉，会污染后续每次调用并把图搞成全黑——每个失败路径都要清。
- `jxl_cms` 必须显式链接，否则 `JxlGetDefaultCms()` 解析不到。
- 该 NDK 环境下 `ALOGI` 宏不展开，直接用 `__android_log_print`。

### 性能：加载提速约 30 倍

4032×2268 的 F16 图在设备上解码要 **~11 秒**。根因不在算法：同一张图、同版本
libjxl 0.12.0，在 M 系列 Mac 上 15 线程只要 **44 ms**——250 倍差距远超任何
CPU 差异。真相是 AGP debug 构建从没设过 `CMAKE_BUILD_TYPE`，
**整个 native 库是按 -O0 编译的**。

修复（都在 perf commit 里）：

- libjxl native 构建加 `-DCMAKE_BUILD_TYPE=Release` → 设备端 **~300 ms**。
- JNI 输入缓冲 4 KiB → 256 KiB（减少喂数据/`ProcessInput` 往返）。
- 并行 runner 用硬件并发数（上限 8）代替保守的分辨率启发式 `SuggestThreads()`。

### 缩放清晰度

旧实现每次绘制都把位图软件降采样到 canvas 尺寸，GPU 缩放放大的其实是
"已经糊过的小图"。现在纹理保持**原图分辨率**（封顶 5× canvas，控制 8K+ 源的
GPU 内存），采样质量用 `FilterQuality.High`——放大到封顶值以内都是 1:1 真实像素。
F16 位图在源色空间内缩放，避免换色空间标签的校验问题。

### 验证

- 设备：Android 16（API 36），OPPO PJX110，1264×2780，峰值 540 nit，
  `supportedHdrTypes=[1,2,3,4]`。
- HDR 测试图激活硬件 HDR 管线（SurfaceFlinger 层 dump 确认），高光显示正确。
- 最终 commit 状态 13/13 ad-hoc 检查通过：Release 编译级、二进制标记、
  无调试残留、装机 + 进程冒烟、无 FATAL。
- 上游 PR：[oupson/jxlviewer#46](https://github.com/oupson/jxlviewer/pull/46)

## 许可证
MIT license（LICENSE-MIT 或 http://opensource.org/licenses/MIT）。
