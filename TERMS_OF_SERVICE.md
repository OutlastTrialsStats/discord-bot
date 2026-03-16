# Terms of Service

**Last updated: March 16, 2026**

By adding the TOTStats Discord Bot ("the Bot") to your Discord server or using its commands, you agree to these Terms of Service.

## 1. Description

The Bot automatically assigns Discord roles to server members based on their player statistics from [The Outlast Trials](https://outlasttrialsstats.com). It connects Discord accounts to player profiles through the TOTStats platform.

## 2. Data Collection and Storage

The Bot collects and stores the following data:

### Server Data
- Discord Server (Guild) ID
- Language preference
- Member count (updated hourly)
- Custom message configurations
- Role mapping configurations (role IDs, category, thresholds)

### User Data
- Discord User ID (used to fetch player profiles from the TOTStats API)

The Bot does **not** store:
- Usernames, avatars, or other Discord profile information
- Message content
- Voice data
- Player statistics (these are fetched on demand from the TOTStats API and not persisted)

## 3. External Services

The Bot communicates with the TOTStats API (`outlasttrialsstats.com`) to retrieve player profiles linked to Discord accounts. This includes:
- Prestige level, player level
- Active reagent skill
- Invasion ranking and total matches played
- Platform type and account creation type

No Discord data is sent to external services other than the Discord User ID used for profile lookup.

## 4. Data Retention

- Server data is stored as long as the Bot remains in your server.
- When the Bot is removed from a server, all associated server data (role mappings, custom messages, language settings) is automatically deleted.
- Role mappings and custom messages can also be manually deleted at any time using `/setup delete` and `/setup messages-reset`.

## 5. Required Permissions

The Bot requires the following Discord permissions to function:

| Permission | Purpose |
|---|---|
| Manage Roles | Create, assign, and remove roles based on player stats |
| Server Members Intent | Detect new members joining and sync roles |

Administrator permission is required for language and message customization commands.

## 6. Age Requirement

You must be at least 13 years old to use the Bot, in accordance with [Discord's Terms of Service](https://discord.com/terms).

## 7. User Responsibilities

- Server administrators are responsible for configuring the Bot appropriately for their server.
- Users must link their Discord account on [outlasttrialsstats.com/verify](https://outlasttrialsstats.com/verify) for role assignment to work.
- Do not attempt to abuse, exploit, or interfere with the Bot's functionality.

## 8. Self-Hosting

The Bot is open-source and can be self-hosted. If you choose to self-host, you are solely responsible for data handling, compliance, and availability of your instance. These Terms only apply to the official instance operated by the developer.

## 9. Availability

The Bot is provided "as is" with no guarantee of uptime or availability. The developer reserves the right to modify, suspend, or discontinue the Bot at any time without notice.

## 10. Limitation of Liability

The developer is not responsible for:
- Incorrect role assignments due to outdated or inaccurate player data
- Any actions taken by Discord or server administrators
- Data loss or service interruptions

## 11. Changes to These Terms

These Terms may be updated at any time. Continued use of the Bot after changes constitutes acceptance of the updated Terms.

## 12. Contact

For questions or data deletion requests, contact: **me@suprex.dev**
