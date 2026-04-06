# CmdAlias

Server-side Fabric mod that adds configurable command aliases such as `/sun -> /weather clear` and `/bp -> /enderchest`.

## Commands

- `/cmdalias add <alias> <target command>`
- `/cmdalias del <alias>`
- `/cmdalias list`

Aliases are stored in `config/cmdalias.json` and are synced back into the command tree so tab-completion updates without restarting the server.
