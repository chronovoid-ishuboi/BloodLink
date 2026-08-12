# BloodLink — Emergency Blood Response & Donor Coordination Platform

CSE 4402: Visual Programming Lab · Islamic University of Technology
Java 17 + JavaFX 17 + MySQL 8 desktop application.

This repo is a **working skeleton**, not just a folder plan: it compiles into
a running app (Login → Register → role dashboards), with the architecture,
DB schema, and CSS design system already in place. Everything marked `TODO`
in the code is a scoped, single-feature task — pick one, implement it,
commit, repeat.

---

## 1. One-time setup in IntelliJ

1. **Install JDK 17** (Temurin or Oracle). In IntelliJ: `File → Project
   Structure → SDKs → Add SDK` if it isn't already listed.
2. **Open the project**: `File → Open` → select the `BloodLink` folder
   (the one with `pom.xml`). IntelliJ detects it as a Maven project and
   downloads dependencies automatically (JavaFX, MySQL connector, jBCrypt,
   JUnit — all already declared in `pom.xml`).
3. **Install the free Scene Builder** app (not an IntelliJ plugin — a
   separate download from `gluonhq.com/products/scene-builder`). Then in
   IntelliJ: `Settings → Languages & Frameworks → JavaFX` → point "Path to
   SceneBuilder" at it. Now right-clicking any `.fxml` file gives you
   **"Open in SceneBuilder"** for drag-and-drop layout editing.
4. **Install MySQL 8** locally (or use IntelliJ Ultimate's built-in
   Database tool if you have it). Create the schema:
   ```bash
   mysql -u root -p -e "CREATE DATABASE bloodlink_db;"
   mysql -u root -p bloodlink_db < database/schema.sql
   ```
5. **Set your DB credentials** in
   `src/main/java/com/bloodlink/util/DBConnection.java` (URL/user/password
   constants at the top). Don't commit real credentials — the `.gitignore`
   already excludes `.env`, so if you want to be strict, move those three
   constants into a local `.env`/`config.properties` and load them there
   instead.
6. **Run it**: open `Main.java` → right-click → Run. If IntelliJ complains
   about JavaFX modules not being on the module path (common with plain
   "Run"), use the Maven side panel instead: `Plugins → javafx → javafx:run`
   — this uses the `javafx-maven-plugin` already configured in `pom.xml`
   and handles the module path for you. Either way, you should land on the
   Login screen.

---

## 2. How the code is organized

```
BloodLink/
├── pom.xml                        Maven build + JavaFX/MySQL/BCrypt deps
├── database/schema.sql            MySQL schema — run this first
├── src/main/java/com/bloodlink/
│   ├── Main.java                  App entry point → loads login.fxml
│   ├── controller/                One controller per FXML screen
│   ├── model/                     User (abstract) → Donor/Requester/Admin,
│   │                              BloodRequest, Notification, Badge
│   ├── dao/                       SQL lives here ONLY — UserDAO is fully
│   │                              implemented as the pattern to copy
│   ├── service/                   Business logic between controller & DAO
│   │                              (AuthService, EligibilityService done;
│   │                              MatchingService/NotificationService TODO)
│   └── util/                      DBConnection, PasswordUtil, SceneManager
│                                  (screen navigation), SessionManager
│                                  (Singleton — current logged-in user)
└── src/main/resources/com/bloodlink/
    ├── view/*.fxml                One FXML per screen, edit in SceneBuilder
    ├── css/theme.css              The whole visual identity — see §4
    └── images/                    Drop logo.png here
```

**Layering rule (keeps the codebase gradeable and debuggable):**
`Controller → Service → DAO → DBConnection`. A controller never writes SQL;
a DAO never touches JavaFX. `LoginController` → `AuthService` →
`UserDAO` → `DBConnection` is the reference chain — copy its shape for
every new feature.

---

## 3. Week-by-week roadmap (12 weeks, 3-person team)

Mapped from the proposal's timeline (slide 13) onto this codebase's actual
files, so each week has a concrete "open this file" starting point.

