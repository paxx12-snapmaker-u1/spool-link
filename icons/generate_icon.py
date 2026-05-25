#!/usr/bin/env python3

import os
from io import BytesIO

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
ICONS_DIR = os.path.join(ROOT_DIR, 'icons')
BASE_SVG_PATH = os.path.join(ICONS_DIR, 'spoolman_base.svg')
APP_ICON_PATH = os.path.join(ICONS_DIR, 'AppIcon.png')

ANDROID_RES_DIR = os.path.join(ROOT_DIR, 'android-app', 'app', 'src', 'main', 'res')
IOS_ICON_PATH = os.path.join(ROOT_DIR, 'ios-app', 'Resources', 'Assets.xcassets', 'AppIcon.appiconset', 'AppIcon.png')
WEB_DIR = os.path.join(ROOT_DIR, 'web-app')


def rasterize_base(size):
    png_bytes = cairosvg.svg2png(url=BASE_SVG_PATH, output_width=size, output_height=size)
    return Image.open(BytesIO(png_bytes)).convert('RGBA')


def draw_rfid_accent(image):
    w, h = image.size
    draw = ImageDraw.Draw(image, 'RGBA')

    lines = ['RFID', 'LINK']
    max_w = int(w * 0.8)
    max_total_h = int(h * 0.72)

    try:
        font_size = int(w * 0.42)
        while font_size > 12:
            font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf', font_size)
            b0 = draw.textbbox((0, 0), lines[0], font=font)
            b1 = draw.textbbox((0, 0), lines[1], font=font)
            w0, h0 = b0[2] - b0[0], b0[3] - b0[1]
            w1, h1 = b1[2] - b1[0], b1[3] - b1[1]
            gap = int(font_size * 0.10)
            total_h = h0 + h1 + gap
            if max(w0, w1) <= max_w and total_h <= max_total_h:
                break
            font_size -= 2
    except OSError:
        font = ImageFont.load_default()
        b0 = draw.textbbox((0, 0), lines[0], font=font)
        b1 = draw.textbbox((0, 0), lines[1], font=font)
        w0, h0 = b0[2] - b0[0], b0[3] - b0[1]
        w1, h1 = b1[2] - b1[0], b1[3] - b1[1]
        gap = int(h * 0.01)

    total_h = h0 + h1 + gap
    top = (h - total_h) // 2

    x0 = (w - w0) // 2 - b0[0]
    x1 = (w - w1) // 2 - b1[0]
    y0 = top - b0[1]
    y1 = top + h0 + gap - b1[1]

    shadow = (10, 16, 30, 210)
    dx = int(w * 0.004)
    dy = int(w * 0.004)

    draw.text((x0 + dx, y0 + dy), lines[0], font=font, fill=shadow)
    draw.text((x1 + dx, y1 + dy), lines[1], font=font, fill=shadow)

    fill = (242, 248, 255, 255)
    stroke = (70, 126, 198, 255)
    sw = max(1, int(w * 0.003))

    draw.text((x0, y0), lines[0], font=font, fill=fill, stroke_width=sw, stroke_fill=stroke)
    draw.text((x1, y1), lines[1], font=font, fill=fill, stroke_width=sw, stroke_fill=stroke)

    return image


def apply_round_mask(image):
    size = image.size[0]
    radius = int(size * 0.18)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(image, mask=mask)
    return out


def save_android(base):
    legacy = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    for folder, size in legacy.items():
        out = base.resize((size, size), Image.Resampling.LANCZOS)
        out.convert('RGBA').save(os.path.join(ANDROID_RES_DIR, folder, 'ic_launcher.png'))
        out.convert('RGBA').save(os.path.join(ANDROID_RES_DIR, folder, 'ic_launcher_round.png'))

    fg = base.resize((432, 432), Image.Resampling.LANCZOS)
    fg.save(os.path.join(ANDROID_RES_DIR, 'drawable', 'ic_launcher_foreground.png'))
    fg.convert('L').save(os.path.join(ANDROID_RES_DIR, 'drawable', 'ic_launcher_monochrome.png'))


def save_ios(base):
    base.convert('RGBA').save(IOS_ICON_PATH)


def save_web(base):
    sizes = {
        'favicon.ico': [16, 32, 48],
        'icon-192.png': [192],
        'icon-512.png': [512],
        'apple-touch-icon.png': [180],
    }

    for name, dims in sizes.items():
        path = os.path.join(WEB_DIR, name)
        if name.endswith('.ico'):
            imgs = [base.resize((d, d), Image.Resampling.LANCZOS).convert('RGBA') for d in dims]
            imgs[0].save(path, format='ICO', sizes=[(d, d) for d in dims], append_images=imgs[1:])
        else:
            d = dims[0]
            base.resize((d, d), Image.Resampling.LANCZOS).convert('RGBA').save(path)


def main():
    icon = rasterize_base(1024)
    icon = draw_rfid_accent(icon)
    icon = apply_round_mask(icon)

    icon.convert('RGBA').save(APP_ICON_PATH)

    save_android(icon)
    save_ios(icon)
    save_web(icon)

    print(f'Generated {APP_ICON_PATH}')
    print('Updated Android launcher assets')
    print('Updated iOS AppIcon asset')
    print('Updated web icon assets')


if __name__ == '__main__':
    main()
