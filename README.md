# TOTStats Discord Bot

A Discord bot that automatically assigns roles based on player stats from [The Outlast Trials](https://outlasttrialsstats.com). Built with Spring Boot, JDA, and jda-commands.

## Features

### Setup (`/setup`)

| Command | Description |
|---|---|
| `/setup start` | Automatically create all prestige and reagent skill roles |
| `/setup delete` | Delete all bot-managed roles and remove mappings |
| `/setup language` | Change the bot's response language (English, Deutsch) |
| `/setup prestige-role` | Link a role to a prestige threshold (auto-creates if not provided) |
| `/setup remove-prestige-role` | Remove a prestige role mapping |
| `/setup skill-role` | Link a role to a reagent skill (auto-creates if not provided) |
| `/setup remove-skill-role` | Remove a reagent skill role mapping |
| `/setup messages-upload` | Upload a custom `.properties` file to override bot messages |
| `/setup messages-download` | Download all current messages as a `.properties` file |
| `/setup messages-reset` | Reset custom messages to defaults |

### Profile

| Command | Description |
|---|---|
| `/profile-update` | Sync your Discord roles with your Outlast Trials stats |
| `/sync-all` | Scan all server members and assign roles based on their stats |

Roles are also automatically assigned when a verified member joins the server.

## TODO

- [ ] More role types (Count of Invasion Types)
- [ ] Live Leaderboard
- [ ] Announcements
- [ ] Build and deploy process (Registry and main Discord bot)

## Prerequisites

- Java 25+
- Docker (for PostgreSQL)
- A [Discord Bot Token](https://discord.com/developers/applications) with **Server Members Intent** enabled

## Getting Started

### 1. Start the database

```bash
docker compose up -d
```

### 2. Configure environment

Create a `.env` file in the project root:

```properties
DISCORD_BOT_TOKEN=your-bot-token-here
```

### 3. Run the bot

```bash
./mvnw spring-boot:run
```