# EaglerstonCraft 26.2 

This ZIP lets you run Eaglercraft 26.2 locally. 
The first launch will download the required server files.


## Setup

Open this project in a GitHub codespace.

Then in a new terminal, run:
```sh
npm start
```
Then make ports 8080, 8081, and 8082 public.

Open port 8080 in a new tab and start play!


## Connecting to the Server

Once everything starts, open your browser and visit:

**Game client:** `http://localhost:8080`

**Multiplayer server:** `ws://localhost:8081/`

**Custom swords and TNT pack:** `http://localhost:8082/swords-and-tnt.zip`

**Paper backend:** `127.0.0.1:25566`

The local multiplayer server should already be added to your server list.

Keep the Terminal or Command Prompt window open while playing. Closing it will stop the server.

The client automatically connects to the WebSocket server on port 8081. In GitHub Codespaces, it derives the matching forwarded server hostname from the client page's port-8080 hostname. The resource-pack URL is also derived for Codespaces; set `publicResourcePackUrl` in `config.json` to override it when hosting elsewhere.

## Configuration

Most server settings can be changed in `config.json`, including:

- Server name
- Game mode
- Maximum players
- Server ports

Survival mode is enabled by default.

## Server Plugins

The Paper backend includes these plugins, selected for Minecraft 26.2:

- **EconomySystem** — simple `/balance` and `/pay <player> <amount>` commands
- **Server Homes** — `/sethome`, `/delhome`, and `/homes`
- **EasyTPA** — teleport requests with `/tpa`, `/tpaccept`, and `/tpdeny`
- **CustomSwords** — custom swords via `/sword`
- **CustomTNT** — custom TNT via `/tnt`; Anti-TNT restores TNT-destroyed blocks within 16 blocks for damage from the last 10 minutes
- **GameplayRewards** — earn $5-$100 in survival; rarer ores and stronger mobs pay more
- **ShopSys** — `/shop [blocks|ores|food|combat|tools]` for 100 purchasable items; `/kit` for Wooden, Stone, Copper, Iron, Golden, Diamond, and Netherite kits. Operators can use `/shopedit <category> add <price>`, `/shopedit <category> remove <index>`, `/shopedit <category> list`, and the corresponding `/kitedit <kit> add|remove|list` commands. Kits have a 24-hour cooldown by default (`kitCooldownHours` in ShopSys' config).
- **UsernameEffects** — formats player names in chat, the player list, and overhead nametags
- **SpawnMobs** — `/multisummon` for spawning multiple entities
- **WorldEdit** and **WorldGuard** — official EngineHub building and region-protection tools

The normal setup installs these automatically. The bundled plugin JARs are stored in `runtime/backend/plugins`. To install or refresh only the Paper plugins without changing server configuration, run:

```sh
npm run setup -- --plugins-only
```

## Server Backups

While `npm start` is running, use these commands in the same terminal:

```sh
backup [name]
loadbackup [name]
```

`backup [name]` saves the server world and configuration into the `backups/` folder.
Running `loadbackup` with no name shows the backups from newest to oldest; type its number to restore it.
You can also type `loadbackup [name]` or `loadbackup [number]` to restore directly.
The `backups/` folder is at the project root. Drop `.tar.gz` backup files into it; world archives containing `level.dat` and full runtime backups are listed and can be restored the same way.

The standalone commands `npm run backup -- [name]` and `npm run loadbackup -- [name]` are available too.

## Stopping the Server

To shut everything down, press `Ctrl+C` in the launcher window.

If something isn't working, run:

```sh
npm run doctor
```

This checks your local setup for common problems.

The server files are downloaded into the `runtime/` folder during the first setup. They aren't included in the ZIP.

The Eaglercraft client and world files are already included, so you won't need to download those separately.
