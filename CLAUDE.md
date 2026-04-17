# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Resource-Profile** (师生资源画像系统) — A microservices-based education resource profiling system with a Vue 3 frontend and Java Spring Cloud backend.

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, Spring Cloud Alibaba 2023.0.1.0, MyBatis Plus 3.5.6, JWT 0.12.5, MySQL 8, Redis
- **Frontend**: Vue 3, Vite, Element Plus, Pinia, Vue Router, ECharts, Axios
- **Infrastructure**: Nacos (service discovery & config), MySQL, Redis, Docker Compose

## Common Commands

### Backend

```bash
# Build all modules
cd backend && mvn clean install

# Run a specific service (from its directory)
cd backend/auth-service && mvn spring-boot:run

# Available services and ports
gateway        :8080  # API gateway, routes all requests
auth-service   :8081  # Authentication & authorization
user-service   :8082  # User management
teacher-service:8083  # Teacher profiles
student-service:8084  # Student profiles
mental-service :8085  # Mental health & questionnaires
data-service   :8086  # Dashboard & analytics
```

### Frontend

```bash
cd frontend
npm install
npm run dev      # Dev server on http://localhost:5173
npm run build    # Production build to frontend/dist
npm run lint     # ESLint + fix
npm run format   # Prettier
```

### Infrastructure (Docker Compose)

```bash
cd docker
docker-compose up -d
```

This starts MySQL (`edu-portrait-mysql`, port 3306), Redis (`edu-portrait-redis`, port 6379), and Nacos (`edu-portrait-nacos`, ports 8848/9848). The first time MySQL starts, it executes `sql/init/01_init.sql`.

## Architecture

### Backend Structure

The backend is a Maven multi-module project under `backend/`:

- **`common`** — Shared utilities (`Result<T>` response wrapper, `JwtUtil`, `JwtProperties`). Imported by all services.
- **`gateway`** — Spring Cloud Gateway. Routes requests by path prefix (`/auth/**`, `/user/**`, `/teacher/**`, `/student/**`, `/mental/**`, `/data/**`) to the appropriate service via Nacos service discovery (`lb://{service-name}`).
- **`auth-service`** — Handles login/logout, token refresh, and user info. Uses Spring Security with BCrypt password encoding and JWT. Connects to MySQL and Redis.
- **`user-service`**, **`teacher-service`**, **`student-service`**, **`mental-service`**, **`data-service`** — Domain-specific services. Each connects to MySQL, registers with Nacos, and uses MyBatis Plus for persistence.

All services read config from `bootstrap.yml`. They expect Nacos at `localhost:8848` by default (override with `NACOS_SERVER`) and MySQL at `localhost:3306` with `root/root` by default (override with `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`). MyBatis Plus is configured with logical delete on field `deleted` (`1` = deleted, `0` = active) in most services.

### Frontend Structure

The frontend is a standard Vite + Vue 3 SPA under `frontend/`:

- **`src/router/index.js`** — Client-side routing. Role-based access uses `meta.roles` (e.g., `roles: ['admin']`). Route guards fetch user info via Pinia if a token exists.
- **`src/store/modules/user.js`** — Pinia store managing JWT token (stored in `localStorage`), user info, and login/logout flow.
- **`src/utils/request.js`** — Axios instance. Automatically attaches `Authorization: Bearer <token>`. Intercepts responses and shows `ElMessage.error` on non-200 `code`. Expects backend responses in `Result<T>` format (`{ code, message, data, timestamp }`).
- **`vite.config.js`** — Dev server proxies `/api` to `http://localhost:8080` (the gateway). The frontend request base URL defaults to `/api`.

### API Routing

Frontend (dev) → Vite proxy `/api` → Gateway `:8080` → Nacos discovery → Target service.

For example, a request to `/api/auth/login` from the frontend hits the gateway, which strips nothing (`StripPrefix: 0`) and forwards to `auth-service` at `/auth/login`.

## Default Test Data

The SQL init script (`sql/init/01_init.sql`) creates default users:

- `admin` / `admin` — System administrator
- `teacher` / `teacher` — Teacher role
- `student` / `student` — Student role

## Notes

- There are currently no automated tests in either the backend or frontend.
- The gateway and `auth-service` reference a shared Nacos config `common.yml`; it does not need to exist for local dev but Nacos must be running for service discovery.
- All backend controllers return `Result.success(...)` or `Result.error(...)` for consistent response handling.
