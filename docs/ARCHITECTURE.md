# NovaDesk architecture

How the app is put together, which decisions are load-bearing, and what
v0.1 honestly cannot do yet. Read this before refactoring anything under
`core/linux`, `core/terminal` or `core/agents`.

## Module map (single `:app` module, packages by layer)

```
com.novadesk
├── NovaDeskApp.kt            hand-rolled service locator lives here (AppContainer)
├── MainActivity.kt           single activity, Compose nav only
├── core/ui/theme             NovaTheme / accents / type (dark, light, AMOLED)
├── core/ui/components        NovaCard, AgentChat, AgentMarkdown — shared visuals
├── core/navigation           Routes + NovaNavGraph (bottom bar, 5 top-level tabs)
├── core/linux                DeviceArch, DebianRelease, DebianSources,
│                             DebianLayout, DebianInstaller, ProotInstaller,
│                             ProotRunner, DevEnvInstaller
├── core/terminal             TerminalSession, TerminalManager, TerminalService,
│                             ShellCommand, AnsiParser
├── core/storage              AppPrefs (DataStore), ProjectsRepository,
│                             FilesRepository (sandboxed), AppContainer
├── core/agents               AgentRunner base + OpenCodeRunner + CodexRunner,
│                             ApprovalPolicy, AgentConfig (writes CLI configs)
├── core/browser              BrowserTab, SearchProvider, BrowserTools,
│                             BrowserBridge (localhost agent→WebView link)
├── core/mcp                  McpCatalog, McpRegistry, McpSecrets, McpClient
├── core/tools                DevTools, GitInfo (pure + tiny process helpers)
├── setup                     SetupCoordinator — the first-run state machine
└── feature/*                 one package per screen, ViewModels inlined where
                              the screen is small enough to stay readable
```

Single-module was deliberate: multi-module Gradle buys little at this size
and multiplies CI failure modes. Split when a second APK target appears.

## First run

`MainActivity` shows `OnboardingScreen` until `SetupCoordinator.isComplete`.
The coordinator runs `DebianInstaller` (which first fetches proot via
`ProotInstaller`), waits for the storage permission, then runs
`DevEnvInstaller`, then writes agent/MCP config with `AgentConfig`. Progress
is a single `SetupState` stream — the UI renders it and nothing else. The
"complete" flag is only written *after* the dev stack lands, so a crash
mid-setup resumes instead of silently skipping opencode/codex.

## Why no Hilt / Room / KSP

Every annotation processor is a build-cache and version-alignment risk on
GitHub-hosted runners. DataStore + flat files cover our persistence
(projects list, MCP installs, prefs, session logs) with zero codegen.
Revisit if the schema outgrows line-based files.

## Debian runtime (the important part)

- **Approach:** proot, unrooted. `ProotRunner` builds the argv
  (`--link2symlink`, `--kill-on-exit`, `--rootfs`, `--bind` home +
  `/sdcard`, `--cwd`, `env -i` with base env). Binary lives at
  `files/bin/proot`.
- **Sources:** pinned and verified, in `DebianSources` — a Termux
  `proot-distro` v4.17.3 rootfs tarball and a static
  `ahmed-alnassif/proot` build. Setup uses these automatically; the manual
  wizard still accepts a custom URL + SHA-256.
- **Install:** `ProotInstaller` unzips proot; `DebianInstaller` downloads
  the rootfs with HTTP Range resume, optionally verifies SHA-256, extracts
  with the device `tar`, then writes `hostname`, `resolv.conf`,
  `sources.list` and `/etc/profile.d/novadesk.sh` (the visible
  `user@novadesk` prompt identity — there is no real Unix `user` account).
- **Dev stack:** `DevEnvInstaller` runs apt + the OpenCode/Codex installers
  inside the guest and streams output lines back to the UI.
- **Persistence:** the tree lives in `files/debian/` (rootfs, home,
  downloads, state) and survives updates; Auto Backup excludes it.
- **Failure rule:** any step throws → status `Failed` with the real
  message. We never report success without a verified tree.

## Terminal

`TerminalSession` = one `Process` + reader thread → bounded tail
(`StateFlow<String>`) plus a `revision` counter so the UI only re-renders
when output actually changed (this bounded subset was the main jank fix).
ANSI SGR colours via `AnsiParser`; cursor-addressing escapes are stripped,
so fullscreen TUIs (vim, htop) degrade to scrolling output instead of
garbage. This limitation is shown in-app, not hidden. `TerminalService`
keeps sessions alive while switching screens (best-effort; Android may
still reclaim us).

## Agents (OpenCode / Codex)

Both runners drive the **real CLIs inside Debian** (`opencode run`,
`codex exec`) and stream stdout into chat. A per-agent model picker
(Settings → Agents) writes `~/.config/opencode/opencode.json` /
`~/.codex/config.toml` via `AgentConfig` and is also passed as
`--model` / `-m` on the command line. Setup states surface the CLI's own
errors plus concrete install steps. `ApprovalPolicy` gates risky shell text
(root wipes, `curl|bash`, fork bombs) into a `NeedsApproval` state with the
verbatim command. The transcript is capped so long runs stay responsive.
No scripted dialogue exists anywhere.

## Browser + agent tools

WebView tabs; `BrowserTools` exposes snapshot/click/fill/scroll over
`evaluateJavascript`. An on-screen indicator (`◉`) marks agent-driven tabs.

`BrowserBridge` is a token-authenticated HTTP server bound to 127.0.0.1.
`AgentConfig` writes a dependency-free Node MCP server
(`~/.novadesk/browser-mcp.mjs`) that proxys tool calls to it, and registers
it in both CLIs' MCP config. The bridge only has a handler while the
Browser tab is open — otherwise an agent call gets a clear "no page to
control" error. Logins, CAPTCHAs and canvas-only pages return errors to the
agent — never fake success.

## MCP

Catalog entries are real upstream servers with exact package refs.
Stdio servers spawn via `npx -y <pkg>` inside Debian; HTTP/remote entries
use their URL. Two remote endpoints (`https://opencode.ai`,
`https://codex.com`) were supplied at build time and are preinstalled +
enabled by `McpRegistry.seedDefaults` on first run, but their MCP support is
unverified and the store/docs say so. `McpClient.probe` speaks enough
JSON-RPC (`initialize`, `tools/list`) to verify startup. Secrets live in a
private DataStore (`McpSecrets`), never in the install list, exports, or
git.

## Files

`FilesRepository.resolve` canonicalises + de-symlinks every path and
requires containment in an allowed root. UI cannot bypass it.

## What v0.1 does NOT do

- Full VT100 emulation (no vim-in-terminal yet).
- Background execution guarantees (Android decides).
- Linux GUI apps (no display server bundled).
- Streaming MCP stdio client (probe-only; full duplex is next).
- Headless agent browsing — the bridge needs the Browser tab open because
  there is no off-screen WebView. An agent call without a tab is a clear
  error, never a silent no-op.
- Verified remote MCP endpoints: the two preloaded remotes are
  user-supplied and unverified; they are reported as "configured" only.
