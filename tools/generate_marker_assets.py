"""Reproducible original32px marker icons, opaque plates and Minecraft models.

Pixel geometry is authored here; no retail or downloaded artwork is included.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/generalsmc'
for kind,name,color in [('start','army_start_marker','#48bfff'),('supply','supply_marker','#62e179'),('oil','oil_derrick_marker','#ffbc42')]:
    icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon)
    dark='#152331';white='#edf5fa'
    if kind=='start':
        d.rectangle((6,3,9,28),fill=dark);d.rectangle((7,3,8,27),fill=white)
        d.polygon([(10,3),(27,3),(23,10),(27,16),(10,16)],fill=dark)
        d.polygon([(10,5),(24,5),(21,10),(24,14),(10,14)],fill=color)
        d.rectangle((4,27,13,29),fill=dark)
    elif kind=='supply':
        d.polygon([(4,9),(16,3),(28,9),(28,23),(16,30),(4,23)],fill=dark)
        d.polygon([(6,11),(15,16),(15,27),(6,22)],fill='#359b58')
        d.polygon([(17,16),(26,11),(26,22),(17,27)],fill=color)
        d.polygon([(7,9),(16,5),(25,9),(16,14)],fill='#9dffac')
        d.line((12,7,22,12,22,17),fill=white,width=2)
    else:
        d.polygon([(13,3),(20,3),(27,28),(24,30),(6,30),(5,27)],fill=dark)
        d.polygon([(15,5),(18,5),(24,27),(9,27)],fill=color)
        d.polygon([(16,10),(13,19),(20,19)],fill=dark)
        d.rectangle((12,21,22,23),fill=dark);d.rectangle((12,25,22,27),fill=dark)
        d.rectangle((5,28,27,29),fill=white)
    for folder in ['textures/item','textures/block','models/item','models/block']:(root/folder).mkdir(parents=True,exist_ok=True)
    icon.save(root/f'textures/item/{name}.png')
    plate=Image.new('RGBA',(32,32),'#263342');p=ImageDraw.Draw(plate);p.rectangle((1,1,30,30),outline=color,width=2);plate.alpha_composite(icon);plate.save(root/f'textures/block/{name}.png')
    (root/f'models/item/{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'generalsmc:item/{name}'}})+'\n')
    # Implicit face UVs use element coordinates. Tall elements exceed16 and
    # sample unrelated atlas sprites unless every face specifies a valid UV.
    faces={face:{'texture':'#plate','uv':[0,0,16,16]} for face in ['down','up','north','south','east','west']}
    model={'textures':{'plate':f'generalsmc:block/{name}','metal':'minecraft:block/iron_block','particle':f'generalsmc:block/{name}'},'elements':[{'from':[0,0,0],'to':[16,2,16],'faces':faces},{'from':[3,2,7],'to':[5,31,9],'faces':{f:{'texture':'#metal','uv':[0,0,16,16]} for f in faces}},{'from':[5,17,7],'to':[16,30,9],'faces':faces}]}
    (root/f'models/block/{name}.json').write_text(json.dumps(model)+'\n')
preview=Image.new('RGBA',(192,64),'#b6bdc6')
for i,name in enumerate(['army_start_marker','supply_marker','oil_derrick_marker']):preview.alpha_composite(Image.open(root/f'textures/item/{name}.png').resize((64,64),Image.Resampling.NEAREST),(i*64,0))
output=root.parents[4]/'evidence/marker-icons-preview.png';output.parent.mkdir(exist_ok=True);preview.save(output)
print(output)
