Utilitaires Pi - scripts

Ces scripts sont des utilitaires d'administration pour le Raspberry Pi où s'exécute l'orchestrator.
Emplacement prévu : `/home/pi/bin/pi` ou dans la distribution `zip/bin/pi`.

Contenu:
- `1_create_service.sh` : crée et active une unité systemd pour le service (par défaut `law-orchestrator.service`).
- `2_drop_db_docker.sh` : supprime une base dans un container Docker MySQL/MariaDB (confirme avant suppression).
- `3_service_control.sh` : contrôle start|stop|restart|status|enable|disable pour un service systemd.
- `4_backup_data.sh` : crée une archive tar.gz du dossier `data/` et l'enregistre dans `/home/pi/backups`.
- `5_tail_logs.sh` : affiche/stream les logs de l'orchestrator (par défaut `logs/orchestrate.log`).

Notes d'utilisation:
1. Rendre les scripts exécutables:
   chmod +x zip/bin/pi/*.sh

2. Copier sur le Pi et placer dans `/home/pi/bin` (ou utiliser directement les scripts dans la distribution):
   scp zip/bin/pi/*.sh pi@192.168.0.37:/home/pi/bin/
   ssh pi@192.168.0.37 "chmod +x /home/pi/bin/*.sh"

3. Exemple d'usage:
   - Créer le service:
     /home/pi/bin/create_service.sh --deploy-dir /home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT --user pi

   - Supprimer une DB dans docker (confirmer):
     /home/pi/bin/drop_db_docker.sh --db law_db --container-name mysql --mysql-root-pwd root

   - Contrôler le service:
     /home/pi/bin/service_control.sh restart law-orchestrator.service

   - Backup des données:
     /home/pi/bin/backup_data.sh --src /home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data --dest /home/pi/backups

   - Tail des logs:
     /home/pi/bin/tail_logs.sh -f

Sécurité et précautions:
- Les scripts pouvant supprimer des données demandent confirmation interactive.
- Testez d'abord avec `--dry-run` si vous ajoutez rsync ou commandes destructrices.

Aide / contact: demandez-moi si vous voulez que je copie et installe ces scripts sur le Pi maintenant.