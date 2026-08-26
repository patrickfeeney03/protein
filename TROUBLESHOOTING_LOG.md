# Production Architecture & Troubleshooting Log

This document records key deployment configurations, edge routing topology, and root-cause fixes applied to the **Protein Per Euro** application.

---

## 1. System Architecture & Edge Routing Topology

```
[ Browser / Client ]
        │
        ▼  HTTPS (proteinpereuro.ie)
[ Cloudflare Pages Edge ] ── (Static HTML / JS / Assets)
        │
        ├─ /api/* requests handled by Pages Worker: functions/api/[[path]].ts
        │
        ▼  HTTPS (origin-api.proteinpereuro.ie + X-Origin-Shared-Secret)
[ Cloudflare Tunnel (cloudflared daemon on VPS 'dec') ]
        │
        ▼  HTTP (127.0.0.1:8081)
[ Nginx Reverse Proxy (dec) ]
        │
        ▼  HTTP (127.0.0.1:9001)
[ Spring Boot Application (protein.service) ]
        │
        ▼
[ SQLite Database (/var/lib/protein/protein.sqlite) ]
```

---

## 2. Issues Encountered & Resolved

### Issue A: 20-Second Delay on `/api/auth/logout`

* **Symptom**: Logging out hung for exactly ~20 seconds in the UI.
* **Backend Error Log**:
  ```text
  Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 20000ms (total=1, active=1, idle=0, waiting=0)
          at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:714)
          at org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl.removeUserTokens(JdbcTokenRepositoryImpl.java:115)
          at com.example.demo.controllers.AuthController.logout(AuthController.java:191)
  ```
* **Root Cause**:
  `application.properties` hardcoded `spring.datasource.hikari.maximumPoolSize=1` with `connectionTimeout=20000` (20 seconds).
  When Spring Security executed `logout()`, `rememberMeServices.logout()` called `removeUserTokens()` to clear cookies in SQLite. Because the pool size was 1 and that 1 connection was held active by the HTTP request context, Hikari blocked and timed out after 20 seconds.
* **Resolution**:
  1. Updated `/etc/protein/application.yml` on production VPS (`dec`):
     ```yaml
     server:
       address: 127.0.0.1
       port: 9001

     spring:
       datasource:
         url: jdbc:sqlite:/var/lib/protein/protein.sqlite?busy_timeout=5000
         hikari:
           maximum-pool-size: 10
           connection-timeout: 5000
     ```
  2. Updated `src/main/resources/application.properties` in backend repo:
     ```properties
     spring.datasource.hikari.connectionTimeout=5000
     spring.datasource.hikari.maximumPoolSize=10
     ```
  3. Restarted `protein.service` on production (`dec`).
  4. Optimistically cleared local auth state (`meSubject.next(null)`) in Angular `auth.service.ts` so UI logout is instant (0ms).
* **Result**: Logout response time reduced from **20.0s** to **0.12s**.

---

### Issue B: Cloudflare Error 1016 (Origin DNS Error) / 530

* **Symptom**: API endpoints returned `530` / `Error 1016 Origin DNS error | api.proteinpereuro.ie`.
* **Root Cause**:
  1. The Cloudflare Tunnel on `dec` was configured to ingress `origin-api.proteinpereuro.ie`.
  2. Cloudflare Pages project for `proteinpereuro.ie` was named **`protein`** (not `protein-frontend`).
  3. Wrangler CLI deployments without project name binding target default or non-custom domain projects.
* **Resolution**:
  1. Updated `wrangler.json` in frontend repository:
     ```json
     {
       "name": "protein",
       "pages_build_output_dir": "dist/protein/browser"
     }
     ```
  2. Configured [`functions/api/[[path]].ts`](file:///home/patrick/.herdr/worktrees/protein-frontend/worktree-calm-harbor-2be1/functions/api/[[path]].ts) to proxy edge requests to `https://origin-api.proteinpereuro.ie`.
  3. Added `duplex: 'half'` to fetch options in `[[path]].ts` to enable proper streaming of non-GET request bodies (`POST`, `PUT`, `DELETE`).
* **Result**: All API calls route seamlessly through `https://proteinpereuro.ie/api/*` with 200 OK.

---

## 3. Key Server & Deployment Paths

| Component | Path / Detail |
| :--- | :--- |
| **Systemd Service** | `/etc/systemd/system/protein.service` (`protein.service`) |
| **Production App Dir** | `/opt/protein/demo-0.0.1-SNAPSHOT.jar` |
| **Production Config** | `/etc/protein/application.yml` |
| **SQLite Database** | `/var/lib/protein/protein.sqlite` |
| **Nginx Port** | `127.0.0.1:8081` |
| **Spring Boot Port** | `127.0.0.1:9001` |
| **Cloudflare Tunnel Target** | `origin-api.proteinpereuro.ie` -> `http://127.0.0.1:8081` |
| **Cloudflare Pages Project** | `protein` (`proteinpereuro.ie`) |

---

## 4. Useful Operational Commands

```bash
# View live Spring Boot logs on production VPS
ssh dec "sudo journalctl -u protein -f"

# Restart Spring Boot service
ssh dec "sudo systemctl restart protein"

# Check Cloudflare Tunnel status
ssh dec "sudo journalctl -u cloudflared -n 30 --no-pager"

# Direct API health check (bypass proxy)
ssh dec "curl -i -H 'X-Origin-Shared-Secret: <SECRET>' http://127.0.0.1:9001/api/auth/csrf"
```
