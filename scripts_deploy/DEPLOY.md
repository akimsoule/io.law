# 🚀 Déploiement Docker - io.law sur Hostinger KVM 8

## Installation rapide

### 1️⃣ Configuration initiale du serveur

```bash
# SSH vers serveur Hostinger KVM 8
ssh root@your-server-ip

# Télécharger et exécuter le script d'installation
curl -fsSL https://raw.githubusercontent.com/akimsoule/io.law/main/scripts_deploy/setup-server.sh | bash

# ⚠️ Déconnexion/reconnexion nécessaire pour groupe 'docker'
exit
ssh root@your-server-ip
```

**Le script `setup-server.sh` installe automatiquement** :
- ✅ Docker Engine + Docker Compose v2
- ✅ Ollama + modèle qwen2.5:7b (~4.7 GB)
- ✅ Git + clone du projet dans ~/io.law
- ✅ Configuration démarrage automatique (systemd)

### 2️⃣ Déploiement de l'application

```bash
cd ~/io.law/scripts_deploy

# Configurer (optionnel, mot de passe par défaut: law_password)
cp .env.example .env
nano .env  # Éditer MYSQL_ROOT_PASSWORD si besoin

# Déployer
./deploy.sh

# Suivre logs orchestration
docker compose logs -f app
```

---

## Scripts disponibles

### 📦 Setup & Déploiement

#### `setup-server.sh`
Installation complète du serveur (première fois uniquement)
```bash
curl -fsSL https://raw.githubusercontent.com/akimsoule/io.law/main/scripts_deploy/setup-server.sh | bash
```

#### `deploy.sh`
Déploiement/redéploiement de l'application
```bash
./deploy.sh
```

### 📊 Consultation

#### `check-data.sh`
Consulter l'état de la base de données MySQL
```bash
./check-data.sh
```
Affiche :
- Documents par statut (FETCHED, DOWNLOADED, EXTRACTED, CONSOLIDATED)
- Derniers documents détectés
- Progression consolidation
- Jobs Spring Batch actifs

#### `check-files.sh`
Consulter les fichiers dans les volumes Docker
```bash
./check-files.sh
```
Affiche :
- Nombre de PDFs (loi + décret)
- Nombre de fichiers OCR
- Nombre de fichiers JSON
- Liste des logs
- Utilisation disque

#### `check-logs.sh`
Consulter les logs d'orchestration
```bash
# Dernières 50 lignes (défaut)
./check-logs.sh

# Dernières 100 lignes
./check-logs.sh 100
```
Affiche les derniers logs du pipeline avec coloration des emojis.

### 🔄 Synchronisation

#### `sync-from-server.sh`
Synchroniser les données du serveur vers localhost
```bash
# 1. Configurer l'IP du serveur
nano sync-from-server.sh
# → Remplir SERVER_HOST="45.xxx.xxx.xxx"

# 2. Exécuter
./sync-from-server.sh
```
Synchronise :
- Base de données MySQL (dump → import)
- Fichiers (PDFs, OCR, JSON) via rsync

---

## Commandes Docker utiles

```bash
cd ~/io.law/scripts_deploy

# Logs en direct
docker compose logs -f app

# Arrêter
docker compose down

# Redémarrer après modif
docker compose restart app

# Statistiques ressources
docker stats

# Shell dans conteneur
docker exec -it law-app bash

# MySQL shell
docker exec -it law-mysql mysql -u root -plaw_password law_db
```

---

## Architecture

```
Hostinger KVM 8 (32GB RAM, 8 vCPU)
├── Ollama (qwen2.5:7b) → Port 11434 (sur host) [~10 GB RAM]
├── MySQL 8.4 (container) [~2 GB RAM]
├── io.law App (container) [~4 GB RAM]
│   └── Orchestration continue (fetch → download → extract → consolidate → fix)
│   └── Traitement parallèle : 8 threads simultanés
└── Volumes Docker:
    ├── ../data → /data (PDFs, OCR, JSON) - Bind mount
    └── mysql_data → base de données - Volume Docker

✅ RAM: ~16 GB utilisés / 32 GB disponibles (50% - confortable)
```

## Spécifications serveur

- **8 vCPU** : Traitement parallèle (max-threads: 8) → Pipeline 2x plus rapide
- **32 GB RAM** : JVM -Xmx4g, Ollama 10GB, MySQL 2GB → Marge confortable
- **400 GB NVMe** : Stockage PDFs + OCR + JSON (~200GB estimés)
- **Prix** : CA$ 27.89/mois (-67%)

## Fichiers de configuration

- `Dockerfile` : Build image multi-stage avec Maven
- `docker-compose.yml` : Stack MySQL + App
- `.env` : Variables d'environnement (passwords)
- `setup-server.sh` : Installation serveur complète
- `deploy.sh` : Déploiement automatique
