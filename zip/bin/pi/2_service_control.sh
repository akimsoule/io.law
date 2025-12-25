#!/usr/bin/env bash
set -euo pipefail

# service_control.sh
# Contrôle systemd pour le service (start|stop|restart|status|enable|disable)
# Usage: ./service_control.sh <start|stop|restart|status|enable|disable> [service-name]

if [ $# -lt 1 ]; then
  echo "Usage: $0 <start|stop|restart|status|enable|disable> [service-name]"
  exit 1
fi

ACTION="$1"
SERVICE_NAME="${2:-law-orchestrator.service}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KILL_SCRIPT="$SCRIPT_DIR/kill_java.sh"

case "$ACTION" in
  start)
    sudo systemctl start "$SERVICE_NAME"
    ;;
  stop)
    sudo systemctl stop "$SERVICE_NAME"
    if [ -x "$KILL_SCRIPT" ]; then
      echo "Invoking $KILL_SCRIPT to stop Java processes..."
      "$KILL_SCRIPT" --force --timeout 5 --yes || echo "Warning: kill script failed" >&2
    else
      echo "Notice: kill script not found at $KILL_SCRIPT" >&2
    fi
    ;;
  restart)
    # stop + ensure Java killed, then start
    sudo systemctl stop "$SERVICE_NAME"
    if [ -x "$KILL_SCRIPT" ]; then
      echo "Invoking $KILL_SCRIPT to stop Java processes before restart..."
      "$KILL_SCRIPT" --force --timeout 5 --yes || echo "Warning: kill script failed" >&2
    fi
    sudo systemctl start "$SERVICE_NAME"
    ;;
  status|enable|disable)
    sudo systemctl "$ACTION" "$SERVICE_NAME"
    ;;
  *)
    echo "Unknown action: $ACTION"; exit 2;;
esac

# show status for convenience
if [ "$ACTION" = "start" ] || [ "$ACTION" = "restart" ] || [ "$ACTION" = "status" ]; then
  sudo systemctl status --no-pager --full "$SERVICE_NAME"
fi