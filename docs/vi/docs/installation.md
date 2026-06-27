# Cài Đặt

## Yêu Cầu

| Yêu cầu | Thông số |
|---------|----------|
| **Phiên bản Minecraft** | 1.21.11 - 26.2 |
| **Phần mềm máy chủ** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) hoặc các bản fork tương thích Paper |
| **Phiên bản Java** | Java 21 |

::: warning Cần Paper 1.21.11 - 26.2
EnhancedEchest yêu cầu Paper hoặc bản fork tương thích (Folia, Purpur). Nó sẽ **không** chạy trên CraftBukkit hay các phiên bản Minecraft cũ hơn 1.21.11.
:::

::: warning Cần Java 21
Plugin được biên dịch cho Java 21. Hãy đảm bảo máy chủ chạy Java 21 hoặc mới hơn, nếu không nó sẽ không tải được.
:::

## Tải Về

Chọn nguồn tải bạn ưa thích:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/enhancedechest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg" alt="Spigot" style="height: 24px;">
    Spigot
  </a>
  <a href="https://hangar.papermc.io/Nighter/EnhancedEchest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

## Các Bước Cài Đặt

### 1. Cài Plugin

1. **Dừng máy chủ** hoàn toàn
2. Tải file `.jar` mới nhất từ một nguồn ở trên
3. Đặt vào thư mục `plugins/` của máy chủ
4. **Khởi động máy chủ** (tránh dùng `/reload`, có thể gây lỗi)

::: tip Không cần cài thêm gì
Mọi thứ plugin cần đã được đóng gói bên trong jar. Bạn không cần cài thêm gì trên máy chủ.
:::

### 2. Kiểm Tra Cài Đặt

Chạy lệnh sau trong console hoặc trong game để xác nhận plugin đã tải:

```
/plugins
```

EnhancedEchest sẽ xuất hiện trong danh sách với trạng thái màu xanh. Mặc định plugin dùng SQLite, hoạt động ngay không cần thiết lập thêm.

### 3. Các File Được Tạo

Plugin tự động tạo các file trong `plugins/EnhancedEchest/`:

| File | Mô tả |
|------|-------|
| `config.yml` | Cấu hình chính: kích thước rương, lưu trữ, chuyển dữ liệu |
| `enderchests.db` | Cơ sở dữ liệu SQLite (lưu trữ mặc định) |
| `language/<locale>/messages.yml` | Tin nhắn hiển thị cho người chơi và prefix của plugin |
| `language/<locale>/gui.yml` | Tiêu đề kho đồ và nhãn menu rương |

## Cập Nhật

1. **Tải** phiên bản mới
2. **Dừng** máy chủ
3. **Thay** file `.jar` cũ bằng file mới
4. **Khởi động** máy chủ

Dữ liệu và cấu hình của bạn được giữ nguyên qua các lần cập nhật.

## Nhận Trợ Giúp

Nếu gặp sự cố, kiểm tra **log console** để tìm thông báo lỗi hoặc báo lỗi trên **[GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues)**.
