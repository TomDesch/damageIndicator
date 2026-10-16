# DamageIndicator

Damage numbers, health bars and holographic names for Spigot/Paper 26.2.
Build and playtest with JDK 26. Java 21 bytecode does not mean Minecraft 26.2
runs on Java 21; the prepared servers require Java 25 or newer.

## Build

```powershell
$env:JAVA_HOME = 'C:\Users\tomde\.jdks\openjdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -B -ntp clean verify
```

Verify both Java reports say 26. Deploy `target\damageindicator-2.1.0.jar`
while the server is stopped, never `original-damageindicator-2.1.0.jar`.
Adventure is shaded/relocated; the same JAR serves both platforms.

## Behaviour and configuration

Edit `plugins\DamageIndicator\config.yml`, then `/damageindicator:reload`.
Never use the server-wide `/reload`.

| `enable_health_bar` | `enable_hologram_health_bar` | `enable_holographic_custom_names` | Result |
|---|---|---|---|
| false | false | false/true | Neither; dependency warning if names enabled |
| false | true | false | Neither |
| false | true | true | Name holograms only |
| true | false | false/true | Inline bars; name holograms inactive |
| true | true | false | Holographic bars |
| true | true | true | Both holographic displays |

`enable_damage_indicator` is independent. `enable_damage_merge` groups hits by
attacker/victim UUID, resolving projectile shooters; environmental hits stay
separate. Lethal numbers finish their animation.

Always-visible bars activate on damage/healing, not on untouched mobs, and
recover after unload/reload/restart. Temporary bars expire independently of
follow speed. Health updates use committed next-tick HP, including healing/totems.
Players support holographic bars only; inline mode leaves player names untouched.

Inline overlays preserve later name-tag/plugin changes. Legacy records restore
their saved original once. Name holograms replace visible custom names only,
excluding players, armour stands and NPCs; discovery checks 128 candidates/second.

| Setting | Range |
|---|---|
| Bar length / duration | 1-100 symbols / 1-10 seconds |
| Follow interval / jump duration | 1-200 ticks |
| Merge timeout | 0-60 seconds |
| Hologram offsets | -64 to 64 blocks |
| Vertical velocity / gravity | -10 to 10 / 0-10 |

Values must be finite. Invalid values use diagnosed defaults; malformed YAML
preserves the live configuration and file. Canonical keys override legacy keys.
Prefix/suffix limits are 256 characters each, symbols 32; combined serialized bar
text must fit Spigot's 256-character limit or default appearance is used.

Text: `&(R,G,B)` (channels 0-255), `&b` bold, `&s` strikethrough, `&u` underline,
`&i` italic, `&o` obfuscated, `&r` reset. Styled symbols repeat as whole components.

| Command | Permission | Scope |
|---|---|---|
| `/damageindicator:reload` | `damageindicator.reload` | Validated configuration reload |
| `/damageindicator:arearemove` | `damageindicator.arearemove` | Owned holograms and tracking |
| `/damageindicator:arearename` | `damageindicator.arearename` | All nearby custom names, including unrelated entities |

Permissions default to op; cleanup is player-only, within a 50-block-per-axis box.
`arearename` is deliberately destructive. Reload aliases `config`, `load`,
`restart` remain available.

`displayVirtualDamage(victim, attacker, damage, cause)` requires the server thread
and an enabled plugin. It displays already-applied damage without changing HP or
firing native events; do not also display the same native hit.

