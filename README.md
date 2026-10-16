# DamageIndicator

Floating damage numbers, activation-based health bars and optional holographic
custom names for Spigot and Paper.

## Supported target and building

The explicit target is Minecraft 26.2 on Spigot and Paper. Compile and playtest
with JDK 26. The plugin emits Java 21 bytecode, but that does not make Minecraft
26.2 run on Java 21, provide support for older Minecraft APIs, or override the
server's Java requirements. The prepared 26.2 servers require at least Java 25.

Compilation uses the common Spigot API. Adventure and Examination are bundled
and relocated so the same packaged artifact can be used on both platforms.

Windows build:

```powershell
$env:JAVA_HOME = 'C:\Users\tomde\.jdks\openjdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
.\mvnw.cmd -B -ntp clean verify
```

Both Java reports must identify JDK 26. Deploy
`target\damageindicator-2.1.0.jar`, not
`target\original-damageindicator-2.1.0.jar`: the original artifact is unshaded.
Stop the server before replacing its plugin JAR.

## Configuration and commands

Configuration is generated at `plugins\DamageIndicator\config.yml`.
Use the plugin's namespaced reload command:

| Command | Permission | Effect |
|---|---|---|
| `/damageindicator:reload` | `damageindicator.reload` | Validate and reload configuration |
| `/damageindicator:arearemove` | `damageindicator.arearemove` | Remove owned holograms in a 50-block-per-axis box and detach their tracking |
| `/damageindicator:arearename` | `damageindicator.arearename` | Detach displays and clear all nearby custom names, including unrelated entities |

Permissions default to op. Cleanup commands require a player. The reload aliases
`config`, `load` and `restart` still exist, but the namespaced command avoids
collisions. Never use the server-wide `/reload` to test configuration reloads.

`arearename` is deliberately destructive: do not grant its permission to
untrusted players or use it near names you want to keep. It is not an
ownership-scoped alternative to `arearemove`.

Feature switches:

| Health bars | Hologram bars | Holographic names | Result |
|---|---|---|---|
| false | false | false | Neither display |
| false | false | true | Neither; dependency warning |
| false | true | false | Neither |
| false | true | true | Custom-name holograms only |
| true | false | false | Inline health bars |
| true | false | true | Inline bars; custom-name holograms inactive |
| true | true | false | Holographic health bars |
| true | true | true | Holographic bars and custom names |

The columns correspond to `enable_health_bar`, `enable_hologram_health_bar` and
`enable_holographic_custom_names`. `enable_damage_indicator` is independent.

`health_bar_always_visible` keeps a bar after its first damage/heal activation;
it does not create bars on untouched mobs. Activation survives unload, reload
and restart in either display mode. Temporary bars expire after the configured
duration regardless of follow speed. Healing does not create an unactivated
temporary bar. Committed health is read on the next tick, coalescing damage,
healing and resurrection updates.

Players support holographic health bars only. Inline mode leaves player names
untouched because Bukkit custom names cannot replace player nameplates.

Inline overlays restore the latest externally changed name/visibility rather
than overwriting later name tags. Older persisted records lacking an ownership
fingerprint restore their saved original once; new records use the actual name
accepted by the server. Holographic custom names replace only visible custom
names on eligible non-player mobs, excluding armour stands and NPCs. A bounded
scan checks up to 128 loaded candidates per second for newly visible names.
Renames on existing holograms follow the configured follow interval.

Merging is by attacker/victim UUID, resolving entity projectile shooters.
Unattributed environmental damage stays separate. Numbers count HP damage after
defences and the platform's hurt-immunity calculation, capped to remaining HP.
Lethal merged numbers finish their animation instead of disappearing with the
victim. There is one lazy ticker per display subsystem, not one repeating task
per victim or attacker/victim pair.

Configuration defaults and legacy `dryout` to `dry_out` migration are identical
at enable and reload. An explicitly supplied canonical key wins. Invalid values
use diagnosed defaults; malformed YAML does not replace the live configuration
or overwrite the file. Valid reloads clean up old displays before recreating
enabled persistent displays.

| Setting | Accepted range |
|---|---|
| `health_bar_length` | 1-100 |
| `health_bar_display_duration` | 1-10 seconds |
| `hologram_follow_speed` | 1-200 ticks |
| `damage_merge_timeout` | 0-60 seconds |
| `damage_merge_jump_duration` | 1-200 ticks |
| `hologram_position`, `hologram_name_position` | -64 to 64 blocks |
| `hologram_velocity_y` | -10 to 10 blocks per tick |
| `hologram_gravity` | 0-10 blocks per tick squared |

