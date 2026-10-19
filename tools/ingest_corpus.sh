#!/bin/bash
# Ingère corpus/*.txt|.pdf|.docx listés dans corpus/manifest.csv via
# POST /api/ingest/file, avec le cdc_type de chaque ligne.
set -euo pipefail

HOST=${HOST:-http://localhost:8080}
ok=0; fail=0

while IFS=$'\t' read -r filename typ; do
  path="corpus/$filename"
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$HOST/api/ingest/file" \
         -F "file=@${path}" -F "cdcType=${typ}")
  if [ "$code" = "200" ]; then
    ok=$((ok+1)); echo "OK   $filename"
  else
    fail=$((fail+1)); echo "FAIL $filename (http $code)"
  fi
done < <(python3 -c "
import csv
for r in csv.DictReader(open('corpus/manifest.csv', encoding='utf-8')):
    print(r['filename'], r['cdc_type'], sep='\t')
")

echo
echo "ingestion : $ok ok, $fail échec(s)"
[ "$fail" -eq 0 ]
