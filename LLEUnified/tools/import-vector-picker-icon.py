"""Technical alpha/size cleanup of the user-approved generated Vector icon.

The 1254px RGB master has a baked checkerboard outside its white tile rim.
The fitted silhouette clips only those outer corners, never the inner artwork.
This is an explicit, user-approved raster cleanup, not a new icon design.
Requires Pillow. The original master is never modified.
"""

import argparse
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("master", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    source = Image.open(args.master).convert("RGBA")
    if source.size != (1254, 1254):
        raise ValueError("The fitted corner silhouette requires the approved 1254px master")
    scale = 4
    mask = Image.new("L", (1254 * scale, 1254 * scale))
    ImageDraw.Draw(mask).rounded_rectangle(
        (2 * scale, 2 * scale, 1252 * scale, 1252 * scale),
        radius=310 * scale, fill=255)
    source.putalpha(mask.resize(source.size, Image.Resampling.LANCZOS))
    source.save(args.master.with_name("g2-vector-lle-transparent.png"))
    icon = source.resize((512, 512), Image.Resampling.LANCZOS)
    target = root / "res/drawable-nodpi/icon_effect_g2_vector_lle.png"
    icon.save(target, optimize=True)
    assert icon.getpixel((0, 0))[3] == 0
    assert icon.getpixel((256, 256))[3] == 255
    assert icon.getchannel("A").getextrema() == (0, 255)
    print(f"{target}: {icon.size}, {icon.mode}, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
