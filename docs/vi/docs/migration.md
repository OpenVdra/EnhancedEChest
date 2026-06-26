# Chuyển Dữ Liệu

EnhancedEchest có thể nhập dữ liệu rương Ender sẵn có vào kho lưu trữ của riêng nó để không mất gì khi cài plugin. Hỗ trợ ba nguồn: rương Ender **vanilla**, plugin **AxVaults** và plugin **PlayerVaultsX**.

## Từ Rương Ender Vanilla

Nếu người chơi của bạn đã có vật phẩm trong rương Ender vanilla, EnhancedEchest có thể nhập dữ liệu đó.

### Cách Hoạt Động

Khi một người chơi được chuyển, các ô rương Ender vanilla của họ được sao chép vào **rương #1** của EnhancedEchest và rương Ender vanilla được xóa sạch. Mỗi người chơi chỉ được chuyển **đúng một lần** và bị bỏ qua trong các lần tiếp theo.

### Tự Động Chuyển Khi Vào

Để chuyển người chơi tự động ngay lần đầu họ vào sau khi cài plugin, bật nó trong `config.yml`:

```yaml
migration:
  enabled: true
```

Khi bật, bất kỳ người chơi chưa được chuyển nào sẽ có rương Ender vanilla nhập ngay khoảnh khắc họ vào. Sau khi mọi người đã đăng nhập, bạn có thể tắt lại.

### Chuyển Thủ Công

Quản trị viên có thể kích hoạt chuyển dữ liệu theo yêu cầu cho người chơi đang trực tuyến:

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate vanilla <player>` | Chuyển một người chơi đang trực tuyến |
| `/ee migrate vanilla all` | Chuyển mọi người chơi hiện đang trực tuyến |

Cả hai đều cần quyền `enhancedechest.admin.migrate`. Người chơi đã được chuyển sẽ được báo là bị bỏ qua.

::: warning Chỉ người chơi trực tuyến
Việc chuyển đọc rương Ender vanilla trực tiếp của người chơi, nên chỉ hoạt động với người **đang trực tuyến**. Người chơi ngoại tuyến sẽ được chuyển tự động ở lần vào tiếp theo nếu `migration.enabled` là `true`.
:::

### Purpur (và các bản fork của Paper) {#purpur}

Rương Ender mở rộng của Purpur (`ender-chest.six-rows` / số hàng theo quyền `purpur.enderchest.rows.<n>`) được hỗ trợ mà không cần cấu hình thêm. Purpur lưu nó trong dữ liệu rương Ender tiêu chuẩn, nên khi chuyển sẽ lấy được toàn bộ số hàng người chơi đã có (lên tới đủ 54 ô), chứ không chỉ 27 ô đầu. Chỉ cần chạy chuyển dữ liệu như trên trong khi server đang chạy Purpur.

## Từ AxVaults {#axvaults}

EnhancedEchest có thể nhập kho từ plugin [AxVaults](https://modrinth.com/plugin/axvaults), bao gồm mọi vật phẩm cùng tên tùy chỉnh, lore và phù phép. Tính năng này đã được kiểm thử với **AxVaults 2.15.0**.

Mỗi kho AxVaults được nhập vào rương EnhancedEchest có **cùng số thứ tự**: kho #1 thành rương #1, kho #2 thành rương #2, v.v. Kích thước rương được chọn đủ lớn để chứa hết vật phẩm.

### Trước Khi Bắt Đầu

Việc nhập đọc trực tiếp cơ sở dữ liệu của AxVaults, nên cần lưu ý hai điều:

- **Lưu AxVaults trước.** AxVaults giữ các kho đang mở trong bộ nhớ và chỉ ghi xuống cơ sở dữ liệu theo chu kỳ. Hãy chạy `/vaultadmin save` một lần để mọi kho được ghi xuống đĩa trước khi chuyển.
- **Dùng SQLite, hoặc tắt AxVaults nếu dùng H2.** Cơ sở dữ liệu H2 mặc định của AxVaults bị khóa khi AxVaults đang chạy nên không thể đọc trực tiếp. Hãy đặt `database.type: sqlite` trong `AxVaults/config.yml` (tệp `data.db` của nó đọc được khi server đang chạy), hoặc tắt server nguồn trước nếu nó dùng H2. EnhancedEchest tự phát hiện tệp cơ sở dữ liệu AxVaults có trong `plugins/AxVaults`.

### Cách Chạy

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate axvaults` | Nhập kho cho mọi người chơi trong cơ sở dữ liệu AxVaults |
| `/ee migrate axvaults <player>` | Nhập kho cho một người chơi (trực tuyến hoặc ngoại tuyến) |

