import unittest
from patch_shader_compat import patch_properties, LIGHTS

class ShaderCompatTest(unittest.TestCase):
    def test_state_filters_exclude_off_and_broken(self):
        out,n=patch_properties('#if MC_VERSION >= 11300\nblock.169=sea_lantern\n#else\nblock.169=minecraft:sea_lantern\n#endif\n')
        self.assertEqual(n,2)
        self.assertEqual(out.count('block.169='),2)
        self.assertNotIn(':power=0',out)
        self.assertEqual(out.count('electron:engineering_lamp:broken=false:'),2)
        self.assertIn('electron:binary_memory:powered=true',out)
    def test_multiline_and_idempotence(self):
        original='block.89 = glowstone sea_lantern \\\n shroomlight\nblock.1=stone\n'
        out,n=patch_properties(original);self.assertEqual(n,1)
        self.assertIn(' shroomlight '+' '.join(LIGHTS),out)
        self.assertEqual(patch_properties(out),(out,0))
    def test_unknown_packs_are_unchanged(self):
        text='# block.169=sea_lantern\nblock.5=stone\n'
        self.assertEqual(patch_properties(text),(text,0))
if __name__=='__main__':unittest.main()
