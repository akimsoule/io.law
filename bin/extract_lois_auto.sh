#!/usr/bin/env sh
# extract_lois_auto.sh
# Scanne "zip/data/pdfs/loi" et copie automatiquement UNE loi par année
# directement dans ./loi/ (pas de sous-répertoires).

set -eu

SRC="zip/data/pdfs/loi"
DEST_ROOT="./loi"

# Vérifier que le répertoire source existe
if [ ! -d "$SRC" ]; then
  printf "Répertoire source introuvable : %s\n" "$SRC" >&2
  exit 1
fi

mkdir -p "$DEST_ROOT"

printf "Scan de %s...\n" "$SRC"

# Récupérer la liste des années distinctes présentes dans les noms de fichiers
unique_years="$(find "$SRC" -type f -iname '*.pdf' -print0 | xargs -0 -n1 basename 2>/dev/null | grep -Eo '19[0-9]{2}|20[0-9]{2}' | sort -u || true)"

if [ -z "$(printf "%s" "$unique_years" | sed -n '1p')" ]; then
  printf "Aucune année trouvée dans les noms des fichiers. Rien à copier.\n"
  exit 0
fi

copied=0
skipped=0

# Pour chaque année, prendre le premier fichier trouvé contenant l'année et le copier
echo "--- Copie d'une loi par année dans $DEST_ROOT ---"
# compter les années distinctes
unique_count="$(printf "%s\n" "$unique_years" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')"

# itérer sur chaque année (for reste dans le shell courant)
for year in $unique_years; do
  [ -z "$year" ] && continue
  dst="$DEST_ROOT/loi-$year.pdf"
  if [ -e "$dst" ]; then
    printf "Déjà présent: %s (année %s)\n" "$dst" "$year"
    skipped=$((skipped + 1))
    continue
  fi
  # trouver le premier fichier correspondant
  src_file="$(find "$SRC" -type f -iname "*$year*.pdf" -print -quit 2>/dev/null || true)"
  if [ -n "$src_file" ]; then
    cp "$src_file" "$dst"
    printf "Copié: %s -> %s\n" "$(basename "$src_file")" "$dst"
    copied=$((copied + 1))
  else
    printf "Aucun fichier trouvé pour l'année %s\n" "$year"
  fi
done

# Compter les fichiers sans année
files_total="$(find "$SRC" -type f -iname '*.pdf' 2>/dev/null | wc -l | tr -d ' ')"
files_with_year="$(find "$SRC" -type f -iname '*.pdf' -print0 | xargs -0 -n1 basename 2>/dev/null | grep -E '19[0-9]{2}|20[0-9]{2}' | wc -l | tr -d ' ' || true)"
files_without_year=$((files_total - files_with_year))


printf "\nRésumé :\n"
printf "  - années distinctes trouvées : %s\n" "$unique_count"
printf "  - PDFs trouvés : %s\n" "$files_total"
printf "  - copiés (une par année) : %s\n" "$copied"
printf "  - déjà présents (skippés) : %s\n" "$skipped"
printf "  - sans année (ignorés) : %s\n" "$files_without_year"

printf "Terminé. Les fichiers sont dans %s\n" "$DEST_ROOT"
