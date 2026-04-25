# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Resource-Profile is a **Teacher-Student Resource Portrait System** (师生资源画像系统) - a microservices-based educational platform with a Vue 3 frontend and Spring Boot backend.

## Architecture

**Multi-Module Project Structure:**
- `/backend` - Java/Spring Boot microservices
- `/frontend` - Vue 3 + Vite single-page application
- `/docker` - Docker and docker-compose configuration
- `/sql` - Database initialization scripts

## Backend - Spring Boot Microservices

**Build Tool:** Maven 3+
**Java Version:** 17
**Spring Boot:** 3.2.5
**Spring Cloud:** 2023.0.1
**Spring Cloud Alibaba:** 2023.0.1.0

**Microservices Modules:**

| Module | Port | Purpose |
|--------|------|---------|
| `gateway` | 8080 | Spring Cloud Gateway, routes to all services |
| `auth-service` | 8081 | Authentication & Authorization |
| `user-service` | 8082 | User management |
| `teacher-service` | 8083 | Teacher profile management |
| `student-service` | 8084 | Student profile management |
| `mental-service` | 8085 | Mental health assessment |
| `data-service` | 8086 | Data analysis and dashboard |
| `common` | - | Shared library (JWT, Result wrapper) |

**Key Dependencies:**
- MyBatis Plus 3.5.11 (ORM)
- JWT 0.12.5 (jjwt-api, jjwt-impl, jjwt-jackson)
- MySQL 8.0.33
- Spring Cloud Alibaba Nacos (Service Discovery + Configuration)
- Lombok 1.18.30

**Backend Code Patterns:**

All services follow a consistent layered architecture:
- `entity/` - MyBatis Plus entities using Lombok `@Data`
- `mapper/` - Mappers extending `BaseMapper<Entity>` (no XML mappers; all SQL via annotations or MyBatis Plus wrappers)
- `service/` + `service/impl/` - Service interfaces and `@Service` implementations
- `controller/` - `@RestController` with `@RequestMapping` and `@RequiredArgsConstructor` for dependency injection
- `dto/` - Request/response DTOs using Lombok
- `config/` - Spring `@Configuration` classes
- `exception/GlobalExceptionHandler.java` - `@RestControllerAdvice` handling validation and runtime exceptions

**MyBatis Plus Global Config** (in each `application.yml`):
- Logic delete: field `deleted`, value `1` = deleted, `0` = not deleted
- ID type: `auto` (database auto-increment)
- `map-underscore-to-camel-case: true`

**API Response Pattern:**
All controllers return `Result<T>` from the `common` module:
- `Result.success(data)` → code 200
- `Result.error(message)` → code 500
- `Result.error(code, message)` → custom code

**Nacos Config Pattern:**
Every service imports two Nacos config files:
```yaml
config:
  import:
    - optional:nacos:{service-name}.yml?group=DEFAULT_GROUP
    - optional:nacos:common.yml?group=DEFAULT_GROUP
```

**Gateway Routing:**
Routes use load-balanced URIs (`lb://{service-name}`) with `StripPrefix=0`:
- `/auth/**` → auth-service
- `/user/**` → user-service
- `/teacher/**` → teacher-service
- `/student/**` → student-service
- `/mental/**` → mental-service
- `/data/**` → data-service

Global CORS is configured on the gateway (`allowedOrigins: "*"`).

**JWT Authentication Flow:**
- `JwtUtil` (common module) generates and parses tokens using HS256
- Access token expires in 24 hours, refresh token in 7 days
- Secret configured via `jwt.secret` (reads `JWT_SECRET` env var if mapped)
- On login: tokens generated, access token stored in Redis as `token:{userId}` with 24h TTL
- Frontend sends `Authorization: Bearer {token}` header
- Auth endpoints (`/auth/**`) are public; all other requests require authentication (enforced by Spring Security in auth-service)

## Frontend - Vue 3

**Build Tool:** Vite 5.2.8
**Framework:** Vue 3.4.21
**Package Manager:** npm

