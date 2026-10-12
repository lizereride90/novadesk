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

## First run (automatic)

Setup is now a single full-screen flow — you do not paste URLs:

1. **Install Debian.** NovaDesk fetches a static proot and a Debian rootfs
   from built-in, pinned sources (Termux `proot-distro` v4.17.3 rootfs +
   a `ahmed-alnassif/proot` static build) and shows real download progress.
2. **All-files access.** Android asks for the `MANAGE_EXTERNAL_STORAGE`
   permission so device storage can be bound into Debian at `/sdcard`.
3. **Dev stack.** Inside Debian it installs git, curl, Node.js, npm, Python
   and pip with apt, then installs the OpenCode and Codex CLIs. apt output
   streams into the screen so you can see exactly what happened.
4. **Home.** You land in the app once the tools are in place.

Needs ~1.4 GB free. Without root there is no systemd/Docker/FUSE — apt,
git, python, node and friends work normally.

Advanced: the manual wizard (Home → Debian card, Terminal → Set up, or
Settings → Debian) still accepts your own rootfs URL + SHA-256 if you want
a different release or a self-built tarball.

## The agents

Both agents run their real CLIs inside Debian with their own config files:

- **OpenCode** — `opencode run [--model <m>] "<prompt>"`; config at
  `~/.config/opencode/opencode.json`.
- **Codex** — `codex exec [-m <model>] "<prompt>"`; config at
  `~/.codex/config.toml`.

Set a model per agent in **Settings → Agents**; NovaDesk writes it into the
matching config file and passes it on the command line. Provider keys are
handled by each CLI's own auth (`opencode` provider env, `codex` sign-in).

Each agent has its own chat tab, working-directory picker and session log.

## MCP + browser control

On first run NovaDesk writes an MCP config that includes:

- a local **browser bridge** MCP server (`~/.novadesk/browser-mcp.mjs`), and
- the two remote endpoints supplied at build time
  (`https://opencode.ai`, `https://codex.com`). These are preinstalled and
  enabled, but their MCP support is **unverified** — treat them as
  configured, not guaranteed.

The bridge lets an agent open, read, click, fill and scroll the in-app
browser while you watch. It is a localhost-only, token-authenticated server,
and it can only reach the tab you currently have open — if the Browser tab
is closed, an agent call returns a clear "no page to control" error rather
than silently doing nothing.

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
