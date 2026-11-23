# Quyền ứng dụng và cấu hình dịch vụ

## Danh sách quyền

| Quyền | Khi nào dùng | Bắt buộc? |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Lấy GPS chính xác khi nhấn nút ghi nhận và hiển thị trạng thái GPS ở màn hình chính | Nên cấp (không cấp vẫn ghi được bằng cách nhập tay) |
| `ACCESS_COARSE_LOCATION` | Khi người dùng chỉ cho phép vị trí gần đúng | Không |
| `INTERNET` | Chuyển tọa độ → địa chỉ; tải ô nền bản đồ cho ảnh báo cáo | Không (offline vẫn ghi đầy đủ) |
| `ACCESS_NETWORK_STATE` | WorkManager chờ có mạng mới chuyển đổi địa chỉ | Không |
| `WRITE_EXTERNAL_STORAGE` (chỉ Android 8–9, `maxSdkVersion=28`) | Lưu ảnh vào `Pictures` trên máy cũ | Chỉ khi xuất ảnh trên Android 8–9 |

**Không** dùng quyền vị trí nền (`ACCESS_BACKGROUND_LOCATION`), không chạy dịch vụ nền theo dõi vị trí, không yêu cầu danh bạ/camera/thông báo.

## Dịch vụ bên ngoài (không cần khóa API)

| Dịch vụ | Mục đích | Dữ liệu gửi đi | Tắt được? |
|---|---|---|---|
| Android `Geocoder` (Google, có sẵn trong hệ điều hành) | Chuyển tọa độ thành địa chỉ | Tọa độ từng sự kiện | Ngắt mạng → địa chỉ "Chưa xác định", tọa độ vẫn lưu |
| OpenStreetMap tile server (`tile.openstreetmap.org`) | Nền bản đồ trong ảnh báo cáo | Vùng ô bản đồ (zoom/x/y) bao quanh các điểm | **Cài đặt → Nền bản đồ trong báo cáo** |

Ứng dụng không gửi hành trình lên máy chủ nào và không có tài khoản đăng nhập trong V1.

### Muốn dùng Google Maps / dịch vụ bản đồ trả phí
V1 cố ý không phụ thuộc khóa API. Nếu sau này cần Google Static Maps hoặc Mapbox: thêm khóa vào `local.properties` (không commit) → đọc qua `BuildConfig` trong `app/build.gradle.kts` → thay hàm `loadTile()` trong `report/MapRenderer.kt`. Cơ chế dự phòng vẽ sơ đồ khi lỗi vẫn giữ nguyên.

## Tính toàn vẹn dữ liệu
- Giờ sự kiện = giờ thiết bị lúc nhấn nút; đồng thời lưu `elapsedRealtime` (không đổi khi chỉnh đồng hồ) và **giờ tham chiếu từ bản tin vị trí**. Nếu giờ máy lệch > 2 phút so với giờ vị trí, hoặc giờ máy lùi so với sự kiện trước, sự kiện được gắn cảnh báo và cảnh báo hiện trên báo cáo. Sự kiện cũ không bao giờ bị tự sửa giờ.
- Phân biệt nguồn vị trí: `GPS_FRESH` (≤ 30 giây), `GPS_CACHED` (người dùng chủ động chọn, ghi tuổi vị trí), `MANUAL` (nhập tay, không có tọa độ). Vị trí do app giả lập vị trí cung cấp được đánh dấu.
- Chống ghi trùng: khóa giao diện khi đang lấy GPS + kiểm tra trong transaction (cùng loại sự kiện trong 3 giây bị bỏ qua) + máy trạng thái chặn chuỗi vô lý.
- Sửa ghi chú/địa chỉ lưu vào bảng `edit_logs` (giá trị cũ, mới, thời điểm, lý do).
- Room `version = 1`, schema xuất tại `app/schemas/`. Nâng cấp sau này phải viết `Migration`, **không** dùng `fallbackToDestructiveMigration`.
- Android Auto Backup bao gồm cơ sở dữ liệu và cài đặt.
