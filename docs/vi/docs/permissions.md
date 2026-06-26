# Quyền

Mọi node quyền mặc định là `op`. Hãy cấp chúng qua plugin quyền của bạn (LuckPerms, v.v.) để mở cho các rank khác.

## Người Chơi

**`enhancedechest.command.open`**
Cho phép dùng `/ec` và `/eclist` bằng lệnh, và đặt rương chính. Chuột phải vào khối rương Ender không bao giờ cần quyền này.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Cấp thêm rương theo rank. Ví dụ, `enhancedechest.additional_amount.2.slot.54` cho người chơi hai rương 54 ô. Nhiều node cộng dồn. Xóa node sẽ xóa các rương đó; vật phẩm dồn sang rương tạm có thể khôi phục từ `/eclist`.

Xem [Rương Cấp Theo Quyền](#permission-granted-chests) bên dưới để biết chi tiết.

## Quản Trị Viên

Mọi lệnh `/ee` đều cần node cơ sở **`enhancedechest.command.admin`** cùng với node riêng của từng lệnh:

**`enhancedechest.admin.add`** — `/ee add`: cấp cho người chơi một rương mới.

**`enhancedechest.admin.resize`** — `/ee resize`: thay đổi số ô của một rương.

**`enhancedechest.admin.delete`** — `/ee delete`: xóa các rương mới nhất của người chơi.

**`enhancedechest.admin.view`** — `/ee view`: mở rương của người chơi khác (chỉ-đọc).

**`enhancedechest.admin.edit`** — kết hợp với `admin.view`, cho phép di chuyển vật phẩm.

**`enhancedechest.admin.reload`** — `/ee reload`: tải lại file cấu hình và ngôn ngữ.

**`enhancedechest.admin.migrate.run`** — `/ee migrate run`: nhập dữ liệu rương Ender vanilla.

::: tip
Để cấp toàn quyền quản trị một lần, cấp cả `enhancedechest.command.admin` và `enhancedechest.admin.*` (nếu plugin quyền của bạn hỗ trợ wildcard).
:::

## Rương Cấp Theo Quyền {#permission-granted-chests}

Dùng `enhancedechest.additional_amount.<count>.slot.<size>` để gắn đặc quyền rương vào rank mà không cần dùng lệnh.

- **`<count>`**: số rương cần cấp.
- **`<size>`**: số ô, bội số của 9 từ 9 đến 54.
- **Node cộng dồn**: cấp `...1.slot.9` và `...2.slot.9` sẽ cho người chơi tổng cộng ba rương 9 ô.
- **Thu hồi sạch sẽ**: hạ rank thì xóa đúng các rương đó; vật phẩm dồn sang rương tạm khôi phục được từ `/eclist`.
- **Rương cơ bản luôn được bảo vệ**: người chơi luôn giữ ít nhất một rương thường.
- Việc cấp đồng bộ ở lần mở rương tiếp theo, không cần đăng nhập lại.

::: warning
Việc cấp theo quyền chỉ hoạt động khi `permission-chests.enabled: true` trong `config.yml`. Tắt nó dừng đồng bộ nhưng giữ nguyên các rương đã cấp.
:::
