# Architecture

## Layered design

```text
JavaFX FXML + CSS
        │
        ▼
Controllers ◄──────── WebSockets (PushClient)
        │
        ▼
Services ───── Domain models/enums
        │
        ▼
DAOs + JDBC transactions
        │
        ▼
MySQL 8+ ◄──────── PushServer (Standalone WebSocket Server)
```

### View layer

Five complete screens are defined in FXML:

- `login.fxml`
- `register.fxml` (includes Tess4J OCR integration for NID scanning)
- `donor_dashboard.fxml`
- `requester_dashboard.fxml`
- `admin_dashboard.fxml`

All screens use one stylesheet, so visual changes remain consistent.

### Controller layer

Controllers bind FXML controls, handle navigation and selection state, call services, and display safe messages. Controllers contain no SQL.
Dashboards use `PushClient` to listen for real-time nudges from the `PushServer`, falling back to background polling if disconnected.

### Service layer

- `AuthService`: login and registration rules
- `EligibilityService`: age, weight, and cooldown rules
- `MatchingService`: compatibility and candidate ranking (includes real Haversine distance calculations and urgency-scaled radius cutoffs)
- `RequestService`: request lifecycle façade (supports multi-donor partial fulfillment)
- `DonorService`: availability and health-profile validation
- `ProfileService`: personal information and password changes
- `NotificationService`: inbox operations
- `AdminService`: protected administrator actions
- `ReviewService`: handles the two-sided donation handshake and reputation scoring
- `LocationService`: distance calculations

### DAO layer

DAOs own prepared SQL, result mapping, transaction boundaries, status locking, and audit writes. Multi-table lifecycle operations use transactions with rollback. Tables with unbounded growth (Users, Requests, Audits) use `PagedResult<T>` for efficient paginated retrieval.

### Domain layer

Models represent users, requests, matches, notifications, donations, charts, demand rows, reviews, and audit/history rows. Enums prevent invalid role/status/blood-group strings inside Java code.

## Main workflows

### Registration and login

1. User selects Donor or Requester.
2. (Optional) User scans NID card; local Tesseract OCR extracts Name and Date of Birth for review.
3. Registration fields are validated.
4. Password is BCrypt-hashed.
5. Requesters are approved immediately; donors wait for administrator approval.
6. Login loads the complete role-specific model into `SessionManager` and connects `PushClient`.
7. `SceneManager` opens the corresponding dashboard.

### Emergency request

1. Requester submits validated request data (including hospital/location and units needed).
2. DAO creates `PENDING` request and initial history row.
3. Matching service loads approved/active/available donors.
4. Eligibility, blood compatibility, and urgency-scaled distance filters are applied.
5. Candidates are ranked and the top N (scaled by units needed) are persisted.
6. Request moves to `MATCHED` when candidates exist.
7. Donors receive in-app notifications and real-time WebSockets nudges.

### Donor response (Multi-Donor Handshake)

1. Donor accepts or declines only a current `NOTIFIED` match.
2. The request row is locked during the transaction.
3. Acceptance moves the specific donor to an `ACCEPTED` handshake state. The request can have multiple independent donors up to the units needed.
4. Declining the last open match (if no other donors exist) changes the request to `DECLINED`.
5. Requester receives a response notification and WebSocket nudge.

### Fulfillment & Review

1. Requester and Donor must *independently* confirm the donation happened.
2. When the required units are confirmed, the request becomes `FULFILLED` (or `PARTIALLY_FULFILLED` while in progress).
3. A verified donation record is inserted.
4. Donor donation count and last-donation date are updated.
5. Donor availability changes to `BUSY` for safe manual review.
6. Cooldown and badge display change automatically.
7. Both parties can submit a 1-5 star review of each other, feeding back into the matching reputation score.

### Administration

1. Administrator searches users or requests using paginated tables.
2. Account and request actions pass through protected service methods.
3. Analytics are calculated from live SQL aggregates (e.g., Geographic demand).
4. Every important action appears in the audit log.

## Security and integrity

- BCrypt hashes; no plaintext passwords in the database
- Environment-based credentials
- Prepared statements
- Role and ownership checks
- Account approval/active checks
- Transactional lifecycle transitions
- Foreign keys, unique constraints, checks, and indexes
- Masked password reset dialog
- Protected administrator accounts in the user-management screen
- Self-service actions protected by `AuthorizationService` depth checks
