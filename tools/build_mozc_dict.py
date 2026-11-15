#!/usr/bin/env python3
"""Build the app's kana-kanji dictionary assets from the Mozc OSS dictionary.

Source (all from https://github.com/google/mozc, branch master):
  src/data/dictionary_oss/dictionary00.txt .. dictionary09.txt   reading, lid, rid, cost, surface
  src/data/dictionary_oss/connection_single_column.txt           N, then N*N costs
  src/data/dictionary_oss/id.def                                 id -> part of speech
  src/data/dictionary_oss/README.txt, LICENSE                    licenses
License: Mozc is BSD-3-Clause; its OSS dictionary derives from IPAdic (NAIST/ICOT terms) and the
Okinawa dictionary (public domain). Both texts end up in LICENSE-MOZC.txt next to the assets.

Connection orientation (checked against Mozc's Connector::GetTransitionCost(rid, lid) and against
the data, e.g. noun -> ます is costly while ます -> noun is not): the matrix is row-major
`cost[rid of the previous word][lid of the next word]`; id 0 is BOS/EOS.

Outputs (gzip, named *.gzip because the Android build unpacks *.gz assets), all little-endian:
  mozc-dict.bin.gzip   "PLMD", u32 version, u32 readings, u32 unknown-word id, u32 block size,
                       u32 cost step, 8 pad bytes, u32 block offset[ceil(readings / block)], then the
                       records sorted by reading in Java String (UTF-16 code unit) order, front
                       coded and restarted at every block: u8 shared chars with the previous reading,
                       u8 new chars, UTF-16LE new chars, u8 entries; per entry (cheapest first) u8 cost
                       / step, u16 flags|lid (bit 15: rid == lid, bits 13-14: 0 explicit surface,
                       1 surface == reading, 2 surface == katakana of reading), [u16 rid],
                       [u8 surface length, UTF-16LE surface].
  mozc-conn.bin.gzip   "PLMC", u32 version, u32 N, u32 step, u32 width (1 or 2), 12 pad bytes, then
                       N*N values; the cost is value * step. width 2 is lossless (int16 range),
                       width 1 is uint8 quantised with the given step.
  LICENSE-MOZC.txt

Pruning (defaults): readings up to 16 chars, at most 20 entries per reading, entries with cost > 8500
(the rarest ~20%, see --max-cost) dropped, SPELLING_CORRECTION entries dropped.

Usage: build_mozc_dict.py SRC_DIR OUT_DIR [--keep 20] [--max-cost 8500] [--conn-step 1] [--max-reading 16]
SRC_DIR holds the files above; missing ones are downloaded. Regenerate and commit the outputs.
"""
import argparse
import gzip
import os
import struct
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/google/mozc/master/"
FILES = (["src/data/dictionary_oss/dictionary%02d.txt" % i for i in range(10)]
         + ["src/data/dictionary_oss/connection_single_column.txt",
            "src/data/dictionary_oss/id.def", "src/data/dictionary_oss/README.txt", "LICENSE"])
UNKNOWN_POS = "名詞,一般,*,*,*,*,*"
MAX_SURFACE = 60
BLOCK = 8  # readings per index block
COST_STEP = 50  # word costs are stored as one byte: cost / COST_STEP (Mozc costs are 500 * ln(1/p))


def fetch(src):
    os.makedirs(src, exist_ok=True)
    for f in FILES:
        path = os.path.join(src, os.path.basename(f))
        if not os.path.isfile(path):
            print("downloading", f)
            urllib.request.urlretrieve(BASE + f, path)


def load_entries(src, max_reading):
    best = {}  # (reading, surface, lid, rid) -> min cost
    for i in range(10):
        with open(os.path.join(src, "dictionary%02d.txt" % i), encoding="utf-8") as f:
            for line in f:
                p = line.rstrip("\n").split("\t")
                if len(p) != 5:  # extra column marks e.g. SPELLING_CORRECTION entries
                    continue
                reading, lid, rid, cost, surface = p
                if u16len(reading) > max_reading or not surface or u16len(surface) > MAX_SURFACE:
                    continue
                k = (reading, surface, int(lid), int(rid))
                c = int(cost)
                if k not in best or c < best[k]:
                    best[k] = c
    return best


def u16len(s):
    return len(s.encode("utf-16-le")) // 2


def katakana(s):
    return "".join(chr(ord(c) + 0x60) if "ぁ" <= c <= "ゖ" else c for c in s)


