"""Geometric regression tests for flush markings, UVs and surface coverage."""
import copy
import unittest
from unittest.mock import patch
import integrate_model_markings as marks
import refine_model_surfaces as surfaces


def cube(lo, hi, texture='#case'):
    return {'from': list(lo), 'to': list(hi), 'faces': {
        side: {'texture': texture, 'uv': [0, 0, 16, 16]} for side in surfaces.FACES}}


class Markings(unittest.TestCase):
    def test_flush_inlay_preserves_area_and_does_not_add_depth_in_six_directions(self):
        for side, (axis, sign, u, _, v, _) in surfaces.FACES.items():
            with self.subTest(side=side):
                host = cube((0, 0, 0), (4, 4, 4))
                plate = cube((1, 1, 1), (3, 3, 3))
                plate['from'][axis], plate['to'][axis] = (3.98, 4.08) if sign > 0 else (-.08, .02)
                plate['faces'][side]['texture'] = '#pin0'
                obj = {'elements': [host, plate]}
                marks.embed(obj, plate, side)
                self.assertNotIn(plate, obj['elements'])
                self.assertEqual(marks.audit(obj), 1)
                faces = [e for e in obj['elements'] if side in e['faces']]
                self.assertAlmostEqual(sum(marks.area(marks.rectangle(e, side)) for e in faces), 16)
                for e in faces:
                    self.assertEqual(marks.plane(e, side), 4 if sign > 0 else 0)
                self.assertEqual(next(e for e in faces if e.get('name'))['faces'][side]['uv'], [0, 0, 16, 16])

    def test_tower_tag_is_printed_on_porcelain_not_suspended_in_air(self):
        disc = cube((1.9, 13, 1.9), (4.1, 14, 4.1), '#ceramic')
        plate = cube((1.7, 14.9, 1.4), (4.3, 15.1, 2.5))
        plate['faces']['up']['texture'] = '#pin0'
        obj = {'elements': [disc, plate]}
        marks.embed(obj, plate, 'up', tower=True)
        label = next(e for e in obj['elements'] if e.get('name'))
        self.assertEqual(marks.plane(label, 'up'), 14)
        self.assertEqual(marks.rectangle(label, 'up'), (1.9, 1.9, 4.1, 2.5))
        self.assertEqual(marks.audit(obj), 1)

    def test_multiple_markings_can_span_already_partitioned_housing(self):
        host = cube((0, 0, 0), (16, 1, 16))
        first = cube((1, .95, 4), (5, 1.08, 6))
        second = cube((3, 1, 11), (13, 1.2, 14))
        first['faces']['up']['texture'] = '#pin0'
        second['faces']['up']['texture'] = '#label'
        obj = {'elements': [host, first, second]}
        marks.embed(obj, first, 'up')
        marks.embed(obj, second, 'up')
        self.assertEqual(marks.audit(obj), 2)
        self.assertEqual(sum(marks.area(marks.rectangle(e, 'up')) for e in obj['elements'] if 'up' in e['faces']), 256)

    def test_rejects_a_tag_with_no_backing_instead_of_hiding_it(self):
        plate = cube((0, 1, 0), (2, 1.1, 1))
        plate['faces']['up']['texture'] = '#pin0'
        with self.assertRaisesRegex(ValueError, 'No solid backing'):
            marks.embed({'elements': [plate]}, plate, 'up')

    def test_material_scale_follows_face_size(self):
        obj = {'elements': [cube((2, 1, 3), (2.8, 1.2, 3.8))]}
        marks.remap_materials(obj)
        uv = obj['elements'][0]['faces']['up']['uv']
        self.assertAlmostEqual(uv[2]-uv[0], .8)
        self.assertAlmostEqual(uv[3]-uv[1], .8)

    def test_original_metal_uv_does_not_stretch_after_cut(self):
        host = cube((0, 0, 0), (4, 1, 4))
        plate = cube((1, 1, 1), (3, 1.1, 3))
        plate['faces']['up']['texture'] = '#pin0'
        obj = {'elements': [host, plate]}
        marks.remap_materials(obj)
        marks.embed(obj, plate, 'up')
        for e in obj['elements']:
            f = e['faces'].get('up')
            if f and f['texture'] == '#case':
                self.assertEqual(f['uv'], list(marks.rectangle(e, 'up')))

    def test_chip_package_is_kept_and_rotation_follows_the_device(self):
        obj = {'textures': {}, 'elements': [cube((5, 2.95, 9), (9, 3.8, 12))]}
        obj['elements'][0]['faces']['up']['texture'] = '#label'
        with patch.object(surfaces, 'opaque_texture', return_value=False):
            count, _ = marks.integrate(obj, 'wire_and_gate', turn=1)
            self.assertEqual(count, 0)
            self.assertEqual(len(obj['elements'][0]['faces']), 6)
            self.assertEqual(obj['textures']['label'], 'electron:block/wire_and_gate_label')
            self.assertEqual(obj['elements'][0]['faces']['up']['rotation'], 90)
            before = copy.deepcopy(obj)
            marks.integrate(obj, 'wire_and_gate', turn=1)
            self.assertEqual(obj, before)

    def test_broken_device_does_not_reintroduce_pristine_lettering(self):
        obj = {'textures': {}, 'elements': [cube((5, 2.95, 9), (9, 3.8, 12))]}
        obj['elements'][0]['faces']['up']['texture'] = '#label'
        with patch.object(surfaces, 'opaque_texture', return_value=False):
            marks.integrate(obj, 'wire_and_gate', broken=True)
        self.assertEqual(obj['textures']['label'], 'electron:block/scorched')

    def test_legacy_generator_cannot_restore_voltage_plate_on_lamp(self):
        base = cube((0, 0, 0), (16, 3, 16))
        plate = cube((3, 3, 2), (11, 3.1, 4.2))
        plate['faces']['up']['texture'] = '#label'
        obj = {'textures': {}, 'elements': [base, plate]}
        marks.integrate(obj, 'engineering_lamp')
        self.assertEqual(obj['elements'], [base])


if __name__ == '__main__':
    unittest.main()
