"""Preview Java-exported explosion geometry with the exact packaged smoke atlas.

This is an offline asset review, not a Minecraft screenshot or a replacement renderer.
Run the ExplosionPlumeTest first so geometry is produced by the runtime sampler.
"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
REVIEW = ROOT / 'build/asset-review'
frames = json.loads((REVIEW / 'plant-plume-geometry.json').read_text())
atlas = Image.open(ROOT / 'src/main/resources/assets/electron/textures/effect/plant_smoke.png').convert('RGBA')
font = lambda size: ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', size)
canvas = Image.new('RGBA', (1440, 1050), '#162231')
draw = ImageDraw.Draw(canvas)
draw.text((28, 18), 'Electron · 发电设施事故烟云', font=font(28), fill='#eef6fc')
draw.text((28, 60), '实际 Java 采样点 + 发行材质的离线预览；非游戏截图。各画面采用相同空间比例。', font=font(17), fill='#aec3d1')
cases = [(0, 30, '机械爆裂 / 1.5 秒'), (1, 30, '锅炉压力事故 / 1.5 秒'), (1, 90, '锅炉烟尘 / 4.5 秒'),
         (2, 30, '核电事故 / 1.5 秒'), (2, 90, '烟柱上升 / 4.5 秒'), (2, 170, '云冠展开 / 8.5 秒')]
for i, (kind, age, title) in enumerate(cases):
    tile = Image.new('RGBA', (454, 440), '#263a4c')
    d = ImageDraw.Draw(tile)
    d.text((16, 12), title, font=font(20), fill='#ecf4fc')
    d.line((10, 393, 444, 393), fill='#71969c', width=2)
    scale = 7.5
    def project(x, y, z):
        return 227 + (x * .832 - z * .555) * scale, 392 - (y + (x * .555 + z * .832) * .10) * scale
    for x in range(-24, 25, 4):
        p = project(x, 0, -24)
        q = project(x, 0, 24)
        d.line((*p, *q), fill='#314b5b')
    frame = next(f for f in frames if f['kind'] == kind and f['age'] == age)
    for x, y, z, radius, alpha, heat, tex, ground in sorted(frame['puffs'], key=lambda p: p[0] * .555 + p[2] * .832):
        if alpha < .005:
            continue
        sprite = atlas.crop((tex * 128, 0, (tex + 1) * 128, 128))
        diameter = max(2, int(radius * scale * 2))
        sprite = sprite.resize((diameter, diameter), Image.Resampling.LANCZOS)
        # Same vertex tint and opacity used by PlantExplosionRenderer.
        tint = (int(160 + 95 * heat), int(164 - 36 * heat), int(170 - 138 * heat))
        channels = sprite.split()
        sprite = Image.merge('RGBA', tuple(channels[c].point(lambda v, c=c: int(v * tint[c] / 255)) for c in range(3)) +
                             (channels[3].point(lambda v: int(v * alpha)),))
        px, py = project(x, y, z)
        tile.alpha_composite(sprite, (int(px - diameter / 2), int(py - diameter / 2)))
    d = ImageDraw.Draw(tile)
    d.line((16, 416, 16 + int(scale * 10), 416), fill='#d7e7ee', width=2)
    d.text((99, 403), '10 格', font=font(15), fill='#bccdd7')
    canvas.alpha_composite(tile, (24 + (i % 3) * 471, 106 + (i // 3) * 452))
draw = ImageDraw.Draw(canvas)
draw.text((28, 1015), '烟尘体积不代表破坏半径。锅炉 / 核电物理爆炸强度仍为 3 / 5；云团自动消散，不留下实体。', font=font(17), fill='#aec3d1')
target = REVIEW / 'plant-plumes.png'
canvas.convert('RGB').save(target)
print(target)
