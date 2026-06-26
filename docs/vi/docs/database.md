# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender vào cơ sở dữ liệu. Chọn backend bằng tùy chọn `database.type` trong `config.yml`.

| Backend | Giá trị `type` | Phù hợp nhất cho |
|---------|----------------|------------------|
| **SQLite** | `sqlite` | Máy chủ đơn lẻ, không cần thiết lập, dùng được ngay |
| **MySQL** | `mysql` | Các network dùng chung một cơ sở dữ liệu |
| **MariaDB** | `mariadb` | Các network dùng chung một cơ sở dữ liệu |
| **PostgreSQL** | `postgres` | Các thiết lập đã chạy sẵn Postgres |

::: tip Không cần cài thêm gì
Tất cả driver cơ sở dữ liệu đã được đóng gói bên trong jar. Bạn không cần cài gì thêm trên máy chủ.
:::

## SQLite (mặc định)

SQLite không cần cấu hình gì. Plugin tự tạo file cơ sở dữ liệu tại `plugins/EnhancedEchest/enderchests.db` khi khởi động lần đầu.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

## MySQL / MariaDB

Trỏ plugin tới một cơ sở dữ liệu MySQL hoặc MariaDB có sẵn:

```yaml
database:
  type: mysql          # hoặc: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  pool-size: 10
```

- Tạo cơ sở dữ liệu trước, ví dụ `CREATE DATABASE enhancedechest;`
- Plugin tự tạo và quản lý các bảng của riêng nó

## PostgreSQL

```yaml
database:
  type: postgres
  host: localhost
  port: 5432
  database: enhancedechest
  username: postgres
  password: "your-password"
  pool-size: 10
```

Port mặc định của PostgreSQL là **5432**, nhớ đổi `port` khỏi giá trị mặc định của MySQL.

## Chia Sẻ Dữ Liệu Giữa Các Máy Chủ

Trỏ nhiều máy chủ tới **cùng một** cơ sở dữ liệu MySQL/MariaDB/PostgreSQL cho phép chúng chia sẻ lưu trữ rương Ender. Người chơi thấy cùng một nội dung dù đăng nhập vào máy chủ nào, miễn là họ chỉ ở trên một máy chủ tại một thời điểm.

## Chuyển Đổi Backend

Để chuyển sang backend khác, đổi `database.type` (và các trường kết nối) rồi khởi động lại máy chủ. Dữ liệu hiện có không được sao chép tự động giữa các backend; chỉ việc nhập [chuyển dữ liệu vanilla](/vi/docs/migration) là được tự động hóa.
