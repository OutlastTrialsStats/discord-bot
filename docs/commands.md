# Commands

## Setup (`/setup`)

| Command | Description |
|---|---|
| `/setup start` | Setup wizard: create roles for the selected categories (Prestige, Level, Reagent Rig, Invasion Ranking, Total Invasion Matches, Season Invasion Points, Platform, Account Type, Connected Account), then optionally turn on auto-nicknames and pick a channel for the community event progress message and alerts. Every step can be skipped |
| `/setup delete` | Delete all bot-managed roles and remove mappings |
| `/setup language` | Change the bot's response language (English, Deutsch) |
| `/setup role-mapping <category>` | Link a role to a category (prestige, level, skill, invasion-ranking, total-invasion-matches, season-invasion-points, platform, account-type, connected-account) |
| `/setup remove-role-mapping <category>` | Remove a role mapping for any category |
| `/setup nickname` | Toggle automatic nickname sync — verified users get their in-game display name as their server nickname |
| `/setup toggle-user-command-visibility <enabled>` | Make `/leaderboard` responses visible to everyone or only to the user who ran it |
| `/setup messages-upload` | Upload a custom `.properties` file to override bot messages |
| `/setup messages-download` | Download all current messages as a `.properties` file |
| `/setup messages-reset` | Reset custom messages to defaults |

`connected-account` maps a role that every member with a linked Outlast Trials Stats account gets.

## Profile

| Command | Description |
|---|---|
| `/sync-profile` | Sync your Discord roles with your Outlast Trials stats |
| `/sync-all` | Scan all server members and assign roles based on their stats |

Roles are also automatically assigned when a verified member joins the server.

## Leaderboard

| Command | Description |
|---|---|
| `/leaderboard <category>` | View a paginated leaderboard for a stat category (ephemeral, with Previous/Next buttons) |
| `/setup leaderboard <channel> <category> <pages>` | Set up an auto-updating leaderboard in a channel (refreshes hourly) |

To remove a leaderboard, simply delete its message in Discord — the bot will automatically clean up the binding.

### Categories

The `category` option uses autocomplete: start typing to filter the list.

| Group | Categories |
|---|---|
| Trials | Completed Trials, Failed Trials, Trials in Hours, Reagents Released, Deaths, Prestige, Stamps, Event Tokens, Extracted Intel |
| Escalation | Escalation Highest Trial, Escalation Best Run (trial and time), Escalation Runs Completed, Escalation Trials Completed, Escalation Time Played |
| Invasion | Invasion Points, Invasion Imposter Wins, Invasion Imposter Losses, Invasion Reagent Wins, Invasion Reagent Losses |
| Minigames | Chess Wins, Chess Rating, Armwrestling Wins, Armwrestling Losses, Armwrestling Rating, Tennis Wins, Tennis Losses, Tennis Rating, Stroop Rating |

## Community Events

| Command | Description |
|---|---|
| `/setup event message <channel> [event]` | Post an auto-updating progress message (phases, progress bar, ETA, rate). Without `event` it always shows the current event and switches automatically when a new one starts |
| `/setup event alerts <channel>` | Set the channel alerts are posted in |
| `/setup event alert <type> [enabled] [role] [remove-role] [lead-time]` | Configure one alert type: turn it on/off, choose the role to ping, and for pre-alerts how early to warn (15 min, 1 h, 3 h) |
| `/setup event status` | Show the progress message, alert channel and every alert type's settings |
| `/setup event remove <message\|alerts\|all>` | Remove the progress message and/or turn off alerts |

Alert types: **Phase reached** (a phase was reached) and **Pre-alert** (the next phase is expected within the lead time). Each alert is sent once per phase, also across bot restarts. When alerts are set up during a running event, already reached phases are not announced again.
