# Verification Report

Date: 2026-09-12

## Passed checks

- Java 21 production-source compilation using `javac --release 21` and local JavaFX API stubs (compiled successfully via `mvn clean compile`)
- Java 21 test-source compilation using local JUnit API stubs
- FXML files checked for `<!-- -- -->` double-hyphen comment bugs that crash `FXMLLoader`.
- Prepared statement parameter indexing (`?`) verified manually for complex conditional and union queries (e.g., matching distance calculations and handshakes).
- Core logic smoke execution:
  - O-negative compatibility
  - AB-positive restriction
  - badge threshold behavior
  - exact 56-day eligibility boundary
  - Bangladeshi phone validation
  - strong-password validation
- XML parsing for `pom.xml` and all FXML files
- FXML controller existence
- Every FXML `fx:id` mapped to its controller source
- Every FXML event handler mapped to a controller method
- Every FXML stylesheet reference resolves
- Java package declarations match filesystem paths
- Runtime and convenience SQL schema copies are identical
- No unfinished implementation markers in active project files

Static checker output:

```text
BloodLink verification PASSED
```

Run the checker again with:

```bash
python scripts/verify-project.py
```

## Known environmental test failures

- `mvn test` currently fails exclusively due to integration tests (e.g., `AdminDAOTest`) attempting to connect to a live MySQL instance at `localhost:3306`, which is not available in the CI/generation environment. The code structure and syntax are verified correct via compilation.
- The OCR functionality (Tess4J) requires the Tesseract binaries to be installed on the host OS. This is documented in `SETUP.md`.
