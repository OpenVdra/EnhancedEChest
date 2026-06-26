# Chuyển Dữ Liệu

Nếu người chơi của bạn đã có vật phẩm trong rương Ender **vanilla**, EnhancedEchest có thể nhập dữ liệu đó vào kho lưu trữ của riêng nó để không mất gì khi cài plugin.

## Cách Hoạt Động

Khi một người chơi được chuyển, 27 ô rương Ender vanilla của họ được sao chép vào **rương #1** của EnhancedEchest và rương Ender vanilla được xóa sạch. Mỗi người chơi chỉ được chuyển **đúng một lần** và bị bỏ qua trong các lần tiếp theo.

## Tự Động Chuyển Khi Vào

Để chuyển người chơi tự động ngay lần đầu họ vào sau khi cài plugin, bật nó trong `config.yml`:

```yaml
migration:
  enabled: true
```

Khi bật, bất kỳ người chơi chưa được chuyển nào sẽ có rương Ender vanilla nhập ngay khoảnh khắc họ vào. Sau khi mọi người đã đăng nhập, bạn có thể tắt lại.

## Chuyển Thủ Công

Quản trị viên có thể kích hoạt chuyển dữ liệu theo yêu cầu cho người chơi đang trực tuyến:

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate run <player>` | Chuyển một người chơi đang trực tuyến |
| `/ee migrate run all` | Chuyển mọi người chơi hiện đang trực tuyến |

Cả hai đều cần quyền `enhancedechest.admin.migrate.run`. Người chơi đã được chuyển sẽ được báo là bị bỏ qua.

::: warning Chỉ người chơi trực tuyến
Việc chuyển đọc rương Ender vanilla trực tiếp của người chơi, nên chỉ hoạt động với người **đang trực tuyến**. Người chơi ngoại tuyến sẽ được chuyển tự động ở lần vào tiếp theo nếu `migration.enabled` là `true`.
:::
