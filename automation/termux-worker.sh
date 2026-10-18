#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

# Breezy's Retro Hub: opt-in local Codex worker. No unattended merges.
BASE_BRANCH="feature/retrohub-rebuild"
REPO_URL="https://github.com/BreezyBuilds/Breezys-Cheat-station.git"
ROOT="${RETROHUB_WORKER_HOME:-$HOME/retrohub-codex-worker}"
REPO="$ROOT/repo"
STATE="$ROOT/state"
POLL_SECONDS="${RETROHUB_POLL_SECONDS:-180}"
mkdir -p "$ROOT" "$STATE"

for cmd in git codex gh sha256sum; do
  command -v "$cmd" >/dev/null || { echo "Missing $cmd"; exit 1; }
done
if [[ "${RETROHUB_ENABLE_AUTONOMOUS:-}" != "YES" ]]; then
  echo 'Worker not activated. Set RETROHUB_ENABLE_AUTONOMOUS=YES after reviewing this script.'
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo 'GitHub CLI authentication required: gh auth login'; exit 1
fi
if [[ ! -d "$REPO/.git" ]]; then
  git clone --branch "$BASE_BRANCH" --single-branch "$REPO_URL" "$REPO"
fi

process_task() {
  local path="$1" task_id task_text digest marker branch output status report
  task_id="${path##*/}"; task_id="${task_id%.md}"
  [[ "$task_id" =~ ^[A-Za-z0-9_-]+$ ]] || { echo "Invalid task name: $task_id"; return 1; }
  task_text="$(git -C "$REPO" show "origin/$BASE_BRANCH:$path")"
  digest="$(printf '%s' "$task_text" | sha256sum | cut -c1-12)"
  marker="$STATE/$task_id-$digest.done"
  [[ ! -e "$marker" ]] || return 0
  branch="codex/${task_id,,}-$digest"
  output="$ROOT/$task_id-$digest.log"
  echo "Starting $task_id ($digest)"
  git -C "$REPO" checkout -B "$branch" "origin/$BASE_BRANCH"
  # The clone is dedicated to the agent: it contains no private signing keys or Termux secrets.
  # Never let repository task text control the shell or GitHub credentials.
  local instruction
  instruction="$(cat <<PROMPT
Implement the following approved Breezy's Retro Hub development task in this repository.
Do not access files outside this repository, credentials, or network services. Do not run git push, gh, curl, wget, or change Git remotes. Do not merge branches. Do not delete unrelated files. Keep changes limited to the task. Run ./gradlew assembleDebug if possible. At the end, provide a concise summary of edits and tests.

TASK ID: $task_id
TASK CONTENT:
$task_text
PROMPT
)"
  status=0
  (cd "$REPO" && codex exec --full-auto "$instruction") >"$output" 2>&1 || status=$?
  # Never upload raw Codex output, which may contain private data.
  if [[ "$status" -ne 0 ]]; then
    echo "Codex failed for $task_id (exit $status); local log: $output"
    return 1
  fi
  if ! git -C "$REPO" diff --check; then echo "Whitespace check failed"; return 1; fi
  # Only stage application/build source and tests; never upload agent logs or home files.
  git -C "$REPO" add -A -- app build.gradle.kts settings.gradle.kts gradle.properties gradle 2>/dev/null || true
  if git -C "$REPO" diff --cached --quiet; then
    echo "No eligible source changes for $task_id"; return 1
  fi
  # Refuse to publish likely credentials or binary signing material.
  if git -C "$REPO" diff --cached --name-only | grep -Ei '(^|/)(\.env|id_rsa|credentials|secrets?|token)(\.|/|$)|\.(jks|keystore|pem|p12|pfx|key)$'; then
    echo 'Sensitive-looking file staged; refusing to push'; git -C "$REPO" reset; return 1
  fi
  if git -C "$REPO" diff --cached --no-ext-diff | grep -Ei '^\+[^+].*(ghp_[A-Za-z0-9]{20}|github_pat_|sk-[A-Za-z0-9]{20})' >/dev/null; then
    echo 'Possible token in staged diff; refusing to push'; git -C "$REPO" reset; return 1
  fi
  git -C "$REPO" -c user.name='RetroHub Codex Worker' -c user.email='retrohub-worker@users.noreply.github.com' commit -m "Codex: $task_id"
  git -C "$REPO" push -u origin "$branch"
  report="Automated Codex task $task_id ($digest). Source changes pushed to $branch. Local Codex execution exited 0; inspect CI and review before merging. Raw local logs are not uploaded."
  (cd "$REPO" && gh pr create --base "$BASE_BRANCH" --head "$branch" --title "Codex: $task_id" --body "$report") || {
    echo "Branch pushed but PR creation failed. Create a PR manually for $branch"; return 1;
  }
  touch "$marker"
  echo "Submitted $task_id for review; no merge performed."
}

while :; do
  if git -C "$REPO" status --porcelain | grep -q .; then
    echo 'Agent clone has uncommitted files; stopping to avoid overwriting them.'; exit 1
  fi
  git -C "$REPO" fetch origin "$BASE_BRANCH" --prune
  git -C "$REPO" checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"
  while IFS= read -r path; do
    [[ "$path" == automation/tasks/*.md && "$path" != automation/tasks/README.md ]] || continue
    process_task "$path" || echo "Task failed; will retry next poll: $path"
  done < <(git -C "$REPO" ls-tree -r --name-only "origin/$BASE_BRANCH" automation/tasks)
  sleep "$POLL_SECONDS"
done
