"""Builds the store and README images from the raw device captures in docs/raw.

    python docs/make_assets.py

Output: docs/store (Google Play) and docs/media (README). English only.
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, 'raw')
STORE = os.path.join(ROOT, 'store')
MEDIA = os.path.join(ROOT, 'media')
FONTS = os.path.join(ROOT, '..', 'mobile', 'src', 'main', 'res', 'font')

GROUND = (11, 15, 20)
SURFACE = (20, 26, 35)
TEXT = (238, 241, 246)
MUTED = (185, 195, 209)
AMBER = (255, 181, 71)
MOON = (169, 180, 255)
MINT = (127, 227, 192)


def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def background(w, h, accent):
    """Night ground with a soft glow of the accent colour behind the device."""
    img = Image.new('RGB', (w, h), GROUND)
    glow = Image.new('RGB', (w, h), GROUND)
    d = ImageDraw.Draw(glow)
    r = int(w * 0.55)
    cx, cy = w // 2, int(h * 0.62)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=tuple(int(c * 0.35 + g * 0.65) for c, g in zip(accent, GROUND)))
    return Image.blend(img, glow.filter(ImageFilter.GaussianBlur(w // 5)), 0.9)


def rounded(img, radius):
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = img.convert('RGBA')
    out.putalpha(mask)
    return out


def centered(draw, text, y, f, fill, width):
    for line in text.split('\n'):
        tw = draw.textlength(line, font=f)
        draw.text(((width - tw) / 2, y), line, font=f, fill=fill)
        y += int(f.size * 1.18)
    return y


def phone_shot(raw, title, subtitle, accent, out):
    """1080x2160 (2:1, as Play allows): headline on top, the app below, status bar cropped."""
    w, h = 1080, 2160
    img = background(w, h, accent)
    d = ImageDraw.Draw(img)
    y = centered(d, title, 110, font('sora.ttf', 76, 800), TEXT, w)
    centered(d, subtitle, y + 18, font('figtree.ttf', 40, 400), MUTED, w)
    shot = Image.open(os.path.join(RAW, raw)).convert('RGB')
    shot = shot.crop((0, 110, shot.width, shot.height - 60))  # status bar and gesture bar
    target_h = h - 520
    scale = target_h / shot.height
    shot = shot.resize((int(shot.width * scale), target_h), Image.LANCZOS)
    x = (w - shot.width) // 2
    frame = Image.new('RGBA', (shot.width + 16, shot.height + 16), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), 64, fill=(42, 52, 66, 255))
    img.paste(frame, (x - 8, 470 - 8), frame)
    img.paste(rounded(shot, 56), (x, 470), rounded(shot, 56))
    img.save(os.path.join(STORE, out))


def draw_icon(size, monochrome_bg=GROUND):
    """The launcher icon (sync arcs around a watch), drawn at 4x and scaled down."""
    s = size * 4
    k = s / 72.0  # the 18..90 part of the 108-unit adaptive icon

    def p(x, y):
        return ((x - 18) * k, (y - 18) * k)

    img = Image.new('RGB', (s, s), monochrome_bg)
    d = ImageDraw.Draw(img)
    r = 30 * k
    c = p(54, 54)
    w = int(4.5 * k)
    # PIL draws an arc's width inside its box: grow the box by half a stroke to centre it on r
    o = r + w / 2
    d.arc((c[0] - o, c[1] - o, c[0] + o, c[1] + o), 210, 330, fill=AMBER, width=w)
    d.arc((c[0] - o, c[1] - o, c[0] + o, c[1] + o), 30, 150, fill=MOON, width=w)

    def stroke(a, b, col, width):
        d.line([p(*a), p(*b)], fill=col, width=width)
        for q in (a, b):
            x, y = p(*q)
            d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=col)

    for a, b in [((80, 39), (78.8, 31.4)), ((80, 39), (72.4, 40.6))]:
        stroke(a, b, AMBER, w)
    for a, b in [((28, 69), (29.2, 76.6)), ((28, 69), (35.6, 67.4))]:
        stroke(a, b, MOON, w)
    # arc ends, rounded
    for (x, y), col in [(p(28, 39), AMBER), (p(80, 69), MOON)]:
        d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=col)
    rw = 17 * k + 3.5 * k / 2
    d.ellipse((c[0] - rw, c[1] - rw, c[0] + rw, c[1] + rw), fill=SURFACE, outline=TEXT, width=int(3.5 * k))
    stroke((54, 54), (54, 44.5), AMBER, int(3.5 * k))
    dot = 2.8 * k
    d.ellipse((c[0] - dot, c[1] - dot, c[0] + dot, c[1] + dot), fill=TEXT)
    return img.resize((size, size), Image.LANCZOS)


def watch_round(raw, size):
    img = Image.open(os.path.join(RAW, raw)).convert('RGB').resize((size, size), Image.LANCZOS)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = img.convert('RGBA')
    out.putalpha(mask)
    return out


def feature_graphic():
    w, h = 1024, 500
    img = background(w, h, MOON)
    d = ImageDraw.Draw(img)
    icon = draw_icon(150)
    img.paste(rounded(icon, 36), (70, 95), rounded(icon, 36))
    d.text((70, 270), 'WatchSync', font=font('sora.ttf', 64, 800), fill=TEXT)
    d.text((72, 352), 'Do Not Disturb, Bedtime and alarms\nin step on phone and watch.', font=font('figtree.ttf', 26, 400), fill=MUTED, spacing=8)
    phone = Image.open(os.path.join(RAW, 'phone_03_rest.png')).convert('RGB').crop((0, 110, 1080, 1560))
    phone = phone.resize((int(phone.width * 430 / phone.height), 430), Image.LANCZOS)
    img.paste(rounded(phone, 26), (520, 35), rounded(phone, 26))
    watch = watch_round('watch_04_rest_app.png', 210)
    ring = Image.new('RGBA', (226, 226), (0, 0, 0, 0))
    ImageDraw.Draw(ring).ellipse((0, 0, 225, 225), fill=(42, 52, 66, 255))
    img.paste(ring, (790, 140), ring)
    img.paste(watch, (798, 148), watch)
    img.save(os.path.join(STORE, 'feature_graphic_1024x500.png'))


def main():
    os.makedirs(STORE, exist_ok=True)
    os.makedirs(MEDIA, exist_ok=True)
    shots = [
        ('phone_01_home.png', 'Your watch,\nin step', 'Do Not Disturb, Rest and alarms\nsynced both ways', MINT, 'phone_1_home.png'),
        ('phone_02_dnd.png', 'Do Not Disturb,\n1:1', 'Turn it on anywhere:\nphone and watch follow', AMBER, 'phone_2_dnd.png'),
        ('phone_03_rest.png', 'Rest becomes\nBedtime', 'Your phone\'s own Rest mode\nlights up Bedtime on the watch', MOON, 'phone_3_rest.png'),
        ('phone_10_ring.png', 'Alarms ring\non both', 'Stop or snooze on either device:\nit stops on both', AMBER, 'phone_4_alarm.png'),
        ('phone_04_info_modes.png', 'Help at\nevery step', 'Every step explains what it is for\nand how to fix it', MOON, 'phone_5_help.png'),
        ('phone_05_settings.png', 'Free, open,\nno ads', 'Five languages, GPL licensed,\nno tracking, no account', MINT, 'phone_6_settings.png'),
    ]
    for raw, title, subtitle, accent, out in shots:
        phone_shot(raw, title, subtitle, accent, out)
    for raw, out in [('watch_01_home.png', 'watch_1_home.png'), ('watch_02_dnd.png', 'watch_2_dnd.png'),
                     ('watch_04_rest_app.png', 'watch_3_rest.png'), ('watch_10_ring.png', 'watch_4_alarm.png'),
                     ('watch_03_rest.png', 'watch_5_bedtime.png')]:
        Image.open(os.path.join(RAW, raw)).convert('RGB').resize((512, 512), Image.LANCZOS).save(os.path.join(STORE, out))
    draw_icon(512).save(os.path.join(STORE, 'icon_512.png'))
    feature_graphic()
    # README: a row of phone screens and a row of watch screens
    row = Image.new('RGB', (4 * 300 + 3 * 24, 600), GROUND)
    for i, n in enumerate(['phone_01_home.png', 'phone_02_dnd.png', 'phone_03_rest.png', 'phone_10_ring.png']):
        im = Image.open(os.path.join(RAW, n)).convert('RGB').crop((0, 110, 1080, 2360)).resize((300, 625), Image.LANCZOS).crop((0, 0, 300, 600))
        row.paste(rounded(im, 26), (i * 324, 0), rounded(im, 26))
    row.save(os.path.join(MEDIA, 'phone_screens.png'))
    wrow = Image.new('RGBA', (4 * 240 + 3 * 24, 240), (0, 0, 0, 0))
    for i, n in enumerate(['watch_01_home.png', 'watch_02_dnd.png', 'watch_04_rest_app.png', 'watch_10_ring.png']):
        wrow.paste(watch_round(n, 240), (i * 264, 0), watch_round(n, 240))
    wrow.save(os.path.join(MEDIA, 'watch_screens.png'))
    print('ok')


if __name__ == '__main__':
    main()
