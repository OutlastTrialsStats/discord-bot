# Commands

## For everyone

| Command | Description |
|---|---|
| `/sync-profile` | Sync your Discord roles with your Outlast Trials stats. If your Discord account is not linked yet, the bot explains how to link it and shows a button to [Outlast Trials Stats](https://outlasttrialsstats.com/link) |
| `/leaderboard <category>` | View a paginated leaderboard for a stat category (with Previous/Next buttons) |
| `/event [event]` | Show the progress of the current community event. Pick another event or counter from the list to see it instead |

Roles are also assigned automatically when a verified member joins the server and from time to time in the background.

Responses to `/leaderboard` and `/event` are only visible to the user who ran the command, unless an admin turns on **Public replies** in the setup panel.

The `category` and `event` options use autocomplete: start typing to filter the list.

## For admins

By default, admin commands need the **Manage Roles** permission. You can allow them for other roles or members under **Server Settings → Integrations → Outlast Trials Stats**. Whoever can run `/setup` can use the whole panel.

| Command | Description |
|---|---|
| `/setup` | Open the setup panel. Everything is configured there with buttons, menus and forms |
| `/sync-all` | Scan all server members and assign roles based on their stats (also available in the panel) |

### Setup panel

`/setup` opens a private panel that shows the current settings of the server at a glance. Every change is applied right away and the panel shows the result at the top.

| Area | What you can do |
|---|---|
| **Roles** | Pick a category to see its roles. **Add role** opens a form: enter the minimum value (Prestige, Level, Total Invasion Matches, Season Invasion Points) or pick the value (Reagent Rig, Invasion Ranking, Platform, Account Type), and optionally choose an existing role. Without a role the bot creates one. Remove roles with the menu below |
| **Community event** | See all progress messages and alert settings. **Post message**: choose the current event (switches automatically when a new event starts) or a specific event or counter, then pick the channel. **Alerts**: choose the alert channel, turn each alert type on or off, pick the role to ping and how early the pre-alert is sent (15 min, 1 h, 3 h). Remove progress messages or turn off alerts |
| **Leaderboards** | See all auto-updating leaderboards (refreshed hourly). **Add leaderboard**: choose a group, a category, the number of pages (1-10) and the channel. Remove leaderboards with the menu |
| **General** | Language (English, Deutsch), auto-nickname (verified members get their in-game name as nickname), public replies for `/leaderboard` and `/event`, sync all members, delete all roles the bot created |
| **Messages** | Download all bot messages as a `.properties` file, upload an edited file, or reset to the defaults |
| **Setup wizard** | Step-by-step setup for new servers: create roles for the selected categories, turn on auto-nicknames and pick a channel for the community event progress message and alerts. Every step can be skipped |

The **Connected Account** role category maps a role that every member with a linked Outlast Trials Stats account gets.

### Community events

A server can have up to 5 progress messages, one per target: the current event, or a specific event or counter such as [Reagent Releases](https://outlasttrialsstats.com/events/characters_released). Posting again for the same target moves that message to the new channel. Deleting a progress message in Discord also removes it.

Alert types: **Phase reached** (a phase was reached) and **Pre-alert** (the next phase is expected within the lead time). Each alert is sent once per phase, also across bot restarts. When alerts are set up during a running event, already reached phases are not announced again.

### Leaderboards

To remove a leaderboard, use the panel or simply delete its message in Discord. The bot cleans up the binding automatically.

| Group | Categories |
|---|---|
| Trials | Completed Trials, Failed Trials, Trials in Hours, Reagents Released, Deaths, Prestige, Stamps, Event Tokens, Extracted Intel |
| Escalation | Escalation Highest Trial, Escalation Best Run (trial and time), Escalation Runs Completed, Escalation Trials Completed, Escalation Time Played |
| Invasion | Invasion Points, Invasion Imposter Wins, Invasion Imposter Losses, Invasion Reagent Wins, Invasion Reagent Losses |
| Minigames | Chess Wins, Chess Rating, Armwrestling Wins, Armwrestling Losses, Armwrestling Rating, Tennis Wins, Tennis Losses, Tennis Rating, Stroop Rating |
