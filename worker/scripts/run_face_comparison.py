#!/usr/bin/env python3
"""
CLI nástroj: porovná selfie s portrétem na občanském průkazu nebo pasu.

Použití:
    python3 run_face_comparison.py <document_image> <selfie_image>
    python3 run_face_comparison.py id_card.jpg selfie.jpg
    python3 run_face_comparison.py passport.jpg selfie.jpg --threshold 0.40
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from src.biometrics.face_comparison import COSINE_THRESHOLD, compare_faces


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Porovná selfie s portrétem na občanském průkazu nebo pasu."
    )
    parser.add_argument("document", help="Cesta k obrázku občanky / pasu")
    parser.add_argument("selfie",   help="Cesta k selfie / liveness fotografii")
    parser.add_argument(
        "--threshold",
        type=float,
        default=COSINE_THRESHOLD,
        help=f"Rozhodovací práh kosinové podobnosti (výchozí {COSINE_THRESHOLD})",
    )
    args = parser.parse_args()

    print(f"\nDoklad : {args.document}")
    print(f"Selfie : {args.selfie}")
    print(f"Práh   : {args.threshold}\n")

    result = compare_faces(args.document, args.selfie, threshold=args.threshold)

    print(f"Obličej na dokladu detekován : {'ANO' if result.document_face_detected else 'NE'}")
    print(f"Obličej na selfie detekován  : {'ANO' if result.selfie_face_detected   else 'NE'}")
    print(f"Kosinová podobnost            : {result.cosine_similarity:.4f}  (práh {result.threshold})")
    print(f"Spolehlivost                  : {result.confidence:.3f}")
    print()

    verdict = "SHODA" if result.match else "NESHODA"
    print(f"Výsledek : {verdict}")
    print(f"Důvod    : {result.reason}")
    print()

    sys.exit(0 if result.match else 1)


if __name__ == "__main__":
    main()