**Key Dependencies:**
- `vue-router` 4.3.0 - Client-side routing
- `pinia` 2.1.7 - State management
- `element-plus` 2.6.3 - UI Component Library
- `@element-plus/icons-vue` 2.3.1 - Icons
- `axios` 1.6.8 - HTTP client
- `echarts` 5.5.0 + `vue-echarts` 6.6.9 - Data visualization
- `js-cookie` 3.0.5 - Cookie management
- `nprogress` 0.2.0 - Progress bar

**Vite Config:**
- Dev Server: `http://localhost:5173`
- API Proxy: `/api` → `http://localhost:8080`
- Auto-import: `unplugin-auto-import` for Vue/Vue Router/Pinia APIs and Element Plus components
- Alias: `@` → `src`

**Frontend Patterns:**

- **API Layer:** Each backend service has a corresponding module in `src/api/`. All use the same `axios` instance from `@/utils/request` which:
  - Attaches `Authorization: Bearer {token}` from Pinia store automatically
  - Intercepts responses: shows `ElMessage.error()` on non-200 codes
  - Handles 401 by calling `userStore.logout()` and redirecting to login
  - Base URL defaults to `/api` (proxied to gateway in dev)

- **State Management:** Pinia stores use the Composition API pattern (`defineStore` with `ref`/`computed`). Token is persisted to `localStorage`. Key stores: `user` (auth state), `app` (UI state like sidebar collapse).

- **Router Guards:** Before each route: starts NProgress, checks token, fetches user info if missing, redirects unauthenticated users to `/login` unless route has `meta.public: true`. Admin-only routes use `meta.roles: ['admin']`.

- **Layout:** Main layout at `src/components/Layout/index.vue` with sidebar, navbar, breadcrumb, and tags-view.

## Docker & Infrastructure

**Services in `docker-compose.yml`:**

| Service | Image | Ports | Purpose |
|---------|-------|-------|---------|
| mysql | mysql:8.0 | 3306:3306 | Primary database |
| redis | redis:7-alpine | 6379:6379 | Cache & session storage |
| nacos | nacos/nacos-server:v2.3.0 | 8848, 9848 | Service discovery & config |
| gateway | Custom build | 8080:8080 | API gateway |

**Database:**
- Name: `edu_portrait`
- User: `edu` / `edu123456`
- Root password: `root`
- Initialization: `/sql/init/01_init.sql`
- Default accounts (all use bcrypt hash, password equals username): `admin`, `teacher`, `student`

## Common Commands

**Backend (Maven):**
```bash
cd backend

# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run individual service (from service directory)
mvn spring-boot:run
```

**Frontend (npm):**
```bash
cd frontend

# Install dependencies
npm install

# Start development server (port 5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint and fix
npm run lint

# Format code
npm run format
```

**Docker:**
```bash
cd docker

# Start all infrastructure services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

## Configuration & Environment Variables

**Nacos Configuration:**
- Server: `localhost:8848` (default)
- Environment variables: `NACOS_SERVER`, `NACOS_NAMESPACE`, `NACOS_USERNAME`, `NACOS_PASSWORD`

**MySQL Configuration:**
- Environment variables: `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`

**Redis Configuration:**
- Environment variables: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

**JWT Configuration:**
- Secret: `JWT_SECRET` environment variable
- Access token expire: 24 hours
- Refresh token expire: 7 days

## Key File Locations

| Purpose | Path |
|---------|------|
| Parent POM | `/backend/pom.xml` |
| Gateway Config | `/backend/gateway/src/main/resources/application.yml` |
| Auth Config | `/backend/auth-service/src/main/resources/application.yml` |
| Frontend Config | `/frontend/vite.config.js` |
| Docker Compose | `/docker/docker-compose.yml` |
| Database Init | `/sql/init/01_init.sql` |
| JWT Utility | `/backend/common/src/main/java/com/edu/common/util/JwtUtil.java` |
| Result Wrapper | `/backend/common/src/main/java/com/edu/common/result/Result.java` |
| API Request | `/frontend/src/utils/request.js` |
| Router | `/frontend/src/router/index.js` |
| User Store | `/frontend/src/store/modules/user.js` |
