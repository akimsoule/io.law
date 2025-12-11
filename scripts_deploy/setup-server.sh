#!/bin/bash
set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Installation Serveur KVM 8 - io.law"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Mise à jour système
echo ""
echo "📦 1/4 - Mise à jour système Ubuntu..."
sudo apt-get update
sudo apt-get upgrade -y

# 2. Installation Docker
echo ""
echo "🐋 2/4 - Installation Docker..."
if ! command -v docker &> /dev/null; then
    # Installation via script officiel
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    
    # Ajouter user au groupe docker (éviter sudo)
    sudo usermod -aG docker $USER
    
    # Démarrage automatique
    sudo systemctl enable docker
    sudo systemctl start docker
    
    # Nettoyage
    rm get-docker.sh
    
    echo "✅ Docker installé: $(docker --version)"
else
    echo "✅ Docker déjà installé: $(docker --version)"
fi

# 3. Installation Docker Compose
echo ""
echo "🔧 3/4 - Installation Docker Compose..."
if ! command -v docker compose &> /dev/null; then
    # Docker Compose v2 (plugin)
    sudo apt-get install -y docker-compose-plugin
    
    echo "✅ Docker Compose installé: $(docker compose version)"
else
    echo "✅ Docker Compose déjà installé: $(docker compose version)"
fi

# 4. Installation Ollama
echo ""
echo "🤖 4/4 - Installation Ollama + qwen2.5:7b..."
if ! command -v ollama &> /dev/null; then
    curl -fsSL https://ollama.com/install.sh | sh
    
    # Démarrage service
    sudo systemctl enable ollama
    sudo systemctl start ollama
    
    # Attendre démarrage (5s)
    sleep 5
    
    # Télécharger modèle (~4.7 GB)
    echo "📥 Téléchargement modèle qwen2.5:7b (patientez ~5 min)..."
    ollama pull qwen2.5:7b
    
    echo "✅ Ollama installé: $(ollama --version)"
else
    echo "✅ Ollama déjà installé: $(ollama --version)"
    
    # Vérifier modèle
    if ! ollama list | grep -q "qwen2.5:7b"; then
        echo "📥 Téléchargement modèle qwen2.5:7b..."
        ollama pull qwen2.5:7b
    fi
fi

# Vérification finale
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Installation terminée !"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Versions installées:"
docker --version
docker compose version
ollama --version
echo ""
echo "🤖 Modèles Ollama disponibles:"
ollama list
echo ""
echo "⚠️  IMPORTANT: Déconnectez-vous et reconnectez-vous pour que"
echo "   le groupe 'docker' soit pris en compte (éviter sudo):"
echo ""
echo "   exit"
echo "   ssh root@your-server-ip"
echo ""
echo "🚀 Prochaine étape: Cloner projet et déployer"
echo "   git clone https://github.com/akimsoule/io.law.git"
echo "   cd io.law/scripts_deploy"
echo "   ./deploy.sh"
echo ""

# 5. Installation Git et clone du projet
echo ""
echo "📦 5/5 - Installation Git et clone du projet..."
if ! command -v git &> /dev/null; then
    sudo apt-get install -y git
    echo "✅ Git installé: $(git --version)"
else
    echo "✅ Git déjà installé: $(git --version)"
fi

# Clone du projet dans le home de l'utilisateur
cd ~
if [ -d "io.law" ]; then
    echo "⚠️  Dossier io.law existe déjà"
    read -p "   Voulez-vous le supprimer et re-cloner ? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -rf io.law
        git clone https://github.com/akimsoule/io.law.git
        echo "✅ Projet re-cloné dans $(pwd)/io.law"
    else
        echo "⏭️  Clone ignoré, utilisation du dossier existant"
    fi
else
    git clone https://github.com/akimsoule/io.law.git
    echo "✅ Projet cloné dans $(pwd)/io.law"
fi

# Se positionner dans le projet
cd io.law
PROJECT_PATH=$(pwd)

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Installation et setup terminés !"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📂 Projet installé dans: $PROJECT_PATH"
echo ""
echo "⚠️  IMPORTANT: Déconnectez-vous et reconnectez-vous pour que"
echo "   le groupe 'docker' soit pris en compte (éviter sudo):"
echo ""
echo "   exit"
echo "   ssh root@your-server-ip"
echo "   cd io.law/scripts_deploy"
echo "   ./deploy.sh"
echo ""

