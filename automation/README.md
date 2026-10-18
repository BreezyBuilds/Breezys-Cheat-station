# Breezy's Retro Hub — Codex/Termux worker

This is an **opt-in** agent, not a direct ChatGPT-to-Termux connection. GitHub is the shared task queue. The worker reads Markdown tasks from `automation/tasks/` on `feature/retrohub-rebuild`, runs local Codex in a **separate clone** at `~/retrohub-codex-worker/repo`, pushes changes to `codex/<task>-<hash>`, and opens a pull request back to `feature/retrohub-rebuild`. It never merges PRs.

## Install on Termux (AYN Thor)

First review `automation/termux-worker.sh` and authenticate the Codex CLI. Then:

```bash
pkg install gh git coreutils -y
gh auth login
cd ~/Breezys-Cheat-station
git fetch origin feature/retrohub-rebuild
git show origin/feature/retrohub-rebuild:automation/termux-worker.sh > ~/retrohub-worker.sh
chmod 700 ~/retrohub-worker.sh
RETROHUB_ENABLE_AUTONOMOUS=YES bash ~/retrohub-worker.sh
```

The worker requires Codex login and `codex exec` support. Keep Termux open while running; Android can suspend it. To stop, press Ctrl+C. For long sessions, `termux-wake-lock` can help, but uses battery.

## Security and limitations

- **Read before activating.** The local agent executes generated shell commands. Codex `--full-auto` is not a security boundary on Android/Termux; sandbox support may be unavailable. The prompt restrictions are advisory, not an OS-level guarantee. Use a separate GitHub credential with minimal repository permissions if possible, and don't run on a device holding sensitive secrets.
- The worker only processes tasks committed to the trusted base branch. Anyone who can push tasks to that branch can cause local agent execution; protect branch write access.
- Tasks are read as text, not shell scripts. Only source paths under `app`, `gradle`, and listed Gradle configuration files are staged. Secret checks are best-effort, not exhaustive.
- **No automatic merges.** Review PR diffs and build results. Don't enable auto-merge or give the worker admin privileges.
- GitHub Actions CI can be added separately to validate APK builds. A successful local agent exit does not prove the Android build passed.
- The worker uses a fresh clone and never touches your working `~/Breezys-Cheat-station` checkout.
- Worker logs stay local under `~/retrohub-codex-worker/` and are not published automatically.
- To enqueue a task, commit `automation/tasks/TASK-ID.md` to the base branch. A changed task body produces a new hash and will be treated as a new task; avoid editing tasks after execution.
