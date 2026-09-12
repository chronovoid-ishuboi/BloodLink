# Resource Map

## Resource tree

```text
src/main/resources/com/bloodlink/
├── config/
│   └── application.properties
├── css/
│   └── theme.css
├── sql/
│   └── schema.sql
└── view/
    ├── admin_dashboard.fxml
    ├── donor_dashboard.fxml
    ├── login.fxml
    ├── register.fxml
    └── requester_dashboard.fxml
```

## Loading references

### FXML navigation

File: `src/main/java/com/bloodlink/util/SceneManager.java`

```java
FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/bloodlink/view/" + fxml));
```

To add another screen, place it in `src/main/resources/com/bloodlink/view/` and pass its filename to `SceneManager`.

### CSS

Every FXML root uses:

```xml
stylesheets="@../css/theme.css"
```

Keep visual styling in `theme.css`, not inline FXML styles.

### Configuration

File: `src/main/java/com/bloodlink/util/AppConfig.java`

```java
AppConfig.class.getResourceAsStream("/com/bloodlink/config/application.properties")
```

The `${ENV_NAME:default}` syntax is resolved by `AppConfig`.

### SQL

File: `src/main/java/com/bloodlink/util/DatabaseSetup.java`

```java
private static final String SCHEMA_RESOURCE = "/com/bloodlink/sql/schema.sql";
```

## Images, icons, and fonts

No external image or font assets are required at startup. The interface intentionally uses system font fallbacks, preventing missing-font startup failures.

### Custom Brand Vector
Instead of relying on unstable platform emojis (like `🩸`), the application features a custom, hand-authored SVG drop vector path directly embedded in FXML via `SVGPath`.

Example usage in `login.fxml`:
```xml
<SVGPath content="M 12 2 C 12 2 4 10 4 16 C 4 20.4 7.6 24 12 24 C 16.4 24 20 20.4 20 16 C 20 10 12 2 12 2 Z" fill="#e11d48"/>
```
This scales perfectly without artifacting via JavaFX's native transforms.

### Dynamic User Images
User profile photos are not stored in the resource tree. They are stored as byte arrays (`BLOB`) in the database (`users.profile_photo_blob`) and fetched on-demand into an `ImageView` managed by the profile controllers.

### External OCR Data
The Tess4J integration requires Tesseract's `tessdata` (specifically `eng.traineddata`). This is not packaged in the Maven JAR due to size constraints. The OS-level Tesseract installation paths are automatically resolved by Tess4J on Linux, Windows, and macOS.
