# SeatCell State Icons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a visual secondary channel (Lock icon for Occupied + reinforced border for Selected) to the SeatCell so seat state is distinguishable without relying solely on color, and reflect the icons in the SeatLegend.

**Architecture:** Pure visual/iconography change inside `SeatSelectionScreen.kt` — one file, ~15 lines added across 3 atomic commits. No new dependencies (Lock and ImageVector resolve from already-included `material-icons-extended`). No logic, navigation, or ViewModel changes. The 3 commits land as: (1) imports, (2) SeatCell body, (3) LegendItem signature + SeatLegend callsites.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, `material-icons-extended` (already in version catalog).

## Global Constraints

These are project-wide requirements — copy verbatim from the spec. Every task's requirements implicitly include this section.

- **Source file path (verbatim from spec §Specific Changes):** `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`
- **No new dependencies**: `Icons.Filled.Lock` and `androidx.compose.ui.graphics.vector.ImageVector` are already available (Lock via `material-icons-extended`; ImageVector is part of Compose UI).
- **Build policy (project AGENTS.md):** Do NOT run `./gradlew assembleDebug` automatically after code changes. Visual verification happens on the already-installed APK in the running emulator (`emulator-5556`, AVD `bitta`) — the user reinstalls and launches manually.
- **Package name note (verbatim from prior spec):** The real installed APK package is `com.sitech.clinica.empleados`, not `com.appmovilidadclinica.passenger` — documented stale reference in `desarrollo_pasajero.md`. Not a blocker for this change (Kotlin source path is unaffected).
- **Stash-and-isolate pattern (mandatory):** The file has uncommitted changes owned by the user (`git status` shows both `SeatSelectionScreen.kt` and `SeatSelectionViewModel.kt` modified). To produce atomic commits with ONLY this feature's changes, every task MUST follow the stash-and-isolate pattern: `git stash push -- <path>` → re-apply ONLY this task's edits on the clean HEAD file → `git diff` to confirm scope is exactly the task's changes → commit → `git stash pop` to restore the user's uncommitted work. This is non-negotiable — committing the user's uncommitted work alongside the feature would be a contract violation.
- **Commit message format:** Conventional commit `<type>(<scope>): <description>`. No AI attribution, no Co-Authored-By line. Use `rtk git` (not raw `git`) — it wraps commit signing automatically.
- **Commit signing:** Always via `rtk git add ... && rtk git commit -m "..."`.

---

## File Structure

### Modified files

- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt` — three separate commits, each isolated to its own logical change (imports / SeatCell body / LegendItem).

### Files explicitly NOT touched

- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt` — the user has uncommitted work here; this plan does not touch it
- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/NavGraph.kt`
- `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/Screen.kt`
- `passenger-android/app/src/main/res/values/strings.xml`
- `passenger-android/gradle/libs.versions.toml`
- Any other screen, ViewModel, repository, or theme file

---

## Task 1: Add Lock and ImageVector imports

**Files:**
- Modify: `passenger-android/app/src/main/java/com/app/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt:25-27` (import block)

**Why first:** Both icons must be in scope before any code that references them. Imports compile in isolation — landing this commit alone produces a file with unused imports (Kotlin compiles with warnings, not errors), giving the reviewer a chance to validate the import list before downstream code that uses them lands.

**Interfaces:**
- Consumes: nothing (the file currently has no `Lock` or `ImageVector` import)
- Produces: `Icons.Filled.Lock` and `androidx.compose.ui.graphics.vector.ImageVector` become resolvable in this file

**Stash-and-isolate preamble** (REQUIRED before any edit in this task):

- [ ] **Step 0: Stash the user's uncommitted changes for `SeatSelectionScreen.kt`**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash push -m "wip: user uncommitted changes (will be restored after Task 1)" -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected: `ok stashed`. The file in the working tree is now identical to its HEAD state (the version committed in `71c7e2e` + any subsequent commits on `feat/user-extra-fields`).

- [ ] **Step 1: Locate the import block**

Open the file. The imports for Material Icons are clustered around lines 24-27:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
```

- [ ] **Step 2: Add the `Lock` import**

Insert one new line alphabetically, immediately after `import androidx.compose.material.icons.filled.Check`:

```kotlin
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock       // ← NEW
import androidx.compose.material.icons.filled.Schedule
```

- [ ] **Step 3: Add the `ImageVector` import**

The file already imports `androidx.compose.ui.graphics.Color` (line 52). Add a sibling import alphabetically:

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector  // ← NEW
```

- [ ] **Step 4: Verify the diff is exactly 2 line additions, 0 modifications, 0 deletions**

Run:

```bash
cd C:\proyectos\appmovilidadclinica
rtk git diff -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected output: a diff with exactly 2 added lines, no other changes. Anything else means the working tree had drift; STOP and re-do from Step 0.

- [ ] **Step 5: Commit**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
rtk git commit -m "feat(passenger): add Lock and ImageVector imports for SeatCell state icons"
```

Expected: `1 file changed, 2 insertions(+), 0 deletions(-)`.

- [ ] **Step 6: Restore the user's uncommitted changes**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash pop
```