Numbers must be finite. Booleans must be true or false. Prefixes/suffixes are
limited to 256 characters each and symbols to 32; composed health-bar text must
also fit Spigot's 256-character serialized custom-name limit. Oversized composed
bars use the default appearance with a diagnostic, retaining a valid bar length.

Text supports `&(R,G,B)` with channels 0-255 and decorators `&b` (bold), `&s`
(strikethrough), `&u` (underlined), `&i` (italic), `&o` (obfuscated), `&r` (reset).
These are not the usual legacy colour letters. Styled symbols repeat as complete
components; whitespace and RGB colours are preserved.

For custom-health integrations, `DamageIndicator.displayVirtualDamage(victim,
attacker, damage, cause)` displays already-applied damage on the server thread.
It requires an enabled plugin, does not apply damage or manufacture Bukkit
events, and does not supply a separate virtual-health-bar model. Do not invoke
both native and virtual display for the same hit.

## Manual playtesting on both platforms

Automated tests and packaged-class linkage are not proof of live server loading
or gameplay. Repeat the following on Paper and Spigot and record results
separately.

### 1. Prepare and launch

The optional local setup uses:

```text
.playtest\paper-26.2\paper-26.2-121.jar
.playtest\spigot-26.2\spigot-26.2.jar
.playtest\start-paper.ps1
.playtest\start-spigot.ps1
```

These local files are not prerequisites for building the plugin. The supplied
scripts explicitly invoke `C:\Users\tomde\.jdks\openjdk-26.0.2.1\bin\java.exe`.

Build using the commands above, stop both servers, copy only the packaged JAR
to each `plugins` directory, and compare hashes:

```powershell
Copy-Item '.\target\damageindicator-2.1.0.jar' '.\.playtest\paper-26.2\plugins\damageindicator-2.1.0.jar'
Copy-Item '.\target\damageindicator-2.1.0.jar' '.\.playtest\spigot-26.2\plugins\damageindicator-2.1.0.jar'
Get-FileHash '.\target\damageindicator-2.1.0.jar', '.\.playtest\paper-26.2\plugins\damageindicator-2.1.0.jar', '.\.playtest\spigot-26.2\plugins\damageindicator-2.1.0.jar'
```

Expected: three identical hashes.

Read and personally accept the Minecraft EULA before changing the chosen
server's `eula.txt` to `eula=true`. Do not accept it automatically. For local
testing, personally set `server-ip=127.0.0.1` in both `server.properties` files.
Keep authentication enabled and RCON/query disabled.

Both prepared servers use port 25565. Start Paper with
`.\.playtest\start-paper.ps1`, finish testing, type `stop` in its console and wait
for shutdown before starting `.\.playtest\start-spigot.ps1`.

Verify the running JVM from another PowerShell window:

```powershell
$serverProcessId = (Get-NetTCPConnection -State Listen -LocalPort 25565 | Select-Object -First 1).OwningProcess
Get-CimInstance Win32_Process -Filter "ProcessId=$serverProcessId" | Select-Object ProcessId, ExecutablePath, CommandLine
& "$env:JAVA_HOME\bin\jcmd.exe" $serverProcessId VM.version
```

Expected: Java 26, the intended server JAR, Minecraft 26.2 and DamageIndicator
2.1.0 enabled without startup exceptions. An EULA-stop message is not a load test.

### 2. Join and check permissions

Join with a Minecraft Java Edition 26.2 client at `127.0.0.1:25565`. In the server
console run `op <player>` using your actual account name. In-game use
`/damageindicator:reload`.

Temporarily `deop <player>` and try all three namespaced commands. Expected:
permission denial and no removal/reload. Restore op for the remaining tests.

### 3. Exercise modes and feature switches

Start with the following controlled values:

```yaml
enable_damage_indicator: true
enable_health_bar: true
enable_hologram_health_bar: false
enable_holographic_custom_names: false
health_bar_always_visible: false
health_bar_length: 20
health_bar_display_duration: 5
hologram_follow_speed: 20
enable_damage_merge: true
damage_merge_timeout: 1.5
damage_merge_jump_duration: 15
```

Reload after each edit. Exercise every feature-table row with damage indicators
false/true, health bars temporary/always-visible, and damage merging false/true
where applicable. Expected: only selected displays, no duplicate/revived
disabled feature, and no bar on an untouched mob.

