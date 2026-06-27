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

Mỗi lệnh `/ee` chỉ cần đúng quyền riêng của lệnh đó. Không còn quyền cơ sở chung. Mọi node quản trị mặc định là `op`.

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

Mở menu từng rương của người chơi khác (hoạt động kể cả khi họ ngoại tuyến). Từ menu bạn mở rương, và quản trị viên có `enhancedechest.admin.clear` còn thấy nút đỏ **(Admin) Dọn rương** để làm trống rương. Chỉ với `enhancedechest.admin.view` thì kho đồ là chỉ-đọc; thêm `enhancedechest.admin.edit` để di chuyển vật phẩm.

</CommandRow>

<CommandRow commands="/ee transfer &lt;from&gt; &lt;to&gt; &lt;#index | name | all&gt; [override | temp]" permission="enhancedechest.admin.transfer">

Chuyển rương của một người chơi sang tài khoản khác, dùng khi ai đó đổi tài khoản. Dùng `all` để chuyển mọi rương (tài khoản đích sẽ có đúng các rương của nguồn), hoặc `#index` / tên rương để chỉ chuyển một rương. Nếu tài khoản đích đã có vật phẩm trong rương sẽ bị thay thế, thêm `override` để bỏ chúng đi hoặc `temp` để chuyển sang kho tạm khôi phục được. Rương của nguồn sẽ bị xóa nên vật phẩm không bao giờ bị nhân đôi.

</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">

Tải lại file cấu hình và ngôn ngữ mà không cần khởi động lại máy chủ.

</CommandRow>

<CommandRow :commands="['/ee migrate vanilla &lt;player&gt;', '/ee migrate vanilla all']" permission="enhancedechest.admin.migrate">

Nhập nội dung rương Ender vanilla vào plugin. Mỗi người chơi chỉ được chuyển một lần. Yêu cầu người chơi đang trực tuyến.

</CommandRow>

<CommandRow :commands="['/ee migrate axvaults', '/ee migrate axvaults &lt;player&gt;']" permission="enhancedechest.admin.migrate">

Nhập kho từ plugin AxVaults vào các rương EnhancedEchest tương ứng. Hoạt động cả với người chơi ngoại tuyến và đọc trực tiếp cơ sở dữ liệu của AxVaults. Xem trang [Chuyển Dữ Liệu](/vi/docs/migration#axvaults) để biết cách thiết lập.

</CommandRow>

<CommandRow :commands="['/ee migrate playervaultsx', '/ee migrate playervaultsx &lt;player&gt;']" permission="enhancedechest.admin.migrate">

Nhập kho từ plugin PlayerVaultsX vào các rương EnhancedEchest tương ứng. Hoạt động cả với người chơi ngoại tuyến và đọc trực tiếp dữ liệu kho tệp phẳng của PlayerVaultsX. Xem trang [Chuyển Dữ Liệu](/vi/docs/migration#playervaultsx) để biết cách thiết lập.

</CommandRow>
