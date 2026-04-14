# CMDAlias

[![GitHub Release](https://img.shields.io/github/v/release/SwordfishBE/CMDAlias?display_name=release&logo=github)](https://github.com/SwordfishBE/CMDAlias/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/SwordfishBE/CMDAlias/total?logo=github)](https://github.com/SwordfishBE/CMDAlias/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/uXcp7SS5?logo=modrinth&logoColor=white&label=Modrinth%20downloads)](https://modrinth.com/project/cmdalias)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1514311?logo=curseforge&logoColor=white&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/cmdalias)

CMDAlias is a server-side Fabric mod for Minecraft that adds configurable command aliases and shortcuts.

It lets you turn long or repetitive commands into short, memorable ones such as:

- `/sun` -> `/weather clear`
- `/bp` -> `/travelbag`
- `/gmsp` -> `/gamemode spectator`

Because aliases are registered back into the server command tree, they support tab completion and behave like normal commands for players who are allowed to use the target command.

---

## ✨ Features

- Server-side only: no client installation required
- Create short aliases for long vanilla or modded commands
- Replace existing aliases without restarting the server
- Delete aliases in-game
- Tab completion updates after aliases are changed
- Aliases are stored in a simple JSON config file

---

## 🔄 Commands

- `/cmdalias add <alias> <target command>`
  Adds a new alias or updates an existing one.
- `/cmdalias del <alias>`
  Deletes an existing alias.
- `/cmdalias list`
  Lists all configured aliases.

These commands require operator permissions.

---

## ❗ How It Works

Aliases are saved in:

`config/cmdalias.json`

When the server starts, CMDAlias loads the configured aliases and injects them into the command dispatcher. When you add or remove an alias in-game, the config is updated immediately and the command tree is resynced to online players.

CMDAlias also prevents alias loops such as:

- `/a -> /b`
- `/b -> /a`

---

## ⚙️ Configuration

Default config:

```json
{
  "aliases": {}
}
```

Example config:

```json
{
  "aliases": {
    "sun": "/weather clear",
    "bp": "/travelbag",
    "spawn": "/tp 0 64 0"
  }
}
```

Notes:

- Alias names are normalized to lowercase.
- Alias names may only contain `a-z`, `0-9`, `_` and `-`.
- Target commands may be entered with or without a leading `/`.
- Additional arguments typed after an alias are passed through to the target command when possible.

Examples:

- `/msgs Steve Hello there` with alias `msgs -> /msg`
- `/day` with alias `day -> /time set day`

---

## 📦 Installation

| Platform | Link |
|----------|------|
| GitHub | [Releases](https://github.com/SwordfishBE/CMDAlias/releases) |
| Modrinth | [CMDAlias](https://modrinth.com/project/cmdalias) |
| CurseForge | [CMDAlias](https://www.curseforge.com/minecraft/mc-mods/cmdalias) |

1. Download the latest version for your Minecraft version.
2. Place the JAR in your server's `mods/` folder.
3. Make sure [Fabric API](https://modrinth.com/mod/fabric-api) is installed.
4. Start the server once to generate the config file.

---

## 🧾 Example Use Cases

- Give staff shortcuts for common moderation commands
- Create short teleports or utility aliases for private servers
- Simplify long commands added by other mods or datapacks
- Standardize frequently used admin commands across a server team

---

## 🧱 Building From Source

```bash
git clone https://github.com/SwordfishBE/CMDAlias.git
cd CMDAlias
chmod +x gradlew
./gradlew build
```

Build output:

`build/libs/cmdalias-<version>.jar`

---

## 📄 License

Released under the [AGPL-3.0 License](LICENSE).
