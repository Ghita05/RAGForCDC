#!/bin/bash
# recall@k : pour chaque document, une question dont la réponse n'existe que dans ce document.
K=${1:-5}
hit=0; total=0
declare -A by_ext_hit by_ext_tot

while IFS=$'\t' read -r filename ref typ; do
  q="Quel est le délai d'exécution et la pénalité de retard prévus par le marché $ref ?"
  srcs=$(curl -s -X POST "localhost:8080/api/search?k=$K" \
         --data-urlencode "question=$q" | jq -r '.[].filename' | tr '\n' ' ')
  ext="${filename##*.}"
  total=$((total+1)); by_ext_tot[$ext]=$(( ${by_ext_tot[$ext]:-0} + 1 ))
  if [[ "$srcs" == *"$filename"* ]]; then
    hit=$((hit+1)); by_ext_hit[$ext]=$(( ${by_ext_hit[$ext]:-0} + 1 )); echo "HIT  $ref"
  else
    echo "MISS $ref -> $srcs"
  fi
done < <(python -c "
import csv
for r in csv.DictReader(open('corpus/manifest.csv', encoding='utf-8')):
    print(r['filename'], r['ref'], r['cdc_type'], sep='\t')
")

echo
echo "recall@$K = $hit/$total"
for e in "${!by_ext_tot[@]}"; do echo "  $e: ${by_ext_hit[$e]:-0}/${by_ext_tot[$e]}"; done
