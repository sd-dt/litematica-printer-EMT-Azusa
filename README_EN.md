<div align="center">

<img src="icon.png" width="160" alt="litematica-printer-EMT-Azusa">

# litematica-printer-EMT-Azusa

**A personal fork of [MoMortis' litematica-printer-EMT](https://github.com/MoMortis/litematica-printer-EMT) (EMT+260925)**

Schematic printer · spherical placement / plane-unbounded mining / packet rate limiter / auto tool switch / simple fluid drain / self-made bedrock breaking

Current version: **litematica-printer-EMT-Azusa-dt261001u-26.2** · Game: Minecraft **26.2** + Fabric · License: **AGPL-3.0**

**[中文说明](README.md)** | English

</div>

---

## What is this

This is a **personal fork** of [litematica-printer](https://github.com/aleksilassila/litematica-printer) → the **EMT** branch by [MoMortis](https://github.com/MoMortis/litematica-printer-EMT), based on the official **EMT+260925** build for 26.2, with a set of my own changes ported on top. Everything the upstream already provides is kept.

> Client-side logic and config UI only — **no server-side component**. Please follow the rules of whatever server you play on.

## What this fork adds on top of EMT+260925

### Placement & mining

| Feature | Description |
|---|---|
| **Spherical placement** | Prints from the player outwards, ordered by distance to the player's feet (`Print → Spherical Place`) |
| **Plane-unbounded mining** | A mining selection type that **ignores the schematic selection's horizontal bounds** and works the whole plane between two configurable Y values (`minePlaneMinY` / `minePlaneMaxY`); the work range still applies |
| **Shape switch in the mining tab** | The work-range shape (cube / sphere / octahedron) is available directly in the mining tab |
| **Ice-for-water optimization** | Optional "wait if a normal block is pending" strategy so the printer does not run back and forth |

### Tools & rate limiting

| Feature | Description |
|---|---|
| **Auto tool switch** | Core option. Picks the best tool for the block from the inventory and switches *before* breaking, so breaking progress is never reset |
| **Packet rate limiter** | Watches server response and outgoing packets; lowers the per-tick action cap when the server lags or rate-limits, and slowly raises it again. The **Reset Packet Limit** button clears everything learned about all servers |

### Bedrock breaking (self-made)

The whole bedrock flow is replaced by a self-made implementation (`printer/bedrockUtils/*`: target collection, environment checks, inventory management, break/place, state machine) and decoupled from external BedrockMiner / BlockMiner mods. A dedicated **Bedrock** page was added to the config GUI.

### Fluid draining

| Feature | Description |
|---|---|
| **Simple fluid mode (place-then-break)** | Covers water sources with a non-gravity block, then breaks them one by one. When an adjacent source (infinite water) is detected, the whole body of water is covered first, so it cannot flow back. The replacement block list defaults to **cobblestone**; if the list only contains gravity blocks such as sand, it falls back to the configured list instead of silently doing nothing |
| **Offhand placement fix** | No pointless hand swapping when the required item is already held; offhand building blocks can be placed from the offhand |
| **Sign placement fix** | Standing / hanging signs are placed with the correct look rotation instead of vanilla's "nearest look direction" variant pick |
| **Glass extra-block fix** | In-air clicks are treated as unconfirmed until the world changes or 40 ticks pass, so the same spot is not clicked twice and blocks do not end up in a neighbouring cell |
| **Rail placement** | Waterlogged rails and shape repairs |

### UI & display

| Feature | Description |
|---|---|
| **MiniHUD work status** | Adds a line with printer state / mode / range to MiniHUD (can be turned off on the core page) |
| **Container GUI guard** | Printing and mining are not triggered while a container screen is open |
| **Tabs** | Core / Hotkeys / Print / Mine / **Bedrock** / Fill / Fluid / Special / Go / Danger |

### Shulker boxes & inventory

| Feature | Description |
|---|---|
| **Shulker auto-fetch** | Only fetches the item that is actually needed, and only while the printer's main switch is on; the fetch resets itself if no container screen appears within 3 seconds. **Tools are fetched too**: if no usable tool is in the inventory, a demand for the matching tool class (pickaxe / axe / shovel / hoe) is registered (at most once per second) |
| **Full inventory** | Swaps material out through a hotbar slot that is **not** a shulker box; if that is impossible it shows "inventory full" and backs off for 5 seconds instead of spamming container screens |

> Upstream features such as **anti-starvation (`EatUtils` / `EatMode`)**, the GO pathing improvements, the update checker and `SIMIAO` support are kept as-is and are **not** reimplemented here.

## Installation

1. Minecraft **26.2** + Fabric Loader.
2. Dependencies: [malilib](https://github.com/maruohon/malilib) ≥ 0.29.6, [litematica](https://github.com/maruohon/litematica) ≥ 0.28.8, `fabric-content-registries-v0` (Fabric API module).
3. Drop `litematica-printer-EMT-Azusa-dt261001u-26.2.jar` into `.minecraft/mods/`.
4. **Only one litematica-printer mod at a time** — this fork cannot be installed next to upstream EMT or other branches (they all use the mod id `litematica-printer`).

## Building from source

No Gradle: plain `javac` + `jar` through the scripts in this repository.

```powershell
# the 26.2 fork line
powershell -File scripts/build.ps1          -Line emt-260925
# output: versions\emt-260925\dist\litematica-printer-EMT-Azusa-dt<date><letter>-26.2.jar

# rebuild verification (entry-by-entry, byte-for-byte against the release jar)
powershell -File scripts/verify-rebuild.ps1 -Line emt-260925
```

* Requires **JDK 25** (class file version 69) and the 26.2 compile-time dependencies (Minecraft client jar, malilib, litematica, Fabric Loader/API, Mixin, MixinExtras, …). Put them into `deps/mc-26.2/` — see `deps/README.md`.
* Artifact naming: `dt<YYMMDD><a|b|c…>`, where the letter is the **n-th build of that day**.

## Configuration

Config file: `.minecraft/config/litematica_printer.json` (or edit in the in-game GUI). Useful keys:

| Key | Description |
|---|---|
| `workingSwitch` | Master switch (default hotkey `CAPS_LOCK`) |
| `printerMode` | Mode: print / mine / bedrock / fluid / fill / replace |
| `workingRange` | Work range |
| `sphericalPlace` | Spherical placement |
| `mineSelectionType` | Mining selection type (includes plane-unbounded) |
| `minePlaneMinY` / `minePlaneMaxY` | Y bounds for plane-unbounded mining |
| `autoToolSwitch` | Auto tool switch |
| `autoPacketLimit` / `resetPacketLimit` | Packet rate limiter / reset |
| `fluidSimpleMode` / `fluidReplaceBlockList` | Simple fluid mode / replacement block list |
| `quickShulker` | Shulker box auto-fetch (required for fetching tools and material) |

## Credits & license

* Original mod: [aleksilassila/litematica-printer](https://github.com/aleksilassila/litematica-printer)
* EMT branch and the base of this fork: **[MoMortis](https://github.com/MoMortis/litematica-printer-EMT) — litematica-printer-EMT (EMT+260925)**
* Fork authors: **sd_dt**, **MoMortis**, **deepseekfl4.1**

Licensed under the upstream license: **AGPL-3.0**. You may use, modify and redistribute it, but you **must keep the same license and attribution**, and **you must provide the complete source** when you distribute a modified version.
