# NovaDesk — Code. Browse. Build. All in one place.

Premium all-in-one Android AI development workspace: local Debian Linux
(no root), interactive terminal, file manager + code editor, OpenCode and
Codex agent chats, integrated browser with agent tools, MCP store, project
workspaces and deep settings. Built with Kotlin + Jetpack Compose (Material 3).

## Status

v0.1.0 — working foundation. The app compiles via GitHub Actions and the
debug APK is attached to every `android.yml` run.

## Install the APK

1. Open the repo → **Actions** → latest `android` run → download the
   `novadesk-debug` artifact (or install the APK attached to releases).
2. Allow install-unknown-apps for your browser, install, open NovaDesk.

## Set up Debian (in the app)

1. Home → **Debian** card (or Terminal → **Set up**) opens the wizard.
2. Pick a release (bookworm recommended).
3. Paste a rootfs tarball URL + SHA-256. Reliable sources for ARM64:
   - Community rootfs builds published for proot apps (check the checksum
     published next to the file), or
   - Your own `debootstrap --foreign` export packed as `.tar.xz`.
4. Install → wait for **Ready** → open the Terminal tab (`user@novadesk:~$`).

Needs ~1.2 GB free. Without root there is no systemd/Docker/FUSE — apt,
git, python, node and friends work normally.

## Set up the agents

- **OpenCode:** in Debian, `curl -fsSL https://opencode.ai/install | bash`,
  then export a provider key (e.g. `ANTHROPIC_API_KEY`). Docs: https://opencode.ai/docs
- **Codex:** in Debian, `npm i -g @openai/codex`, then run `codex` once to
  sign in. Docs: https://developers.openai.com/codex/cli

Each agent has its own chat tab, working-directory picker and session log.

## Build it yourself

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/*.apk
./gradlew testDebugUnitTest
```

Requires JDK 17 + Android SDK (compileSdk 34). CI does exactly this on
every push (see `.github/workflows/android.yml`).

## Layout

`app/` (single module) → `core/` (theme, debian, terminal, agents,
browser, mcp, storage, tools) + `feature/` (one package per screen).
Details and honest limitations: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## License

MIT — see [LICENSE](LICENSE).