### 4. Check damage, healing and immunity windows

In a safe disposable area:

```mcfunction
/summon minecraft:cow ~ ~ ~3 {Tags:["di_audit","di_target"],NoAI:1b,PersistenceRequired:1b}
/data get entity @e[type=minecraft:cow,tag=di_target,limit=1] Health
/damage @e[type=minecraft:cow,tag=di_target,limit=1] 4 minecraft:generic
/effect give @e[type=minecraft:cow,tag=di_target,limit=1] minecraft:instant_health 1 0 true
```

Check between commands. Expected: 20 -> 16 -> 20 HP, a 4-damage number, 16/20
live symbols after the hit, a full bar after healing, and no damage number for
healing. Bar values settle on the next tick.

Restore the cow to 20 HP for setup with `/data merge entity
@e[type=minecraft:cow,tag=di_target,limit=1] {Health:20.0f}`, wait one second, then
paste both lines together into the server console, replacing `<player>`:

```text
minecraft:damage @e[type=minecraft:cow,tag=di_target,limit=1] 4 minecraft:mob_attack by <player>
minecraft:damage @e[type=minecraft:cow,tag=di_target,limit=1] 6 minecraft:mob_attack by <player>
```

Expected on both platforms: 14 HP and total indicated damage 6, not 10. Repeat
with 4 and 4: expected 16 HP and indicated total 4, not 8. Zero or fully absorbed
damage must not produce a floating zero.

### 5. Check merging, sources, death and resurrection

Hit approximately 0.6 seconds apart, within the 1.5-second merge timeout.
Expected: one accumulated number per attacker/victim pair, then a jump-out
after inactivity. A second attacker gets a separate total. With merging off,
expect separate numbers.

Shoot a bow twice as the same player, then as a second player. Expected: shooter
identity determines merging. Also test melee, a fall, fire and attributed
`/damage` commands; colours and HP loss must match the cause.

Place a dispenser facing the cow, load arrows and pulse it once. Expected: the
number survives the arrow's hit/removal. Optional destructive test in a
disposable area: ignite a creeper near the cow with flint and steel. Expected:
nonlethal explosion numbers finish their animation after the creeper disappears.

With a fresh target at 3 HP, apply 100 damage attributed to your player.
Expected: a capped 3-HP number finishes its animation; no surviving bar/follower.

For a fresh target:

```mcfunction
/item replace entity @e[type=minecraft:cow,tag=di_target,limit=1] weapon.offhand with minecraft:totem_of_undying
/damage @e[type=minecraft:cow,tag=di_target,limit=1] 100 minecraft:generic
```

Expected: totem consumption, survival, and a bar reflecting resurrection and
subsequent healing. Repeat with length 100 to make low-health differences clear.
Use an absorption effect to check absorbed hits, then exhaust it and verify HP
damage resumes. A shield test needs a survival player facing the attacker.

Cancellation and virtual-health integration require an additional local fixture:
they cannot be established just by running vanilla `/damage`. A cancelled
native event must produce no display; a virtual call must not alter native HP or
fire another native event. Leave these checks marked unverified without a fixture.

### 6. Check names, visibility, formatting and expiry

```mcfunction
/summon minecraft:cow ~ ~ ~3 {Tags:["di_audit","di_named"],NoAI:1b,PersistenceRequired:1b,CustomName:{text:"Original",color:"#123456",bold:true},CustomNameVisible:1b}
```

Repeat with `CustomNameVisible:0b`, and with no custom name. Expected after
temporary inline expiry: exact original name/formatting/visibility; an unnamed
mob becomes unnamed again.

Apply an anvil-renamed name tag while an inline bar is active. Hit again, wait
for expiry and reload. Expected: the new name survives all three operations.
In custom-name hologram mode rename, clear, and later reassign a visible name.
Expected: text updates without duplicate stands or reload; originally hidden
names remain hidden.

Use prefix `&(18,52,86)&bHP `, suffix `&r <<`, and separately test empty,
format-only and whitespace-only text. Expected: RGB, decorations, reset and
spacing survive without literal formatting codes or exceptions.

At length 4, set the alive symbol to `&(255,0,0)A&(0,0,255)B`.
Expected at full health: `ABABABAB` with alternating colours, not `AAAAB`;
zero dead symbols emit no child text.

