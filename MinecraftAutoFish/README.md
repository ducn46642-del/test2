# Auto Fish MCPE

Ứng dụng Android (Kotlin) tự động câu cá trong Minecraft PE bằng cách:
- Dùng **Accessibility Service** để giả lập thao tác chạm (không cần root).
- Dùng **MediaProjection** để theo dõi một vùng nhỏ trên màn hình (nơi phao câu rơi xuống nước) và phát hiện khi có "tõm" nước (độ sáng thay đổi đột ngột) — dấu hiệu cá cắn câu.
- Khi phát hiện tõm nước → tự động chạm để giật cần → chờ chút → chạm lại để thả câu → lặp lại.

## Cách build KHÔNG CẦN Android Studio (dành cho máy Windows 32-bit)

Android Studio chỉ chạy trên Windows 64-bit, nhưng bạn có thể build file APK hoàn toàn miễn phí "trên mây" bằng GitHub Actions, chỉ cần trình duyệt web, không cần cài gì trên máy:

1. Vào https://github.com và tạo tài khoản miễn phí (nếu chưa có).
2. Bấm nút "+" góc trên bên phải → "New repository" → đặt tên bất kỳ (ví dụ `MinecraftAutoFish`) → chọn Public hoặc Private đều được → Create repository.
3. Trong trang repo vừa tạo, bấm "Add file" → "Upload files".
4. Giải nén file `MinecraftAutoFish.zip` trên máy tính, sau đó kéo thả **toàn bộ nội dung bên trong thư mục `MinecraftAutoFish`** (không phải kéo cả thư mục ngoài cùng, mà kéo các file/folder con: `app`, `.github`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`...) vào khung upload của GitHub → bấm "Commit changes".
5. Vào tab **"Actions"** ở trên cùng repo. GitHub sẽ tự động chạy workflow "Build APK" (mất khoảng 3-5 phút). Nếu không tự chạy, bấm vào workflow "Build APK" bên trái → "Run workflow".
6. Khi thấy dấu tích xanh ✓ (build thành công), bấm vào lần chạy đó → kéo xuống mục **"Artifacts"** ở cuối trang → tải file `app-debug-apk.zip` về máy.
7. Giải nén ra sẽ được file `app-debug.apk`. Chuyển file này qua điện thoại (gửi qua Zalo/Google Drive/email cho chính mình, hoặc dây USB) rồi mở file đó trên điện thoại để cài (Android sẽ hỏi cho phép "cài từ nguồn không xác định" — đồng ý).

Sau khi cài xong, làm theo phần "Cách dùng" bên dưới như bình thường. Về sau nếu bạn (hoặc mình) sửa code, chỉ cần upload lại các file đã thay đổi lên GitHub là Actions sẽ tự build lại APK mới.

## Cách build bằng Android Studio (nếu máy bạn là 64-bit)

1. Cài **Android Studio** (bản mới nhất).
2. Mở thư mục `MinecraftAutoFish` này bằng Android Studio (File → Open).
3. Đợi Gradle sync xong (lần đầu cần internet để tải các thư viện).
4. Cắm điện thoại qua USB (bật **Tùy chọn nhà phát triển → Gỡ lỗi USB**), chọn thiết bị, bấm nút Run (▶) để cài trực tiếp lên máy.
   - Hoặc: Build → Build Bundle(s)/APK(s) → Build APK(s), sau đó lấy file `app-debug.apk` trong `app/build/outputs/apk/debug/` và cài thủ công vào điện thoại.

## Cách dùng

1. Mở app **Auto Fish MCPE**.
2. Bước 1: Bấm "Mở cài đặt Accessibility" → tìm "Auto Fish MCPE" trong danh sách → bật lên.
3. Bước 2: Bấm "Mở cài đặt Overlay" → cho phép app hiển thị đè lên ứng dụng khác.
4. Mở Minecraft PE, vào thế giới, đứng cạnh nước, **cầm cần câu trong tay, đưa tầm ngắm vào mặt nước** (giữ nguyên góc nhìn này, đừng di chuyển camera nữa vì phao sẽ luôn rơi vào cùng 1 chỗ trên màn hình).
5. Quay lại app Auto Fish, bấm "Chọn vùng phao câu":
   - Kéo **ô vàng** đúng vào vị trí mặt nước nơi phao sẽ rơi xuống (nên chọn vùng nước phẳng, ít vật cản, không có lá/hoa súng che).
   - Kéo **chấm xanh** đúng vào vị trí nút "dùng vật phẩm" trên giao diện chạm của Minecraft PE (nút này thường nằm bên phải màn hình, hiện ra khi bạn cầm cần câu).
   - Bấm "LƯU VỊ TRÍ".
6. Quay lại app, bấm "Bắt đầu quay màn hình + Auto Fish", màn hình sẽ hỏi quyền ghi màn hình → Đồng ý.
7. Mở lại Minecraft PE. Bạn sẽ thấy một **bong bóng tròn nhỏ** nổi trên màn hình — bấm vào đó để Bắt đầu (▶) / Dừng (⏸) auto câu bất cứ lúc nào, kể cả trong lúc đang chơi.

## Giới hạn số lần thả cần

Trong màn hình chính có ô "Giới hạn số lần thả cần (0 = không giới hạn)":
- Nhập một số (ví dụ 60) rồi bấm "Lưu" để app tự dừng sau khi thả cần đủ số lần đó — hữu ích để tránh gãy cần câu (cần câu trong Minecraft có độ bền giới hạn) hoặc để giới hạn thời gian auto chạy.
- Để 0 hoặc để trống nếu không muốn giới hạn.
- Số lần đã thả cần hiện tại được hiển thị trong thông báo (notification) của app khi đang chạy.
- Khi đạt giới hạn, app tự dừng và bong bóng nổi tự chuyển về icon ▶ (dừng), có thể bấm lại để chạy tiếp một phiên mới (bộ đếm sẽ reset về 0).

## Tinh chỉnh độ nhạy

Nếu app giật cần nhầm liên tục (không có cá cắn câu vẫn giật) hoặc bỏ lỡ khi cá cắn câu, mở file:

`app/src/main/java/com/example/autofish/ScreenCaptureService.kt`

và chỉnh hằng số `SPLASH_THRESHOLD` (mặc định 22.0):
- Tăng lên (ví dụ 30-40) nếu bị giật nhầm quá nhiều.
- Giảm xuống (ví dụ 12-18) nếu không phát hiện được lúc cá cắn câu.

## Lưu ý

- Việc dùng công cụ tự động hóa như thế này có thể vi phạm điều khoản sử dụng của một số máy chủ Minecraft (đặc biệt là máy chủ nhiều người chơi/PvP có luật cấm bot/macro). Nên chỉ dùng ở thế giới chơi một mình (Singleplayer) hoặc máy chủ cho phép, để tránh bị khóa tài khoản/cấm chơi.
- Vì đây là app tự build, Android có thể cảnh báo "ứng dụng không rõ nguồn gốc" — vì bạn tự biên dịch nên chỉ cần bấm "Cài đặt dù sao" (Install anyway).
- Thuật toán phát hiện dựa trên thay đổi độ sáng đơn giản, không phải máy học, nên có thể cần thử vài lần để chọn đúng vùng và ngưỡng phù hợp với ánh sáng trong game của bạn (ban ngày/ban đêm, trời mưa... có thể ảnh hưởng).
