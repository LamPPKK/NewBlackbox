# Hướng dẫn phát triển NewBlackbox

## Đối tượng và trạng thái

Tài liệu này dành cho người tiếp tục phát triển fork `LamPPKK/NewBlackbox`. Nhánh hiện tại là baseline hiện đại hóa: `compileSdk`/`targetSdk` 36, AGP 8.13.2, Gradle 8.14.5, Kotlin 2.2.21, Java 21 và NDK 28.2.13676358. Debug, release và unit-test task đã build thành công; chưa có kết quả chạy thực tế trên thiết bị Android 14–16.

## Kiến trúc hiện tại

Luồng chính:

```text
app/App + BlackBoxLoader
        |
        v
BlackBoxCore (API và bootstrap)
        |
        +-- BPackageManagerService: cài đặt, dữ liệu package, user ảo
        +-- BActivityManagerService: activity, process, service, broadcast
        +-- BActivityThread: bind và chạy mã guest trong process proxy
        +-- HookManager/IActivityManagerProxy: chuyển lời gọi framework
        +-- NativeCore/IOCore: native hook và ánh xạ filesystem
        +-- ProxyActivity/ProxyService/ProxyBroadcastReceiver: component của host
```

Các điểm vào quan trọng:

- `app/src/main/java/top/niunaijun/blackboxa/app/App.kt`: vòng đời host app.
- `app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt`: cấu hình và khởi động engine.
- `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`: facade công khai; install, launch, user và lifecycle.
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/`: các system service ảo.
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/`: hook/delegate cho Android framework.
- `Bcore/src/main/cpp/`: native hook, build bằng ndk-build.

## Hợp đồng API chính

- Khởi tạo phải đi qua `BlackBoxCore.doAttachBaseContext(...)` trước `doCreate()`; UI hiện thực hiện việc này qua `BlackBoxLoader`.
- Cài APK host hoặc file/URI qua `BlackBoxCore.installPackageAsUser(...)`.
- Chạy app bằng `BlackBoxCore.launchApk(packageName, userId)`.
- Mọi thao tác package phải truyền đúng `userId`; user `0` không đồng nghĩa với mọi clone.
- Guest component không được khai báo trực tiếp như app bình thường; engine chuyển chúng qua các proxy component của host.

## Chuẩn bị môi trường

Yêu cầu:

- JDK 21 đầy đủ, bao gồm `jlink`. Một JRE/JBR rút gọn sẽ làm AGP `JdkImageTransform` thất bại.
- Android SDK Platform 36.
- Android NDK `28.2.13676358`.
- Kết nối tới Google Maven, Maven Central và JitPack cho lần resolve dependency đầu tiên.

Ví dụ biến môi trường:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/sdk
```

## Build và xác minh

Từ thư mục gốc repo:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

APK được tạo tại:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

Mỗi variant có bản `arm64-v8a`, `armeabi-v7a` và `universal`. Sau thay đổi native, kiểm tra ELF `libblackbox.so` vẫn có `PT_LOAD p_align=0x4000` để đáp ứng thiết bị 16 KB page size.

## Những thay đổi Android 14–16 đã có

- File APK/DEX/JAR/native được chuyển read-only trước khi nạp động; có migration cho file cũ còn writable.
- Dynamic receiver dùng cờ exported/not-exported phù hợp target mới.
- Foreground service proxy/daemon khai báo `specialUse`, tạo channel và promote ngay khi được khởi động theo FGS.
- Activity PendingIntent có creator-side background-activity opt-in trên API 35+; broadcast PendingIntent được chuyển qua proxy rõ ràng.
- Launcher xử lý system-bar insets; các màn hình có hành vi back riêng dùng `OnBackPressedDispatcher`.
- Native build dùng NDK r28 và alignment 16 KB.

## Bất biến, giới hạn và điểm dễ lỗi

- Không chuyển Java/Kotlin xuống dưới 21 nếu chưa thay hoặc rebuild các AAR cục bộ được biên dịch với class-file version 65.
- Release hiện dùng debug signing config. Phải tạo keystore/release signing riêng trước khi phân phối.
- Mutable implicit service `PendingIntent` của guest chưa có bridge đầy đủ. Widget, alarm hoặc notification action dùng đường này cần test riêng.
- Host target 36 áp dụng edge-to-edge cho proxy Activity. Guest app cũ có thể vẽ dưới system bars dù launcher đã xử lý inset.
- FGS proxy đảm bảo host không timeout, nhưng notification/id của guest chưa được ánh xạ đầy đủ sang record FGS của host.
- Thay đổi hook, manifest proxy hoặc số lượng process phải được thử trên ít nhất API 34, 35 và 36, cả `arm64-v8a` và `armeabi-v7a` khi thiết bị hỗ trợ.

## Ma trận kiểm thử trước khi phát hành

1. Cài mới và nâng cấp từ bản target cũ trên Android 14, 15 và 16.
2. Cài APK từ package host, file và content URI; chạy nhiều user ảo.
3. Activity, service/FGS, receiver, provider, notification action và shortcut của guest.
4. Permission runtime, storage/media picker, fake location và GMS nếu bật.
5. Kill/restart host, reboot thiết bị và phục hồi app đã cài trong container.
6. Thiết bị/emulator 16 KB page size và APK universal.

## Câu hỏi mở / backlog

- Có cần một flavor `targetSdk 34` cho phân phối sideload trong khi target 36 tiếp tục được kiểm thử không?
- Có nên xây bridge hoàn chỉnh cho service PendingIntent và guest FGS notification không?
- Có thể thay các AAR/JAR cũ bằng dependency có source hoặc artifact reproducible để giảm yêu cầu toolchain đặc biệt không?
- Cần CI trên GitHub Actions để build ba APK và chạy lint/test sau mỗi thay đổi.
