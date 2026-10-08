"""Builds the foreground-service demo video for the Play Console from docs/raw/scene{1,2}_{phone,watch}.mp4.

    python docs/make_fgs_video.py <ffmpeg.exe>
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, 'raw')
WORK = os.path.join(RAW, 'fgs')
FONTS = os.path.join(ROOT, '..', 'mobile', 'src', 'main', 'res', 'font')
FF = sys.argv[1]
W, H = 1080, 1920
BG = (11, 15, 20)
TEXT = (238, 241, 246)
MUTED = (185, 195, 209)
AMBER = (255, 181, 71)


def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def wrap(draw, text, f, width):
    lines, line = [], ''
    for word in text.split():
        test = (line + ' ' + word).strip()
        if draw.textlength(test, font=f) <= width:
            line = test
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines


def card(path, title, body, note=None, step=None, phone_label=True):
    img = Image.new('RGBA', (W, H), BG + (255,))
    d = ImageDraw.Draw(img)
    y = 70
    if step:
        d.text((60, y), step, font=font('figtree.ttf', 34, 600), fill=AMBER)
        y += 56
    for line in wrap(d, title, font('sora.ttf', 54, 800), W - 120):
        d.text((60, y), line, font=font('sora.ttf', 54, 800), fill=TEXT)
        y += 66
    y += 14
    for line in wrap(d, body, font('figtree.ttf', 32, 400), W - 120):
        d.text((60, y), line, font=font('figtree.ttf', 32, 400), fill=MUTED)
        y += 44
    if note:
        nf = font('figtree.ttf', 28, 400)
        ny = H - 60 - 40 * len(wrap(d, note, nf, W - 120))
        for line in wrap(d, note, nf, W - 120):
            d.text((60, ny), line, font=nf, fill=MUTED)
            ny += 40
    # labels under the two screens
    lf = font('figtree.ttf', 30, 600)
    if phone_label:
        d.text((60 + 230 - d.textlength('Phone', font=lf) / 2, 1600), 'Phone', font=lf, fill=TEXT)
        d.text((620 + 200 - d.textlength('Watch', font=lf) / 2, 1250), 'Watch', font=lf, fill=TEXT)
    else:
        d.text(((W - d.textlength('Watch', font=lf)) / 2, 1360), 'Watch', font=lf, fill=TEXT)
    img.save(path)
    return y


def run(args):
    subprocess.run([FF, '-v', 'error', '-y'] + args, check=True)


def scene(n, title, body, step, duration, with_phone=True):
    overlay = os.path.join(WORK, 'scene%d.png' % n)
    card(overlay, title, body, 'Screen recordings carry no sound: the phone plays the alarm sound and vibrates, the watch vibrates.', step, with_phone)
    phone = os.path.join(RAW, 'scene%d_phone.mp4' % n)
    watch = os.path.join(RAW, 'scene%d_watch.mp4' % n)
    out = os.path.join(WORK, 'scene%d.mp4' % n)
    # Phone: the cover screen sits in the middle of the 2172-wide capture; crop it, drop the status bar
    if with_phone:
        args = ['-loop', '1', '-i', overlay, '-i', phone, '-i', watch, '-filter_complex',
                '[1:v]crop=1080:2240:546:110,scale=460:954,fps=30,setsar=1[p];'
                '[2:v]scale=400:400,fps=30,setsar=1[w];'
                '[0:v][p]overlay=60:620[a];[a][w]overlay=620:830,format=yuv420p']
    else:
        args = ['-loop', '1', '-i', overlay, '-i', watch, '-filter_complex',
                '[1:v]scale=640:640,fps=30,setsar=1[w];[0:v][w]overlay=220:680,format=yuv420p']
    run(args + ['-t', str(duration), '-r', '30', '-c:v', 'libx264', '-crf', '20', '-preset', 'medium', out])
    return out


def main():
    os.makedirs(WORK, exist_ok=True)
    intro_png = os.path.join(WORK, 'intro.png')
    img = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(img)
    icon = Image.open(os.path.join(ROOT, 'store', 'icon_512.png')).resize((300, 300))
    img.paste(icon, ((W - 300) // 2, 560))
    for i, (txt, f, col) in enumerate([('WatchSync', font('sora.ttf', 84, 800), TEXT),
                                         ('Foreground services', font('figtree.ttf', 40, 600), AMBER),
                                         ('Alarms mirrored between phone and watch', font('figtree.ttf', 36, 400), MUTED)]):
        y = 920 + [0, 120, 180][i]
        d.text(((W - d.textlength(txt, font=f)) / 2, y), txt, font=f, fill=col)
    img.save(intro_png)
    intro = os.path.join(WORK, 'intro.mp4')
    run(['-loop', '1', '-i', intro_png, '-t', '4', '-r', '30', '-vf', 'format=yuv420p', '-c:v', 'libx264', '-crf', '20', intro])

    s1 = scene(1, 'An alarm rings on the watch: the phone rings too',
               'WatchSync starts a foreground service of type mediaPlayback on the phone: it plays the alarm '
               'sound and vibration and shows the alarm screen while the watch alarm rings. When the alarm is '
               'stopped, both devices go quiet and the service ends at once.', 'mediaPlayback  ·  phone', 16)
    s2 = scene(2, 'An alarm rings on the phone: the watch rings too',
               'On the watch WatchSync runs foreground services of type specialUse only while an alarm rings: '
               'one vibrates and shows the alarm screen, one follows the ringing alarm so Stop and Snooze reach '
               'it. Stop is pressed on the watch: both alarms stop and the services end at once. (The phone, '
               'showing its own alarm on the lock screen, is left out of this recording.)',
               'specialUse  ·  watch', 18, with_phone=False)

    listing = os.path.join(WORK, 'list.txt')
    with open(listing, 'w') as f:
        for p in (intro, s1, s2):
            f.write("file '%s'\n" % p.replace('\\', '/'))
    final = os.path.normpath(os.path.join(os.path.expanduser('~'), 'Documents', 'WatchSync_store', 'WatchSync_foreground_services_demo.mp4'))
    run(['-f', 'concat', '-safe', '0', '-i', listing, '-c', 'copy', final])
    print(final)


if __name__ == '__main__':
    main()
