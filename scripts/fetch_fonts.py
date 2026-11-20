#!/usr/bin/env python3
"""Берёт шрифты из репозитория google/fonts (SIL OFL), оставляет только с кириллицей,
копирует первые 50 в assets/www/fonts и пишет fonts.css + fonts.js.
Запуск: python3 scripts/fetch_fonts.py <папка google-fonts/ofl> <папка вывода>"""
import sys, os, glob, shutil, json
from fontTools.ttLib import TTFont

# (папка в google/fonts/ofl, отображаемое имя)
CANDIDATES = [
 ("roboto","Roboto"),("opensans","Open Sans"),("montserrat","Montserrat"),("ptsans","PT Sans"),
 ("ptserif","PT Serif"),("rubik","Rubik"),("nunito","Nunito"),("raleway","Raleway"),
 ("manrope","Manrope"),("ubuntu","Ubuntu"),("firasans","Fira Sans"),("exo2","Exo 2"),
 ("oswald","Oswald"),("playfairdisplay","Playfair Display"),("merriweather","Merriweather"),
 ("lora","Lora"),("robotoslab","Roboto Slab"),("comfortaa","Comfortaa"),("pacifico","Pacifico"),
 ("lobster","Lobster"),("lobstertwo","Lobster Two"),("caveat","Caveat"),("markscript","Marck Script"),
 ("neucha","Neucha"),("badscript","Bad Script"),("amaticsc","Amatic SC"),("pangolin","Pangolin"),
 ("kellyslab","Kelly Slab"),("pressstart2p","Press Start 2P"),("jura","Jura"),("play","Play"),
 ("russoone","Russo One"),("yesevaone","Yeseva One"),("underdog","Underdog"),("ruslandisplay","Ruslan Display"),
 ("poiretone","Poiret One"),("cormorantgaramond","Cormorant Garamond"),("alice","Alice"),
 ("philosopher","Philosopher"),("stalinistone","Stalinist One"),("seymourone","Seymour One"),
 ("bellota","Bellota"),("scada","Scada"),("arimo","Arimo"),("tinos","Tinos"),("rubikmonoone","Rubik Mono One"),
 ("ledger","Ledger"),("oldstandardtt","Old Standard TT"),("forum","Forum"),("podkova","Podkova"),
 ("cuprum","Cuprum"),("andika","Andika"),("kurale","Kurale"),("spectral","Spectral"),
 ("ibmplexsans","IBM Plex Sans"),("ibmplexserif","IBM Plex Serif"),("ibmplexmono","IBM Plex Mono"),
 ("sourcesans3","Source Sans 3"),("notoserif","Noto Serif"),("balsamiqsans","Balsamiq Sans"),
 ("yanonekaffeesatz","Yanone Kaffeesatz"),("rubikmonoone","Rubik Mono One"),("tenorsans","Tenor Sans"),
 ("fixelheading","Fixel"),("unbounded","Unbounded"),("jetbrainsmono","JetBrains Mono"),
 ("anonymouspro","Anonymous Pro"),("bitter","Bitter"),("literata","Literata"),("vollkorn","Vollkorn"),
 ("marmelad","Marmelad"),("rubikbubbles","Rubik Bubbles"),("rubikglitch","Rubik Glitch"),
 ("ibarrarealnova","Ibarra Real Nova"),("gabriela","Gabriela"),("tektur","Tektur"),
]
TARGET = 50

def has_cyr(path):
    try:
        cm = TTFont(path, lazy=True).getBestCmap()
        return all(c in cm for c in (0x410, 0x416, 0x42F, 0x430, 0x44F, 0x451))
    except Exception:
        return False

def pick(d):
    files = sorted(glob.glob(os.path.join(d, "*.ttf")))
    if not files: return None
    pref = [f for f in files if "Italic" not in os.path.basename(f)]
    files = pref or files
    for key in ("-Regular", "[wght]", "Regular"):
        for f in files:
            if key in os.path.basename(f): return f
    return files[0]

def main(src, dst):
    os.makedirs(dst, exist_ok=True)
    css, names, seen = [], [], set()
    for folder, name in CANDIDATES:
        if len(names) >= TARGET: break
        if name in seen: continue
        f = pick(os.path.join(src, folder))
        if not f or not has_cyr(f):
            print("skip", name); continue
        seen.add(name)
        out = "f%02d.ttf" % (len(names) + 1)
        shutil.copy(f, os.path.join(dst, out))
        weight = "100 900" if "[" in os.path.basename(f) else "400"
        css.append("@font-face{font-family:'%s';src:url('%s') format('truetype');font-weight:%s;font-display:swap}" % (name, out, weight))
        names.append(name)
        print("ok  ", name, "<-", os.path.basename(f))
    if len(names) < TARGET:
        sys.exit("ERROR: найдено только %d шрифтов из %d" % (len(names), TARGET))
    open(os.path.join(dst, "fonts.css"), "w", encoding="utf-8").write("\n".join(css) + "\n")
    open(os.path.join(dst, "fonts.js"), "w", encoding="utf-8").write("window.RIN_FONTS = %s;\n" % json.dumps(names, ensure_ascii=False))
    print("Готово:", len(names), "шрифтов")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
