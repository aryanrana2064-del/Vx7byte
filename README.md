# VX7 Marketing

Offline Android app (Kotlin, Jetpack Compose, Material 3, Room/SQLite) for managing **products, rates, quantities, collections and notes**.
No login, no ads, no internet, no paid services. It is **not** a billing app: there is no revenue, profit, GST or payment tracking.

- Package: `com.vx7.marketing` | Min Android: 8.0 (API 26) | Target/compile: API 35
- APK: built by GitHub Actions and uploaded as the artifact **VX7-Marketing.apk**

## BUILD STATUS (read this first)
This source was written and reviewed **without a compiler or Android SDK** (the authoring environment had no SDK and no internet).
**Nothing has been compiled, run, or tested on a device or emulator, and no APK was produced here.**
The unit tests (`UtilTest`, `BackupTest`) were written but **not executed**.
A second line-by-line source review (imports, Room queries/DAOs, migration 2 -> 3 against the old schema, Compose APIs, tests) found no compile error, but a review is not a compiler.
Changes in this revision: Replace-restore warns when the backup holds no private data (Pvt List would be lost); "Today's activity" rolls over at midnight; extra Compose opt-in in Ui.kt.
The first GitHub Actions run is the first real compile. If it fails, the log step "Show the real error" prints the compiler messages;
paste them to get an exact fix.

## What is inside
| Area | What it does |
|---|---|
| Dashboard | Counts from the database: products, Normal List, Pvt List, collections, product entries, today's activity; 9 quick-action cards; recent activity |
| Drawer (hamburger) | 12 items; toggles open/close, scrim tap and Back close it; highlights current screen; closes after navigating; navigation always rebuilds `Dashboard > screen` so the back stack cannot grow or duplicate; scrolls on small screens |
| Products | Add/edit/delete (confirm), search, category filter, sort, description, unit (13 incl. Custom), optional photo (private app folder), validation, duplicate = error, near-duplicate = warning |
| Normal List | Search + Name / Quantity / Price (Price = Real Rate); add, edit, delete (confirm), details screen |
| Pvt List | Separate table rows (`listType = PVT`), Price = Pvt Rate, same features, private banner |
| Rates | Real Rate change saves the old value as Last Rate; unchanged = Last Rate untouched; Pvt Rate independent; blank handled safely |
| Collections | Title, shop, location, date/time, notes, many product entries (qty, unit, Real Rate, Last Rate info, Pvt Rate), edit, delete, details |
| History | Search by title/product/shop; Today / Last 7 days / This month / one date / custom range |
| Notes | Create, edit, delete (confirm), search, pin/unpin, created + updated time |
| Reports | Activity for any period; unit-wise and product-wise quantity (units never added together); Normal List summary; **Pvt summary on its own screen** |
| Notifications | Real channel, `POST_NOTIFICATIONS` request on Android 13+, denial handled, in-app persistent history that records whether a system notification was really posted, clear with confirmation, tap opens screen |
| PDF / PNG | One renderer: A4 multi-page PDF; one tall PNG with computed height; Save (file picker), Share, Open (FileProvider) |
| Backup | JSON, format version 2 (reads version 1), Merge or Replace, validation, single DB transaction, explicit confirmations, read-back verification after export |
| Settings | Theme, default range, default unit, Pvt-reveal confirmation, notification status, shortcuts, version, clear-all (type DELETE) |

## Privacy rules (how Pvt Rate is protected)
- Pvt Rate is hidden (`••••`) in product details, collection details and the collection form until you tap the eye (optionally with a confirmation, see Settings). It hides again when the app restarts.
- Normal reports, the Normal List export and normal backups never contain a Pvt Rate or a Pvt List row.
- Collection PDF/PNG: Pvt Rate is included only if you tick "Include private rates" **and** confirm the warning.
- Pvt List export and the Pvt summary export always show a confirmation and put a red PRIVATE banner on every page.
- Backup with private data needs the switch **and** a warning confirmation; the file name ends in `-PRIVATE.json` and is flagged `"includesPrivate": true`.

## Design decisions you should know
1. **Normal List and Pvt List are two kinds of rows in one table**, never shared. Editing one cannot touch the other. List entries can be pre-filled from a product but are independent copies afterwards (so a product's Real Rate change does not change Pvt entries, and nothing is overwritten silently).
2. **Rate logic** (`nextRates` in `Util.kt`): `if new == old -> nothing moves, else last = old, real = new`. Clearing a Real Rate keeps the old value as Last Rate. If you type a Last Rate by hand, your value wins.
3. Collection entries store a **snapshot** of rates at save time (like the product-name snapshot), so history never changes later.
4. Restore never keeps database ids from the file; it re-links relationships (product -> list entries, collection -> entries). Merge matches products by name and skips content that already exists. Replace deletes current data only after an explicit confirmation that shows the counts.
5. Database migration 2 -> 3 keeps all existing products, records and notes (adds columns/tables). There is no destructive fallback.

## Build
**Android Studio:** open the folder, let Gradle sync (JDK 17), run `:app:assembleDebug`.
No Gradle wrapper is committed; Android Studio creates one, or run with a local Gradle 8.9.

**GitHub Actions (free):**
1. Create a new empty repository on github.com (private is fine).
2. Unzip this project. Upload the *contents* of the folder (the `app` folder, `build.gradle.kts`, `settings.gradle.kts`, `.github`, ...) to the repository root using "Add file > Upload files" (make sure the hidden `.github` folder is included; or use `git push`).
3. Open the **Actions** tab. The workflow "Build VX7 Marketing APK" starts on every push, or choose it and press **Run workflow**.
4. When it is green, open the run and download the artifact **VX7-Marketing.apk** under *Artifacts* (it is a zip containing `VX7-Marketing.apk`). Unzip it and install on the phone (allow "install unknown apps").
5. If it is red, open the step **Show the real error** and read the lines starting with `e:`. The artifact `build-reports` contains `build.log`.
The workflow uses JDK 17 (Temurin), Gradle 8.9, runs unit tests and `assembleDebug`, uses no secrets and no paid service.

## Manual test list (run these on a phone/emulator after the first build)
1. Hamburger opens, second tap/outside tap/Back closes; each of the 12 items opens its screen and is highlighted.
2. Add products; close the app completely; reopen - products are still there.
3. Real Rate 50 -> edit to 55: Real 55, Last 50. Save again without change: Last stays 50.
4. Normal List shows Real Rate, Pvt List shows Pvt Rate for the same name; edit one, the other is unchanged.
5. New collection with 2 products and different units; history filters; report totals per unit.
6. PDF and PNG export, Save / Share / Open; collection export with and without private rates.
7. Backup (normal and private) -> Clear all data -> Restore -> everything back; try a damaged file.
8. Android 13+: deny the notification permission; save a product; history shows it as *not* delivered.
9. Small screen (e.g. 320dp wide) and font size "largest".

## Known limits
- Not compiled or run yet (see top).
- The unsaved collection form survives rotation but not process death.
- A very long report may be too tall for one PNG on low-memory phones; the app then tells you to use PDF.
- Cancelling the product form after picking a new photo can leave one unused photo file in the app's private folder.
- Notification delivery cannot be tested by unit tests; use item 8 above.
