"""Read saved vanilla block states for blueprint wiring diagnostics (no world writes)."""
import argparse
import gzip
import io
import pathlib
import struct
import zlib


class Nbt:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.stream.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.stream.read(self.number('H')).decode('utf-8')

    def value(self, kind):
        if kind in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 7:
            return self.stream.read(self.number('i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.number('B'), self.number('i')
            return [self.value(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while child := self.number('B'):
                name = self.string()
                result[name] = self.value(child)
            return result
        if kind in (11, 12):
            return [self.number('i' if kind == 11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(kind)

    def root(self):
        kind = self.number('B')
        self.string()
        return self.value(kind)


parser = argparse.ArgumentParser()
parser.add_argument('world', type=pathlib.Path)
parser.add_argument('--origin', type=int, nargs=3, required=True)
parser.add_argument('--point', type=int, nargs=3, required=True)
parser.add_argument('--radius', type=int, default=1)
parser.add_argument('--blueprint', type=pathlib.Path)
parser.add_argument('--source-net', type=int)
args = parser.parse_args()
if args.source_net is not None:
    with gzip.open(args.blueprint, 'rb') as stream:
        header = struct.unpack('>6i', stream.read(24))
        stream.read(header[5] * 13)
        for _ in range(2):
            count = struct.unpack('>i', stream.read(4))[0]
            for _ in range(count):
                length = struct.unpack('>H', stream.read(2))[0]
                stream.read(length + 6)
        count = struct.unpack('>i', stream.read(4))[0]
        for _ in range(count):
            net, x, y, z = struct.unpack('>i3H', stream.read(10))
            if net == args.source_net:
                args.point = (x, y, z)
                print('source', net, args.point)

cache = {}


def block_at(x, y, z):
    chunk_x, chunk_z = x >> 4, z >> 4
    key = chunk_x, chunk_z
    if key not in cache:
        region = args.world / 'region' / f'r.{chunk_x >> 5}.{chunk_z >> 5}.mca'
        with region.open('rb') as stream:
            stream.seek(((chunk_x & 31) + 32 * (chunk_z & 31)) * 4)
            offset = struct.unpack('>I', stream.read(4))[0] >> 8
            if offset == 0:
                return {'Name': 'unwritten_chunk'}
            stream.seek(offset * 4096)
            size, compression = struct.unpack('>IB', stream.read(5))
            data = stream.read(size - 1)
        cache[key] = Nbt(zlib.decompress(data) if compression == 2 else gzip.decompress(data)).root()
    section = next((s for s in cache[key]['sections'] if s['Y'] == y >> 4), None)
    if section is None or 'block_states' not in section:
        return {'Name': 'minecraft:air'}
    states = section['block_states']
    palette = states['palette']
    if len(palette) == 1:
        return palette[0]
    bits = max(4, (len(palette) - 1).bit_length())
    index = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
    per_long = 64 // bits
    value = states['data'][index // per_long] & ((1 << 64) - 1)
    return palette[(value >> (index % per_long * bits)) & ((1 << bits) - 1)]


for dx in range(-args.radius, args.radius + 1):
    for dy in range(-args.radius, args.radius + 1):
        for dz in range(-args.radius, args.radius + 1):
            relative = tuple(p + d for p, d in zip(args.point, (dx, dy, dz)))
            position = tuple(p + o for p, o in zip(relative, args.origin))
            state = block_at(*position)
            if state['Name'] != 'minecraft:air':
                print(relative, state)
