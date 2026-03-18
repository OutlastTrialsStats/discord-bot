# TOTStats Discord Bot

A Discord bot that automatically assigns roles based on player stats from [The Outlast Trials](https://outlasttrialsstats.com).

## Features

### Setup (`/setup`)

| Command | Description |
|---|---|
| `/setup start` | Automatically create roles for selected categories (Prestige, Level, Reagent Rig, Invasion Ranking, Total Invasion Matches, Platform, Account Type) |
| `/setup delete` | Delete all bot-managed roles and remove mappings |
| `/setup language` | Change the bot's response language (English, Deutsch) |
| `/setup role-mapping prestige` | Link a role to a prestige threshold (auto-creates if not provided) |
| `/setup role-mapping level` | Link a role to a level threshold (auto-creates if not provided) |
| `/setup role-mapping skill` | Link a role to a reagent skill |
| `/setup role-mapping invasion-ranking` | Link a role to an invasion ranking |
| `/setup role-mapping total-invasion-matches` | Link a role to a total invasion matches threshold (auto-creates if not provided) |
| `/setup role-mapping platform` | Link a role to a gaming platform |
| `/setup role-mapping account-type` | Link a role to an account type |
| `/setup remove-role-mapping <category>` | Remove a role mapping for any category |
| `/setup messages-upload` | Upload a custom `.properties` file to override bot messages |
| `/setup messages-download` | Download all current messages as a `.properties` file |
| `/setup messages-reset` | Reset custom messages to defaults |

### Profile

| Command | Description |
|---|---|
| `/sync-profile` | Sync your Discord roles with your Outlast Trials stats |
| `/sync-all` | Scan all server members and assign roles based on their stats |

Roles are also automatically assigned when a verified member joins the server.

## TODO

- [x] More role types
- [ ] Live Leaderboard
- [ ] Announcements

## Self-Hosting with Docker

The easiest way to run the bot is with Docker.

### 1. Set up `docker-compose.yml`

```yaml
services:
  bot:
    container_name: totstats-discord-bot
    image: ghcr.io/outlasttrialsstats/discord-bot:latest
    restart: always
    mem_limit: 1g
    environment:
      DISCORD_BOT_TOKEN: "your-bot-token"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/totstats"
      SPRING_DATASOURCE_USERNAME: "totstats"
      SPRING_DATASOURCE_PASSWORD: "change-me"
    depends_on:
      - postgres

  postgres:
    container_name: totstats-discord-postgres
    image: postgres:17
    restart: always
    mem_limit: 512m
    environment:
      POSTGRES_USER: totstats
      POSTGRES_PASSWORD: change-me
      POSTGRES_DB: totstats
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### 2. Start the bot

```bash
docker compose up -d
```

### 3. Invite the bot

Invite the bot to your server using the OAuth2 URL from the Developer Portal with the `bot` and `applications.commands` scopes.

## Development

### Prerequisites

- Java 25+
- Docker (for PostgreSQL)
- A [Discord Bot Token](https://discord.com/developers/applications) with **Server Members Intent** enabled

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