Time a temporary holographic bar with duration 5 and follow periods 1, 3 and 20.
Expected: approximately five seconds in every case, refreshed by another hit.

### 7. Check cleanup and reload failure

```mcfunction
/summon minecraft:armor_stand ~2 ~ ~ {Tags:["di_audit","di_sentinel"],CustomName:{text:"Unrelated sentinel"},CustomNameVisible:1b}
```

While numbers and bars are active, run `/damageindicator:arearemove`.
Expected: owned stands disappear, the sentinel survives, and the next hit creates
a fresh number immediately.

Optional, explicit destructive action in this disposable area only: run
`/damageindicator:arearename`. Expected: every nearby custom name, including the
sentinel, is cleared; old scheduled display restoration does not bring it back.

During active combat, temporarily introduce a YAML syntax error, then reload.
Expected: an explicit failure, no overwrite of the malformed file, and the old
configuration/displays remain active. Fix the syntax and reload again; expected:
normal cleanup/recreation and the new settings.

Test typo booleans, NaN, negative/out-of-range settings and oversized text one
at a time. Expected: useful defaults/diagnostics, no per-tick warning flood or
failing tasks. Do not use enormous velocity/OOM probes on a real server.

### 8. Check persistence and world/player lifecycle

Reload with every display active, then disable all features and reload.
Expected: correct restoration and no disabled display recreated by healing.

Work far from spawn, record the target's coordinates, activate a persistent bar
and leave more than the view/simulation distance (2,048 blocks is convenient).
Wait and query from the console:

```text
minecraft:execute in minecraft:overworld if loaded X Y Z run say DI_CHUNK_STILL_LOADED
```

Use the recorded coordinates. Do not count this as an unload test while that
message appears. Return: expected one persistent bar/name hologram, no temporary
bar left behind, and no duplicate native/holographic name. Repeat after disabling
features before returning. Also test a quick return with replacement wrappers.

Change world through a portal, and log out/reconnect with a player holographic
bar active. Expected: no stranded stands, lost names or continuing task errors.
Stop and restart with activated bars: expected activation recovery in both modes,
only when enabled; untouched mobs remain untouched.

Unloading an entire world without restarting requires an additional local
world-management/test fixture; portal travel alone does not test this. Expected:
original names/visibility are restored before saving and owned displays are
detached even if no entity-unload event fires. A fixture cancelling armour-stand
spawns should produce one diagnostic per reload, no repeated background retries,
and no retained display callbacks. Leave these live checks unverified without
the relevant fixture.

### 9. Run a bounded performance smoke test and stop safely

In a disposable area, enable holographic health bars, always-visible bars and
merging; disable holographic custom names. Summon ten cows maximum with tags
`["di_audit","di_smoke"]` using the summon pattern above. For 30 seconds,
approximately once per second:

```mcfunction
/execute as @e[type=minecraft:cow,tag=di_smoke,limit=10] run damage @s 0.1 minecraft:mob_attack by <player>
/minecraft:tick query
/execute if entity @e[type=minecraft:armor_stand]
```

Expected during combat: about ten health stands plus ten merged numbers, not
unbounded growth. After timeout/animation only ten persistent bars remain.
Disable health bars and reload: stand counts return to the recorded baseline.
Watch average/p95 tick times, GC trends, repeated task exceptions and "Can't keep
up" messages. Compare with idle; sustained tick time above the 50-ms tick budget
needs investigation.

Optional local recording: `/minecraft:jfr start`, then `/minecraft:jfr stop`.
Keep profiles and logs local rather than uploading them to external services.

Deliberate crash recovery is optional and requires explicit user action in a
separately copied disposable server/world. Save active displays with
`save-all flush`, terminate only that copy's verified Java PID, then restart the
copy with features enabled and disabled. Expected: names recover, orphaned
stands disappear and recovery remains idempotent across a second restart.
Never reuse a copied script that still targets the original server directory.

Normally always type `stop` in the console and wait for saving/shutdown before
launching the other platform. Keep exact server/JDK versions, JAR hash,
configuration, reproduction commands, screenshots, `logs\latest.log`, crash
reports and local JFR output. Restore your normal configuration and remove
temporary operator access after testing.

## Project

[Source](https://github.com/stealingdapenta/damageindicator) |
[Spigot resource](https://www.spigotmc.org/resources/damage-indicator-healthbar.113612/) |
[Discord](https://discord.gg/Hv4S43qUuG)

License: MIT.
