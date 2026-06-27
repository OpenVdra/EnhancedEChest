# Paper và Folia

EnhancedEchest chạy trên Paper, Folia và các bản fork của Paper như Purpur. Gần như mọi thứ hoạt động giống hệt nhau trên mọi nền tảng. Chỉ có một khác biệt đáng lưu ý, và nó chỉ xảy ra khi **cùng một rương** được mở trên hai màn hình cùng lúc.

## Khác biệt duy nhất: mở cùng một rương cùng lúc

Một rương ender có thể được mở trên nhiều màn hình cùng lúc. Trường hợp phổ biến là quản trị viên dùng `/ee view` lên một rương trong khi chủ rương đang mở chính rương đó.

| Tình huống | Paper | Folia |
|-----------|-------|-------|
| Hai người mở **cùng một** rương cùng lúc | Cả hai cùng xem và chỉnh sửa, được đồng bộ | Chỉ người đầu tiên được vào; người thứ hai được báo rương **đang được dùng** |
| Mở các rương **khác nhau** | Luôn hoạt động với mọi người | Luôn hoạt động với mọi người |
| Một người chơi, một rương mỗi lần | Bình thường | Bình thường |

Tóm lại: trên Paper, quản trị viên và chủ rương (hoặc hai quản trị viên) có thể mở cùng một rương cạnh nhau. Trên Folia, mỗi rương chỉ cho một người xem tại một thời điểm, nên người thứ hai sẽ được yêu cầu thử lại sau giây lát.

Lý do đơn giản là Folia chia các phần khác nhau của thế giới sang các luồng riêng, nên plugin giữ mỗi rương ở một người xem trực tiếp để đảm bảo an toàn. Không có gì bị mất, và vật phẩm không bao giờ bị nhân đôi trên cả hai nền tảng.

## Quản trị viên sẽ thấy gì

Khi bạn chạy `/ee view` (hoặc dùng nút **Dọn rương**) lên một rương mà chủ rương đang mở:

- **Paper**: rương mở ra và bạn có thể xem hoặc chỉnh sửa trực tiếp cùng chủ rương.
- **Folia**: thao tác bị từ chối với thông báo "đang được dùng" cho đến khi chủ rương đóng lại. Hãy chạy lại lệnh khi rương rảnh, hoặc thao tác khi chủ rương ngoại tuyến.

Đây là khác biệt duy nhất mà người dùng thấy được. Nếu máy chủ của bạn chạy Paper, bạn sẽ không bao giờ thấy thông báo "đang được dùng".

## Mọi thứ còn lại đều giống nhau

Lưu trữ, nhiều rương mỗi người, tên và biểu tượng tùy chỉnh, rương cấp theo quyền, rương hết hạn và rương tạm, chuyển dữ liệu từ plugin khác, chuyển rương giữa các tài khoản, và mọi lệnh đều hoạt động giống hệt nhau trên Paper và Folia. Bạn có thể chuyển một thế giới giữa hai nền tảng mà không cần thay đổi gì trong EnhancedEchest.
