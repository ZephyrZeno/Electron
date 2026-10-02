"""Print blueprint statistics, or a small block neighborhood for wiring diagnostics."""
import argparse
import gzip
import struct

parser = argparse.ArgumentParser()
parser.add_argument('path')
parser.add_argument('--point', nargs=3, type=int)
parser.add_argument('--radius', type=int, default=3)
parser.add_argument('--net', type=int)
args = parser.parse_args()
parts = ['WOOL','WIRE','REPEATER','TORCH','LEVER','LAMP','STANDING_TORCH','BUTTON','COPPER_BULB','COMPARATOR','STONE','DARK','WHITE','GLASS']
with gzip.open(args.path, 'rb') as stream:
    magic, width, height, depth, ticks, count = struct.unpack('>6i', stream.read(24))
    assert magic == 0x454C4301
    print(f'{args.path}: {width}x{height}x{depth}, {count} blocks, budget={ticks} GT')
    matches = 0
    for _ in range(count):
        x,y,z,part,module,direction,net = struct.unpack('>3H3Bi', stream.read(13))
        if args.point and all(abs(p-q)<=args.radius for p,q in zip((x,y,z),args.point)):
            print((x,y,z),parts[part],f'dir={direction} net={net}')
        if args.net is not None and net == args.net:
            matches += 1
            if matches<=15: print((x,y,z),parts[part],f'dir={direction} net={net}')
    if args.net is not None: print('net blocks',matches)