Damage-modifier reads are isolated compatibility code: Spigot 26.2 has no
equivalent stable post-damage API. See [Bukkit's event contract](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/entity/EntityDamageEvent.html).

## Playtest checklist

Repeat on Paper and Spigot; automated tests/linkage do not certify gameplay.

1. Build above, stop both servers, copy the packaged JAR into both prepared
   `.playtest\paper-26.2\plugins` and `.playtest\spigot-26.2\plugins` directories.
   Compare `Get-FileHash` for the three JARs; hashes must match.
2. Personally read/accept the EULA before setting `eula=true`. Bind each local
   server to `server-ip=127.0.0.1`; leave authentication enabled and RCON disabled.
3. Run `.\.playtest\start-paper.ps1`. Both scripts explicitly select JDK 26.
   Finish and type console `stop` before `.\.playtest\start-spigot.ps1`;
   both use port 25565. Startup must reach plugin enable without exceptions.
4. Verify the actual JVM, then join `127.0.0.1:25565` with a 26.2 client:

```powershell
$serverProcessId = (Get-NetTCPConnection -State Listen -LocalPort 25565 | Select-Object -First 1).OwningProcess
& "$env:JAVA_HOME\bin\jcmd.exe" $serverProcessId VM.version
```

Console `op <player>` grants test access. Temporarily deop and try all three
namespaced commands: expect denial without side effects; then restore op.

Baseline: indicators/bars/merging true; both hologram toggles false; always-visible
false; length 20, duration 5, follow interval 20, merge timeout 1.5, jump 15.
Run every mode-table row with indicators false/true, temporary/always-visible
bars, and merging false/true where applicable. Reload after every edit.

Use one target at a time in a disposable area; check HP between commands:

```mcfunction
/summon minecraft:cow ~ ~ ~3 {Tags:["di_target"],NoAI:1b,PersistenceRequired:1b}
/damage @e[type=minecraft:cow,tag=di_target,limit=1] 4 minecraft:generic
/data get entity @e[type=minecraft:cow,tag=di_target,limit=1] Health
/effect give @e[type=minecraft:cow,tag=di_target,limit=1] minecraft:instant_health 1 0 true
```

Expected: 20 -> 16 -> 20 HP, a 4-damage number, then a full bar without a healing
damage number. Reset with `/data merge entity @e[type=minecraft:cow,tag=di_target,limit=1] {Health:20.0f}`,
wait one second, and paste both lines together in the console:

```text
minecraft:damage @e[type=minecraft:cow,tag=di_target,limit=1] 4 minecraft:mob_attack by <player>
minecraft:damage @e[type=minecraft:cow,tag=di_target,limit=1] 6 minecraft:mob_attack by <player>
```

Expected on both platforms: 14 HP and indicated total 6, not 10. Repeat 4/4:
expect 16 HP and indicated total 4. Zero/fully absorbed hits emit no zero number.

| Check | Action and expected result |
|---|---|
| Merging/sources | Hit 0.6 seconds apart; one total per attacker. Repeat with two players, bows and dispenser arrows. Numbers survive arrow removal; environmental hits stay separate. |
| Death/totem | A 3-HP target hit for 100 displays 3 and finishes falling. Equip a fresh target using `/item replace entity @e[type=minecraft:cow,tag=di_target,limit=1] weapon.offhand with minecraft:totem_of_undying`, then apply lethal damage: survival/healing updates the bar. |
| Names/visibility | With bars inactive, set `CustomName:{text:"Original",color:"#123456",bold:true}` and `CustomNameVisible:1b` using `/data merge entity`; repeat with visibility `0b` and no name. Expiry/reload restores each original state. Name tags applied during an overlay must survive later hits and expiry. |
| Text | Prefix `&(18,52,86)&bHP `, suffix `&r <<`; check RGB/reset/spacing, empty and format-only text. At length 4, alive symbol `&(255,0,0)A&(0,0,255)B` renders `ABABABAB`, not `AAAAB`. |
| Timing | Duration 5 with follow periods 1, 3 and 20 always expires near five seconds; another hit refreshes it. |
| Cleanup | Add an unrelated named armour stand. `arearemove` preserves it and the next hit creates a fresh number. Optional destructive `arearename` clears all nearby names without later restoration. |
| Invalid config | Test typos, NaN, bounds and oversized text: one-time diagnostics/defaults, no task/log flood. An unmatched YAML `[` makes reload fail without overwriting the file or stopping existing displays; fixing it permits reload. |
| Chunk/restart | Activate far from spawn, record coordinates, leave >2,048 blocks and wait for unload. Return: one persistent display, no stale temporary bar. Repeat with features disabled and after graceful restart; untouched mobs remain untouched. |
| Player/world | Portal travel and logout/rejoin leave no stranded displays. Players retain their names in inline mode. |

From console, `minecraft:execute in minecraft:overworld if loaded X Y Z run say STILL_LOADED`
checks recorded target coordinates without loading them. Do not count an unload
test while that message appears.

For a bounded smoke test, summon ten cows with tag `di_smoke`; enable persistent
holographic bars/merging, disable name holograms. Once/second for 30 seconds:

```mcfunction
/execute as @e[type=minecraft:cow,tag=di_smoke,limit=10] run damage @s 0.1 minecraft:mob_attack by <player>
/minecraft:tick query
/execute if entity @e[type=minecraft:armor_stand]
```

Expect about 20 extra stands during combat, ten after numbers expire, and baseline
after disabling bars/reloading. Compare idle tick time/GC; watch sustained >50-ms
ticks, exceptions and repeated warnings. Local profiling: `/minecraft:jfr start`,
then `/minecraft:jfr stop`; do not upload private logs/profiles.

Cancellation, virtual-health integration, cancelled spawns and whole-world unload
need an additional local fixture; leave live checks unverified without one.
Optional explosion/crash recovery tests require explicit user action and a
disposable world/server copy. For crash testing, `save-all flush`, terminate only
that copy's verified Java PID, restart, and check names/orphans twice.

Normally stop through console `stop` and wait for saving before switching servers.
Keep JAR hash, config, exact server/JDK versions, reproduction steps and
`logs\latest.log`; restore normal settings/operator access afterwards.

[Source](https://github.com/stealingdapenta/damageindicator) |
[Spigot resource](https://www.spigotmc.org/resources/damage-indicator-healthbar.113612/)

License: MIT.
