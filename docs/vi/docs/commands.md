# Lệnh

## Lệnh Người Chơi

<CommandRow commands="/ec" :aliases="['/enderchest']" permission="enhancedechest.command.open">

Mở rương Ender của bạn. Với một rương sẽ mở thẳng. Với nhiều rương, mở **rương chính** nếu đã đặt, hoặc menu quản lý nếu chưa. Rương đầu tiên được tạo tự động trong lần mở đầu tiên.

</CommandRow>

<CommandRow :commands="['/ec #&lt;index&gt;', '/ec &lt;name&gt;']" permission="enhancedechest.command.open">

Mở thẳng một rương cụ thể theo số thứ tự (ví dụ `/ec #2`) hoặc tên tùy chỉnh (ví dụ `/ec Loot`), bỏ qua menu.

</CommandRow>

<CommandRow commands="/eclist" permission="enhancedechest.command.open">

Luôn mở menu quản lý liệt kê mọi rương của bạn. Từ đây bạn có thể mở, đổi tên, đặt biểu tượng, hoặc đặt rương làm rương chính. Rương chính được đánh dấu bằng **★** màu vàng.

</CommandRow>

::: tip
Chuột phải vào khối rương Ender hoạt động giống hệt `/ec`. Không cần quyền để chuột phải vào khối.
:::

## Lệnh Quản Trị Viên

Mọi lệnh `/ee` đều cần `enhancedechest.command.admin` cùng với quyền riêng của từng lệnh.

<CommandRow commands="/ee add &lt;player&gt; &lt;size&gt; [count] [duration]" permission="enhancedechest.admin.add">

Cấp cho người chơi một hoặc nhiều rương. `<size>` là bội số của 9 từ 9 đến 54. Thêm `[duration]` (ví dụ `7d`, `1d_12h`) để tạo rương tạm thời.

</CommandRow>

<CommandRow commands="/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;" permission="enhancedechest.admin.resize">

Thay đổi số ô của một rương. Vật phẩm ở các ô bị cắt sẽ được dồn sang rương tạm thay vì bị hủy.

</CommandRow>

<CommandRow commands="/ee delete &lt;player&gt; &lt;count&gt; [force]" permission="enhancedechest.admin.delete">

Xóa `<count>` rương mới nhất của người chơi. Vật phẩm được dồn sang rương tạm theo mặc định; thêm `force` để xóa luôn nội dung. Rương đầu tiên của người chơi không bao giờ bị xóa.

</CommandRow>

<CommandRow commands="/ee view &lt;player&gt; [list | index]" permission="enhancedechest.admin.view">

Mở rương của người chơi khác (hoạt động kể cả khi họ ngoại tuyến). Chỉ với `enhancedechest.admin.view` thì kho đồ là chỉ-đọc; thêm `enhancedechest.admin.edit` để di chuyển vật phẩm.

</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">

Tải lại file cấu hình và ngôn ngữ mà không cần khởi động lại máy chủ.

</CommandRow>

<CommandRow :commands="['/ee migrate run &lt;player&gt;', '/ee migrate run all']" permission="enhancedechest.admin.migrate.run">

Nhập nội dung rương Ender vanilla vào plugin. Mỗi người chơi chỉ được chuyển một lần. Yêu cầu người chơi đang trực tuyến.

</CommandRow>
