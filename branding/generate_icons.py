"""Generate launcher assets from the approved transparent camera artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BRANDING = ROOT / 'branding'
RES = ROOT / 'app/src/main/res'
source = Image.open(BRANDING / 'shutternote-camera-fullbleed.png').convert('RGBA')
assert source.getchannel('A').getextrema() == (255, 255), 'Opaque full-bleed artwork required'
camera = source

def foreground(size, fraction):
    canvas = Image.new('RGBA', (size, size))
    artwork = camera.copy()
    artwork.thumbnail((round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(artwork, ((size-artwork.width)//2, (size-artwork.height)//2))
    return canvas

# The visible adaptive viewport is 72 of 108 units. Extend its edges into
# the overscan area so launcher masks and animation never reveal a backdrop.
visible = camera.resize((720, 720), Image.Resampling.LANCZOS)
adaptive = Image.new('RGBA', (1080, 1080))
adaptive.paste(visible, (180, 180))
adaptive.paste(visible.crop((0, 0, 720, 1)).resize((720, 180)), (180, 0))
adaptive.paste(visible.crop((0, 719, 720, 720)).resize((720, 180)), (180, 900))
adaptive.paste(adaptive.crop((180, 0, 181, 1080)).resize((180, 1080)), (0, 0))
adaptive.paste(adaptive.crop((899, 0, 900, 1080)).resize((180, 1080)), (900, 0))
adaptive.save(RES / 'drawable-nodpi/shutternote_lens.png')

def icon(size, rounded=False):
    canvas = camera.resize((size, size), Image.Resampling.LANCZOS)
    if rounded:
        mask = Image.new('L', (size, size))
        ImageDraw.Draw(mask).ellipse((0, 0, size-1, size-1), fill=255)
        canvas.putalpha(mask)
    return canvas

icon(512).convert('RGB').save(BRANDING / 'shutternote-play-icon.png')
for density, size in [('mdpi',48), ('hdpi',72), ('xhdpi',96), ('xxhdpi',144), ('xxxhdpi',192)]:
    for rounded in (False, True):
        icon(size, rounded).save(RES / f'mipmap-{density}' / ('ic_launcher_round.png' if rounded else 'ic_launcher.png'))
