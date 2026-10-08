"""Generate only four sequential PCB packages; preserve all previously repaired models.

Uses existing opaque materials and the shared hidden-face repair. --check verifies
the committed assets without rewriting them. No image or global asset generation.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

from refine_model_surfaces import refine

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/electron"
DATA = ROOT / "src/main/resources/data/electron"
TEXTURES = {name: "electron:block/pcb/" + name for name in
            ("ic_package", "rubber", "brushed_steel", "solder_joint", "enamel")}
TEXTURES["particle"] = TEXTURES["ic_package"]
DISPLAY = {
    "gui": {"rotation": [35, 225, 0], "scale": [.92, .92, .92]},
    "ground": {"translation": [0, 3, 0], "scale": [.35, .35, .35]},
    "fixed": {"rotation": [90, 180, 0], "scale": [.7, .7, .7]},
    "firstperson_righthand": {"rotation": [15, 45, 0], "scale": [.55, .55, .55]},
    "firstperson_lefthand": {"rotation": [15, -45, 0], "scale": [.55, .55, .55]},
    "thirdperson_righthand": {"rotation": [65, 45, 0], "translation": [0, 2, 0], "scale": [.4, .4, .4]},
    "thirdperson_lefthand": {"rotation": [65, -45, 0], "translation": [0, 2, 0], "scale": [.4, .4, .4]},
}
PACKAGES = {"smd_tff": 6, "smd_sr_latch": 5, "smd_counter4": 8, "smd_shift4": 8}
# Each crafted package represents one whole logic component, never a free upgrade.
INGREDIENTS = {
    "smd_tff": ["smd_dff", "smd_xor", "smd_and", "solder_alloy", "copper_foil"],
    "smd_sr_latch": ["smd_nor", "smd_nor", "solder_alloy", "copper_foil"],
    "smd_counter4": ["smd_tff"] * 4 + ["smd_and"] * 2 + ["smd_not", "solder_alloy", "copper_foil"],
    "smd_shift4": ["smd_dff"] * 4 + ["smd_and", "solder_alloy", "copper_foil"],
}


def cube(start, end, texture, name):
    start, end = [round(v, 5) for v in start], [round(v, 5) for v in end]
    dx, dy, dz = [b - a for a, b in zip(start, end)]
    faces = {}
    for face in ("up", "down", "north", "south", "east", "west"):
        w, h = (dx, dz) if face in ("up", "down") else (dx, dy) if face in ("north", "south") else (dz, dy)
        # Reuse the unprinted moulding patch: the original IC bitmap also has
        # a border and lettering that would conflict with the actual cap geometry.
        uv = [2, 11, round(2 + w * .25, 5), round(11 + h * .25, 5)] if texture == "ic_package" else [0, 0, round(w, 5), round(h, 5)]
        faces[face] = {"texture": "#" + texture, "uv": uv}
    return {"name": name, "from": start, "to": end, "faces": faces}


def lead(side, offset, number):
    """Gull-wing foot, riser, and shoulder, rotated as a single connected lead."""
    result = []
    for start, end, texture, label in [
        ((2.3, 5.7, offset - .42), (3.45, 6.02, offset + .42), "solder_joint", "foot"),
        ((3.13, 6.02, offset - .42), (3.45, 6.83, offset + .42), "brushed_steel", "riser"),
        ((3.13, 6.83, offset - .42), (4.6, 7.12, offset + .42), "solder_joint", "shoulder"),
    ]:
        def rotate(point):
            x, y, z = point
            for _ in range(side):
                x, z = 16 - z, x
            return x, y, z
        a, b = rotate(start), rotate(end)
        result.append(cube([min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)],
                           texture, f"pin_{number}_{label}"))
    return result


def package(name, count):
    elements = [
        cube((4.45, 6.12, 4.45), (11.55, 6.55, 11.55), "rubber", "lower_mold"),
        cube((4.2, 6.55, 4.2), (11.8, 7.62, 11.8), "ic_package", "body"),
        # The front upper cap is split around a physical orientation notch.
        cube((4.45, 7.62, 5.15), (11.55, 8.02, 11.55), "ic_package", "upper_cap"),
        cube((4.45, 7.62, 4.45), (7.1, 8.02, 5.15), "ic_package", "notch_left"),
        cube((8.9, 7.62, 4.45), (11.55, 8.02, 5.15), "ic_package", "notch_right"),
        cube((7.1, 7.62, 4.45), (8.9, 7.71, 5.15), "rubber", "notch_recess"),
        cube((4.95, 8.02, 5.65), (5.57, 8.1, 6.27), "enamel", "pin_one_dot"),
    ]
    pads = [(0, 5.1), (0, 8), (0, 10.9), (2, 5.1), (2, 8), (2, 10.9)]
    if count == 5:
        pads.remove((2, 8))
    if count == 8:
        pads += [(1, 8), (3, 8)]
    for index, (side, offset) in enumerate(pads):
        elements.extend(lead(side, offset, index + 1))

    # Shallow moulded symbols remain readable in the inventory, without nameplates.
    marks = {
        "smd_tff": [(6.1, 6.25, 10.35, 6.8), (7.95, 6.8, 8.5, 10.3)],
        "smd_sr_latch": [(6.1, 6.4, 7.85, 6.9), (6.1, 6.9, 6.6, 8.3),
                         (6.6, 7.8, 9.8, 8.3), (9.3, 8.3, 9.8, 9.9), (7.95, 9.4, 9.3, 9.9)],
        "smd_counter4": [(6.05 + i * 1.13, 9.6 - i * .86, 6.65 + i * 1.13, 10.15) for i in range(4)],
        "smd_shift4": [(6.05, 7.75, 9.55, 8.3), (8.85, 6.65, 9.4, 9.4),
                       (9.4, 7.2, 9.95, 8.85), (9.95, 7.75, 10.5, 8.3)],
    }[name]
    for index, (x, z, xx, zz) in enumerate(marks):
        elements.append(cube((x, 8.02, z), (xx, 8.1, zz), "enamel", f"symbol_{index}"))
    obj = {"parent": "minecraft:block/block", "ambientocclusion": True, "textures": TEXTURES,
           "elements": elements, "display": DISPLAY}
    refine(obj)
    return obj


def files():
    result = {}
    for name, count in PACKAGES.items():
        result[ASSETS / "models/item" / f"{name}.json"] = package(name, count)
        result[DATA / "recipe" / f"{name}.json"] = {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": "electron:" + item} for item in INGREDIENTS[name]],
            "result": {"id": "electron:" + name, "count": 1},
        }
        result[DATA / "advancement/recipes" / f"{name}.json"] = {
            "criteria": {"material": {"trigger": "minecraft:inventory_changed", "conditions": {
                "items": [{"items": ["electron:" + INGREDIENTS[name][0]]}]}}},
            "requirements": [["material"]], "rewards": {"recipes": ["electron:" + name]},
        }
    return result


def validate(path, obj):
    if "elements" in obj:
        name = path.stem
        feet = [e for e in obj["elements"] if e.get("name", "").endswith("_foot")]
        assert len(feet) == PACKAGES[name], (name, "lead count")
        assert any(e.get("name") == "notch_recess" for e in obj["elements"])
        for e in obj["elements"]:
            assert 1 <= len(e["faces"]) <= 6, (name, "empty faces")
            assert all(0 <= a < b <= 16 for a, b in zip(e["from"], e["to"])), (name, "bounds")
            for face in e["faces"].values():
                texture = obj["textures"][face["texture"][1:]]
                assert (ASSETS / "textures" / (texture.split(":")[1] + ".png")).is_file(), texture
        again = copy.deepcopy(obj)
        refine(again)
        assert again == obj, (name, "surface cleanup not stable")
    if "ingredients" in obj:
        assert 1 <= len(obj["ingredients"]) <= 9, path
        assert obj["result"]["count"] == 1
        for ingredient in obj["ingredients"]:
            item = ingredient["item"].split(":")[1]
            assert item in PACKAGES or (ASSETS / "models/item" / f"{item}.json").is_file(), item


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Read and validate generated assets without changing files")
    args = parser.parse_args()
    for path, obj in files().items():
        validate(path, obj)
        text = json.dumps(obj, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            assert path.read_text(encoding="utf-8") == text, f"Stale asset: {path}"
        elif not path.exists() or path.read_text(encoding="utf-8") != text:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
    print(("Validated" if args.check else "Generated") + " 4 sequential PCB models, 4 paid recipes and 4 recipe unlocks.")


if __name__ == "__main__":
    main()
