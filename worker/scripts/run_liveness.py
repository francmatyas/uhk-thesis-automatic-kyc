#!/usr/bin/env python3
"""
CLI nástroj pro ruční testování kontroly živosti.

Použití:
    python3 run_liveness.py face1.jpg face2.jpg face3.jpg face4.jpg
    python3 run_liveness.py *.jpg
"""
import sys
from pathlib import Path

# Umožní spouštění odkudkoli ukotvením na kořen projektu
sys.path.insert(0, str(Path(__file__).parent.parent))

from src.biometrics.liveness_check import check_liveness


def main() -> None:
    if len(sys.argv) < 2:
        print("Použití: python3 run_liveness.py <image1> [image2 ...]\n")
        print("Zadejte alespoň 4 snímky obličeje s různými směry hlavy")
        print("(např. pohled vlevo, vpravo, nahoru a přímo do kamery).")
        sys.exit(1)

    paths = sys.argv[1:]
    print(f"\nAnalyzuji {len(paths)} obrázků...\n")

    result = check_liveness(paths)

    # ---- Tabulka po jednotlivých snímcích -------------------------------
    print(f"{'#':<4} {'Soubor':<35} {'Oblič.':<6} {'Yaw':>7} {'Pitch':>7} {'Roll':>7}  Směr")
    print("-" * 80)
    for r in result.per_image:
        fname = Path(r.image_path).name
        face  = "ano" if r.face_detected else "NE"
        if r.face_detected:
            row = (
                f"{r.image_index:<4} {fname:<35} {face:<6} "
                f"{r.yaw:>6.1f}° {r.pitch:>6.1f}° {r.roll:>6.1f}°  {r.direction}"
            )
        else:
            note = r.error or "—"
            row = f"{r.image_index:<4} {fname:<35} {face:<6}  ({note})"
        print(row)

    # ---- Souhrn ---------------------------------------------------------
    print()
    print(f"Analyzované snímky : {result.images_analyzed}")
    print(f"Detekované obličeje: {result.images_with_face}")
    print(f"Rozsah yaw         : {result.yaw_range_deg}°")
    print(f"Rozsah pitch       : {result.pitch_range_deg}°")
    print(f"Zachycené směry    : {', '.join(result.distinct_directions) or '—'}")
    print(f"Spolehlivost       : {result.confidence:.3f}")
    print()

    verdict = "ŽIVÝ" if result.is_alive else "NENÍ ŽIVÝ"
    print(f"Výsledek : {verdict}")
    print(f"Důvod    : {result.reason}")
    print()

    sys.exit(0 if result.is_alive else 1)


if __name__ == "__main__":
    main()
