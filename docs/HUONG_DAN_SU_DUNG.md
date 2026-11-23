# Hướng dẫn sử dụng VIGO Driver Log

## Màn hình Hành trình (tab đầu tiên)
- **Đầu màn hình:** tên lái xe, biển số, ngày, trạng thái chuyến đi.
- **Thẻ GPS:** đang hoạt động / đang tìm / đang tắt, tọa độ, độ chính xác, giờ cập nhật. Chưa cấp quyền hay tắt định vị sẽ có nút xử lý ngay.
- **Nút thao tác lớn** – chỉ hiện các nút hợp lệ với trạng thái hiện tại:

| Trạng thái | Nút hiển thị |
|---|---|
| Chưa bắt đầu | BẮT ĐẦU ĐI |
| Đang di chuyển | ĐÃ ĐẾN NƠI · KẾT THÚC CHUYẾN ĐI |
| Đã đến nơi | ĐỖ XE · TIẾP TỤC ĐI · KẾT THÚC CHUYẾN ĐI |
| Đang đỗ xe | RỜI ĐIỂM ĐỖ |
| Đã rời điểm đỗ | TIẾP TỤC ĐI · ĐỖ XE · KẾT THÚC CHUYẾN ĐI |

Mỗi lần nhấn: giờ được chốt **ngay lúc nhấn**, sau đó ứng dụng lấy vị trí GPS mới (tối đa ~25 giây).
- GPS tốt (≤ 50 m): lưu ngay, hiện thông báo "Đã ghi nhận".
- GPS kém (> 50 m): hỏi **VẪN LƯU** (đánh dấu "độ chính xác thấp") hoặc **Thử lại**.
- Không lấy được GPS: chọn **THỬ LẠI**, **Dùng vị trí gần nhất** (ghi rõ là vị trí cache và cũ bao nhiêu phút) hoặc **Nhập địa điểm thủ công** (ghi rõ là nhập tay, không có tọa độ).

**GHI NHẬN NHANH** dùng mọi lúc (kể cả khi không có chuyến): ghé ngân hàng, kho hàng, đón khách, giao tài liệu… Sau khi lưu có thể chọn nhanh một ghi chú hoặc tự gõ.

**Thời gian đỗ** được tự tính = giờ RỜI ĐIỂM ĐỖ − giờ ĐỖ XE và hiển thị dạng "45 phút", "1 giờ 20 phút".

Đóng ứng dụng, tắt máy, hết pin… **không** kết thúc chuyến đi. Mở lại là tiếp tục đúng trạng thái. Nếu chuyến của ngày hôm trước chưa kết thúc, màn hình sẽ cảnh báo: có thể tiếp tục, kết thúc ngay (ghi giờ/vị trí hiện tại) hoặc **đánh dấu chưa hoàn thành** (không tạo giờ kết thúc giả).

## Lịch sử
- Chọn ngày bằng mũi tên hoặc chạm vào ngày để mở lịch.
- Mỗi sự kiện: biểu tượng, tên, giờ, địa chỉ, tọa độ, độ chính xác, cảnh báo (nếu có).
- Chạm vào sự kiện để xem chi tiết: **thời gian và tọa độ gốc không thể sửa**. Có thể sửa *ghi chú* và *địa chỉ hiển thị*; mỗi lần sửa lưu giá trị cũ, giá trị mới, thời điểm và lý do vào **Nhật ký chỉnh sửa**.
- Bản ghi tạo lúc mất mạng hiện "Chưa xác định địa chỉ" và tự được bổ sung địa chỉ khi có mạng trở lại.

## Báo cáo
1. Chọn ngày → ứng dụng tự dựng bản xem trước từ dữ liệu thật của ngày đó.
2. Nhấn **XUẤT ẢNH PNG & LƯU VÀO THƯ VIỆN** → ảnh lưu tại `Pictures/VIGO Driver Log/VIGO_DriverLog_YYYY-MM-DD.png`.
3. Sau khi xuất: **XEM ẢNH**, **CHIA SẺ** (Zalo, Gmail, …), **XUẤT LẠI** (dựng lại từ dữ liệu mới nhất và thay file cũ).

Nội dung ảnh: tiêu đề, thông tin lái xe/xe, tổng quan (chỉ các chỉ số có dữ liệu), sơ đồ các điểm GPS đánh số theo thứ tự, timeline chi tiết, chân trang ngày/giờ xuất và dòng xác nhận. Ngày quá nhiều sự kiện sẽ tự chia thành nhiều trang (`…_trang1.png`, `…_trang2.png`).

Sơ đồ là **đường nối giữa các điểm đã ghi nhận**, không phải tuyến đường thực tế. Khi có mạng, nền là bản đồ OpenStreetMap (được lưu cache); khi không có mạng, ứng dụng vẽ sơ đồ theo tọa độ – báo cáo không bao giờ bị trống.

## Cài đặt
- Tên lái xe, biển số, tên công ty (tự dùng cho các ngày sau).
- Định dạng giờ 24h, đơn vị khoảng cách, bật/tắt nền bản đồ trực tuyến.
- Kiểm tra quyền GPS và trạng thái định vị.
- Lưu trữ: số chuyến/sự kiện, dung lượng, bộ nhớ trống.
- **SAO LƯU DỮ LIỆU** (file JSON – nên gửi lên Drive/Zalo), **KHÔI PHỤC** (gộp, không ghi đè, không nhân đôi), **XUẤT CSV** để mở bằng Excel.
