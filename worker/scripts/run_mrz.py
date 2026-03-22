#!/usr/bin/env python3
"""
Ruční nástroj pro čtení MRZ z českých dokladů.

Načte MRZ data z obrázku dokladu a vypíše naparsovaná pole.
Podporuje české občanské průkazy (TD1) s volitelným OCR přední strany
a obecné cestovní doklady (pasy, TD3).

Použití:
    # Obecný doklad nebo občanka (pouze zadní strana):
    python3 scripts/run_mrz.py <image>

    # Občanka i s přední stranou (doplní místo narození):
    python3 scripts/run_mrz.py <back_image> --front <front_image>
"""

import argparse
import sys
from pathlib import Path

# Umožní spouštění odkudkoli ukotvením na kořen projektu
sys.path.insert(0, str(Path(__file__).parent.parent))

try:
    from src.document.mrz_reader import read_mrz_enhanced, clean_name, correct_country_code
    from src.document.czech_id import read_czech_id, parse_czech_id, CzechIDResult
except ModuleNotFoundError as exc:
    if exc.name in ("passporteye", "cv2", "src"):
        print("[ERROR] Chybí závislost. Nejprve aktivujte venv:")
        print('  source ".venv/bin/activate"')
        print("  pip install -r requirements.txt")
        sys.exit(1)
    raise


# ---------------------------------------------------------------------------
# Příkazová řádka
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("image", help="Obrázek dokladu (zadní strana nebo libovolná stránka s MRZ)")
    p.add_argument("--front", metavar="IMAGE",
                   help="Obrázek přední strany občanky (doplní místo narození)")
    return p


def main() -> None:
    args = _build_parser().parse_args()

    path = Path(args.image)
    if not path.exists():
        print(f"[ERROR] Soubor nebyl nalezen: {path}")
        sys.exit(1)

    front_path: str | None = None
    if args.front:
        fp = Path(args.front)
        if not fp.exists():
            print(f"[ERROR] Obrázek přední strany nebyl nalezen: {fp}")
            sys.exit(1)
        front_path = str(fp)

    print(f"\n[*] Čtu: {path.resolve()}")
    print("[*] Zkouším detekční strategie...")
    print("-" * 60)

    # MRZ se detekuje pouze jednou - sdílené pro větev občanky i obecnou větev.
    mrz = read_mrz_enhanced(str(path))

    print("-" * 60)

    if mrz is None:
        print("[FAIL] MRZ nebyla detekována.")
        print("       Tipy:")
        print("         - Ujistěte se, že je MRZ zóna viditelná a není oříznutá")
        print("         - Použijte sken ve vyšším rozlišení (300 DPI+)")
        print("         - Ověřte, že je nainstalovaný Tesseract: tesseract --version")
        return

    # Nejprve zkusit parser občanky; pokud nejde o TD1/CZE, použít obecný výstup.
    try:
        czech = parse_czech_id(mrz)
        from src.document.czech_id_face import read_czech_id_face
        from src.document.czech_id import _merge_face_fields

        back_face  = read_czech_id_face(str(path))
        front_face = read_czech_id_face(front_path) if front_path else None
        face       = _merge_face_fields(back_face, front_face)

        if face:
            czech = parse_czech_id(mrz, face)

        _print_czech_id(czech)
        return

    except ValueError:
        pass  # nejde o český TD1 doklad - pokračuj na obecný výstup

    _print_generic(mrz)


# ---------------------------------------------------------------------------
# Pomocné funkce výstupu
# ---------------------------------------------------------------------------
def _print_czech_id(r: CzechIDResult) -> None:
    print(f"\nČeský občanský průkaz  (spolehlivost: {r.confidence.upper()})")
    print()
    print(f"  Příjmení           : {r.surname}")
    print(f"  Jména              : {r.given_names}")
    print(f"  Datum narození     : {r.date_of_birth}")
    print(f"  Pohlaví            : {r.sex}")
    print(f"  Číslo dokladu      : {r.document_number}")
    print(f"  Platnost do        : {r.expiration_date}")
    print(f"  Rodné číslo        : {r.national_number or 'nedetekováno'}")
    print(f"  Místo narození     : {r.place_of_birth or 'nedetekováno'}")
    print(f"  Adresa             : {r.address or 'nedetekováno'}")
    if r.barcodes:
        print()
        print("  Čárové kódy:")
        for bc in r.barcodes:
            print(f"    [{bc['format']}] {bc['text']}")
    if r.confidence_notes:
        print()
        print("  Poznámky:")
        for note in r.confidence_notes:
            print(f"    - {note}")
    print("-" * 60)


def _print_generic(mrz) -> None:
    d = mrz.to_dict()
    print(f"\nNejlepší výsledek  (valid_score: {d.get('valid_score')} / 100)")
    print()
    print(f"  Typ MRZ       : {d.get('mrz_type')}")
    print(f"  Číslo dokladu : {d.get('number')}  ({'OK' if d.get('valid_number') else 'FAIL'})")
    print(f"  Typ           : {d.get('type')}")
    print(f"  Země          : {correct_country_code(d.get('country', ''))}")
    print(f"  Národnost     : {correct_country_code(d.get('nationality', ''))}")
    print(f"  Příjmení      : {clean_name(d.get('surname', ''))}")
    print(f"  Jména         : {clean_name(d.get('names', ''))}")
    print(f"  Pohlaví       : {d.get('sex')}")
    print(f"  Datum nar.    : {d.get('date_of_birth')}  ({'OK' if d.get('valid_date_of_birth') else 'FAIL'})")
    print(f"  Platnost do   : {d.get('expiration_date')}  ({'OK' if d.get('valid_expiration_date') else 'FAIL'})")
    print(f"  Osobní číslo  : {d.get('personal_number', 'N/A')}")
    print()
    print(f"  Souhrnný kontrolní součet : {'OK' if d.get('valid_composite') else 'FAIL'}")
    print("-" * 60)


if __name__ == "__main__":
    main()
