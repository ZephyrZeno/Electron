"""Read Java-edition NBT and Anvil files without launching Minecraft or importing packaging scripts."""
from pathlib import Path
import gzip,io,struct,zlib

class Nbt:
    def __init__(self,data):self.f=io.BytesIO(data)
    def read(self,fmt):return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
    def string(self):return self.f.read(self.read('H')).decode('utf-8')
    def value(self,t):
        if t in (1,2,3,4,5,6):return self.read({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7:return self.f.read(self.read('i'))
        if t==8:return self.string()
        if t==9:
            subtype=self.read('B');return [self.value(subtype) for _ in range(self.read('i'))]
        if t==10:
            result={}
            while (tag:=self.read('B')):name=self.string();result[name]=self.value(tag)
            return result
        if t in (11,12):return [self.read('i' if t==11 else 'q') for _ in range(self.read('i'))]
        raise ValueError(t)
    def root(self):tag=self.read('B');self.string();return self.value(tag)

def read_gzip(path):return Nbt(gzip.decompress(Path(path).read_bytes())).root()
def chunks(path):
    raw=Path(path).read_bytes()
    for i in range(1024):
        entry=int.from_bytes(raw[i*4:i*4+4],'big');offset=(entry>>8)*4096
        if not offset:continue
        size=int.from_bytes(raw[offset:offset+4],'big');compression=raw[offset+4];blob=raw[offset+5:offset+4+size]
        if compression not in (1,2,3):raise ValueError(f'Unsupported Anvil compression {compression}: {path}')
        yield Nbt(zlib.decompress(blob) if compression==2 else gzip.decompress(blob) if compression==1 else blob).root()

def dropped_items(world):
    """Scan all saved entity regions; runtime iteration can miss asynchronously loaded entities."""
    return [entity for file in (Path(world)/'entities').glob('*.mca') for chunk in chunks(file)
            for entity in chunk.get('Entities',[]) if entity.get('id')=='minecraft:item']

def assert_no_dropped_items(world):
    items=dropped_items(world)
    if items:raise ValueError(f'Refusing to publish map with {len(items)} dropped item entities: {world}')

def block_state(chunk,x,y,z):
    """Resolve one post-1.18 chunk section palette entry, including non-straddling packed longs."""
    section=next((s for s in chunk.get('sections',[]) if s['Y']==y//16),None)
    if not section or 'block_states' not in section:return {'Name':'minecraft:air'}
    state=section['block_states'];palette=state['palette']
    if len(palette)==1:return palette[0]
    bits=max(4,(len(palette)-1).bit_length());per_long=64//bits
    index=((y&15)*16+(z&15))*16+(x&15)
    return palette[(state['data'][index//per_long]>>((index%per_long)*bits))&((1<<bits)-1)]

def position(n):
    def signed(x,bits):return x-(1<<bits) if x&(1<<(bits-1)) else x
    return signed((n>>38)&0x3ffffff,26),signed(n&0xfff,12),signed((n>>12)&0x3ffffff,26)
