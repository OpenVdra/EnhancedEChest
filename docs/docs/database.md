# Database

EnhancedEchest stores every ender chest's contents in a database. You choose the backend with the `database.type` option in `config.yml`.

| Backend | `type` value | Best for |
|---------|--------------|----------|
| **SQLite** | `sqlite` | Single servers, zero setup, works out of the box |
| **MySQL** | `mysql` | Networks sharing one database |
| **MariaDB** | `mariadb` | Networks sharing one database |
| **PostgreSQL** | `postgres` | Setups already running Postgres |

::: tip No extra installations needed
All database drivers are bundled inside the plugin jar. You do not need to install anything on your server.
:::

## SQLite (default)

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

## MySQL / MariaDB

Point the plugin at an existing MySQL or MariaDB database:

```yaml
database:
  type: mysql          # or: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  pool-size: 10
```

- Create the database (schema) beforehand, for example `CREATE DATABASE enhancedechest;`
- The plugin creates and manages its own tables automatically

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

The default PostgreSQL port is **5432**, so remember to change `port` from the MySQL default.

## Sharing Data Across Servers

Pointing several servers at the **same** MySQL/MariaDB/PostgreSQL database lets them share ender chest storage. Players see the same contents regardless of which server they log in to, as long as they are only on one server at a time.

## Switching Backends

To move to a different backend, change `database.type` (and the connection fields) and restart the server. Existing data is not copied automatically between backends; only the [vanilla migration](/docs/migration) import is automated.
