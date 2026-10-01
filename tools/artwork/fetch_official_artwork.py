#!/usr/bin/env python3
"""Optional desktop import helper. Requires internet; never called by Android at runtime.

Usage: python3 tools/artwork/fetch_official_artwork.py --out ./downloaded-artwork
Then use Figma > Place image on the GOLDEN artwork instances manually or via upload_assets.
No downloaded art is bundled in this source ZIP.
"""
import argparse
import csv
from pathlib import Path
from urllib.error import URLError, HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

MANIFEST = Path(__file__).with_name('official_artwork_manifest.csv')


def fetch(url: str, target: Path) -> bool:
    p = urlsplit(url)
    if p.scheme != 'https' or p.hostname != 'raw.githubusercontent.com' or not p.path.startswith('/PokeAPI/sprites/master/'):
        raise ValueError('URL fora da fonte aprovada')
    try:
        with urlopen(Request(url, headers={'User-Agent': 'PokemonPvP-artwork-reference/1.50'}), timeout=15) as response:
            if response.status != 200 or not response.headers.get('content-type', '').startswith('image/'):
                return False
            data = response.read(3 * 1024 * 1024 + 1)
        if not data.startswith(b'\x89PNG\r\n\x1a\n') or len(data) > 3 * 1024 * 1024:
            return False
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        return True
    except (URLError, HTTPError, TimeoutError, OSError):
        return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=Path('downloaded-artwork'))
    parser.add_argument('--shiny', action='store_true', help='Pedir também os Shiny disponíveis')
    args = parser.parse_args()
    with MANIFEST.open(newline='', encoding='utf-8') as f:
        rows = list(csv.DictReader(f))
    ok = 0
    for row in rows:
        species, dex = row['species'], int(row['dex'])
        for kind, link in [('normal', row['official_png'])] + ([('shiny', row['shiny_png'])] if args.shiny else []):
            target = args.out / f'{dex:04d}-{species.lower()}-{kind}.png'
            got = fetch(link, target)
            print(('OK ' if got else 'UNAVAILABLE ') + str(target))
            ok += int(got)
    print(f'DOWNLOAD_RESULT {ok} of {len(rows) * (2 if args.shiny else 1)}')


if __name__ == '__main__':
    main()
