# Chronoscore user guide

Chronoscore keeps score on one shared screen. Put the device in the middle of the table, or pass it around. Everything stays on the device: there's no account, no sync and no network.

## The table

<p>
  <img src="images/phone-neutral.png" width="280" alt="Four seats, Neutral style">
  <img src="images/phone-colored.png" width="280" alt="Four seats, Colored style">
</p>

Each player has a **seat**, a card showing their name, their score and any counters.

- **The left half subtracts and the right half adds.** Tap a half for ±1. Hold it to repeat, and after about a second it switches to steps of ±5.
- While you count, the **running delta** (for example **−7**) shows next to the name. When you pause, it folds into the match log as one entry, so one undo reverts the whole press.
- **Seats in the top half are rotated 180°** so they face the players sitting across from the device. To turn a seat by hand, use the rotate button (or *Edit seat → Rotate 180°*).
- The header buttons are **#** (keypad), **⟲** (rotate) and **✎** (edit seat). On narrow phone seats only ✎ is shown. The other actions are inside the seat editor.

The layout adapts to how many players there are and to the orientation. On a phone, up to 8 seats fit:

<img src="images/phone-8-players.png" width="280" alt="Eight seats on a phone">

## The center hub

The pill between the two halves of the table holds the shared controls:

| Button | Action |
| --- | --- |
| ↶ Undo | Reverts the last change (or the last batch). It's dimmed when the log is empty. |
| ⚄ Dice | Opens the table utilities |
| ▦ Edit all seats | Changes a score or counter on many seats at once |
| ☰ Log | Opens the match log, organized by round |
| ⚌ Menu (sliders) | Seats, appearance and new games |

## Keypad

<img src="images/dialog-keypad.png" width="320" alt="Keypad">

Open it with **#** on a seat, or by tapping a counter's value.

- **Set** replaces the value, and **+ Add** / **− Sub** change it by the amount you type.
- **±** makes a Set value negative.
- **Only *name* / All seats** chooses the scope: the change applies to this seat only, or to every seat at once.

A keypad opened from a flipped seat is turned to face that player.

## Counters

Each seat holds up to **4 counters** (for example Poison, Commander, Energy, VP, Fouls, Tax, or one you name yourself). To add them to one seat, use **+ COUNTER** or **✎ → Counters**. To add them to the whole table, use **Menu → Counters on every seat**:

<img src="images/dialog-counters-all.png" width="320" alt="Counters on every seat">

Turning a counter on adds it to every seat at 0. A half-filled switch means the counter is only on some seats. Seats that already hold 4 counters are skipped, and their names are listed.

## Edit all seats

<img src="images/dialog-edit-all.png" width="320" alt="Edit all seats">

1. Pick the seats (or **All**).
2. Pick what to change: **Score** or a counter by name.
3. Choose **Set to**, **+ Add** or **− Sub**.

Each seat shows its preview as *old → new*. **Apply** writes one log entry per seat, and a single **Undo** reverts all of them.

## Table utilities

<img src="images/dialog-utilities.png" width="320" alt="Table utilities">

- **Dice:**
  - d4, d6, d8, d10, d12 and d20, or a custom **dX** from 2 to 1000.
  - **Dice per roll** rolls several dice and shows their total.
- **Flip coin:** heads or tails.
- **First player:** picks a random seat. That seat gets a ring and a **FIRST** tag for a few seconds.
- **Turn order:** a random order of all players.

## Match log

<img src="images/dialog-log.png" width="320" alt="Match log">

Every change is listed under its round, with the player, what changed, the change (green for up, red for down) and the resulting value.

- **Next round →** starts a new round.
- **Undo** reverts the most recent entry.

## Saved matches and game history

<img src="images/dialog-library.png" width="320" alt="Saved matches">

Every match is saved automatically and kept until you delete it. Each match has its own players, counters and log. In **Saved matches** (from the menu or the log):

- Matches are **grouped by game** and sorted with the most recently played first.
- **Continue** brings a match back to the table exactly as you left it.
- The 🗑 button deletes a match. You have to tap it a second time to confirm.
- **This match belongs to** files the current match under a game. You can type a name or tap a game you've played before.

## Menu and new games

<img src="images/dialog-menu.png" width="320" alt="Menu">

The menu has three sections:

- **Current game:**
  - Edit all seats and Counters on every seat.
  - Add seat, and Reset scores (tap it twice to confirm).
  - Saved matches.
- **Appearance:**
  - **Seat style:** *Neutral* (dark cards with a color bar) or *Colored* (each card filled with the seat's color).
  - **Theme:** System, Dark or Light.
- **New game:**
  - An optional **game name**, so the match is filed under that game's history.
  - The number of players and a starting score (20, 30, 40 or a custom value).

  The match you leave stays in Saved matches.

## Editing a seat

<img src="images/dialog-editor.png" width="320" alt="Edit seat">

Tap a seat's name or **✎** to rename it, set its score, rotate it, change its color, manage its counters, or remove the seat (tap twice to confirm).

## Desktop

<img src="images/desktop-neutral.png" width="720" alt="Desktop, Neutral style">

The desktop app works the same way. Click the halves to count, and click and hold to repeat. On wide windows the seat headers show the keypad and rotate buttons directly.

## Your data

Data stays only on your device:

| Platform | Where it's stored |
| --- | --- |
| Android | Private app storage. It's excluded from cloud backup and device transfer. |
| Windows | `%APPDATA%\Chronoscore\chronoscore.db` |
| Linux | `~/.local/share/chronoscore/chronoscore.db` |

Uninstalling the Android app deletes its data. On desktop, delete the file to start fresh.
