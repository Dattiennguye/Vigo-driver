# Báo cáo kiểm thử – VIGO Driver Log 1.0.0

Kiểm thử tự động chạy trên GitHub Actions mỗi lần đẩy code (workflow *Build VIGO Driver Log APK*).
Kết quả mới nhất (ảnh báo cáo do test tạo, báo cáo JUnit) được đăng ở release **ci-test-output**.

## 1. Unit test (JVM) – 9/9 đạt
| Nhóm | Nội dung |
|---|---|
| Máy trạng thái | Chỉ cho BẮT ĐẦU khi chưa có chuyến; chuỗi đầy đủ Bắt đầu → Đến → Đỗ → Rời → Tiếp tục → Đến → Đỗ → Rời → Kết thúc; đang đỗ chỉ được Rời điểm đỗ; chặn PARK_END khi đang chạy, chặn START khi đang có chuyến |
| Định dạng | "15 phút", "1 giờ 20 phút", "2 giờ 05 phút", "Dưới 1 phút"; tọa độ 6 chữ số |
| Dựng báo cáo | Chỉ số đúng từ dữ liệu (giờ bắt đầu/kết thúc, số điểm đến, số lần đỗ, tổng đỗ 45 phút, tổng làm việc 9 giờ 30 phút); ngày trống không có chỉ số; chuyến chưa kết thúc không có giờ kết thúc và có ghi chú |
| Sao lưu | Mã hóa/giải mã JSON giữ nguyên mọi trường, kể cả null và ký tự đặc biệt |

## 2. Instrumented test trên emulator Android 11 (API 30, Google APIs) – 11/11 đạt
| Test | Kiểm tra |
|---|---|
| `fullTripFlowAndRules` | Hành trình đầy đủ 10 sự kiện; chặn ĐẾN NƠI trước BẮT ĐẦU, chặn BẮT ĐẦU lần 2, chặn RỜI ĐIỂM ĐỖ khi đang chạy, chặn KẾT THÚC khi đang đỗ; GHI NHẬN NHANH không đổi trạng thái; chuyến chuyển COMPLETED có giờ kết thúc |
| `duplicateTapIsRejected` | Bấm cùng nút 2 lần trong 1 giây → chỉ lưu 1 bản ghi |
| `manualLocationIsFlagged` | Vị trí nhập tay lưu `MANUAL`, không có tọa độ |
| `clockDriftIsWarned` | Giờ máy lệch 10 phút so với giờ vị trí → có cảnh báo |
| `editsAreLoggedAndOriginalsKept` | Sửa địa chỉ: tọa độ, thời gian gốc giữ nguyên; nhật ký có 1 dòng |
| `dataSurvivesReopen` | Đóng DB, mở lại (tương đương đóng app/khởi động lại máy) → dữ liệu và chuyến đang chạy còn nguyên |
| `staleTripMarkedIncompleteWithoutFakeEnd` | Chuyến ngày cũ đánh dấu INCOMPLETE, không tạo giờ kết thúc |
| `backupRoundTripIsIdempotent` | Sao lưu → khôi phục vào DB khác đủ dữ liệu; khôi phục lần 2 không nhân đôi |
| `reportRendersSavesAndPaginates` | Ngày 102 sự kiện: ảnh rộng 1080 px, tự chia 5 trang ≤ 6000 px; lưu vào `Pictures/VIGO Driver Log` qua MediaStore, đọc lại được; XUẤT LẠI giữ đúng tên file |
| `reportSmallDayOfflineSchematic` | Ngày 9 sự kiện (có ghi nhận nhanh nhập tay, địa chỉ không xác định): 1 trang; offline vẽ sơ đồ theo tọa độ; online có nền OpenStreetMap |
| `locationServiceNeverCrashes` | Gọi lấy GPS thật trên emulator không crash, trả kết quả hợp lệ |

Ảnh báo cáo do test tạo đã được kiểm tra bằng mắt: tiếng Việt có dấu hiển thị đúng, không tràn chữ, không chồng chữ, địa chỉ dài tự xuống dòng, điểm trùng vị trí trên sơ đồ được dời ra để không đè số, chân trang luôn ở đáy ảnh, số trang "Trang x/y" khi chia trang.

## 3. Build
- `assembleDebug` và `assembleRelease` (R8 thu gọn + `lintVitalRelease`) đạt. APK Release ~2 MB.

## 4. Chưa kiểm thử được trong môi trường tự động – cần thử trên điện thoại thật
| Hạng mục | Ghi chú |
|---|---|
| GPS thật ngoài trời / GPS yếu trong hầm | Emulator CI không nhận tọa độ giả qua Fused Location (test trả về "hết thời gian chờ" – đúng hành vi xử lý GPS yếu, không crash) |
| Chuyển tọa độ → địa chỉ | Phụ thuộc dịch vụ Geocoder của máy có Google Play Services |
| Hết pin / Android tự đóng app giữa chuyến | Logic đã đảm bảo (mỗi sự kiện được ghi ngay vào Room); nên thử thực tế |
| Màn hình nhỏ, cỡ chữ hệ thống lớn | Giao diện dùng cuộn dọc, chữ tự xuống dòng; nên kiểm tra trên máy thật |
| Chia sẻ qua Zalo/Gmail | Dùng Intent chuẩn của Android |
| Android 8–9 | Đường lưu ảnh riêng (quyền lưu trữ) chưa chạy trên emulator |

Danh sách đề nghị khi thử trên máy thật: bắt đầu → đến điểm 1 → đỗ → rời → đến điểm 2 → đỗ nhiều lần → kết thúc → ghi nhận nhanh → xem lịch sử → xuất ảnh; bật chế độ máy bay khi ghi nhận rồi tắt để xem địa chỉ tự bổ sung; tắt GPS rồi nhấn nút; vuốt tắt app giữa chuyến rồi mở lại; khởi động lại máy.

## 5. Lỗi / hạn chế còn tồn tại
1. **Khóa ký Release:** khi repo chưa có Secrets, mỗi bản build ký bằng khóa tạm khác nhau → không cài đè được bản trước. Cần cấu hình Secrets (xem `HUONG_DAN_CAI_DAT.md` mục 5).
2. Nền bản đồ dùng máy chủ ô bản đồ công cộng của OpenStreetMap; nếu máy chủ chặn/quá tải, báo cáo tự chuyển sang sơ đồ theo tọa độ.
3. Ứng dụng khóa màn hình dọc.
4. Báo cáo chỉ xuất PNG (đúng phạm vi V1), chưa có PDF.
5. Địa chỉ do Geocoder trả về có thể không có số nhà ở một số khu vực; người dùng có thể sửa (có nhật ký).
