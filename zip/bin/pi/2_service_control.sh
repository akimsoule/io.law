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

case "$ACTION" in
  start|stop|restart|status|enable|disable)
    sudo systemctl "$ACTION" "$SERVICE_NAME"
    ;;
  *)
    echo "Unknown action: $ACTION"; exit 2;;
esac

# show status for convenience
if [ "$ACTION" = "start" ] || [ "$ACTION" = "restart" ] || [ "$ACTION" = "status" ]; then
  sudo systemctl status --no-pager --full "$SERVICE_NAME"
fi