# Database Design

## Relationship overview

```text
users 1 ─── 0..1 donor_profiles
users 1 ─── * blood_requests (requester_id)
users 1 ─── * request_matches (donor_id)
blood_requests 1 ─── * request_matches
blood_requests 1 ─── * request_status_history
users 1 ─── * notifications
blood_requests 0..1 ─── * notifications
users 1 ─── * donation_history (donor_id)
blood_requests 0..1 ─── 0..1 donation_history
users 0..1 ─── * audit_logs
users 1 ─── * reviews (reviewer_id, reviewed_id)
hospitals 1 ─── * donor_profiles (reference_hospital_id)
hospitals 1 ─── * blood_requests (hospital_id)
```

## Tables

### `users`
Shared identity, authentication, role, approval, active state, and contact information. Email is unique. Supports BLOB storage for profile photos.

### `donor_profiles`
One-to-one donor extension containing blood group, health information, availability, verified donation count, and an optional `reference_hospital_id` for precise distance matching.

### `blood_requests`
Emergency request data including hospital location and units needed. Tracks `units_fulfilled` for partial multi-donor fulfillment. `status` transitions logically based on match states.

### `request_matches`
Ranked request-to-donor candidates. The `(request_id, donor_id)` pair is unique. Contains `requester_confirmed` and `donor_confirmed` boolean flags to handle the two-sided independent donation handshake, allowing partial fulfillment.

### `request_status_history`
Immutable transition history containing from/to state, actor, note, and time.

### `notifications`
Per-user in-app messages with unread state and optional request relation. Backed by real-time WebSocket nudges.

### `donation_history`
Verified donations. `request_id` and `donor_id` combination ensures one request cannot produce duplicate donation credit per donor.

### `audit_logs`
Administrative and security-sensitive application actions. Accessed via paginated queries.

### `hospitals`
Curated list of real hospitals with geographic coordinates (`latitude`, `longitude`) used for precise Haversine distance calculations and location-based matching.

### `reviews`
Tracks 1-5 star ratings and tags submitted between parties of a verified completed donation. Feeds into reputation scores for match ranking.

## Initialization

The canonical source is:

```text
src/main/resources/com/bloodlink/sql/schema.sql
```

A convenient duplicate is included at:

```text
database/schema.sql
```

Run:

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.bloodlink.util.DatabaseSetup
```

The setup utility:

1. Parses the configured MySQL JDBC URL.
2. Creates the database if needed.
3. Executes every schema statement.
4. Upserts demo accounts, hospitals, and donor profiles.
5. Adds representative requests (including partial match states), history, notifications, donations, reviews, and audit records.

It is safe to run repeatedly; demo users are upserted and demo request seeding is guarded.
