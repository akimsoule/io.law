#!/bin/bash

# Script pour générer et afficher le rapport de couverture JaCoCo
# Usage: ./scripts/coverage-report.sh [--html]

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# Couleurs
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📊 GÉNÉRATION DU RAPPORT DE COUVERTURE JACOCO${NC}"
echo "=========================================="
echo ""

# Générer les rapports JaCoCo
echo -e "${YELLOW}🔄 Exécution des tests et génération des rapports...${NC}"
mvn clean test jacoco:report -Dmaven.test.failure.ignore=true -q

echo ""
echo -e "${GREEN}✅ Rapports générés avec succès${NC}"
echo ""
echo -e "${BLUE}📊 STATISTIQUES DE COUVERTURE PAR MODULE${NC}"
echo "=========================================="
echo ""

# Fonction pour extraire les stats d'un fichier jacoco.xml
extract_coverage() {
    local jacoco_file="$1"
    local module_name="$2"
    
    if [ ! -f "$jacoco_file" ]; then
        return
    fi
    
    # Extraire les valeurs avec grep/sed (compatible tous systèmes)
    local line_covered=$(grep 'counter type="LINE"' "$jacoco_file" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/')
    local line_missed=$(grep 'counter type="LINE"' "$jacoco_file" | head -1 | sed 's/.*missed="\([0-9]*\)".*/\1/')
    local branch_covered=$(grep 'counter type="BRANCH"' "$jacoco_file" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/')
    local branch_missed=$(grep 'counter type="BRANCH"' "$jacoco_file" | head -1 | sed 's/.*missed="\([0-9]*\)".*/\1/')
    local class_covered=$(grep 'counter type="CLASS"' "$jacoco_file" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/')
    local class_missed=$(grep 'counter type="CLASS"' "$jacoco_file" | head -1 | sed 's/.*missed="\([0-9]*\)".*/\1/')
    
    if [ -z "$line_covered" ] || [ -z "$line_missed" ]; then
        return
    fi
    
    local line_total=$((line_covered + line_missed))
    local branch_total=$((branch_covered + branch_missed))
    local class_total=$((class_covered + class_missed))
    
    local line_percent=0
    local branch_percent=0
    local class_percent=0
    
    if [ $line_total -gt 0 ]; then
        line_percent=$(echo "scale=1; ($line_covered * 100) / $line_total" | bc)
    fi
    
    if [ $branch_total -gt 0 ]; then
        branch_percent=$(echo "scale=1; ($branch_covered * 100) / $branch_total" | bc)
    fi
    
    if [ $class_total -gt 0 ]; then
        class_percent=$(echo "scale=1; ($class_covered * 100) / $class_total" | bc)
    fi
    
    # Couleur selon le taux de couverture
    local color=$RED
    if (( $(echo "$line_percent >= 80" | bc -l) )); then
        color=$GREEN
    elif (( $(echo "$line_percent >= 60" | bc -l) )); then
        color=$YELLOW
    fi
    
    printf "${color}%-45s${NC} | Lines: %5s%% (%4d/%4d) | Branches: %5s%% (%3d/%3d) | Classes: %5s%% (%2d/%2d)\n" \
           "$module_name" "$line_percent" "$line_covered" "$line_total" \
           "$branch_percent" "$branch_covered" "$branch_total" \
           "$class_percent" "$class_covered" "$class_total"
}

# Parcourir tous les modules
total_line_covered=0
total_line_missed=0
total_branch_covered=0
total_branch_missed=0

for jacoco in $(find . -name "jacoco.xml" -path "*/target/site/jacoco/*" | sort); do
    module=$(echo "$jacoco" | sed 's|./||' | sed 's|/target/site/jacoco/jacoco.xml||')
    extract_coverage "$jacoco" "$module"
    
    # Accumuler les totaux
    line_covered=$(grep 'counter type="LINE"' "$jacoco" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/')
    line_missed=$(grep 'counter type="LINE"' "$jacoco" | head -1 | sed 's/.*missed="\([0-9]*\)".*/\1/')
    branch_covered=$(grep 'counter type="BRANCH"' "$jacoco" | head -1 | sed 's/.*covered="\([0-9]*\)".*/\1/')
    branch_missed=$(grep 'counter type="BRANCH"' "$jacoco" | head -1 | sed 's/.*missed="\([0-9]*\)".*/\1/')
    
    total_line_covered=$((total_line_covered + line_covered))
    total_line_missed=$((total_line_missed + line_missed))
    total_branch_covered=$((total_branch_covered + branch_covered))
    total_branch_missed=$((total_branch_missed + branch_missed))
done

# Calculer les totaux globaux
total_line=$((total_line_covered + total_line_missed))
total_branch=$((total_branch_covered + total_branch_missed))

if [ $total_line -gt 0 ]; then
    total_line_percent=$(echo "scale=1; ($total_line_covered * 100) / $total_line" | bc)
else
    total_line_percent="0.0"
fi

if [ $total_branch -gt 0 ]; then
    total_branch_percent=$(echo "scale=1; ($total_branch_covered * 100) / $total_branch" | bc)
else
    total_branch_percent="0.0"
fi

echo ""
echo "=========================================="
printf "${BLUE}%-45s${NC} | Lines: %5s%% (%4d/%4d) | Branches: %5s%% (%3d/%3d)\n" \
       "TOTAL PROJET" "$total_line_percent" "$total_line_covered" "$total_line" \
       "$total_branch_percent" "$total_branch_covered" "$total_branch"
echo ""

# Afficher les chemins des rapports HTML
echo -e "${BLUE}📁 RAPPORTS HTML DÉTAILLÉS${NC}"
echo "=========================================="
echo ""

for html in $(find . -name "index.html" -path "*/site/jacoco/index.html" | sort); do
    module=$(echo "$html" | sed 's|./||' | sed 's|/target/site/jacoco/index.html||')
    abs_path="$PROJECT_ROOT/$(echo $html | sed 's|^\./||')"
    echo -e "  ${GREEN}${module}${NC}"
    echo -e "    file://${abs_path}"
    echo ""
done

# Option pour ouvrir dans le navigateur
if [[ "$1" == "--html" ]]; then
    echo -e "${YELLOW}🌐 Ouverture des rapports dans le navigateur...${NC}"
    for html in $(find . -name "index.html" -path "*/site/jacoco/index.html" | head -3); do
        abs_path="$PROJECT_ROOT/$(echo $html | sed 's|^\./||')"
        open "file://${abs_path}" 2>/dev/null || xdg-open "file://${abs_path}" 2>/dev/null || echo "  Veuillez ouvrir manuellement: file://${abs_path}"
    done
fi

echo ""
echo -e "${GREEN}✅ Rapport de couverture terminé${NC}"
echo ""
echo -e "💡 ${YELLOW}Conseil${NC}: Utilisez ${BLUE}./scripts/coverage-report.sh --html${NC} pour ouvrir les rapports dans le navigateur"