| Wk | Focus | Files you'll touch | Suggested owner split |
|----|-------|--------------------|----|
| 1–2 | Planning: finalize ER diagram against `schema.sql`, wireframe remaining screens in Scene Builder, agree on Git branch strategy | `database/schema.sql`, all `view/*.fxml` (review only) | Whole team |
| 3–4 | Auth is **already implemented** — spend this block on `registerRequester()` in `UserDAO` (mirror `registerDonor()`), add a role toggle to `register.fxml`/`RegisterController`, and test the full login round-trip against your local DB | `UserDAO.java`, `register.fxml` | 1 person |
| 5–6 | Donor & Request modules: implement `DonorDAO` (all methods are stubbed with SQL sketches in the class Javadoc), `RequestDAO.create()`/`findByRequester()`, build out `request_form.fxml` fields | `DonorDAO.java`, `RequestDAO.java`, `request_form.fxml` + `RequestFormController.java` | 1 person |
| 7–8 | Matching & Eligibility: `EligibilityService` is **done** — wire `MatchingService.findRankedMatches()` to `DonorDAO.findEligibleMatches()`, render results on `donor_dashboard.fxml`'s "Nearby matching requests" card | `MatchingService.java`, `donor_dashboard.fxml` | 1 person |
| 9–10 | Lifecycle & Notifications: implement `NotificationDAO`, wire status transitions (`RequestDAO.updateStatus`) to also insert a notification row, build the unread-badge counter | `NotificationDAO.java`, `NotificationService.java`, `notification_panel.fxml` | Whole team |
| 11 | Admin dashboard: `BarChart`/`PieChart` + `TableView` — every card in `admin_dashboard.fxml` has a `TODO` naming the exact chart type and SQL aggregate needed | `admin_dashboard.fxml` + `AdminDashboardController.java` | 1 person |
| 12 | Testing, Javadoc, README polish, GitHub cleanup, demo rehearsal | everywhere | Whole team |

Search the codebase for `TODO` (IntelliJ: `Cmd/Ctrl+Shift+F` → search
`TODO`) to get a live, ordered task list at any point in the term.

---

## 4. The UI direction — why it looks the way it does

Most donor-management course projects default to "alarm red + white,"
which reads stressful for something already about a medical emergency.
`theme.css` instead makes a **deep, trustworthy teal** the dominant color
(the color of scrubs, calm competence) and reserves a **muted, desaturated
rose-red** — never a pure `#FF0000` — as an accent used sparingly: the
logo, the primary "Request" call-to-action, urgency chips. Full design
token table is documented at the top of `theme.css`.

Practical rules the whole team should follow so new screens don't drift
from this system:
- **Never hardcode a color in an FXML file's inline `style=`.** Add a class
  to `theme.css` instead (`.btn-primary`, `.chip-success`, `.stat-card`,
  etc.) and reference it via `styleClass`. This is what keeps 14 screens
  built by 3 people looking like one product.
- **Status always renders as a `.chip-*`**, never plain text — a request's
  `PENDING/MATCHED/ACCEPTED/DECLINED/FULFILLED/CANCELLED` state and a
  donor's availability should always be visually scannable at a glance.
- Every dashboard reuses the same sidebar + topbar shell (see
  `donor_dashboard.fxml` for the fullest example) — copy that structure
  rather than inventing a new layout per screen.
- If you want a genuinely distinctive display typeface instead of the
  system-font fallback stack currently in `theme.css`, drop `.ttf` files
  into `src/main/resources/com/bloodlink/fonts/` and add an
  `@font-face { src: url("../fonts/...") }` block at the top of
  `theme.css` — JavaFX's CSS engine supports this directly.

---

## 5. Git workflow (per proposal: "feature branches, PRs, commit history")

```
main            — always demo-able
feature/<name>  — one branch per TODO item, e.g. feature/donor-dao
```
Open a PR into `main` when a `TODO` is fully implemented and the app still
runs end-to-end. Keep commits scoped to one file/feature so the history
itself becomes part of your Javadoc trail for the final submission.
