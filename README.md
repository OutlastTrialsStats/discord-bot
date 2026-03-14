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

## Self-Hosting with Docker

The easiest way to run the bot is with Docker.

### 1. Create a Discord Bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application and add a bot
3. Enable **Server Members Intent** under *Bot → Privileged Gateway Intents*
4. Copy the bot token

### 2. Set up `docker-compose.yml`

```yaml
services:
  bot:
    image: ghcr.io/outlasttrialsstats/discord-bot:latest
    restart: always
    environment:
      DISCORD_BOT_TOKEN: "your-bot-token"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/totstats"
      SPRING_DATASOURCE_USERNAME: "totstats"
      SPRING_DATASOURCE_PASSWORD: "change-me"
    depends_on:
      - postgres

  postgres:
    image: postgres:17
    restart: always
    environment:
      POSTGRES_USER: totstats
      POSTGRES_PASSWORD: change-me
      POSTGRES_DB: totstats
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### 3. Start the bot

```bash
docker compose up -d
```

### 4. Invite the bot

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