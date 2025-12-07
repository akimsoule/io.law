#!/bin/bash
# Script de régénération des JSON depuis les échantillons OCR
# Usage: ./regenerate-json.sh [--clean] [--specific loi-2024-1]

set -e

# Couleurs pour output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Répertoires
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SAMPLES_OCR_DIR="$PROJECT_DIR/src/test/resources/samples_ocr"
SAMPLES_JSON_DIR="$PROJECT_DIR/src/test/resources/samples_json"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}📄 Régénération JSON depuis OCR${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Parse arguments
CLEAN_MODE=false
SPECIFIC_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN_MODE=true
            shift
            ;;
        --specific)
            SPECIFIC_FILE="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}❌ Option inconnue: $1${NC}"
            echo "Usage: $0 [--clean] [--specific loi-2024-1]"
            exit 1
            ;;
    esac
done

# Mode clean : supprime les JSON existants
if [ "$CLEAN_MODE" = true ]; then
    echo -e "${YELLOW}🧹 Nettoyage des JSON existants...${NC}"
    
    if [ -d "$SAMPLES_JSON_DIR/loi" ]; then
        rm -rf "$SAMPLES_JSON_DIR/loi"
        echo -e "${GREEN}✅ Supprimé samples_json/loi/${NC}"
    fi
    
    if [ -d "$SAMPLES_JSON_DIR/decret" ]; then
        rm -rf "$SAMPLES_JSON_DIR/decret"
        echo -e "${GREEN}✅ Supprimé samples_json/decret/${NC}"
    fi
    
    echo ""
fi

# Vérifier que les répertoires OCR existent
if [ ! -d "$SAMPLES_OCR_DIR" ]; then
    echo -e "${RED}❌ Erreur : Répertoire samples_ocr/ introuvable${NC}"
    exit 1
fi

# Compter les fichiers OCR disponibles
loi_count=$(find "$SAMPLES_OCR_DIR/loi" -name "*.txt" 2>/dev/null | wc -l | tr -d ' ')
decret_count=$(find "$SAMPLES_OCR_DIR/decret" -name "*.txt" 2>/dev/null | wc -l | tr -d ' ')
total_ocr=$((loi_count + decret_count))

echo -e "${BLUE}📊 Échantillons OCR disponibles :${NC}"
echo -e "   Lois     : $loi_count fichiers"
echo -e "   Décrets  : $decret_count fichiers"
echo -e "   Total    : $total_ocr fichiers"
echo ""

# Mode spécifique : traiter un seul fichier
if [ -n "$SPECIFIC_FILE" ]; then
    echo -e "${YELLOW}🎯 Mode spécifique : $SPECIFIC_FILE${NC}"
    echo ""
    
    # Déterminer le type (loi/decret)
    if [[ "$SPECIFIC_FILE" == loi-* ]]; then
        TYPE="loi"
    elif [[ "$SPECIFIC_FILE" == decret-* ]]; then
        TYPE="decret"
    else
        echo -e "${RED}❌ Format invalide. Attendu: loi-YYYY-N ou decret-YYYY-N${NC}"
        exit 1
    fi
    
    # Vérifier que le fichier OCR existe
    OCR_FILE="$SAMPLES_OCR_DIR/$TYPE/$SPECIFIC_FILE.txt"
    if [ ! -f "$OCR_FILE" ]; then
        echo -e "${RED}❌ Fichier OCR introuvable: $OCR_FILE${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}🔄 Extraction en cours...${NC}"
    cd "$PROJECT_DIR"
    
    # Exécuter le test d'extraction avec Maven
    mvn test -Dtest=OcrToJsonExtractionTest#extractAllSamplesAndGenerateJson \
        -Dspecific.file="$SPECIFIC_FILE" \
        2>&1 | grep -E "(🔄|✅|❌|📊)" || true
    
    # Vérifier si le JSON a été généré
    JSON_FILE="$SAMPLES_JSON_DIR/$TYPE/$SPECIFIC_FILE.json"
    if [ -f "$JSON_FILE" ]; then
        echo ""
        echo -e "${GREEN}✅ JSON généré avec succès !${NC}"
        echo -e "   Fichier : $JSON_FILE"
        
        # Afficher les statistiques
        articles=$(grep -o '"number"' "$JSON_FILE" | wc -l | tr -d ' ')
        confidence=$(grep -o '"confidence": [0-9.]*' "$JSON_FILE" | head -1 | cut -d' ' -f2)
        
        echo -e "   Articles : $articles"
        echo -e "   Confiance : $confidence"
    else
        echo ""
        echo -e "${RED}❌ Échec de la génération${NC}"
        exit 1
    fi
    
    exit 0
fi

# Mode complet : traiter tous les fichiers
echo -e "${BLUE}🔄 Extraction complète en cours...${NC}"
echo ""

cd "$PROJECT_DIR"

# Exécuter le test Maven complet
echo -e "${YELLOW}Lancement du test d'extraction...${NC}"
mvn test -Dtest=OcrToJsonExtractionTest#extractAllSamplesAndGenerateJson \
    2>&1 | tee /tmp/extraction-output.log

# Extraire le rapport final
echo ""
echo -e "${BLUE}========================================${NC}"

grep -A 10 "RAPPORT FINAL" /tmp/extraction-output.log || echo -e "${YELLOW}⚠️ Rapport final non trouvé dans les logs${NC}"

echo -e "${BLUE}========================================${NC}"
echo ""

# Compter les JSON générés
json_loi_count=$(find "$SAMPLES_JSON_DIR/loi" -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
json_decret_count=$(find "$SAMPLES_JSON_DIR/decret" -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
total_json=$((json_loi_count + json_decret_count))

echo -e "${BLUE}📊 Résultat de l'extraction :${NC}"
echo -e "   Lois     : $json_loi_count JSON générés"
echo -e "   Décrets  : $json_decret_count JSON générés"
echo -e "   Total    : $total_json JSON générés"

if [ $total_json -gt 0 ]; then
    success_rate=$(awk "BEGIN {printf \"%.1f\", ($total_json / $total_ocr) * 100}")
    echo -e "   Taux     : ${success_rate}%"
    echo ""
    echo -e "${GREEN}✅ Régénération terminée avec succès !${NC}"
else
    echo ""
    echo -e "${RED}❌ Aucun JSON généré${NC}"
    exit 1
fi

# Calculer le nombre total d'articles
total_articles=0
for json_file in "$SAMPLES_JSON_DIR"/*/*.json; do
    if [ -f "$json_file" ]; then
        articles=$(grep -o '"number"' "$json_file" | wc -l | tr -d ' ')
        total_articles=$((total_articles + articles))
    fi
done

echo -e "   Articles : $total_articles extraits au total"
echo ""

# Afficher le top 5 des meilleures extractions
echo -e "${BLUE}🏆 Top 5 meilleures extractions (confiance) :${NC}"
find "$SAMPLES_JSON_DIR" -name "*.json" -exec sh -c '
    file="$1"
    confidence=$(grep -o "\"confidence\": [0-9.]*" "$file" | head -1 | cut -d" " -f2)
    basename=$(basename "$file" .json)
    articles=$(grep -o "\"number\"" "$file" | wc -l | tr -d " ")
    echo "$confidence $basename $articles"
' _ {} \; | sort -rn | head -5 | awk '{printf "   %s : %s articles, confiance %.2f\n", $2, $3, $1}'

echo ""
echo -e "${GREEN}✅ Script terminé${NC}"
