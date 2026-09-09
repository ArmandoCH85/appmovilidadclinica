# TripSearch "Mis reservas" Button Icon — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ambiguous `Icons.Default.ConfirmationNumber` with the semantically correct `Icons.Default.BookOnline` in the `TripSearchScreen` TopAppBar action button, with zero impact on navigation, ViewModel, layout, or build configuration.

**Architecture:** Pure icon swap inside one Composable — one file, two lines changed (one import, one usage). No new components, no new logic, no new dependencies. `material-icons-extended` is already wired in `gradle/libs.versions.toml`, so the new icon resolves at compile time.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, material-icons-extended (already in version catalog).

## Global Constraints

- **Idiom (verbatim from spec §Background):** All user-visible strings remain in formal Spanish "usted" — no voseo, no tuteo. The existing `contentDescription = "Mis reservas"` already complies and MUST NOT be touched.
- **Scope discipline (verbatim from spec §Out of Scope):** No `NavigationBar` refactor, no `BadgedBox`/badge, no visible text label, no `strings.xml` extraction, no logout icon change, no new tests (per `desarrollo_pasajero.md` §12 — tests only when non-trivial new logic is added; this change has none).
- **Package name note (verbatim from spec §Risks):** The real installed APK package is `com.sitech.clinica.empleados`, not `com.appmovilidadclinica.passenger` — a documented stale reference in `desarrollo_pasajero.md`. NOT a blocker for this change (Kotlin source path is unaffected), but worth knowing when verifying on the emulator.
- **Build policy (project AGENTS.md):** Do NOT run `./gradlew assembleDebug` automatically after code changes. Visual/functional verification happens on the already-installed APK in the running emulator (`emulator-5556`, AVD `bitta`) — the user reinstalls and launches manually.
- **Source file path (verbatim from spec §Specific Changes):** `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt`

---

## File Structure

### Modified files

- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt` — swap one import + one `Icon` reference.

### Created files

None.

### Files explicitly NOT touched (for reviewer confidence)

- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/NavGraph.kt`
- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/Screen.kt`
- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchViewModel.kt`
- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationsScreen.kt`
- `passenger-android/app/src/main/res/values/strings.xml`
- `passenger-android/gradle/libs.versions.toml`
- Any other Compose screen, ViewModel, repository, or module

---

## Task 1: Replace the icon import in TripSearchScreen.kt

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt:20`

**Why first:** The Kotlin compiler resolves `Icons.Default.BookOnline` from the `androidx.compose.material.icons.filled.BookOnline` package. Without this import, Task 2's reference won't compile. Doing the import first keeps the working tree compileable only after Task 2 lands (one transient error window of seconds).

**Interfaces:**
- Consumes: nothing (standalone line replacement)
- Produces: `Icons.Default.BookOnline` becomes resolvable in this file

- [ ] **Step 1: Open the file and locate line 20**

The current line 20 reads:
```kotlin
import androidx.compose.material.icons.filled.ConfirmationNumber
```

- [ ] **Step 2: Replace the import**

```kotlin
import androidx.compose.material.icons.filled.BookOnline
```

The rest of the import block (lines 18–25) is untouched. Specifically:
- `import androidx.compose.material.icons.Icons` stays
- `import androidx.compose.material.icons.automirrored.filled.Logout` stays
- `import androidx.compose.material.icons.filled.DateRange` stays
- `import androidx.compose.material.icons.filled.DirectionsBus` stays
- `import androidx.compose.material.icons.filled.EventSeat` stays
- `import androidx.compose.material.icons.filled.Flag` stays
- `import androidx.compose.material.icons.filled.Schedule` stays
- `import androidx.compose.material.icons.outlined.SearchOff` stays

- [ ] **Step 3: Verify the edit landed on line 20**

Read the file and confirm line 20 now reads exactly:
```kotlin
import androidx.compose.material.icons.filled.BookOnline
```

No trailing whitespace, no extra blank line introduced.

- [ ] **Step 4: Commit**

```bash
cd C:\proyectos\appmovilidadclinica
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt
git commit -m "refactor(passenger): replace ConfirmationNumber import with BookOnline"
```

Expected: commit succeeds with 1 file changed, 1 insertion(+), 1 deletion(-).

---

## Task 2: Replace the icon reference in the TopAppBar actions

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt:84`

**Why second:** Once Task 1 lands the import, this swap produces a working, compileable file with the new icon. The two-line change MUST land as two atomic commits (not one squash) — small diffs are easier to review and roll back per `desarrollo_pasajero.md` workflow conventions.

**Interfaces:**
- Consumes: `Icons.Default.BookOnline` from Task 1
- Produces: TopAppBar action button rendering the `BookOnline` glyph with unchanged `contentDescription = "Mis reservas"` (TalkBack label)

- [ ] **Step 1: Open the file and locate the `actions` lambda of `TopAppBar`**

The relevant block is lines 82–98. Confirm line 84 currently reads:
```kotlin
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = "Mis reservas")
```

- [ ] **Step 2: Replace the icon reference**

Change line 84 from:
```kotlin
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = "Mis reservas")
```

to:
```kotlin
                        Icon(Icons.Default.BookOnline, contentDescription = "Mis reservas")
