"""Regression fixtures for UV-preserving overlap repair (no game or asset rewrites)."""
import copy,unittest
import refine_model_surfaces as repair

def cube(lo,hi,texture='solid'):
    return {'from':list(lo),'to':list(hi),'faces':{side:{'texture':'electron:block/'+texture,'uv':[0,0,16,16]} for side in repair.FACES}}

class Surfaces(unittest.TestCase):
    def setUp(self):
        self.opaque=repair.opaque_texture;repair.opaque_texture=lambda ref:not ref.endswith('glass')
    def tearDown(self):repair.opaque_texture=self.opaque
    def test_coplanar_inlay_keeps_exact_top_area_and_uv_crop(self):
        obj={'elements':[cube((0,0,0),(4,1,4)),cube((1,.5,1),(3,1,3))]}
        repair.refine(obj)
        top=[e for e in obj['elements'] if 'up' in e['faces']]
        self.assertEqual(sum((e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2]) for e in top),16)
        for i,e in enumerate(top):
            for other in top[i+1:]:
                overlap=min(e['to'][0],other['to'][0])-max(e['from'][0],other['from'][0])
                depth=min(e['to'][2],other['to'][2])-max(e['from'][2],other['from'][2])
                self.assertTrue(overlap<=0 or depth<=0)
        patch=next(e for e in top if e['from'][0]==0)
        self.assertEqual(patch['faces']['up']['uv'],[0,0,4,16])
    def test_buried_faces_removed_but_cutout_does_not_hide_surface(self):
        obj={'elements':[cube((0,0,0),(1,1,1)),cube((-.1,-.1,-.1),(1.1,1.1,1.1),'glass')]}
        repair.refine(obj);self.assertEqual(len(obj['elements'][0]['faces']),6)
        obj={'elements':[cube((0,0,0),(1,1,1)),cube((-.1,-.1,-.1),(1.1,1.1,1.1))]}
        repair.refine(obj);self.assertEqual(len(obj['elements']),1);self.assertEqual(obj['elements'][0]['from'],[-.1,-.1,-.1])
    def test_reapplying_does_not_keep_splitting_faces(self):
        obj={'elements':[cube((0,0,0),(4,1,4)),cube((1,.5,1),(3,1,3))]}
        repair.refine(obj);before=copy.deepcopy(obj);repair.refine(obj);self.assertEqual(obj,before)

if __name__=='__main__':unittest.main()
