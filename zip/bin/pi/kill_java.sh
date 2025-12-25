#!/usr/bin/env bash
# kill_java.sh - Tue tous les processus Java de la machine (safe by default)
# Usage:
#   ./kill_java.sh            # interactive confirmation
#   ./kill_java.sh --dry-run  # affiche les processus trouvés sans les tuer
#   ./kill_java.sh --yes      # non interactif, tue sans demande
#   ./kill_java.sh --force    # envoie SIGKILL après timeout si nécessaire
#   ./kill_java.sh --timeout N # délai (sec) avant SIGKILL (défaut 10)

set -euo pipefail
trap 'echo "ERROR: $0: line $LINENO" >&2' ERR

DRY_RUN=false
FORCE=false
YES=false
TIMEOUT=10

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --force) FORCE=true; shift ;;
    --yes) YES=true; shift ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    -h|--help) cat <<EOF
Usage: $0 [--dry-run] [--force] [--yes] [--timeout N]
  --dry-run   : list Java processes without killing
  --force     : send SIGKILL after timeout to any remaining processes
  --yes       : non-interactive, do not ask for confirmation
  --timeout N : seconds to wait after SIGTERM before SIGKILL (default: 10)
EOF
      exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

# Find Java PIDs (works on Linux/macOS)
PIDS=$(pgrep -f java || true)
if [[ -z "$PIDS" ]]; then
  echo "No Java processes found."
  exit 0
fi

# Show found processes
echo "Found Java processes:"
ps -o pid,ppid,cmd -p $PIDS 2>/dev/null || ps -p $PIDS -o pid,cmd 2>/dev/null || true

if [[ "$DRY_RUN" == true ]]; then
  echo "Dry run: not killing any process."
  exit 0
fi

if [[ "$YES" != true ]]; then
  read -r -p "Kill these Java processes? [y/N] " ans
  case "$ans" in
    [yY][eE][sS]|[yY]) ;; 
    *) echo "Aborted."; exit 1 ;;
  esac
fi

# Send SIGTERM
echo "Sending SIGTERM to PIDs: $PIDS"
kill $PIDS || true

# Wait for processes to exit up to TIMEOUT seconds
SECONDS_WAITED=0
while [[ $SECONDS_WAITED -lt $TIMEOUT ]]; do
  sleep 1
  ((SECONDS_WAITED++))
  REMAINING=$(pgrep -f java || true)
  if [[ -z "$REMAINING" ]]; then
    echo "All Java processes terminated after ${SECONDS_WAITED}s."
    exit 0
  fi
done

# After timeout, if still running
REMAINING=$(pgrep -f java || true)
if [[ -n "$REMAINING" ]]; then
  echo "Processes still running after ${TIMEOUT}s: $REMAINING"
  if [[ "$FORCE" == true ]]; then
    echo "Sending SIGKILL to: $REMAINING"
    kill -9 $REMAINING || true
    sleep 1
    if pgrep -f java >/dev/null 2>&1; then
      echo "Warning: Some Java processes could not be killed." >&2
      exit 2
    else
      echo "SIGKILL successful."
      exit 0
    fi
  else
    echo "Not forcing kill. Re-run with --force to send SIGKILL after timeout." >&2
    exit 3
  fi
fi

exit 0
