# EaglerstonCraft 26.2 

This ZIP lets you run Eaglercraft 26.2 locally. 
The first launch will download the required server files.


## Setup

Open this project in a GitHub codespace.

Then in a new terminal, run:
```sh
npm start
```
Then make ports 8080 and 8081 public.

Open port 8080 in a new tab and start play!


## Connecting to the Server

Once everything starts, open your browser and visit:

**Game client:** `http://localhost:8080`

**Multiplayer server:** `ws://localhost:8081/`

**Paper backend:** `127.0.0.1:25566`

The local multiplayer server should already be added to your server list.

Keep the Terminal or Command Prompt window open while playing. Closing it will stop the server.

If you want friends to connect over the internet, you'll need to host the web client and WebSocket endpoint publicly. Update `publicWebSocketUrl` in `config.json` with your public `wss://` address.

## Configuration

Most server settings can be changed in `config.json`, including:

- Server name
- Game mode
- Maximum players
- Server ports

Survival mode is enabled by default.

## Stopping the Server

To shut everything down, press `Ctrl+C` in the launcher window.

If something isn't working, run:

```sh
npm run doctor
```

This checks your local setup for common problems.

The server files are downloaded into the `runtime/` folder during the first setup. They aren't included in the ZIP.

The Eaglercraft client and world files are already included, so you won't need to download those separately.