Expected: `ok stash pop`. The file returns to its pre-task state (with the user's uncommitted work restored). `git status` shows the file as `M` again.

---

## Task 2: SeatCell — add Lock icon to Occupied + reinforce Selected border

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt:184-227` (the `SeatCell` composable body)

**Why this commit:** Both SeatCell visual changes share the same function and the same atomic-reviewer's gate (a reviewer would reject one but approve the other only if they had separate concerns — but they both modify the same composable's visual output and share `Icons.Filled.*` imports from Task 1). Keeping them together keeps the diff scoped to one logical unit ("SeatCell now distinguishes states by icon + border, not color alone").

**Interfaces:**
- Consumes: `Icons.Filled.Lock` from Task 1's import
- Produces:
  - `SeatCell` with a new branch rendering `Icons.Filled.Lock` (14dp, top-end, padding 2dp, tint `onSurfaceVariant`) when `!seat.isSelectable`
  - `SeatCell` with border thickness changed from 2dp to 3dp in the `selected` branch

**Stash-and-isolate preamble** (REQUIRED before any edit in this task):

- [ ] **Step 0: Stash the user's uncommitted changes for `SeatSelectionScreen.kt`**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash push -m "wip: user uncommitted changes (will be restored after Task 2)" -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected: `ok stashed`.

- [ ] **Step 1: Change the border thickness on the `selected` branch**

Inside the `SeatCell` Box's modifier chain (around line 202), change:

```kotlin
Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
```

to:

```kotlin
Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
```

Only the `2.dp` → `3.dp` change. Nothing else in that line.

- [ ] **Step 2: Add the `Lock` icon branch after the `selected` branch**

Find the closing of the existing `if (selected) { ... }` block (around line 215-225). It currently ends with:

```kotlin
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp),
            )
        }
    }
```

Add a new branch immediately after the closing `}` of the `selected` block, BEFORE the closing `}` of the `Box`:

```kotlin
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp),
            )
        } else if (!seat.isSelectable) {                  // ← NEW
            Icon(                                          // ← NEW
                Icons.Filled.Lock,                         // ← NEW
                contentDescription = null,                 // ← NEW
                tint = MaterialTheme.colorScheme.onSurfaceVariant,  // ← NEW
                modifier = Modifier                       // ← NEW
                    .align(Alignment.TopEnd)              // ← NEW
                    .padding(2.dp)                        // ← NEW
                    .size(14.dp),                         // ← NEW
            )                                              // ← NEW
        }                                                  // ← NEW
    }
```

- [ ] **Step 3: Verify the diff is scoped to this task only**

Run:

```bash
cd C:\proyectos\appmovilidadclinica
rtk git diff -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected diff: only the `2.dp` → `3.dp` change on the border line, plus the new `else if (!seat.isSelectable) { Icon(...) }` block. No import changes (Task 1's imports are already in HEAD). No changes to SeatLegend, LegendItem, or any other composable. Anything beyond that scope means the stash-and-isolate was skipped; STOP and re-do from Step 0.

- [ ] **Step 4: Commit**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
rtk git commit -m "feat(passenger): add Lock icon to occupied seats and reinforce selected border"
```

Expected: `1 file changed, ~12 insertions(+), ~1 deletion(-)`. The deletion is the single `2.dp` → `3.dp` swap counted as one removal + one addition by Git.

- [ ] **Step 5: Restore the user's uncommitted changes**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash pop
```

Expected: `ok stash pop`. The file returns to its pre-task state.

---

## Task 3: LegendItem — add `icon` parameter + update SeatLegend callsites

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt:229-253` (`SeatLegend` and `LegendItem` composables)

**Why this commit:** The `icon` parameter on `LegendItem` is a backward-compatible addition (default `null`), so existing call sites that don't pass it keep working. But to actually USE the new parameter and reflect the new SeatCell states, all three SeatLegend callsites must be updated in the same commit — a reviewer would reject the parameter addition if the callsites didn't use it (dead code), and would reject the callsite updates if the parameter didn't exist yet. Atomic.

**Interfaces:**
- Consumes: `Icons.Filled.Check`, `Icons.Filled.Lock`, and `ImageVector` from Task 1's import
- Produces:
  - `LegendItem(color: Color, label: String, icon: ImageVector? = null)` — new optional `icon` parameter
  - When `icon != null`, renders the icon inside the color swatch (10dp, top-end, padding 1dp, tint `onSurface`)
  - `SeatLegend` callsites updated to pass `null` / `Icons.Filled.Check` / `Icons.Filled.Lock`

**Stash-and-isolate preamble** (REQUIRED before any edit in this task):

- [ ] **Step 0: Stash the user's uncommitted changes for `SeatSelectionScreen.kt`**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash push -m "wip: user uncommitted changes (will be restored after Task 3)" -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected: `ok stashed`.

- [ ] **Step 1: Update `SeatLegend` callsites to pass `icon` arguments**

The current callsites (around lines 235-237) are:

```kotlin
        LegendItem(color = MaterialTheme.colorScheme.primaryContainer, label = "Disponible")
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "Seleccionado")
        LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Ocupado")
