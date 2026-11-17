"""Scales emulator screenshots down to 720 px wide so they stay small in the repository.

Usage: shrink-screenshots.py <input dir> <output dir>
"""
import pathlib
import sys

from PIL import Image

source, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
target.mkdir(parents=True, exist_ok=True)
for path in sorted(source.glob("*.png")):
    image = Image.open(path).convert("RGB")
    width = 720
    height = round(image.height * width / image.width)
    image.resize((width, height), Image.LANCZOS).save(target / path.name, optimize=True)
    print(f"{path.name}: {image.width}x{image.height} -> {width}x{height}")