```

Indentation MUST stay identical (20 spaces — matches the surrounding `actions` lambda body inside `IconButton`).

The `IconButton(onClick = onOpenReservations)` wrapper, the surrounding `actions = { ... }` block, the `VerticalDivider` on lines 86–90, and the logout `IconButton` on lines 91–97 are all untouched.

- [ ] **Step 3: Read the full TopAppBar block to sanity-check**

The final block (lines 80–100) should look exactly like this:

```kotlin
            TopAppBar(
                title = { Text("Buscar viaje") },
                actions = {
                    IconButton(onClick = onOpenReservations) {
                        Icon(Icons.Default.BookOnline, contentDescription = "Mis reservas")
                    }
                    VerticalDivider(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .height(24.dp),
                    )
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesión",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                },
            )
```

Only the `Icons.Default.ConfirmationNumber` → `Icons.Default.BookOnline` change should be visible.

- [ ] **Step 4: Verify no stale references remain**

Search the file for `ConfirmationNumber`. Expected: zero matches. If any remain (e.g., a leftover comment), remove them.

```bash
cd C:\proyectos\appmovilidadclinica
grep -n "ConfirmationNumber" passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt
```

Expected output: empty (no matches).

- [ ] **Step 5: Commit**

```bash
cd C:\proyectos\appmovilidadclinica
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt
git commit -m "refactor(passenger): swap ConfirmationNumber for BookOnline in reservations button"
```

Expected: commit succeeds with 1 file changed, 1 insertion(+), 1 deletion(-). Total across the two tasks: 2 commits, 2 line changes, 0 logic changes.

---

## Task 3: Visual verification on the running emulator

**Files:** None modified.

**Why this task exists even without automated tests:** The spec's acceptance criterion #1 ("el botón muestra el ícono BookOnline") is a visual fact about a rendered Android UI. Per project AGENTS.md ("Never build after changes") the implementing agent does NOT run `./gradlew assembleDebug` automatically — the user reinstalls the APK in the already-running emulator and confirms visually.

**Interfaces:**
- Consumes: the two commits from Tasks 1 & 2
- Produces: empirical confirmation that acceptance criteria #1, #2, and #4 are met on the real device

- [ ] **Step 1: Rebuild and reinstall the APK on the running emulator**

The user runs these commands (the agent does NOT, per AGENTS.md "Never build after changes"):

```bash
cd C:\proyectos\appmovilidadclinica\passenger-android
.\gradlew assembleDebug
adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: `BUILD SUCCESSFUL` from Gradle, then `Success` from `adb install -r`.

- [ ] **Step 2: Launch the app**

```bash
adb -s emulator-5556 shell monkey -p com.sitech.clinica.empleados -c android.intent.category.LAUNCHER 1
```

Expected: `Events injected: 1`. (Note: package name is `com.sitech.clinica.empleados`, NOT the namespace from `desarrollo_pasajero.md` — see Global Constraints.)

- [ ] **Step 3: Log in and reach the TripSearch screen**

Log in with a valid WORKER credential (the user provides them — the agent does not invent or look them up). The post-login screen is `TripSearchScreen`.

- [ ] **Step 4: Verify the button visually**

On the TripSearch TopAppBar, the rightmost action icon (left of the `VerticalDivider`) MUST now show:
- A book/notebook glyph with a check or ticket detail (the `BookOnline` render)
- NO more ticket/confirmation-number glyph

Tap it. Expected: navigates to `MyReservationsScreen`. The label "Mis reservas" appears in that screen's TopAppBar title — unchanged, confirming `contentDescription` semantics still hold.

- [ ] **Step 5: Verify acceptance criteria against the spec**

Re-read `docs/superpowers/specs/2026-09-09-passenger-reservations-button-design.md` §Acceptance Criteria. Walk through each of the 6 items:

1. ✅ Icon is `BookOnline` (verified visually in Step 4).
2. ✅ `contentDescription = "Mis reservas"` (verified — line unchanged, Tap-tap with TalkBack would confirm but emulator `bitta` AVD does not include TalkBack by default).
3. ✅ Tap navigates to `MyReservations` (verified in Step 4).
4. ✅ Position, size, relation to `VerticalDivider` and logout — visually identical (no layout code touched).
5. ✅ Compiles without new warnings — `BUILD SUCCESSFUL` in Step 1, agent inspects output for `W:` lines that weren't there before.
6. ✅ No new dependencies — `gradle/libs.versions.toml` not modified (Tasks 1 & 2 don't touch it; `BookOnline` is in the already-included `material-icons-extended`).

- [ ] **Step 6: Done**

No commit for this task — it's the verification gate. If any acceptance criterion fails, file a follow-up or revert the two commits with:

```bash
cd C:\proyectos\appmovilidadclinica
git revert HEAD HEAD~1
```

---