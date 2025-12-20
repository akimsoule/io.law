#!/usr/bin/env bash
set -euo pipefail

# Usage: 15_service_control.sh <start|stop|restart|status>
ACTION=${1:-status}
SERVICE=law-orchestrator.service

case "$ACTION" in
  start|stop|restart|status)
    sudo systemctl $ACTION $SERVICE
    ;;
  logs)
    sudo journalctl -u $SERVICE -n 200 --no-pager
    ;;
  *)
    echo "Usage: $0 <start|stop|restart|status|logs>"; exit 2 ;;
esac