def build_dict(best, keep, unknown_id, max_cost):
    by_reading = {}
    for (reading, surface, lid, rid), cost in best.items():
        if cost <= max_cost:
            by_reading.setdefault(reading, []).append((cost, surface, lid, rid))
    readings = sorted(by_reading, key=lambda r: r.encode("utf-16-be"))
    records = []
    kept = 0
    for i, r in enumerate(readings):
        es = sorted(by_reading[r])[:keep]
        kept += len(es)
        # Lengths are UTF-16 code units, as Java reads them (a non-BMP character counts as 2).
        ru = r.encode("utf-16-le")
        shared = 0
        if i % BLOCK:  # front coding against the previous reading, restarted at every block
            pu = readings[i - 1].encode("utf-16-le")
            while 2 * shared < min(len(pu), len(ru)) and pu[2 * shared:2 * shared + 2] == ru[2 * shared:2 * shared + 2]:
                shared += 1
        b = bytearray([shared, len(ru) // 2 - shared]) + ru[2 * shared:] + bytes([len(es)])
        for cost, surface, lid, rid in es:
            kind = 1 if surface == r else 2 if surface == katakana(r) else 0
            assert lid < 8192
            b += struct.pack("<BH", min(255, (cost + COST_STEP // 2) // COST_STEP), lid | kind << 13 | (rid == lid) << 15)
            if rid != lid:
                b += struct.pack("<H", rid)
            if kind == 0:
                su = surface.encode("utf-16-le")
                b += bytes([len(su) // 2]) + su
        records.append(bytes(b))
    nblocks = (len(readings) + BLOCK - 1) // BLOCK
    out = bytearray(b"PLMD" + struct.pack("<IIIII", 1, len(readings), unknown_id, BLOCK, COST_STEP) + bytes(8))
    off = 32 + 4 * nblocks
    for i, rec in enumerate(records):
        if i % BLOCK == 0:
            out += struct.pack("<I", off)
        off += len(rec)
    for rec in records:
        out += rec
    return bytes(out), len(readings), kept


def build_conn(src, step):
    toks = open(os.path.join(src, "connection_single_column.txt")).read().split()
    n = int(toks[0])
    vals = [int(t) for t in toks[1:]]
    assert len(vals) == n * n
    if step == 1:
        assert max(vals) < 32768
        body = struct.pack("<%dH" % len(vals), *vals)
        width = 2
    else:
        q = [min(255, (v + step // 2) // step) for v in vals]
        body = bytes(q)
        width = 1
    return b"PLMC" + struct.pack("<IIII", 1, n, step, width) + bytes(12) + body, n


def gz(data, path):
    with open(path, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as g:
            g.write(data)
    return os.path.getsize(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--keep", type=int, default=20)
    ap.add_argument("--max-cost", type=int, default=8500)
    ap.add_argument("--conn-step", type=int, default=1)
    ap.add_argument("--max-reading", type=int, default=16)
    a = ap.parse_args()
    fetch(a.src)
    unknown_id = None
    for line in open(os.path.join(a.src, "id.def"), encoding="utf-8"):
        i, name = line.rstrip("\n").split(" ", 1)
        if name == UNKNOWN_POS:
            unknown_id = int(i)
            break
    assert unknown_id is not None
    os.makedirs(a.out, exist_ok=True)
    best = load_entries(a.src, a.max_reading)
    d, nread, kept = build_dict(best, a.keep, unknown_id, a.max_cost)
    dz = gz(d, os.path.join(a.out, "mozc-dict.bin.gzip"))
    c, n = build_conn(a.src, a.conn_step)
    cz = gz(c, os.path.join(a.out, "mozc-conn.bin.gzip"))
    with open(os.path.join(a.out, "LICENSE-MOZC.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(open(os.path.join(a.src, "LICENSE"), encoding="utf-8").read().rstrip("\n"))
        f.write("\n\n" + "=" * 79 + "\nMozc OSS dictionary README (IPAdic / Okinawa dictionary licenses)\n"
                + "=" * 79 + "\n\n")
        f.write(open(os.path.join(a.src, "README.txt"), encoding="utf-8").read().rstrip("\n") + "\n")
    print("entries before prune %d, kept %d in %d readings (keep=%d, max-reading=%d)"
          % (len(best), kept, nread, a.keep, a.max_reading))
    print("dict: raw %d B, gzip %d B; conn N=%d step=%d: raw %d B, gzip %d B; total gzip %d B"
          % (len(d), dz, n, a.conn_step, len(c), cz, dz + cz))


if __name__ == "__main__":
    sys.exit(main())
