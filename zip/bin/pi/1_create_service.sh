#!/usr/bin/env bash
set -euo pipefail

# create_service.sh
# Crée une unité systemd simple pour l'orchestrator et l'active.
# Usage: ./create_service.sh --service-name law-orchestrator.service --deploy-dir /home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT --user pi

SERVICE_NAME="law-orchestrator.service"
DEPLOY_DIR="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT"
USER="pi"

while [[ $# -gt 0 ]]; do
  case $1 in
    --service-name) SERVICE_NAME="$2"; shift 2;;
    --deploy-dir) DEPLOY_DIR="$2"; shift 2;;
    --user) USER="$2"; shift 2;;
    -h|--help) echo "Usage: $0 [--service-name NAME] [--deploy-dir DIR] [--user USER]"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}"
SYSLOG_ID="${SERVICE_NAME%%.*}"

# Write the unit file with variables expanded (no single-quoted heredoc)
sudo bash -lc "cat > ${UNIT_PATH} <<UNIT
[Unit]
Description=LAW Orchestrator - orchestration runner
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${USER}
WorkingDirectory=${DEPLOY_DIR}
ExecStart=/bin/bash -lc '${DEPLOY_DIR}/bin/orchestrate.sh'
Restart=on-failure
RestartSec=10
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=${SYSLOG_ID}

[Install]
WantedBy=multi-user.target
UNIT"

sudo systemctl daemon-reload
sudo systemctl enable --now "${SERVICE_NAME}"

echo "Service ${SERVICE_NAME} created and started. Check status with: sudo systemctl status ${SERVICE_NAME}"