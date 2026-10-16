# DamageIndicator

Damage numbers, health bars and holographic names for Spigot/Paper 26.2.
Build with JDK 26. The plugin emits Java 21 bytecode, but Minecraft 26.2
requires Java 25 or newer.

## Build

Set `JAVA_HOME` to your JDK 26 installation. Verify Maven's Java version, then build:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -B -ntp clean verify
```

Deploy `target\damageindicator-2.1.0.jar` while the server is stopped, never
`original-damageindicator-2.1.0.jar`. Adventure is shaded/relocated; the same
packaged JAR serves both platforms.

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

[Source](https://github.com/stealingdapenta/damageindicator) |
[Spigot resource](https://www.spigotmc.org/resources/damage-indicator-healthbar.113612/)

License: MIT.
