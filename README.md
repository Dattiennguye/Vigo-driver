# VIGO Driver Log

Ứng dụng Android ghi nhật ký hành trình lái xe công tác và xuất **ảnh báo cáo** chuyên nghiệp để gửi cấp quản lý.

- Ghi nhận bằng một chạm: **BẮT ĐẦU ĐI → ĐÃ ĐẾN NƠI → ĐỖ XE → RỜI ĐIỂM ĐỖ → TIẾP TỤC ĐI → KẾT THÚC CHUYẾN ĐI**, cộng **GHI NHẬN NHANH** dùng bất cứ lúc nào.
- Mỗi sự kiện tự lấy: giờ lúc nhấn nút, GPS mới (Fused Location), vĩ độ/kinh độ, độ chính xác, địa chỉ (khi có mạng).
- Hoạt động offline hoàn toàn, dữ liệu lưu bằng Room trên máy. Có sao lưu/khôi phục JSON và xuất CSV.
- Ảnh báo cáo PNG 1080 px: tiêu đề, tổng quan, sơ đồ các điểm, timeline chi tiết, chân trang xác nhận. Lưu vào `Pictures/VIGO Driver Log`, chia sẻ qua Zalo, Gmail…

## Tải APK

Vào tab **Releases** của repo, chọn bản mới nhất:

| File | Dùng để |
|---|---|
| `VIGO-DriverLog-1.0.0-release.apk` | Cài đặt sử dụng thực tế |
| `VIGO-DriverLog-1.0.0-debug.apk` | Kiểm thử (cài song song, dữ liệu riêng) |

APK được build tự động bằng GitHub Actions mỗi khi đẩy code lên nhánh `main` (workflow **Build VIGO Driver Log APK**).

## Công nghệ

Kotlin · Jetpack Compose (Material 3) · MVVM + Repository · Room · Google Play Services Fused Location · Android Geocoder · DataStore · WorkManager · Canvas/StaticLayout (ảnh báo cáo) · OpenStreetMap tiles (nền bản đồ, có cache, tùy chọn).

Không cần khóa API hay dịch vụ trả phí nào.

## Cấu trúc mã nguồn

```
app/src/main/java/com/vigo/driverlog/
├── VigoApp.kt, MainActivity.kt     # khởi tạo, điều hướng 4 tab
├── domain/                         # mô hình, máy trạng thái hành trình, định dạng, dựng báo cáo (thuần Kotlin)
├── data/
│   ├── db/                         # Room: trips, events, edit_logs
│   ├── location/                   # Fused Location: GPS mới, kiểm tra tuổi/độ chính xác
│   ├── geo/                        # Geocoder + WorkManager chuyển đổi địa chỉ khi có mạng
│   ├── settings/                   # DataStore
│   ├── backup/                     # sao lưu/khôi phục JSON, xuất CSV
│   └── TripRepository.kt           # ghi sự kiện trong transaction, chống trùng, nhật ký chỉnh sửa
├── report/                         # vẽ ảnh báo cáo, bản đồ, lưu MediaStore, chia sẻ
└── ui/                             # Compose: home, history (+chi tiết), report, settings
```

## Build trên máy

Mở thư mục repo bằng Android Studio (Koala trở lên, JDK 17) → *Build > Build APK(s)*. Hoặc:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # cần keystore, xem docs/HUONG_DAN_CAI_DAT.md
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

## Tài liệu

- [Hướng dẫn cài đặt & cấu hình build](docs/HUONG_DAN_CAI_DAT.md)
- [Hướng dẫn sử dụng](docs/HUONG_DAN_SU_DUNG.md)
- [Quyền ứng dụng & cấu hình dịch vụ](docs/QUYEN_VA_CAU_HINH.md)
- [Báo cáo kiểm thử & lỗi còn tồn tại](docs/BAO_CAO_KIEM_THU.md)