```

Change to:

```kotlin
        LegendItem(color = MaterialTheme.colorScheme.primaryContainer, label = "Disponible",  icon = null)
        LegendItem(color = MaterialTheme.colorScheme.primary,         label = "Seleccionado", icon = Icons.Filled.Check)
        LegendItem(color = MaterialTheme.colorScheme.surfaceVariant,  label = "Ocupado",      icon = Icons.Filled.Lock)
```

- [ ] **Step 2: Add the `icon` parameter to `LegendItem`'s signature**

Find the current `LegendItem` declaration (around line 241):

```kotlin
private fun LegendItem(color: Color, label: String) {
```

Change to:

```kotlin
private fun LegendItem(
    color: Color,
    label: String,
    icon: ImageVector? = null,
) {
```

- [ ] **Step 3: Render the optional icon inside the color swatch**

The current `LegendItem` body has:

```kotlin
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        ) {
            // ← no content
        }
```

Change to (add a content lambda body):

```kotlin
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(1.dp)
                        .size(10.dp),
                )
            }
        }
```

Note: the `Box` gains a `contentAlignment = Alignment.TopEnd` so the Icon lands at top-right of the 12dp swatch without needing an `.align()` modifier.

- [ ] **Step 4: Verify the diff is scoped to this task only**

Run:

```bash
cd C:\proyectos\appmovilidadclinica
rtk git diff -- passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
```

Expected diff: only the SeatLegend callsites (3 lines updated), the `LegendItem` signature (changed from one-liner to multi-line with new param), and the `Box` body (gains `contentAlignment` + new `if (icon != null)` block). No changes outside `SeatLegend` and `LegendItem`. Anything beyond that scope means the stash-and-isolate was skipped; STOP and re-do from Step 0.

- [ ] **Step 5: Commit**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
rtk git commit -m "feat(passenger): show state icons in SeatLegend swatches"
```

Expected: `1 file changed, ~14 insertions(+), ~3 deletions(-)`.

- [ ] **Step 6: Restore the user's uncommitted changes**

```bash
cd C:\proyectos\appmovilidadclinica
rtk git stash pop
```

Expected: `ok stash pop`. The file returns to its pre-task state. The working tree now has the user's uncommitted work PLUS the3 new commits on top of HEAD.

---

## Task 4: Visual verification on the running emulator

**Files:** None modified.

**Why this task exists even without automated tests:** The spec's acceptance criteria #1, #2, #3, #4 are visual facts about a rendered Android UI. Per project AGENTS.md ("Never build after changes") the implementing agent does NOT run `./gradlew assembleDebug` automatically — the user reinstalls the APK in the already-running emulator and confirms visually.

**Interfaces:**
- Consumes: the3 commits from Tasks 1, 2, 3
- Produces: empirical confirmation that acceptance criteria #1, #2, #3, #4 are met on the real device

- [ ] **Step 1: Rebuild and reinstall the APK on the running emulator**

The user runs these commands (the agent does NOT, per AGENTS.md):

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

Expected: `Events injected: 1`.

- [ ] **Step 3: Reach the SeatSelection screen**

Log in with a valid WORKER credential, search a trip with multiple seats (the user already has a flow that reaches SeatSelection). The post-search → seat-grid screen is `SeatSelectionScreen`.

- [ ] **Step 4: Verify the visual acceptance criteria**

On the SeatSelection screen:

1. **Available cells** (green light, no icon): the cell shows the number with NO icon in top-right. ✓ unchanged
2. **Occupied cells** (gray, lock icon): each occupied cell shows a small lock icon in top-right corner. ✓ new
3. **Selected cell** (dark green, thicker border): after tapping an available cell, it becomes dark green with the check icon AND a noticeably thicker (3dp vs 2dp) border. ✓ new
4. **Legend swatches** match the cells:
   - "Disponible" swatch: green light, NO icon
   - "Seleccionado" swatch: dark green, mini check icon
   - "Ocupado" swatch: gray, mini lock icon

- [ ] **Step 5: Verify acceptance criteria against the spec**

Re-read `docs/superpowers/specs/2026-09-09-passenger-seatcell-state-icons-design.md` §Acceptance Criteria. Walk through all 7 items:

1. ✅ Occupied cell shows Lock icon in top-end (verified visually in Step 4).
2. ✅ Selected cell has 3dp border (verified visually — compare with screenshots before this change).
3. ✅ Available cell has no icon (verified visually).
4. ✅ Legend swatches show icons (verified visually).
5. ✅ `contentDescription` semantics unchanged (inspected via `adb shell uiautomator dump` and grep for the "Asiento" content-desc).
6. ✅ No new compile warnings — `BUILD SUCCESSFUL` in Step 1, no new `w:` lines in Gradle output.
7. ✅ No new dependencies — `gradle/libs.versions.toml` not modified; `Lock` and `ImageVector` are already available.

- [ ] **Step 6: Done**

No commit for this task — it's the verification gate. If any acceptance criterion fails, file a follow-up or revert the3 commits with:

```bash
cd C:\proyectos\appmovilidadclinica
rtk git revert HEAD HEAD~1 HEAD~2
```

---