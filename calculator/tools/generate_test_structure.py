"""Minimal vanilla NBT structures to reserve isolated GameTest regions."""
import gzip
import struct
from pathlib import Path

def string(s):
    b=s.encode()
    return struct.pack('>H',len(b))+b
def tag(t,name,payload): return bytes([t])+string(name)+payload
def structure(name, size):
    root = Path(__file__).resolve().parents[1]/'src/gametest/resources/data/electron_calculator/structure'
    root.mkdir(parents=True,exist_ok=True)
    data=b'\x0a\x00\x00'
    data+=tag(3,'DataVersion',struct.pack('>i',3955))
    data+=tag(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',*size))
    data+=tag(9,'palette',b'\x0a'+struct.pack('>i',1)+tag(8,'Name',string('minecraft:air'))+b'\x00')
    data+=tag(9,'blocks',b'\x0a'+struct.pack('>i',0))
    data+=tag(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\x00'
    (root/f'{name}.nbt').write_bytes(gzip.compress(data,mtime=0))

structure('components_test',(10,6,18))
structure('compact_test',(240,60,200))
structure('full_calculator_test',(288,250,300))
structure('console_calculator_test',(640,250,320))
