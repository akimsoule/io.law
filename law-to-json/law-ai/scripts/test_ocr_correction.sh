#!/bin/bash
# Test fonctionnel de correction OCR via Ollama (law-ocr-cor)
# Usage: ./test_ocr_correction.sh

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧪 TEST FONCTIONNEL : Correction OCR via IA (Ollama)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📦 Module : law-ocr-cor (correction IA uniquement)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Vérifier Ollama disponible
echo ""
echo "1️⃣ Vérification Ollama..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama accessible sur http://localhost:11434"
    
    # Lister modèles disponibles
    echo "   📋 Modèles disponibles :"
    curl -s http://localhost:11434/api/tags | jq -r '.models[].name' | sed 's/^/      • /'
else
    echo "   ❌ Ollama non accessible"
    echo "   💡 Démarrer Ollama : ollama serve"
    exit 1
fi

# 2. Déterminer modèle à utiliser
echo ""
echo "2️⃣ Sélection modèle..."

# Essayer gemma3n d'abord
if curl -s http://localhost:11434/api/tags | jq -r '.models[].name' | grep -q "gemma3n"; then
    MODEL="gemma3n"
    echo "   ✅ Utilisation : gemma3n"
else
    # Fallback sur premier modèle disponible
    MODEL=$(curl -s http://localhost:11434/api/tags | jq -r '.models[0].name')
    echo "   ⚠️  gemma3n non trouvé, utilisation : $MODEL"
fi


# 3. Test correction OCR basique
echo ""
echo "3️⃣ Test correction OCR simple..."

RAW_OCR="Articlc 1e : Le présent décret porte..."

echo "   📝 Texte OCR brut : $RAW_OCR"

# Créer prompt de correction (plus strict pour éviter explications)
PROMPT="Corrige ces erreurs OCR: $RAW_OCR"

# Envoyer à Ollama
echo "   🤖 Envoi à Ollama ($MODEL)..."
RESPONSE=$(curl -s -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL\",
    \"prompt\": \"$PROMPT\",
    \"stream\": false,
    \"options\": {
      \"temperature\": 0.1,
      \"num_predict\": 100
    }
  }")

# Extraire réponse
CORRECTED=$(echo "$RESPONSE" | jq -r '.response')

echo ""
echo "   📄 Réponse complète :"
echo "   $CORRECTED"
echo ""

# Extraire juste la première ligne si explications multiples
CORRECTED_FIRST_LINE=$(echo "$CORRECTED" | head -1)

# 4. Vérifier correction
echo ""
echo "4️⃣ Vérification résultat..."

if echo "$CORRECTED" | grep -iq "Article"; then
    echo "   ✅ TEST RÉUSSI : 'Article' détecté (correction appliquée)"
    
    if echo "$CORRECTED" | grep -iq "1er"; then
        echo "   🎉 BONUS : '1er' aussi corrigé !"
    fi
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎉 CORRECTION OCR VIA IA FONCTIONNELLE !"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📊 Résultat :"
    echo "   • Entrée  : $RAW_OCR"
    echo "   • Sortie  : $(echo "$CORRECTED_FIRST_LINE" | head -c 80)..."
    echo "   • Modèle  : $MODEL"
    echo "   • Durée   : $(echo "$RESPONSE" | jq -r '.total_duration / 1000000000')s"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 0
else
    echo "   ⚠️  TEST PARTIEL : Correction non optimale"
    echo "   📋 Résultat obtenu : $CORRECTED"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "⚠️  CORRECTION NÉCESSITE AJUSTEMENT PROMPT"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
fi
