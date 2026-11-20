# Quest1HomeManager

A home environment manager for a **rooted Oculus Quest 1** (Horizon OS v50).
Version 0.1, not yet tested on a headset.

- **Installed:** every installed home; tap one to apply it. "Use default home"
  and "Random home now". Auto-rotate: off, at boot, or every 1 to 24 hours.
- **Market:** searches `catalog/catalog.json`. Homes with a direct APK are
  downloaded and installed as root; other links open in the browser.
  "Update list" fetches the latest catalog from this repo's `main`.

Needs root (Magisk will ask once). Build: GitHub Actions, artifact
`Quest1HomeManager-N`. Install with `adb install -r Quest1HomeManager.apk`;
every build is signed with the same key in `signing/`, so `-r` works.

## How the Quest picks its home (v50, checked on the headset)

- The current home is the Oculus preference `environment_selected`, with the
  value `apk://PACKAGE/assets/scene.zip`. When it is empty, the headset uses
  `environment_default`, then a built-in fallback (the Dome on Quest 1).
  Source: SystemUX `EnvironmentUtil` / `SettingsEnvironment`.
- Root: `oculuspreferences --getc environment_selected` reads it,
  `--setc environment_selected apk://...` sets it, and `--reset` undoes it.
- Meta's own picker lists sideloaded homes that carry the manifest meta-data
  `com.oculus.environmentVersion` >= 1 and were not installed by the Store.

## Catalog

`tools/build_catalog.py --xlsx Quest_Home_Environments.xlsx` writes
`catalog/catalog.json` from:

1. [Quest-Homes/Home-Environments](https://github.com/Quest-Homes/Home-Environments):
   188 standalone `*_Environment.apk` homes (direct download).
2. Creator GitHub repos with a hand-checked standalone APK.
3. The Quest Homes Discord spreadsheet: every row with a link. Each link has a
   `kind` that tells the app whether it can download it itself (`apk`) or must
   open it in the browser (Drive, Mega, SideQuest, store pages and so on).

Every entry starts as `"status": "unchecked"` until a link checker runs.
Old "ClassicHome / WinterLodge / SpaceStation / CyberCity" variants reuse Meta
package names and lack the environment tag, so the catalog uses the
standalone build where one exists.
