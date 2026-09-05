# JxlViewer
[![](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/fr.oupson.jxlviewer)](https://apt.izzysoft.de/fdroid/index/apk/fr.oupson.jxlviewer)
[![GitHub release (latest by date)](https://img.shields.io/github/downloads/oupson/jxlviewer/latest/total)](https://github.com/oupson/jxlviewer/releases/latest)
[![Support me](https://img.shields.io/liberapay/patrons/oupson.svg?logo=liberapay)](https://liberapay.com/oupson/)
[![Maven Central Version](https://img.shields.io/maven-central/v/fr.oupson/libjxl)](https://central.sonatype.com/artifact/fr.oupson/libjxl/overview)

A Jpeg XL viewer for android using [libjxl](https://github.com/libjxl/libjxl).

## Installation
- Get it on [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/fr.oupson.jxlviewer) !
- Or install apk from [latest release](https://github.com/oupson/jxlviewer/releases/) (install universal if you don't know your abi).

## Changelog
[See here](app/CHANGELOG.md)

## HDR (Hardware) Implementation Notes

Notes on how HDR JXL images (Ultra HDR, `intensity_target > 0`) are displayed
with the device's hardware HDR pipeline, plus the load-time and zoom
optimizations that came with it. This is a development summary, not a user
manual — the app behaves identically for SDR images.

### Goal and constraints

- Show HDR JXL at **absolute luminance** on HDR panels (540 nit-class devices),
  with highlights preserved.
- The SDR path must stay bit-identical in behavior: any HDR failure degrades to
  the plain SDR route, it never aborts the decode.

Two architectural dead ends were found empirically before the final design:

1. **Tone-mapping in the decoder (route A1)** produces a correct-looking SDR
   image but does not trigger the hardware HDR pipeline — the panel never
   boosts.
2. **Display-referred F16 through the Compose/Canvas path (route A2-via-Compose)**:
   the rendering container tone-maps the `RGBA_F16` + PQ bitmap into the
   8-bit sRGB window buffer, flattening highlights before the display stack
   sees them. SurfaceFlinger confirmed the window layer as `V0_SCRGB` with no
   HDR metadata even though `hdrSdrRatio` was 5x. Display-referred HDR only
   works if the window itself runs in the HDR pipeline.

### Final data form

| Stage | Format |
|---|---|
| JXL source | scene-referred, BT.2100, PQ, intensity_target ≈ 1000 nit |
| Decoder output profile | **BT.2020 primaries + PQ transfer**, `SetDesiredIntensityTarget(10000)` |
| Bitmap | `RGBA_F16`, stores **PQ code values** (1.0 = 10000 nit) |
| Bitmap color space | `ColorSpace.Named.BT2020_PQ` (ICC parametric, 10000 nit reference), **set at creation time** |
| Window | `colorMode = ENHANCED` (value 2) on API 31+, `WIDE_COLOR_GAMUT` below |

Numerically verified on Mac with the same libjxl 0.12.0: a 1000 nit peak
source decodes back to ≈ 1125 nit (p50 = 41.8 nit, p999 = 1015 nit) — dynamic
range preserved through the round trip.

### Code path

1. **`JXL_DEC_COLOR_ENCODING`** (`Decoder.cpp`): for HDR + F16 output, request
   `BT.2020 + PQ` with a 10000 nit intensity target via
   `JxlDecoderSetDesiredIntensityTarget` + `JxlDecoderSetCms` +
   `JxlDecoderSetOutputColorProfile`. Enable the **HDR passthrough** flag in
   the image-out callback.
2. **Image-out callback** (`ImageOutCallbackData.h`): passthrough mode copies
   the F16 code values **verbatim** into the bitmap and pads alpha = 1.0f.
   `skcms_Transform` must *not* run here — it would re-encode the values.
3. **Bitmap creation**: `createBitmap(w, h, RGBA_F16, false, Named.BT2020_PQ)`
   (API 28+ overload), resolved once in the JNI constructor and held as a
   global ref.
4. **Window** (`ViewerScreen.kt`): `ENHANCED` color mode on Android 13+ so
   SurfaceFlinger composites the F16 buffer in the HDR pipeline.
5. **Fallbacks**: every JNI resolution step clears any pending exception and
   degrades to the SDR route. A missing API, a failed method lookup, or a
   bitmap-creation exception never aborts the decode.

### Pitfalls (measured, not theoretical)

- `Bitmap.setColorSpace()` rejects re-tagging an existing sRGB bitmap to
  BT.2020_PQ ("cannot increase the minimum value for any of the components").
  The color space must be supplied **at creation time**.
- Custom `ColorSpace.Rgb` with a linear lambda OETF is *also* rejected —
  `setColorSpace()` only accepts ICC parametric spaces. `Named.BT2020_PQ` is
  the platform-provided anchor that matches the 10000 nit decoder reference.
- A pending JNI exception poisons the next call and blanks every image:
  clear it on every failure path.
- `jxl_cms` must be linked explicitly for `JxlGetDefaultCms()` to resolve.
- In this NDK setup the `ALOGI` macro does not expand; use
  `__android_log_print` directly.

### Performance: ~30x faster load

A 4032×2268 F16 image took **~11 s** to decode on device. The root cause was
not the algorithm: building the same libjxl 0.12.0 with 15 threads on an
M-series Mac took **44 ms** for the same file — a 250x gap, far beyond any
CPU difference. The AGP debug build never set `CMAKE_BUILD_TYPE`, so the
entire native library was compiled at **-O0**.

Fixes (all in the `perf` commit):

- `-DCMAKE_BUILD_TYPE=Release` for the libjxl native build → **~300 ms** on device.
- JNI input buffer 4 KiB → 256 KiB (fewer feed/`ProcessInput` round trips).
- Parallel runner given the hardware concurrency (capped at 8) instead of the
  conservative resolution-based `SuggestThreads()` suggestion.

### Zoom sharpness

The old painter down-sampled the bitmap to the canvas size on **every draw**,
so GPU zoom enlarged an already-blurred copy. The texture now keeps the
**native image resolution** (capped at 5× the canvas to bound GPU memory on
8K+ sources), with `FilterQuality.High`. Up to the cap, zoom shows 1:1 real
pixels. F16 bitmaps are scaled inside their source color space to avoid the
re-tagging validation.

### Verification

- Device: Android 16 (API 36), OPPO PJX110, 1264×2780, peak 540 nit,
  `supportedHdrTypes=[1,2,3,4]`.
- HDR test image activates the hardware HDR pipeline (confirmed in
  SurfaceFlinger layer dump) and displays highlights correctly.
- 13/13 ad-hoc checks on the final commit state: Release build type, binary
  markers, no debug leftovers, install + process smoke, no FATAL.
- Upstream PR: [oupson/jxlviewer#46](https://github.com/oupson/jxlviewer/pull/46)

## License
Licensed under MIT license (LICENSE-MIT or http://opensource.org/licenses/MIT).
