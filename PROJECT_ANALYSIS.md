# Project Analysis and Source Reconciliation

## Reviewed source materials

1. Proposal slide deck: 14 slides covering the problem, differentiation, objectives, core and advanced features, roles, modules, UI concepts, stack, and timeline.
2. Uploaded Maven file: Java 21, JavaFX 21, MySQL connector, jBCrypt, JUnit, Java-WebSocket, Tess4J.
3. INSTRUCTIONS.md: The canonical source of truth containing the feature evolution across all development phases.

## Conflicts and resolutions

| Conflict or gap | Resolution |
|---|---|
| Proposal and uploaded build specify Java 17, while the user explicitly requires Java 21 | Java 21 is the controlling requirement. All source and build configuration use release 21 and JavaFX 21. |
| “Real-time” is requested, but the specified stack is a standalone JavaFX/JDBC desktop application with no Spring server or push service | Built a standalone Java WebSocket `PushServer` that clients connect to. The app falls back to 10-second polling if disconnected, ensuring robust correctness. |
| Location-based matching is mentioned, but no GPS/maps API is specified | Added a curated `hospitals` table. Implemented precise Haversine distance matching and urgency-scaled radius cutoffs without relying on paid external maps APIs. |
| Password reset is requested without email/SMS APIs | Authenticated users change their own passwords; administrators can securely assign a temporary password through a masked confirmation dialog. |
| Memory usage with 1000+ requests | Implemented pagination via `PagedResult<T>` with `LIMIT`/`OFFSET` queries for Admin tables. |
| NID-assisted Registration | Implemented using local OCR (Tess4J/Tesseract) strictly for form-fill assistance; no identity storage to maintain strict medical/eligibility boundaries. |

## Complete feature checklist

### Authentication and authorization
- [x] Donor and requester registration
- [x] Tess4J NID OCR form-fill assistance
- [x] Role-aware login for Donor, Requester, and Admin
- [x] BCrypt password hashing and verification
- [x] Session singleton
- [x] Donor approval gate
- [x] Suspended account protection
- [x] Authenticated password change
- [x] Administrator temporary-password reset

### Donor management
- [x] Blood group, birth date, weight, last donation date
- [x] Availability states and cooldown progress
- [x] Verified donation count and Badge tiers
- [x] Accept/decline response handling
- [x] Reference hospital location
- [x] Profile photos (BLOB stored, fetched on demand)
- [x] Mutual reviews and 5-star reputation scoring

### Requests and matching
- [x] Multi-donor partial fulfillment (e.g. 3 of 5 units)
- [x] Haversine distance calculations and urgency-scaled radius
- [x] Prepared-statement persistence
- [x] District, exact-group, cooldown, reputation, distance, and urgency ranking
- [x] Re-matching after all donors decline
- [x] Donor and requester notifications (Push Server + Polling)

### Lifecycle (Two-Sided Handshake)
- [x] PENDING, MATCHED, ACCEPTED, DECLINED, FULFILLED, PARTIALLY_FULFILLED, CANCELLED, ESCALATED
- [x] Two-sided independent confirmation for fulfillment
- [x] Accepted donor locking and partial matches
- [x] Competing match expiration and cooldown suppression
- [x] Request lifecycle history

### Notifications
- [x] Real-time WebSocket nudges (`PushClient`)
- [x] Configurable automatic polling fallback
- [x] Match, response, fulfillment, and cancellation notifications
- [x] Unread counter and Mark read

### Administrator
- [x] Total-donor, pending, active, and fulfillment cards
- [x] Geographic demand by district and Monthly-request charts
- [x] Sortable/searchable user and request paginated tables
- [x] Approve/suspend/activate/reset
- [x] Audit log viewer with pagination

### UI/UX
- [x] Custom vector-based brand icon replacing emojis
- [x] Shared teal/rose design system
- [x] Consistent top bars, cards, tabs, tables, forms, and dialogs
- [x] Hover, pressed, focus, disabled states and Status chips
- [x] Responsive scroll containers and paginated tables

## Out-of-scope items not supported by the uploaded requirements

- Hospital-side user role
- SMS/email/WhatsApp notifications
- Live GPS tracking (Distance is calculated point-to-point via Haversine instead)
- Blood bank inventory
- Spring Boot or web frontend

These were not silently added because the uploaded proposal explicitly selects a JavaFX/MySQL desktop architecture.
