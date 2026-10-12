# Hackathon starter

Generic Spring Boot + React (Vite) starter. No business logic. See `STARTER_MANIFEST.md` for every file.

## Prerequisites
- Java 21 (Temurin recommended), checked with 21.0.2
- Node 20+ and npm (checked with Node 24, npm 11)
- Docker (optional: Postgres and the image build), checked with 29.x
- Maven is not needed: use the `./mvnw` wrapper (Maven 3.9.9)

## Commands (from the repo root)

| Task | Command |
|---|---|
| Start Postgres (host port **5433**) | `docker compose up -d --wait` |
| Stop Postgres | `docker compose down` |
| Backend on H2 | `cd backend && ./mvnw spring-boot:run` |
| Backend on Postgres | `cd backend && SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run` |
| UI dev server | `cd ui && npm ci && npm run dev` |
| Run tests | `cd backend && ./mvnw test` |
| Build Docker image | `cd backend && docker build -t hackathon-backend .` |

PowerShell: set the profile first with `$env:SPRING_PROFILES_ACTIVE="postgres"`.

## Check it works
- Backend: http://localhost:8080/actuator/health returns `{"status":"UP"}`
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:hackathon`, user `sa`, empty password)
- UI: http://localhost:5173 (also proxies `/api` and `/actuator` to the backend)

## Warm the caches (do this once, online)
```
cd backend && ./mvnw dependency:go-offline && ./mvnw test
cd ui && npm ci
```
`dependency:go-offline` alone does not fetch the test runner, so the extra `./mvnw test` is what makes `./mvnw -o test` work offline.

## Config notes
- LLM key: copy `.env.example` to `.env` and set `LLM_API_KEY`, or export it in your shell. The app starts without it. Never commit it.
- Postgres is on host port 5433, not 5432, so it does not clash with a locally installed Postgres.
- The app pins its JVM timezone to UTC in `HackathonApplication`. Without that, Windows machines in India fail to connect to `postgres:16` (`invalid value for parameter "TimeZone": "Asia/Calcutta"`).
