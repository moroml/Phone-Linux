#!/usr/bin/env python3
"""Convert SKK-JISYO.L into the app's compact dictionary asset.

Source:  https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.L
         (EUC-JP, GPLv2-or-later, see the generated LICENSE-SKK-JISYO.txt)

Output (gzip, saved as .tsv.gzip because the Android build unpacks *.gz assets): UTF-8, one line per reading `reading<TAB>cand1/cand2/...`, sorted by reading in
Java String (UTF-16 code unit) order. Okuri-ari entries (reading ends with an ASCII letter,
e.g. `かk`) and okuri-nasi entries share the one sorted file. `;annotations` are stripped and
SKK lisp candidates such as `(concat ...)` are dropped.

Usage: build_skk_dict.py SKK-JISYO.L out.tsv.gzip license.txt
"""
import gzip
import re
import sys

LISP = re.compile(r"^\([a-z]")


def main(src, out, lic):
    with open(src, "rb") as f:
        lines = f.read().decode("euc-jp").split("\n")

    header = []
    for line in lines:
        if line.startswith(";; okuri-ari entries."):
            break
        header.append(line)
    with open(lic, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(header).rstrip("\n") + "\n")

    entries = {}
    for line in lines:
        if not line or line.startswith(";"):
            continue
        reading, sep, rest = line.partition(" ")
        if not sep or reading.startswith("#") or "\t" in reading:
            continue
        cands = []
        for c in rest.strip().rstrip("/").lstrip("/").split("/"):
            c = c.split(";", 1)[0]
            if c and not LISP.match(c) and "\t" not in c and c not in cands:
                cands.append(c)
        if cands:
            merged = entries.setdefault(reading, [])
            merged.extend(c for c in cands if c not in merged)

    with gzip.GzipFile(out, "wb", compresslevel=9, mtime=0) as f:
        for reading in sorted(entries, key=lambda s: s.encode("utf-16-be")):
            f.write(f"{reading}\t{'/'.join(entries[reading])}\n".encode("utf-8"))
    print(len(entries), "readings")


if __name__ == "__main__":
    main(*sys.argv[1:4])
