# Commit Convention

This repository uses [Conventional Commits](https://www.conventionalcommits.org/). Commit messages
on `main` drive the release: [release-please](https://github.com/googleapis/release-please) reads
them to determine the next version and to generate `CHANGELOG.md`.

## Format

```
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
```

```
feat(widget): add opt-in enrollment for the profile widget
fix(leaderboard): avoid Discord 429 rate limits on scheduled message edits
feat(command)!: rename /verify to /link
```

Common scopes in this project: `command`, `leaderboard`, `widget`, `role`, `nickname`, `api`,
`db`, `docker`.

## Types

| Type       | Version bump | Changelog                |
| ---------- | ------------ | ------------------------ |
| `feat`     | minor        | Features                 |
| `fix`      | patch        | Bug Fixes                |
| `perf`     | patch        | Performance Improvements |
| `refactor` | patch        | Code Refactoring         |
| `chore`    | patch        | Miscellaneous Chores     |
| `deps`     | patch        | Dependencies             |
| `docs`     | patch        | Documentation            |
| `ci`       | patch        | Continuous Integration   |
| `style`    | none         | hidden                   |
| `test`     | none         | hidden                   |
| `build`    | none         | hidden                   |
| `revert`   | none         | hidden                   |

Release-please opens (or updates) the release PR as soon as at least one commit since the last
tag lands in a non-hidden changelog section. Hidden types (`style`, `test`, `build`, `revert`)
are valid but neither appear in the changelog nor trigger a release on their own — they ride
along with the next one. Triggering only means the release PR exists; nothing is released until
a maintainer merges it.

A `!` before the colon or a `BREAKING CHANGE:` footer bumps the **major** version, regardless of
type.

## Dependabot

Dependabot uses these types automatically: `deps` for runtime dependencies and the Docker base
image (they are part of the shipped artifact, so they warrant a release), `chore` for dev- and
test-only dependencies such as build plugins, and `ci` for GitHub Actions (they only affect
tooling and workflows).

## Pull requests

Commits on a feature branch do not have to follow the convention — only what lands on `main`
does. Pull requests are merged with **squash**, so the PR title becomes the commit message on
`main`; give the PR a conventional title, e.g. `fix(leaderboard): keep embeds under the field
limit`. Commits that do not follow the convention are simply ignored by release-please and will
not show up in the changelog.

## Release flow (maintainers)

Every push to `main` runs [`release.yml`](../.github/workflows/release.yml), which maintains a
**release PR** collecting all releasable commits since the last tag. Merging that PR is the
release: it bumps the version in `pom.xml`, updates `CHANGELOG.md`, tags `vX.Y.Z`, creates the
GitHub release and pushes the Docker image to `ghcr.io/outlasttrialsstats/discord-bot` under both
`X.Y.Z` and `latest`. Nothing goes live before a maintainer merges the release PR.
