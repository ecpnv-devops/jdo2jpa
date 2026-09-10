#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/release.sh prepare <release-version>
  ./scripts/release.sh verify <release-version>
  ./scripts/release.sh publish <release-version>
  ./scripts/release.sh tag <release-version>
  ./scripts/release.sh github-release <release-version>
  ./scripts/release.sh snapshot <next-version>

Examples:
  ./scripts/release.sh prepare 1.2.3
  ./scripts/release.sh verify 1.2.3
  ./scripts/release.sh publish 1.2.3
  ./scripts/release.sh tag 1.2.3
  ./scripts/release.sh github-release 1.2.3
  ./scripts/release.sh snapshot 1.2.4
EOF
}

require_version() {
  local version="${1:-}"
  if [[ -z "$version" ]]; then
    echo "Missing version argument." >&2
    usage >&2
    exit 1
  fi
}

require_main_branch() {
  local current_branch
  current_branch="$(git rev-parse --abbrev-ref HEAD)"
  if [[ "$current_branch" != "main" ]]; then
    echo "Release steps must be run from main; current branch is '$current_branch'." >&2
    exit 1
  fi
}

require_clean_worktree() {
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean. Commit or stash your changes before continuing." >&2
    git status --short >&2
    exit 1
  fi
}

set_release_version() {
  local version="$1"
  mvn versions:set \
    -DnewVersion="${version}" \
    -DgenerateBackupPoms=false
}

set_snapshot_version() {
  local version="$1"
  mvn versions:set \
    -DnewVersion="${version}-SNAPSHOT" \
    -DgenerateBackupPoms=false
}

cmd="${1:-help}"
case "$cmd" in
  prepare)
    require_version "${2:-}"
    require_main_branch
    require_clean_worktree
    set_release_version "${2}"
    echo "Prepared pom.xml for release ${2}."
    echo "Next: ./scripts/release.sh verify ${2}"
    ;;

  verify)
    require_version "${2:-}"
    require_main_branch
    mvn clean verify
    echo "Version check:"
    git diff -- pom.xml
    echo "If the diff is limited to the version bump, continue with ./scripts/release.sh publish ${2}."
    ;;

  publish)
    require_version "${2:-}"
    require_main_branch
    git add pom.xml
    git commit -m "Release ${2}"
    git push origin main
    echo "The GitHub Action will publish the Maven package from main."
    echo "Wait for the workflow to succeed, then run: ./scripts/release.sh tag ${2}"
    ;;

  tag)
    require_version "${2:-}"
    git tag "v${2}"
    git push origin "v${2}"
    echo "Pushed tag v${2}."
    echo "Optional GitHub release: ./scripts/release.sh github-release ${2}"
    ;;

  github-release)
    require_version "${2:-}"
    if command -v gh >/dev/null 2>&1; then
      gh release create "v${2}" \
        --title "v${2}" \
        --generate-notes
    else
      cat <<EOF
GitHub CLI is not installed in this environment.
Create the release in the GitHub UI instead:
  1. Open Releases
  2. Click Draft a new release
  3. Select tag v${2}
  4. Add a title and release notes
  5. Publish the release
EOF
    fi
    ;;

  snapshot)
    require_version "${2:-}"
    require_main_branch
    set_snapshot_version "${2}"
    git add pom.xml
    git commit -m "Prepare ${2}-SNAPSHOT"
    git push origin main
    echo "The next snapshot version has been pushed to main."
    ;;

  help|--help|-h)
    usage
    ;;

  *)
    echo "Unknown command: $cmd" >&2
    usage >&2
    exit 1
    ;;
 esac
