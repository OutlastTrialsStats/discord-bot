# Development

## Prerequisites

- Java 25+
- Docker (for PostgreSQL)
- A [Discord Bot Token](https://discord.com/developers/applications) with **Server Members Intent** enabled

## 1. Start the database

```bash
docker compose up -d
```

## 2. Configure environment

Create a `.env` file in the project root:

```properties
DISCORD_BOT_TOKEN=your-bot-token-here
```

## 3. Run the bot

```bash
./gradlew bootRun
```

## Tests

```bash
./gradlew test
```

The integration tests (`*TestIT`) start PostgreSQL with Testcontainers, so Docker has to be running. Without Docker they fail, while the unit tests still run.

## Commits and releases

Commits on `main` follow [Conventional Commits](https://www.conventionalcommits.org/) — they drive
the version bump and the generated [`CHANGELOG.md`](../CHANGELOG.md). See
[commit-convention.md](commit-convention.md) for the types and the release flow.
