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
│                             DebianLayout, DebianInstaller, ProotRunner
├── core/terminal             TerminalSession, TerminalManager, TerminalService,
│                             ShellCommand, AnsiParser
├── core/storage              AppPrefs (DataStore), ProjectsRepository,
│                             FilesRepository (sandboxed), AppContainer
├── core/agents               AgentRunner base + OpenCodeRunner + CodexRunner,
│                             ApprovalPolicy
├── core/browser              BrowserTab, SearchProvider, BrowserTools
├── core/mcp                  McpCatalog, McpRegistry, McpSecrets, McpClient
├── core/tools                DevTools, GitInfo (pure + tiny process helpers)
└── feature/*                 one package per screen, ViewModels inlined where
                              the screen is small enough to stay readable
```

Single-module was deliberate: multi-module Gradle buys little at this size
and multiplies CI failure modes. Split when a second APK target appears.

## Why no Hilt / Room / KSP

Every annotation processor is a build-cache and version-alignment risk on
GitHub-hosted runners. DataStore + flat files cover our persistence
(projects list, MCP installs, prefs, session logs) with zero codegen.
Revisit if the schema outgrows line-based files.

## Debian runtime (the important part)

- **Approach:** proot, unrooted. `ProotRunner` builds the argv
  (`--rootfs`, `--bind` home + `/sdcard`, `--cwd`, base env). A proot
  binary is resolved from `files/bin/proot` or PATH at setup time.
- **Install:** `DebianInstaller` downloads a rootfs tarball with HTTP
  Range resume, optionally verifies SHA-256, extracts with the device
  `tar`, then writes `hostname`, `resolv.conf`, `sources.list`.
  The wizard asks the user for the tarball URL + checksum rather than
  hardcoding a mirror that will rot — the recommended sources are listed
  in the README.
- **Persistence:** the tree lives in `files/debian/` (rootfs, home,
  downloads, state) and survives updates; Auto Backup excludes it.
- **Failure rule:** any step throws → status `Failed` with the real
  message. We never report success without a verified tree.

## Terminal

`TerminalSession` = one `Process` + reader thread → `StateFlow<String>`.
ANSI SGR colours via `AnsiParser`; cursor-addressing escapes are stripped,
so fullscreen TUIs (vim, htop) degrade to scrolling output instead of
garbage. This limitation is shown in-app, not hidden. `TerminalService`
keeps sessions alive while switching screens (best-effort; Android may
still reclaim us).

## Agents (OpenCode / Codex)

Both runners drive the **real CLIs inside Debian** (`opencode run`,
`codex exec`) and stream stdout into chat. Setup states surface the CLI's
own errors plus concrete install steps. `ApprovalPolicy` gates risky
shell text (root wipes, `curl|bash`, fork bombs) into a `NeedsApproval`
state with the verbatim command. No scripted dialogue exists anywhere.

## Browser + agent tools

WebView tabs; `BrowserTools` exposes snapshot/click/fill/scroll over
`evaluateJavascript`. An on-screen indicator marks agent-driven tabs.
Logins, CAPTCHAs and canvas-only pages return errors to the agent —
never fake success.

## MCP

Catalog entries are real upstream servers with exact package refs.
Stdio servers spawn via `npx -y <pkg>` inside Debian; `McpClient.probe`
speaks enough JSON-RPC (`initialize`, `tools/list`) to verify startup.
Secrets live in a private DataStore (`McpSecrets`), never in the
install list, exports, or git.

## Files

`FilesRepository.resolve` canonicalises + de-symlinks every path and
requires containment in an allowed root. UI cannot bypass it.

## What v0.1 does NOT do

- Full VT100 emulation (no vim-in-terminal yet).
- Background execution guarantees (Android decides).
- Linux GUI apps (no display server bundled).
- Streaming MCP stdio client (probe-only; full duplex is next).