Cả hai đều cần quyền `enhancedechest.admin.migrate`. Việc nhập chạy nền và báo số người chơi cùng số kho đã nhập khi hoàn tất.

::: tip An toàn khi chạy lại
Một kho sẽ **không bao giờ** được nhập đè lên rương đã có vật phẩm. Nếu người chơi đã có rương ở số thứ tự đó, kho tương ứng sẽ được báo là bỏ qua và giữ nguyên, nên chạy lệnh hai lần cũng không nhân đôi hay ghi đè bất cứ thứ gì.
:::

## Từ PlayerVaultsX {#playervaultsx}

EnhancedEchest có thể nhập kho từ plugin [PlayerVaultsX](https://www.spigotmc.org/resources/playervaultsx.45741/), bao gồm mọi vật phẩm cùng tên tùy chỉnh, lore và phù phép. Tính năng này đã được kiểm thử với **PlayerVaultsX 4.4.13**.

Mỗi kho PlayerVaultsX được nhập vào rương EnhancedEchest có **cùng số thứ tự**: kho #1 thành rương #1, kho #2 thành rương #2, v.v. Kích thước rương được chọn đủ lớn để chứa hết vật phẩm.

### Trước Khi Bắt Đầu

PlayerVaultsX lưu kho của mỗi người chơi trong một tệp phẳng tại `plugins/PlayerVaults/newvaults/<uuid>.yml` — không có cơ sở dữ liệu nào cần cấu hình. EnhancedEchest đọc trực tiếp các tệp đó:

- **Kho được lưu khi đóng.** PlayerVaultsX ghi một kho xuống đĩa khi người chơi đóng nó, nên hãy chắc chắn không ai đang mở kho trong lúc chuyển dữ liệu. Khởi động lại server nguồn (hoặc đơn giản là cho người chơi đăng xuất) sẽ ghi mọi thứ xuống đĩa.
- **Chạy trên server Paper hiện đại.** EnhancedEchest giải mã vật phẩm PlayerVaultsX bằng định dạng vật phẩm của Paper. Dữ liệu kho tạo trên server Paper hiện đại (1.20.6+) nhập trơn tru; dữ liệu được ghi từ lâu bởi server Spigot cũ dùng định dạng nội bộ khác và có thể không giải mã được.

Thư mục plugin được đặt tên là `PlayerVaults` (tên plugin của jar PlayerVaultsX), nên EnhancedEchest tìm trong `plugins/PlayerVaults`. Thư mục `plugins/PlayerVaultsX` cũng được chấp nhận làm phương án dự phòng.

### Cách Chạy

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate playervaultsx` | Nhập kho cho mọi người chơi có dữ liệu PlayerVaultsX |
| `/ee migrate playervaultsx <player>` | Nhập kho cho một người chơi (trực tuyến hoặc ngoại tuyến) |

Cả hai đều cần quyền `enhancedechest.admin.migrate`. Việc nhập chạy nền và báo số người chơi cùng số kho đã nhập khi hoàn tất.

::: tip An toàn khi chạy lại
Một kho sẽ **không bao giờ** được nhập đè lên rương đã có vật phẩm. Nếu người chơi đã có rương ở số thứ tự đó, kho tương ứng sẽ được báo là bỏ qua và giữ nguyên, nên chạy lệnh hai lần cũng không nhân đôi hay ghi đè bất cứ thứ gì.
:::
