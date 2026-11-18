#!/usr/bin/env python3
"""Yolda kanal listesi denetimi.

Her kanalın yayın adresini dener (master m3u8 / akış), açılma süresini ölçer. Ölü ya da yavaş olanlar için
herkese açık iptv-org Türkiye listesindeki aynı adlı kanalların adreslerini dener; çalışan en hızlısını seçer.
Çıktı: güncellenmiş liste (aynı biçimde, logo ve grup korunur) ve Markdown rapor.

Kullanım: python3 tools/kanal_kontrol.py kanallar/yolda.m3u cikti/yolda.m3u cikti/rapor.md
"""
import concurrent.futures as cf
import re
import sys
import time
import unicodedata
import urllib.request

ALT_URL = "https://iptv-org.github.io/iptv/countries/tr.m3u"
UA = "Mozilla/5.0 (Linux; Android 14) YoldaKanalKontrol/1.0"
TIMEOUT = 8
SLOW_MS = 2500  # bundan yavaşsa alternatif aranır


def parse(text):
    out, head = [], None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("#EXTINF"):
            head = line
        elif line and not line.startswith("#") and head:
            name = head.rsplit(",", 1)[-1].strip()
            out.append({"head": head, "name": name, "url": line})
            head = None
    return out


def norm(name):
    n = unicodedata.normalize("NFKD", name.lower().replace("ı", "i"))
    n = "".join(c for c in n if not unicodedata.combining(c))
    n = re.sub(r"\((.*?)\)|\[(.*?)\]", "", n)
    n = re.sub(r"\b(hd|fhd|sd|tv|1080p|720p|480p|turkiye|turkey|tr)\b", "", n)
    return re.sub(r"[^a-z0-9]", "", n)


def probe(url):
    """(çalışıyor mu, süre ms). Oynatma listesi gelirse çalışıyor sayılır."""
    t0 = time.monotonic()
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            data = r.read(4096)
            ms = int((time.monotonic() - t0) * 1000)
            ctype = r.headers.get("Content-Type", "")
            ok = (b"#EXTM3U" in data) or ("audio" in ctype) or ("mpegurl" in ctype.lower()) or ("octet" in ctype)
            return ok, ms
    except Exception:
        return False, int((time.monotonic() - t0) * 1000)


def main(src, dst, report):
    chans = parse(open(src, encoding="utf-8").read())
    try:
        alt_text = urllib.request.urlopen(urllib.request.Request(ALT_URL, headers={"User-Agent": UA}), timeout=30).read().decode("utf-8", "replace")
        alts = parse(alt_text)
    except Exception as ex:
        print("Alternatif liste alınamadı:", ex)
        alts = []
    by_name = {}
    for a in alts:
        by_name.setdefault(norm(a["name"]), []).append(a["url"])

    with cf.ThreadPoolExecutor(16) as ex:
        res = list(ex.map(lambda c: probe(c["url"]), chans))

    rows, changed = [], 0
    for c, (ok, ms) in zip(chans, res):
        best_url, best_ms, note = c["url"], ms if ok else None, "çalışıyor" if ok else "ÇALIŞMIYOR"
        if (not ok) or (ms > SLOW_MS):
            cands = [u for u in by_name.get(norm(c["name"]), []) if u != c["url"]]
            if cands:
                with cf.ThreadPoolExecutor(8) as ex:
                    pr = list(ex.map(probe, cands))
                good = sorted([(m, u) for u, (o, m) in zip(cands, pr) if o])
                if good and ((not ok) or (good[0][0] < ms * 0.6)):
                    best_ms, best_url = good[0]
                    note = ("değiştirildi (ölüydü)" if not ok else f"değiştirildi ({ms} → {best_ms} ms)")
                    changed += 1
        c["url"] = best_url
        rows.append((c["name"], ms if ok else None, best_ms, note))

    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        f.write("#EXTM3U\n")
        for c in chans:
            f.write(c["head"] + "\n" + c["url"] + "\n")

    dead = sum(1 for r in rows if r[2] is None)
    with open(report, "w", encoding="utf-8") as f:
        f.write(f"# Kanal denetimi\n\n{len(rows)} kanal · {changed} adres değişti · {dead} kanal hâlâ çalışmıyor\n\n")
        f.write("| Kanal | Eski (ms) | Yeni (ms) | Durum |\n|---|---|---|---|\n")
        for n, o, b, note in rows:
            f.write(f"| {n} | {o if o is not None else '—'} | {b if b is not None else '—'} | {note} |\n")
    print(f"{len(rows)} kanal, {changed} değişti, {dead} ölü")


if __name__ == "__main__":
    main(*sys.argv[1:4])
