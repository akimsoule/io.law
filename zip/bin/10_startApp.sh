#!/bin/bash

# Script pour démarrer l'application law-app via le fichier JAR

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"

# Vérifier si le fichier JAR existe
if [ ! -f "$JAR" ]; then
  echo "Erreur : Le fichier JAR $JAR n'existe pas. Veuillez construire le projet avec 'mvn clean package'."
  exit 1
fi

# Démarrer l'application
java -jar "$JAR" --spring.profiles.active=web