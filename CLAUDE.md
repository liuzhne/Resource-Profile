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

1. **`common`** - Shared common library
   - JWT utilities (`JwtUtil`)
   - Result wrapper (`Result<T>`)
   - JWT configuration properties

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

**Frontend Structure:**
```
frontend/src/
├── api/           - API service calls
├── components/    - Reusable Vue components
├── router/        - Vue Router configuration
├── store/         - Pinia state management
├── styles/        - Global SCSS styles
├── utils/         - Utility functions
├── views/         - Page components
├── App.vue        - Root component
└── main.js        - Entry point
```

**Vite Config:**
- Dev Server: `http://localhost:5173`
- API Proxy: `/api` → `http://localhost:8080`
- Auto-import for Element Plus components

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
