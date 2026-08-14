# Changelog

## [1.3.3](https://github.com/OutlastTrialsStats/discord-bot/compare/v1.3.2...v1.3.3) (2026-08-14)


### Dependencies

* **deps:** bump org.apache.maven:apache-maven from 3.9.12 to 3.9.16 ([1946d84](https://github.com/OutlastTrialsStats/discord-bot/commit/1946d84fc26365e344866a5d6631489fb27e1e15))

## [1.3.2](https://github.com/OutlastTrialsStats/discord-bot/compare/v1.3.1...v1.3.2) (2026-08-08)


### Miscellaneous Chores

* **ci:** add manual workflow to publish branch images to GHCR ([4ef3bdc](https://github.com/OutlastTrialsStats/discord-bot/commit/4ef3bdc5134db31a424c8117b63d9ac2b2f66a48))
* **ci:** add manual workflow to publish branch images to GHCR ([d3adf86](https://github.com/OutlastTrialsStats/discord-bot/commit/d3adf86fdd927b390c3abf01159a387b702a8542))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webflux-ui ([368b12f](https://github.com/OutlastTrialsStats/discord-bot/commit/368b12f10429725e37811f8be109e76336c88190))
* **deps:** bump org.springdoc:springdoc-openapi-starter-webflux-ui from 3.0.3 to 3.1.0 ([58f18b0](https://github.com/OutlastTrialsStats/discord-bot/commit/58f18b09123098f74d9c0ee74070bedd9b45689a))


### Continuous Integration

* bump actions/checkout from 4 to 7 ([08915d1](https://github.com/OutlastTrialsStats/discord-bot/commit/08915d11637370a695bab4c841080c7d42e2d51c))
* bump actions/checkout from 4 to 7 ([a810355](https://github.com/OutlastTrialsStats/discord-bot/commit/a81035503a43e3172fab19f00799bd1294bec051))
* bump actions/setup-java from 4 to 5 ([8285a18](https://github.com/OutlastTrialsStats/discord-bot/commit/8285a18f03d0c72b75ef03d239c4d3d1383339e0))
* bump actions/setup-java from 4 to 5 ([9bdbe50](https://github.com/OutlastTrialsStats/discord-bot/commit/9bdbe50dbc12e473b9d323f984829d695ee13136))
* bump docker/build-push-action from 6 to 7 ([88d419f](https://github.com/OutlastTrialsStats/discord-bot/commit/88d419f988f8eed4e4c4f4b44762f9afc8361458))
* bump docker/build-push-action from 6 to 7 ([bc6642f](https://github.com/OutlastTrialsStats/discord-bot/commit/bc6642f6ffd25ce8aead600be50d485a80379570))
* bump docker/login-action from 3 to 4 ([1a2b624](https://github.com/OutlastTrialsStats/discord-bot/commit/1a2b62426ff54ccec0536a2162b1375f304ca538))
* bump docker/login-action from 3 to 4 ([237b27e](https://github.com/OutlastTrialsStats/discord-bot/commit/237b27e8b8fa3e0052e370c7e5147cae702d283d))
* bump docker/metadata-action from 5 to 6 ([638b705](https://github.com/OutlastTrialsStats/discord-bot/commit/638b705b645f234c7f79956e913c8a35ecf8b2b4))
* bump docker/metadata-action from 5 to 6 ([44f0c59](https://github.com/OutlastTrialsStats/discord-bot/commit/44f0c59e36201504b040d34a05e7704316648ad4))
* bump docker/setup-buildx-action from 3 to 4 ([41256b5](https://github.com/OutlastTrialsStats/discord-bot/commit/41256b57bdb62f15c16526768547e59bfc52d487))
* bump docker/setup-buildx-action from 3 to 4 ([bc571e2](https://github.com/OutlastTrialsStats/discord-bot/commit/bc571e2e9a90612df63f37cc2cdfc5ec224869a3))
* clean up comments in Dependabot config ([790dbd9](https://github.com/OutlastTrialsStats/discord-bot/commit/790dbd9b11bec3df893cdc2071c1201d8516c8ef))
* switch releases to release-please ([18a4752](https://github.com/OutlastTrialsStats/discord-bot/commit/18a475284ecf6e3a3be50ee15b1b6ead54f5c0e0))
* switch releases to release-please ([fcced82](https://github.com/OutlastTrialsStats/discord-bot/commit/fcced821a35b489e74563f62dd49880053c59f9a))

## [1.3.1] - 2026-08-02

- fix(leaderboard): avoid Discord 429 rate limits on scheduled message edits (8ce2402)
- chore(deps-dev): bump org.openapitools:openapi-generator-maven-plugin (9556a35)
- chore(deps): bump org.openapitools:jackson-databind-nullable (7ace560)
- chore(deps): bump org.openapitools:openapi-generator-cli (81ec90d)
- chore(deps): bump net.dv8tion:JDA from 6.4.2 to 6.5.0 (de8fe8a)
- chore(deps): bump org.springframework.boot:spring-boot-starter-parent (b90a692)
- chore(deps-dev): bump org.openapitools:openapi-generator-maven-plugin (806cbd0)
- chore(deps): bump net.dv8tion:JDA from 6.4.1 to 6.4.2 (bcf9da5)
- chore(deps): bump org.openapitools:openapi-generator-cli (861cd42)

## [1.3.0] - 2026-04-26

- chore(deps): bump org.springframework.boot:spring-boot-starter-parent (9b3905c)
- Rename `PublicLeaderboardsCommand` to `ToggleUserCommandVisibility` and update schema (bf33792)
- Update link from `/verify` to `/link` throughout codebase (017df05)
- chore(deps): bump org.springdoc:springdoc-openapi-starter-webflux-ui (c708d5d)
- Rename `LeaderboardVisibilityCommand` to `PublicLeaderboardsCommand` and enhance guild server schema (4c89d5e)
- Add `LeaderboardVisibilityCommand` and connected account role management (efbe1da)

## [1.2.6] - 2026-04-11

- Add `RoleDeleteListener` to handle stale role mappings cleanup (f36bacb)

## [1.2.5] - 2026-04-11

- Improve logging and error handling in `RoleAssignmentService` (84b32b9)

## [1.2.4] - 2026-04-11

- Add error handling to syncMember in VerificationPollScheduler (d9a9d09)
- Refine sync result tracking and messaging (9b379d3)
- Validate channel type for leaderboard setup command (19334c2)
- Improve role assignment logging and error handling (1f3017e)

## [1.2.3] - 2026-04-11

- Add new fields to Discord schema and profile mapping (3e8c503)
- Refactor `updateNicknameIfEnabled` to avoid redundant `displayName` extraction (3b45c80)
- Add logging for role assignment success and failure (b1ae43f)
- Adding Permission Error Handling (294345f)
- Fix Command Handling (653261e)

## [1.2.2] - 2026-04-10

- Prevent duplicate processing of verifications in `VerificationPollScheduler` with ID-based tracking (5f00909)

## [1.2.1] - 2026-04-10

- Add JavaTimeModule to WebClient's ObjectMapper configuration (bf9d1b1)
- Update verification link in message properties to include `/verify` endpoint (e961079)
- Update README to include leaderboard and nickname sync features (9ad471f)

## [1.2.0] - 2026-04-10

- Update README and tests to include auto-nickname feature (bd6fc5b)
- Refactor RoleAssignmentService to use method reference for auto-nickname check (1d0d8d4)
- Add NicknameCommand with auto-nickname support and integration (381f44b)
- Add verification poll and bulk profile syncing functionality (fad485d)
- Update README: enhance invite badge style and add direct invite link (cc17083)
- Add invite link badge to README.md (7d924fb)
- chore(deps): bump net.dv8tion:JDA from 6.3.2 to 6.4.1 (084d96f)

## [1.1.0] - 2026-04-02

- Add Liquibase integration with initial database schema and configuration (b159bca)
- Refactor tests for leaderboard updates and threshold validations, adding Season Invasion Points and improving mock configuration (9550d72)
- Add support for Season Invasion Points roles and leaderboard updates (29d347b)

## [1.0.3] - 2026-03-27

- Bump org.openapitools:jackson-databind-nullable from 0.2.9 to 0.2.10 (#23) (a5734b0)
- Bump org.openapitools:openapi-generator-maven-plugin (#24) (0f0719b)
- Bump org.springframework.boot:spring-boot-starter-parent (#25) (95f901f)
- Bump org.openapitools:openapi-generator-cli from 7.20.0 to 7.21.0 (#26) (46f2518)

## [1.0.2] - 2026-03-21

- Bugfixes (#22) (8a1ad8e)

## [1.0.1] - 2026-03-21

- Remove incomplete leaderboard categories (#21) (771f2cb)
- Simplify member count retrieval in MemberCountScheduler (#20) (f39590e)

## [1.0.0] - 2026-03-21

- Bump org.springdoc:springdoc-openapi-starter-webflux-ui (#6) (11f541d)
- Bump org.springframework.boot:spring-boot-starter-parent (#17) (82a8f18)
- Bump org.testcontainers:junit-jupiter from 1.21.1 to 1.21.4 (#18) (40f028e)
- Bump org.testcontainers:postgresql from 1.21.1 to 1.21.4 (#19) (d4c03d1)
- Migrate from JDAC to native JDA interactions and refactor command handling system (#16) (dae5e36)
- Add Leaderboard (#15) (4711320)

## [0.1.0] - 2026-03-18

Initial release, including the out-of-order tags `v0.0.2`–`v0.0.4` and `v0.1.1`.

- Update (#14) (2b683f5)
- Adding TERMS_OF_SERVICES.md (#12) (511da45)
- Update Role Color and Fix InputStreamReader (#11) (7810fa7)
- Add Maven cache and improve user setup (#13) (95afbe6)
- Enable manual trigger for verification workflow (f3e0dee)
- Add Maven cache and improve user setup (#10) (f38e080)
- Add LICENSE file with Non-Commercial MIT Clause (#9) (582f12e)
- Set memory limits for Docker services and configure Java max RAM allocation (#8) (ddddce1)
- Containerize build process in Dockerfile (344bb66)
- Update bump-version workflow (fcecbaa)
- Add GitHub Actions workflow for verifying pull requests (7c41782)
- Addind Tests (#7) (e1e2c8d)
- Bump org.openapitools:openapi-generator-cli from 7.17.0 to 7.20.0 (#4) (a56ff92)
- Bump org.openapitools:openapi-generator-maven-plugin (#5) (cb6efe1)
- Bump org.openapitools:jackson-databind-nullable from 0.2.6 to 0.2.9 (#3) (10f4211)
- Setup (#1) (0cee594)
- first commit (eba72d9)
