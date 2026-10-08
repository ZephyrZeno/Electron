"""Check actual client framebuffer captures, in addition to the client event assertions."""
from pathlib import Path
import json
from PIL import Image,ImageChops

ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'build/interface-review'
report=json.loads((folder/'verified.json').read_text())
assert not (folder/'failure.txt').exists()
assert all(report[k] for k in ('manual_rendered','pcb_left_drag','pcb_middle_drag','zoom_at_pointer','resize_preserves_view'))
shots=folder/'screenshots'
guide=Image.open(shots/'guide.png').convert('RGB')
colors=dict((color,count) for count,color in guide.getcolors(guide.width*guide.height))
paper=colors.get((238,241,237),0)+colors.get((252,252,247),0)
assert paper>guide.width*guide.height*.12,('Article was covered by another background pass',paper)
first=Image.open(shots/'pcb-fit.png').convert('RGB');panned=Image.open(shots/'pcb-panned.png').convert('RGB')
assert first.size==panned.size
difference=ImageChops.difference(first,panned).crop((first.width//3,first.height//6,first.width*2//3,first.height*2//3))
assert difference.getbbox() is not None,'PCB panning did not change the rendered canvas'
for name in ('guide.png','pcb-fit.png','pcb-panned.png','pcb-scaled.png','pcb-region.png','pcb-sequential.png','pcb-analog.png','pcb-buck.png','surfaces-top.png','surfaces-profile.png'):
    with Image.open(shots/name) as image:assert image.width>=1000 and image.height>=700
canvas_bounds=json.loads((folder/'canvas-bounds.json').read_text())
for name, (left,top,right,bottom) in canvas_bounds.items():
    image=Image.open(shots/name).convert('RGB')
    copper=(219,183,117)
    inside=outside=0
    for y in range(image.height):
        for x in range(image.width):
            if image.getpixel((x,y))!=copper:continue
            if left-2<=x<right+2 and top-2<=y<bottom+2:inside+=1
            else:outside+=1
    assert inside>(10 if name in ('pcb-region.png','pcb-sequential.png','pcb-analog.png','pcb-buck.png') else 100),(name,'Missing PCB copper in canvas',inside)
    assert outside==0,(name,'PCB copper escaped the canvas into tools or inventory',outside)
assert all(report[k] for k in ('region_selection','copy_preview','tool_switch_cancels_paste','select_all_cancels_paste','sequential_tray','analog_tray','analog_samples'))
surface_review=json.loads((folder/'surface-rendering.json').read_text())
assert surface_review['baked_world_models']==6 and surface_review['cabinet_variants']==3
for name in ('surfaces-top.png','surfaces-profile.png'):
    picture=Image.open(shots/name).convert('RGB')
    # A missing/invisible mesh must not pass merely because the screen background rendered.
    for column in range(3):
        for row in range(3):
            region=picture.crop((int((12+column*205+12)*2),int((40+row*119+6)*2),int((12+column*205+193)*2),int((40+row*119+94)*2)))
            assert sum(n for n,c in region.getcolors(region.width*region.height) if c not in ((16,27,37),(27,42,54)))>region.width*region.height*.02,(name,column,row,'Missing model')
log=(ROOT/'build/surfaces-v17-interface-final.log').read_text(encoding='utf-8',errors='replace')
assert 'Failed to load model electron:' not in log
assert "Unable to load model: 'electron:" not in log
report.update({'uncovered_paper_pixels':paper,'model_load_errors':0,'screenshots':10,'canvas_clipping':True,'surface_model_views':surface_review})
(folder/'verified.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
