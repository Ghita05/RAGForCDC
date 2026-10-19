#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Évalue la recherche sémantique sur le corpus synthétique.

Les questions sont construites à partir de l'OBJET du document (contenu
distinctif) et non de sa référence : "CDC-2023-102" n'a quasiment aucun sens
pour un modèle d'embedding, donc toutes les questions deviendraient identiques.

Mesure le recall@k en DOCUMENTS DISTINCTS, car plusieurs chunks d'un même
document peuvent occuper plusieurs places du top-k.
"""
import csv, json, subprocess, sys
from collections import Counter

K = int(sys.argv[1]) if len(sys.argv) > 1 else 10


def search(question, k):
    out = subprocess.run(
        ["curl", "-s", "-X", "POST", f"http://localhost:8080/api/search?k={k}",
         "--data-urlencode", f"question={question}"],
        capture_output=True, text=True).stdout
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return []


rows = list(csv.DictReader(open("corpus/manifest.csv", encoding="utf-8")))
hits = rr = 0
by_ext, by_ext_hit, hub = Counter(), Counter(), Counter()

for r in rows:
    docs = []
    for h in search(r["objet"], K):
        if h["filename"] not in docs:
            docs.append(h["filename"])
    hub.update(docs)
    ext = r["filename"].rsplit(".", 1)[-1]
    by_ext[ext] += 1
    if r["filename"] in docs:
        rank = docs.index(r["filename"]) + 1
        hits += 1; rr += 1 / rank; by_ext_hit[ext] += 1
        print(f"HIT  rang {rank:2d}  {r['ref']}  {r['objet'][:52]}")
    else:
        print(f"MISS         {r['ref']}  {r['objet'][:52]}")

n = len(rows)
print(f"\nrecall@{K} (documents distincts) = {hits}/{n} = {hits/n:.0%}")
print(f"MRR = {rr/n:.3f}")
for e in sorted(by_ext):
    print(f"  {e}: {by_ext_hit[e]}/{by_ext[e]}")
print("\nDocuments les plus renvoyés (hubs) :")
for f, c in hub.most_common(5):
    print(f"  {c:3d}  {f}")
