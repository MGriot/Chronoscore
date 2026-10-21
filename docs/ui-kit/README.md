# Chronoscore UI Kit

The app's UI follows this kit. `chronoscore-ui-kit.pdf` is the source; the PNGs are its pages exported at 144 dpi.

| Page | Contents |
| --- | --- |
| [01-foundations](01-foundations.png) | Neutral palettes, seat accents, type scale, shape radii, spacing, and icons |
| [02-controls](02-controls.png) | Center hub, seat header, counter chip, pills, segmented control, stepper, text field, swatches, and preset chips |
| [03-seat-viewport](03-seat-viewport.png) | A seat in its default, highlighted (first player), and flipped states |
| [04-dialogs](04-dialogs.png) | The keypad, table utilities, and match log dialogs |
| [05-menu-and-all-seats](05-menu-and-all-seats.png) | The menu, Edit all seats, and Counters on every seat dialogs |
| [06-table-neutral](06-table-neutral.png) | A 4-seat phone table in the Neutral seat style |
| [07-table-colored](07-table-colored.png) | A 4-seat phone table in the Colored seat style |

The kit's tokens are implemented in these files:

- `composeApp/src/commonMain/kotlin/io/chronoscore/ui/theme/Theme.kt`: colors, type, and shapes.
- `ui/theme/Icons.kt`: the 1.75-stroke icons.
- `ui/dialogs/DialogKit.kt`: the controls.

The bundled fonts are Inter and Inter Tight, under the SIL OFL 1.1 license; see `docs/licenses/Inter-OFL.txt`.
