#!/bin/bash
# Test du script setup-server.sh dans un conteneur Ubuntu 25.10

set -e

CONTAINER_NAME="io-law-test"
IMAGE="ubuntu:25.10"

echo "🧪 Test setup-server.sh dans Ubuntu 25.10"
echo "=========================================="
echo ""

# Nettoyer conteneur existant
if docker ps -a | grep -q "$CONTAINER_NAME"; then
    echo "🗑️  Suppression ancien conteneur..."
    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
fi

# Démarrer conteneur Ubuntu
echo "🐳 Démarrage conteneur Ubuntu 25.10..."
docker run -d \
    --name "$CONTAINER_NAME" \
    --privileged \
    -v "$(pwd)/setup-server.sh:/root/setup-server.sh:ro" \
    "$IMAGE" \
    sleep infinity

echo "✅ Conteneur démarré"
echo ""

# Installer sudo dans le conteneur (requis par le script)
echo "📦 Installation sudo..."
docker exec "$CONTAINER_NAME" apt-get update -qq
docker exec "$CONTAINER_NAME" apt-get install -y -qq sudo

echo ""
echo "🚀 Exécution setup-server.sh..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Exécuter le script
docker exec "$CONTAINER_NAME" bash /root/setup-server.sh

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔧 Démarrage manuel MySQL (systemd non disponible en Docker)..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Créer répertoires MySQL
docker exec "$CONTAINER_NAME" bash -c "mkdir -p /var/run/mysqld && chown mysql:mysql /var/run/mysqld"

# Démarrer MySQL en background
echo "⚙️  Démarrage MySQL..."
docker exec -d "$CONTAINER_NAME" bash -c "mysqld_safe --skip-grant-tables > /dev/null 2>&1 &"
sleep 8

# Configurer MySQL
echo "🔐 Configuration MySQL..."
docker exec "$CONTAINER_NAME" bash -c "mysql -e \"FLUSH PRIVILEGES;\" 2>/dev/null || true"
docker exec "$CONTAINER_NAME" bash -c "mysql -e \"ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root';\" 2>/dev/null || true"
docker exec "$CONTAINER_NAME" bash -c "mysql -e \"CREATE DATABASE IF NOT EXISTS law_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\" 2>/dev/null || true"
docker exec "$CONTAINER_NAME" bash -c "mysql -e \"FLUSH PRIVILEGES;\" 2>/dev/null || true"

# Redémarrer MySQL normalement
echo "🔄 Redémarrage MySQL..."
docker exec "$CONTAINER_NAME" pkill mysqld 2>/dev/null || true
sleep 3
docker exec -d "$CONTAINER_NAME" bash -c "mysqld_safe > /dev/null 2>&1 &"
sleep 8

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Vérifications finales"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Vérifier Java
echo "☕ Java:"
docker exec "$CONTAINER_NAME" java -version 2>&1 | head -1

# Vérifier Maven
echo ""
echo "📦 Maven:"
docker exec "$CONTAINER_NAME" mvn -version 2>&1 | head -1

# Vérifier MySQL
echo ""
echo "🗄️  MySQL:"
docker exec "$CONTAINER_NAME" mysql --version

# Tester connexion MySQL
echo ""
echo "🔌 Test connexion MySQL:"
if docker exec "$CONTAINER_NAME" mysql -uroot -proot -e "SELECT 'OK' as status;" 2>/dev/null | grep -q "OK"; then
    echo "   ✅ Connexion MySQL réussie"
else
    echo "   ⚠️  Connexion MySQL échouée"
fi

# Vérifier base de données
echo ""
echo "💾 Bases de données:"
docker exec "$CONTAINER_NAME" mysql -uroot -proot -e "SHOW DATABASES;" 2>/dev/null | grep -E "(law_db|Database)"

# Vérifier si le repo a été cloné
echo ""
echo "📂 Repository:"
if docker exec "$CONTAINER_NAME" test -d ~/io.law; then
    echo "   ✅ Repository cloné dans ~/io.law"
    docker exec "$CONTAINER_NAME" bash -c "ls -la ~/io.law | head -5"
else
    echo "   ⚠️  Repository non trouvé"
fi

# Vérifier le build Maven
echo ""
echo "🏗️  Build Maven:"
if docker exec "$CONTAINER_NAME" test -f ~/io.law/law-app/target/law-app-1.0-SNAPSHOT.jar; then
    echo "   ✅ JAR généré"
    docker exec "$CONTAINER_NAME" ls -lh ~/io.law/law-app/target/law-app-1.0-SNAPSHOT.jar
else
    echo "   ⚠️  JAR non trouvé"
fi

# Vérifier scripts de gestion
echo ""
echo "🛠️  Scripts de gestion:"
for script in io.law-start io.law-stop io.law-status io.law-logs io.law-update; do
    if docker exec "$CONTAINER_NAME" test -f /usr/local/bin/$script; then
        echo "   ✅ $script"
    else
        echo "   ❌ $script manquant"
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Test terminé"
echo ""
echo "📋 Commandes utiles:"
echo "   docker exec -it $CONTAINER_NAME bash       # Shell interactif"
echo "   docker exec $CONTAINER_NAME io.law-status  # Vérifier service"
echo "   docker logs $CONTAINER_NAME                # Logs conteneur"
echo "   docker rm -f $CONTAINER_NAME               # Supprimer conteneur"
echo ""
