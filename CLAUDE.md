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

**Build Tool:** Maven 3+ (no wrapper)
**Java Version:** 17
**Spring Boot:** 3.2.5
**Spring Cloud:** 2023.0.1
**Spring Cloud Alibaba:** 2023.0.1.0

**Microservices Modules:**

1. **`common`** - Shared common library (auto-configured via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)
   - JWT utilities (`JwtUtil`)
   - Result wrapper (`Result<T>`)
   - JWT configuration properties (`JwtProperties`)

2. **`gateway`** (Port: 8080) - Spring Cloud Gateway
   - API Gateway routing to all services
   - Load balancing with Spring Cloud LoadBalancer
   - CORS configuration
   - Routes: `/auth`, `/user`, `/teacher`, `/student`, `/mental`, `/data`

3. **`auth-service`** (Port: 8081) - Authentication & Authorization
   - User login/logout
   - JWT token generation and validation
   - User/Role management
   - MySQL + Redis

4. **`user-service`** - User management
5. **`teacher-service`** - Teacher profile management
6. **`student-service`** - Student profile management
7. **`mental-service`** - Mental health assessment
   - Questionnaires
   - Mental health assessments
8. **`data-service`** - Data analysis and dashboard

**Key Dependencies:**
- MyBatis Plus 3.5.11 (ORM)
- JWT 0.12.5 (jjwt-api, jjwt-impl, jjwt-jackson)
- MySQL 8.0.33
- Spring Cloud Alibaba Nacos (Service Discovery + Configuration)

### Backend Architectural Patterns

**Controller-Service-Mapper-Entity pattern:**
All services follow this structure. Controllers use `@RestController` + `@RequestMapping` with `@RequiredArgsConstructor` for dependency injection. There are no XML mappers — all MyBatis Plus mappers extend `BaseMapper<Entity>` and use annotation-based or programmatic queries.

**Uniform API Response (`Result<T>`):**
All controllers return `Result<T>` from the `common` module. The wrapper uses `code` (200 for success), `message`, `data`, and `timestamp`. Frontend request interceptors expect this shape.

**MyBatis Plus Conventions:**
- Logic delete field: `deleted` (1 = deleted, 0 = active)
- ID type: `auto`
- Underscore-to-camel-case mapping enabled
- SQL logging to stdout enabled in dev

**JWT Authentication Flow:**
- Tokens are JWT signed with `JWT_SECRET` (configured via `jwt.secret`)
- Access tokens expire in 24 hours; refresh tokens expire in 7 days
- Auth service stores active tokens in Redis (`token:{userId}`) with 24h TTL
- Controllers parse the `Authorization: Bearer <token>` header to extract `userId` claim
- Auth endpoints: `POST /auth/login`, `GET /auth/userInfo`, `POST /auth/logout`, `POST /auth/refresh`

**Gateway Routing Convention:**
Routes use `StripPrefix: 0`, meaning the path prefix is **not** stripped. A request to `/auth/login` reaches `auth-service` as `/auth/login`. Controllers must include the full prefix in their `@RequestMapping` (e.g., `@RequestMapping("/auth")`).

**Nacos Config Externalization:**
Each service loads config from Nacos in addition to local `application.yml`:
- `optional:nacos:{service-name}.yml?group=DEFAULT_GROUP`
- `optional:nacos:common.yml?group=DEFAULT_GROUP`
Database and Redis credentials may be defined in Nacos rather than local files.

## Frontend - Vue 3

**Build Tool:** Vite 5.2.8
**Framework:** Vue 3.4.21 (Composition API)
**Package Manager:** npm

**Key Dependencies:**
- `vue-router` 4.3.0 - Client-side routing
- `pinia` 2.1.7 - State management (Composition API stores)
- `element-plus` 2.6.3 - UI Component Library
- `@element-plus/icons-vue` 2.3.1 - Icons
- `axios` 1.6.8 - HTTP client
- `echarts` 5.5.0 + `vue-echarts` 6.6.9 - Data visualization
- `js-cookie` 3.0.5 - Cookie management
- `nprogress` 0.2.0 - Progress bar

**Frontend Structure:**
```
frontend/src/
├── api/           - API service calls (one file per domain)
├── components/    - Reusable Vue components
│   └── Layout/    - Layout shell (Sidebar, Navbar, Breadcrumb, TagsView)
├── router/        - Vue Router configuration
├── store/         - Pinia state management
│   └── modules/   - Domain stores (user.js, app.js)
├── styles/        - Global SCSS styles
├── utils/         - Utility functions
│   └── request.js - Axios instance with interceptors
├── views/         - Page components
├── App.vue        - Root component
└── main.js        - Entry point
```

**Vite Config:**
- Dev Server: `http://localhost:5173`
- API Proxy: `/api` → `http://localhost:8080`
- Auto-import for Element Plus components and Vue/Pinia/Router APIs via `unplugin-auto-import`

### Frontend Architectural Patterns

**Axios Request Handling (`utils/request.js`):**
- Base URL defaults to `/api` (proxied to gateway in dev)
- Request interceptor injects `Authorization: Bearer <token>` from Pinia `userStore`
- Response interceptor unwraps `Result<T>`:
  - `code === 200`: returns `res` (the full Result object)
  - `code !== 200`: shows `ElMessage.error(res.message)` and rejects
  - `code === 401`: triggers logout and redirects to `/login`
- Network errors show `ElMessage.error` with the response message

**Pinia Stores (Composition API):**
Stores use `defineStore` with setup function syntax (`ref`, `computed`).
- `userStore`: manages `token` (persisted to `localStorage`), `userInfo`, login/logout, role extraction
- `appStore`: layout state (sidebar collapse, etc.)

**Router Guards:**
- `router.beforeEach` checks `userStore.token`
- If token exists but `userInfo` is missing, fetches user info; on failure, logs out
- Public routes marked with `meta: { public: true }` (only `/login`)
- Route `meta.roles` exists for role-based hiding but is not enforced in the navigation guard

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

## Common Commands

**Backend (Maven):**
```bash
# Build all modules
cd backend
mvn clean install

# Build without tests
cd backend
mvn clean install -DskipTests

# Build a single module and its dependencies
cd backend
mvn clean install -pl auth-service -am -DskipTests

# Run individual service (from service directory)
cd backend/auth-service
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
| Axios Instance | `/frontend/src/utils/request.js` |
| Router | `/frontend/src/router/index.js` |
| User Store | `/frontend/src/store/modules/user.js` |
