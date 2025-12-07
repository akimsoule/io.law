#!/bin/bash
# Script d'analyse des extractions OCR → JSON
# Compare chaque JSON avec son fichier OCR source pour identifier corrections nécessaires

set -e

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SAMPLES_OCR_DIR="$MODULE_ROOT/src/test/resources/samples_ocr"
SAMPLES_JSON_DIR="$MODULE_ROOT/src/test/resources/samples_json"
REPORT_FILE="$MODULE_ROOT/ANALYSE_EXTRACTIONS.md"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}🔍 Analyse des Extractions OCR → JSON${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Initialiser le rapport
cat > "$REPORT_FILE" << 'EOF'
# 🔍 Analyse des Extractions OCR → JSON

**Date** : 6 décembre 2025  
**Objectif** : Identifier corrections et patterns manquants

---

## 📊 Vue d'ensemble

EOF

total_json=0
total_issues=0

# Analyser chaque JSON
for json_file in "$SAMPLES_JSON_DIR"/*/*.json; do
    if [ ! -f "$json_file" ]; then
        continue
    fi
    
    total_json=$((total_json + 1))
    
    # Extraire type et nom
    basename=$(basename "$json_file" .json)
    dirname=$(dirname "$json_file")
    type=$(basename "$dirname")
    
    # Fichier OCR correspondant
    ocr_file="$SAMPLES_OCR_DIR/$type/$basename.txt"
    
    if [ ! -f "$ocr_file" ]; then
        echo -e "${YELLOW}⚠️ OCR manquant : $basename${NC}"
        continue
    fi
    
    echo -e "${BLUE}🔄 Analyse : $basename${NC}"
    
    # Extraire métadonnées JSON
    confidence=$(grep -o '"confidence": [0-9.]*' "$json_file" | head -1 | cut -d' ' -f2)
    article_count=$(grep -o '"number"' "$json_file" | wc -l | tr -d ' ')
    has_title=$(grep -q '"title"' "$json_file" && echo "✅" || echo "❌")
    has_date=$(grep -q '"promulgationDate"' "$json_file" && echo "✅" || echo "❌")
    has_city=$(grep -q '"promulgationCity"' "$json_file" && echo "✅" || echo "❌")
    has_signatories=$(grep -q '"signatories"' "$json_file" && echo "✅" || echo "❌")
    
    # Analyser le texte OCR
    ocr_lines=$(wc -l < "$ocr_file" | tr -d ' ')
    
    # Chercher patterns manquants dans OCR
    issues=""
    
    # Check Article patterns
    if grep -qi "Articlc " "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'Articlc' au lieu de 'Article'"
        total_issues=$((total_issues + 1))
    fi
    
    if grep -qi "Arlicle " "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'Arlicle' au lieu de 'Article'"
        total_issues=$((total_issues + 1))
    fi
    
    if grep -qi "Articfe " "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'Articfe' au lieu de 'Article'"
        total_issues=$((total_issues + 1))
    fi
    
    # Check République patterns
    if grep -q "REPUBLIOUE" "$ocr_file"; then
        issues="${issues}\n  - ⚠️ 'REPUBLIOUE' détecté (déjà corrigé)"
    fi
    
    if grep -q "REPUBLIOU" "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'REPUBLIOU' au lieu de 'REPUBLIQUE'"
        total_issues=$((total_issues + 1))
    fi
    
    # Check Assemblée patterns
    if grep -q "ASSEÀABLÉE" "$ocr_file"; then
        issues="${issues}\n  - ⚠️ 'ASSEÀABLÉE' détecté (déjà toléré)"
    fi
    
    if grep -q "ASSEÎVIBLEÉ" "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'ASSEÎVIBLEÉ' au lieu de 'ASSEMBLÉE'"
        total_issues=$((total_issues + 1))
    fi
    
    # Check dates patterns
    if grep -qE "[0-9]{1,2} [a-zéû]+ [0-9]{4}" "$ocr_file" && [ "$has_date" = "❌" ]; then
        issues="${issues}\n  - ⚠️ Date potentielle non extraite"
    fi
    
    # Check ville patterns
    if grep -qi "Fait a Cotonou" "$ocr_file"; then
        issues="${issues}\n  - ⚠️ 'Fait a Cotonou' détecté (déjà toléré)"
    fi
    
    if grep -qi "Fait â Cotonou" "$ocr_file"; then
        issues="${issues}\n  - ❌ Erreur OCR : 'Fait â' au lieu de 'Fait à'"
        total_issues=$((total_issues + 1))
    fi
    
    # Écrire dans le rapport
    cat >> "$REPORT_FILE" << EOF

### $basename

| Métrique | Valeur |
|----------|--------|
| **Confiance** | $confidence |
| **Articles** | $article_count |
| **Titre** | $has_title |
| **Date** | $has_date |
| **Ville** | $has_city |
| **Signataires** | $has_signatories |
| **Lignes OCR** | $ocr_lines |

EOF

    if [ -n "$issues" ]; then
        echo -e "**Issues détectées** :${issues}" >> "$REPORT_FILE"
        echo -e "${RED}  ⚠️ Issues détectées${NC}"
    else
        echo -e "${GREEN}  ✅ Pas d'issues${NC}"
    fi
    
    echo "" >> "$REPORT_FILE"
done

# Résumé final
cat >> "$REPORT_FILE" << EOF

---

## 📊 Résumé

| Métrique | Valeur |
|----------|--------|
| **JSON analysés** | $total_json |
| **Issues détectées** | $total_issues |

EOF

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ Analyse terminée${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "JSON analysés  : $total_json"
echo -e "Issues trouvées : $total_issues"
echo ""
echo -e "Rapport généré : ${YELLOW}$REPORT_FILE${NC}"
