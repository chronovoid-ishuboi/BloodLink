# Test and Verification Plan

## Automated tests

Run:

```bash
mvn clean compile test
```

Included tests cover:

- ABO/Rh compatibility
- universal and restricted donor behavior
- badge thresholds
- age/weight/cooldown eligibility
- Bangladesh phone validation
- email validation
- password strength validation

## Manual acceptance tests

### Authentication & Registration
1. Run database setup.
2. Sign in with each demo role.
3. Test NID OCR registration: Click "Scan NID", select a mock NID image. Ensure extracted name and DOB appear in the review dialog, and pre-fill the form upon confirmation.
4. Test NID OCR fallback: Submit a non-image or corrupted file and confirm graceful error handling without crashing.
5. Suspend a user as admin and confirm login is blocked.
6. Register a donor and confirm login is blocked until approval.

### Donor workflow
1. Log in as a donor.
2. Verify reference hospital selection on profile edit.
3. Wait for a WebSocket real-time nudge to appear when a new match is created.
4. Open a `NOTIFIED` match and accept it, observing the handshake status change to "Waiting on requester".
5. Provide a mutual 1-5 star review after a fulfilled donation.
6. Upload, preview, and save a circular profile photo; verify it renders correctly in the dashboard header.

### Requester workflow
1. Submit a request with multiple units needed (e.g., 3 units) and a specific hospital.
2. Confirm the exact Haversine distance correctly filtered out distant donors based on the selected urgency radius.
3. Accept multiple donor responses until `units_fulfilled` matches `units_needed`.
4. Perform the requester-side donation confirmation.
5. Provide a mutual review of the donor.
6. Check that remaining notified donors are automatically moved to EXPIRED once the final unit is fulfilled.

### Admin workflow
1. Verify charts and summary cards contain seeded data (including geographic demand by district).
2. Browse paginated user, request, and audit log tables using Next/Previous buttons.
3. Approve a pending donor.
4. Reset a password using the masked confirmation dialog.
5. Escalate an open request and close another request.
6. Confirm each action appears in the audit log on page 1.

### Integrity tests
1. Attempt fulfillment before independent acceptance by both parties; it must remain `PARTIALLY_FULFILLED` or fail.
2. Have all notified donors decline; status must become `DECLINED`.
3. Re-run matching; verify it's purely additive and doesn't overwrite existing accepted/declined donors.
4. Shut down the PushServer process and verify the app seamlessly falls back to 10-second polling.
5. Fulfill a multi-donor request completely; confirm distinct donation records exist for each donor, but no duplicates for the same donor-request pair.

## Static verification already performed during generation

- All production Java sources compiled with `javac --release 21`.
- FXML parsing and XML comment formatting strictly verified.
- Prepared statement parameter indexing manually verified for demo data injection.
