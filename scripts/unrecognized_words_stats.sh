#!/usr/bin/env zsh
set -euo pipefail

# Paths
ROOT_DIR=${0:a:h}/..
WORD_FILE="$ROOT_DIR/data/word_non_recognize.txt"
OCR_DIR="$ROOT_DIR/data/ocr"
OUT_CSV="$ROOT_DIR/data/word_non_recognize_stats.csv"

if [[ ! -f "$WORD_FILE" ]]; then
  echo "Error: $WORD_FILE not found" >&2
  exit 1
fi
if [[ ! -d "$OCR_DIR" ]]; then
  echo "Error: $OCR_DIR not found" >&2
  exit 1
fi

# Build a single stream of all OCR text (lowercased)
# Exclude binary, ensure UTF-8 normalization where possible
TMP_ALL=$(mktemp)
trap 'rm -f "$TMP_ALL"' EXIT

# Concatenate all .txt under OCR_DIR
find "$OCR_DIR" -type f -name '*.txt' -print0 | xargs -0 cat | tr '[:upper:]' '[:lower:]' > "$TMP_ALL"

# Prepare output CSV header
print -r -- "word,count" > "$OUT_CSV"

# For each word in the word file, count whole-word occurrences using awk
# Use word boundaries via regex (^|[^a-zA-ZÀ-ÿ])word([^a-zA-ZÀ-ÿ]|$)
while IFS= read -r word; do
  [[ -z "$word" ]] && continue
  # Escape regex special chars if any
  esc_word=$(printf '%s' "$word" | sed -E 's/([\\.^$|?*+()\[\]{}])/\\\1/g')
  count=$(awk -v w="$esc_word" 'BEGIN{IGNORECASE=1}
    {
      n=gsub(("(^|[^A-Za-zÀ-ÿ])" w "([^A-Za-zÀ-ÿ]|$)"), "&");
      c+=n;
    }
    END{print c+0}' "$TMP_ALL")
  print -r -- "$word,$count" >> "$OUT_CSV"
done < "$WORD_FILE"

# Show top 20 by count
print -r -- "Generated: $OUT_CSV"
print -r -- "Top 20:"
awk -F, 'NR>1{print $2"\t"$1}' "$OUT_CSV" | sort -nr | head -20 | awk '{printf "%8d  %s\n", $1, $2}'
