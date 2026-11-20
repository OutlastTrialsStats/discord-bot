# Self-Hosting with Docker

The easiest way to run the bot is with Docker.

## 1. Set up `docker-compose.yml`

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

## 2. Start the bot

```bash
docker compose up -d
```

## 3. Invite the bot

Invite the bot to your server using the OAuth2 URL from the Developer Portal with the `bot` and `applications.commands` scopes.
