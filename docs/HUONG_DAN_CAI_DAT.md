# Hướng dẫn cài đặt APK

## 1. Tải APK
1. Mở `https://github.com/Dattiennguye/Vigo-driver/releases` trên điện thoại.
2. Chọn bản mới nhất (`VIGO Driver Log 1.0.0 (build …)`) → mục **Assets** → tải `VIGO-DriverLog-1.0.0-release.apk`.

## 2. Cài đặt
1. Mở file APK vừa tải (thanh thông báo hoặc ứng dụng Files).
2. Nếu Android hỏi *"Cho phép cài ứng dụng từ nguồn này"* → bật cho trình duyệt/Files → quay lại → **Cài đặt**.
3. Xiaomi/HyperOS: nếu bị chặn bởi "quét bảo mật", chọn **Vẫn cài đặt**.
4. Yêu cầu: Android 8.0 (API 26) trở lên. Nên có Google Play Services để định vị chính xác (không có vẫn chạy bằng định vị của Android).

## 3. Lần đầu mở ứng dụng
1. Cho phép **Vị trí → Chính xác → Khi dùng ứng dụng**.
2. Vào tab **Cài đặt** nhập *Tên lái xe*, *Biển số xe* (và tên công ty nếu cần) → **LƯU THÔNG TIN**.
3. Bật định vị (GPS) trên điện thoại.

## 4. Cập nhật phiên bản mới
Cài đè APK mới lên bản cũ – dữ liệu được giữ nguyên (Room không dùng migration phá hủy).
**Lưu ý:** chỉ cài đè được khi hai bản được ký cùng một khóa (xem mục 5). Trước khi cập nhật nên vào **Cài đặt → SAO LƯU DỮ LIỆU**.

## 5. Khóa ký APK Release (quan trọng cho việc cập nhật)
Workflow GitHub Actions đọc khóa ký từ **Secrets** của repo. Nếu chưa có Secrets, mỗi lần build sẽ ký bằng một khóa tạm khác nhau → bản sau không cài đè được bản trước (phải gỡ app, mất dữ liệu nếu chưa sao lưu). Release notes ghi rõ đang dùng khóa ổn định hay tạm.

Tạo khóa ổn định (làm một lần, giữ file `.jks` và mật khẩu cẩn thận):

```bash
keytool -genkeypair -v -keystore vigo-release.jks -alias vigodriverlog \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 vigo-release.jks > vigo-release.b64     # macOS: base64 -i vigo-release.jks
```

Trên GitHub: **Settings → Secrets and variables → Actions → New repository secret**, thêm 4 secret:

| Tên | Giá trị |
|---|---|
| `VIGO_KEYSTORE_BASE64` | nội dung file `vigo-release.b64` |
| `VIGO_KEYSTORE_PASSWORD` | mật khẩu keystore |
| `VIGO_KEY_ALIAS` | `vigodriverlog` |
| `VIGO_KEY_PASSWORD` | mật khẩu khóa |

Sau đó vào tab **Actions → Build VIGO Driver Log APK → Run workflow** để build lại bản ký ổn định.

Build Release trên máy: tạo file `keystore.properties` ở thư mục gốc (đã nằm trong `.gitignore`):

```
storeFile=/đường/dẫn/vigo-release.jks
storePassword=...
keyAlias=vigodriverlog
keyPassword=...
```

## 6. Build từ mã nguồn
- Android Studio Koala (2024.1) trở lên, JDK 17, Android SDK 34.
- Mở thư mục repo → đợi Gradle sync → *Build > Build Bundle(s)/APK(s) > Build APK(s)*.
- Dòng lệnh: `./gradlew assembleDebug assembleRelease`.
