# OI CAN Tool (Android)

Android port of the core features of [openinverter-can-tool](https://github.com/davefiddes/openinverter-can-tool),
for a CANable / FYSETC UCAN running **slcan** firmware (USB CDC, ad50:60c4) over USB OTG.

## Open it
Android Studio (Koala or newer) -> Open -> select this folder. If Gradle sync complains about the wrapper,
create an empty "Empty Activity (Compose)" project with package `app.oic` and copy `app/src/main`
and `app/build.gradle.kts` into it. Plugin and library versions are pinned to what I knew; let Studio
bump them if it suggests.

## Features (oic equivalents)
| App | oic |
|---|---|
| Device > Scan | `scan` |
| Device > Serial number | `serialno` |
| Device > Load database (device download + cache by checksum, or JSON file) | `cache`, `-d` |
| Params > list, search, read all, edit | `listparams`, `read`, `write`, `dumpall` |
| Live > poll, CSV log | `log` |
| Files > save / load parameters JSON | `save`, `load` |
| Device > Save, Load, Defaults, Reset, Start (mode), Stop | `cmd ...` |
| Device > Error log | `errors` |
| CAN map > list, add, remove, clear | `can list/add/remove/clear` |

## Not included yet
DBC / JSON export and import of CAN maps, firmware upgrade (docs/upgrader-protocol.md),
background logging with the screen off (needs a foreground service).

## Notes
* The adapter is opened in normal mode (`O`), not listen-only, because SDO requires transmitting.
* All protocol constants are copied from `constants.py`, `oi_node.py` and `paramdb.py` in the repo.
* Parameter values are 32-bit fixed point with 5 fractional bits (x32), as in `fpfloat.py`.
