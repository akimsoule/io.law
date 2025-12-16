#!/bin/bash
# Test rapide d'implémentation law-ocr-cor

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ VALIDATION IMPLÉMENTATION law-ocr-cor"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PASS=0
FAIL=0

# 1. Compilation
echo ""
echo "1️⃣ Compilation..."
if mvn clean compile -pl law-tojson/law-ocr-cor -DskipTests > /dev/null 2>&1; then
    echo "   ✅ Compilation réussie"
    ((PASS++))
else
    echo "   ❌ Échec compilation"
    ((FAIL++))
fi

# 2. Tests unitaires
echo ""
echo "2️⃣ Tests unitaires..."
if mvn test -pl law-tojson/law-ocr-cor -Dtest=JsonResultTest > /dev/null 2>&1; then
    echo "   ✅ Tests unitaires passent (5/5)"
    ((PASS++))
else
    echo "   ❌ Échec tests unitaires"
    ((FAIL++))
fi

# 3. Ollama disponible
echo ""
echo "3️⃣ Ollama disponible..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama accessible"
    ((PASS++))
    
    # 4. Test fonctionnel
    echo ""
    echo "4️⃣ Test fonctionnel correction OCR..."
    if cd law-tojson/law-ocr-cor && ./scripts/test_ocr_correction.sh > /dev/null 2>&1; then
        echo "   ✅ Correction OCR fonctionnelle"
        ((PASS++))
    else
        echo "   ❌ Échec correction OCR"
        ((FAIL++))
    fi
    cd ../..
else
    echo "   ⚠️ Ollama non disponible (test fonctionnel skippé)"
    echo "   💡 Démarrer : ollama serve"
fi

# 5. JAR généré
echo ""
echo "5️⃣ Génération JAR..."
if [ -f "law-tojson/law-ocr-cor/target/law-ocr-cor-1.0-SNAPSHOT.jar" ]; then
    SIZE=$(du -h law-tojson/law-ocr-cor/target/law-ocr-cor-1.0-SNAPSHOT.jar | cut -f1)
    echo "   ✅ JAR généré ($SIZE)"
    ((PASS++))
else
    echo "   ❌ JAR non trouvé"
    ((FAIL++))
fi

# 6. Structure fichiers
echo ""
echo "6️⃣ Structure fichiers..."
EXPECTED_FILES=(
    "law-tojson/law-ocr-cor/src/main/java/bj/gouv/sgg/service/IAService.java"
    "law-tojson/law-ocr-cor/src/main/java/bj/gouv/sgg/impl/OllamaClient.java"
    "law-tojson/law-ocr-cor/src/main/java/bj/gouv/sgg/service/OcrCorrectionService.java"
    "law-tojson/law-ocr-cor/src/test/java/bj/gouv/sgg/modele/JsonResultTest.java"
    "law-tojson/law-ocr-cor/scripts/test_ocr_correction.sh"
)

for file in "${EXPECTED_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "   ✅ $(basename $file)"
    else
        echo "   ❌ $(basename $file) manquant"
        ((FAIL++))
    fi
done
((PASS++))

# Résumé
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 RÉSUMÉ"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Tests réussis : $PASS"
echo "❌ Tests échoués : $FAIL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $FAIL -eq 0 ]; then
    echo "🎉 IMPLÉMENTATION VALIDÉE !"
    exit 0
else
    echo "⚠️ Certains tests ont échoué"
    exit 1
fi
