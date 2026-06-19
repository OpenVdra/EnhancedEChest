# EnhancedEChest

Plugin Minecraft (Paper) mở rộng ender chest từ 27 ô lên **54 ô**, lưu dữ liệu vào database riêng thay vì file thế giới.

## Yêu cầu

- Paper 1.21.11 trở lên
- Java 21 trở lên

## Cài đặt

1. Tải file `.jar` mới nhất ở trang [Releases](../../releases).
2. Bỏ vào thư mục `plugins/` của server.
3. Khởi động lại server.

Plugin tự tạo `plugins/EnhancedEChest/config.yml` và file database khi khởi động lần đầu.

## Cấu hình

`plugins/EnhancedEChest/config.yml`:

```yaml
gui:
  title: "Ender Chest"

database:
  type: sqlite           # sqlite hoặc mysql

  # Chỉ cần điền phần dưới nếu dùng MySQL/MariaDB
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""

migration:
  enabled: false         # đặt true để tự chuyển dữ liệu ender chest cũ sang khi player đăng nhập
```

## Lệnh

| Lệnh | Tác dụng |
|------|----------|
| `/ec` | Mở ender chest của bạn |
| `/enhancedechest reload` | Tải lại cấu hình |
| `/enhancedechest migrate run <player>` | Chuyển dữ liệu EC cũ cho player chỉ định |
| `/enhancedechest migrate run all` | Chuyển dữ liệu EC cũ cho tất cả player đang online |

Lệnh `/ee` là alias của `/enhancedechest`, `/enderchest` là alias của `/ec`.

## Quyền

| Quyền | Tác dụng | Mặc định |
|-------|----------|----------|
| `ee.use` | Mở ender chest | OP |
| `ee.admin.reload` | Reload config | OP |
| `ee.admin.migrate.toggle` | Bật/tắt migration | OP |
| `ee.admin.migrate.run` | Chạy migration | OP |

## License

GPLv3 — xem file [LICENSE](LICENSE).
