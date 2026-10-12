# Personal Expense Tracking System – Backend (Spring Boot)

Java 17+, Spring Boot 3.2, Spring Security + JWT, Spring Data JPA/Hibernate, H2 (default) or MySQL, Maven.

## Run (easiest, VS Code)
1. Install the "Extension Pack for Java" (it includes Maven support) and JDK 17 or newer.
2. Open this `backend` folder, open `ExpenseTrackerApplication.java` and click **Run** above `main`
   (or run `mvn spring-boot:run` if Maven is installed).
3. Wait for `Started ExpenseTrackerApplication` in the console. Check http://localhost:8080/api/health → `{"status":"UP"}`.

## Database
- **Default = H2 file database**: nothing to install; data is stored in the `data/` folder.
- **MySQL**: in `application.properties` change `spring.profiles.active=h2` to `mysql`, then enter your username/password in
  `application-mysql.properties`. MySQL must be running; the `expense_tracker` database is created automatically.

## Features
Register/login (BCrypt + JWT), per-user data, expense CRUD with search/filter/sort/pagination and CSV export,
analytics (totals, averages, category/daily/monthly/weekday/payment charts data, month-over-month, projection),
monthly budgets with status, smart insights, validation and JSON errors, CORS for the separate frontend.

## API
POST /api/auth/register · POST /api/auth/login · GET /api/health
GET/POST /api/expenses · PUT/DELETE /api/expenses/{id} · GET /api/expenses/categories · GET /api/expenses/export
GET /api/analytics/summary?from=&to= · GET/POST /api/budgets · DELETE /api/budgets/{id}
(all except auth/health need `Authorization: Bearer <token>`)

## Troubleshooting
- Port 8080 busy → set `server.port=8081` and update `API_URL` in the frontend `js/config.js`.
- MySQL `Access denied` / `Communications link failure` → wrong password or MySQL not running; or switch back to the `h2` profile.
- Java version error → install JDK 17+ and select it in VS Code.
