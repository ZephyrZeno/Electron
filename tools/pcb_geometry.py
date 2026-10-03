"""Chassis-specific, segmented workshop models. Rear sockets match ComponentPins exactly."""
def precision_geometry(name,cube,round_vertical):
 c=cube
 def bolt(x,y,z):
  return [c((x-.19,y,z-.19),(x+.19,y+.12,z+.19),'brushed_steel'),c((x-.13,y+.121,z-.025),(x+.13,y+.13,z+.025),'rubber')]
 def legs(top=2):
  a=[]
  for x in (1.2,13.4):
   for z in (1.2,13.4):a += [c((x-.2,0,z-.2),(x+1.2,.45,z+1.2),'rubber'),c((x,.45,z),(x+1,top,z+1),'brushed_steel')]
  return a
 def base():
  return legs()+[c((.7,1.3,.7),(15.3,2,15.3),'brushed_steel'),c((1.1,2,1.1),(14.9,2.18,14.9),'rubber')]
 def service():
  # Low horizontal junction enclosure; the two contacts sit flush in the rear wall.
  a=[c((.8,5.2,.12),(13.2,8.8,1.5),'enamel'),c((1.3,5.7,.10),(12.7,8.3,.13),'rubber')]
  for x,tx in ((3,'terminal_red'),(11,'terminal_black')):
   a += [c((x-.85,6.1,.06),(x+.85,7.9,.3),'brushed_steel'),c((x-.62,6.35,.04),(x+.62,7.65,.13),tx),
         c((x-.31,6.65,.018),(x+.31,7.35,.045),'rubber'),c((x-.11,6.9,0),(x+.11,7.1,.019),'gold_trace')]
  for x in (1.5,12.5):
   for y in (5.5,8.5):a += [c((x-.12,y-.12,.07),(x+.12,y+.12,.14),'brushed_steel')]
  a += [c((5,6.2,.075),(9,7.8,.105),'rating_plate')]
  return a
 def panel(a,b):
  x,y,z=a;u,v,w=b
  return [c(a,b,'enamel'),c((x+.15,y+.15,z-.025),(u-.15,v-.15,z),'panel_seam')]
 def ribs(x,y,z,width,rows=5):
  return [c((x,y+j*.42,z),(x+width,y+j*.42+.14,z+.10),'rubber') for j in range(rows)]
 dynamic=[]
 if name=='precision_workbench':
  e=legs(11.3)+[c((.45,10.85,.45),(15.55,11.5,15.55),'brushed_steel'),c((.5,11.5,.5),(15.5,11.72,15.5),'enamel'),c((1,11.73,7.2),(15,11.8,15),'mat_detail')]
  e += [c((1.2,3,1.2),(14.4,3.45,2),'brushed_steel'),c((1.2,3,13.6),(14.4,3.45,14.4),'brushed_steel')]
  # Drawer cabinet, individual face seams and handles under the mat.
  e += [c((10,7,3),(15.25,10.8,14.5),'enamel')]
  for y in (7.25,8.85):
   e += [c((10.2,y,14.5),(15,y+1.35,14.65),'panel_seam'),c((10.4,y+.15,14.65),(14.8,y+1.15,14.73),'enamel'),c((11.5,y+.55,14.73),(13.7,y+.8,15),'brushed_steel')]
  # Optical inspection head, base, column, lens and adjustable task light.
  e += [c((1.4,11.72,1.8),(5.2,12.1,4.8),'rubber'),c((2,12.1,2.1),(2.8,15.1,3),'brushed_steel'),
        c((2,14.3,2.1),(6.6,15.1,3),'enamel'),c((5.5,13.2,2),(6.9,14.3,3.6),'ic_package'),
        c((5.8,12.9,2.3),(6.6,13.2,3.3),'uv_lens'),c((13.4,11.8,2),(14,15,2.7),'brushed_steel'),
        c((8,14.8,1.5),(14.5,15.4,3.3),'enamel'),c((8.4,14.75,1.8),(13.8,14.82,3),'porcelain')]
  # Soldering control station with a ribbed iron holder and cable strain relief.
  e += [c((10.2,11.72,3.7),(14.8,13.15,6.4),'enamel'),c((10.7,13.15,4.1),(13,13.20,5.8),'display_glass'),
        c((13.5,13.15,4.7),(14.2,13.6,5.4),'rubber'),c((7.8,11.8,4.1),(9.3,12.1,6.8),'rubber')]
  for z in (4.3,4.9,5.5,6.1):e += [c((8,12.1,z),(9.1,12.5,z+.16),'brushed_steel')]
  e += [c((8.35,12.35,4.1),(8.75,12.85,6.5),'rubber'),c((8.43,12.45,6.5),(8.67,12.7,7.15),'brushed_steel')]
  for x in (1.5,3.4,5.3):
   e += [c((x,11.8,5),(x+1.6,11.95,6.5),'porcelain')]
   for z in (5,6.4):e += [c((x,11.95,z),(x+1.6,12.2,z+.1),'brushed_steel')]
   e += [c((x+.45,11.95,5.4),(x+1.1,12.16,6.1),'ic_package')]
  e += [c((4,11.8,8.3),(11.8,12,13.8),'laminate'),c((6.6,12,10),(8.8,12.6,12.1),'ic_package')]
  for x in (3.6,12):e += [c((x,11.8,8.2),(x+.5,12.5,14),'brushed_steel'),c((x-.3,12.2,10.5),(x+.8,12.6,11.6),'rubber')]
  e+=service()
 elif name=='emergency_lamp':
  e=[c((x,0,z),(x+1,1.4,z+1),'rubber') for x in (2,13) for z in (2,5.5)]
  e += [c((1,1.4,1.5),(15,9.1,7),'enamel'),c((1.4,1.8,7),(14.6,8.7,7.16),'panel_seam'),c((1.7,2.1,7.16),(14.3,8.4,7.27),'enamel')]
  for x in (2,14):
   for y in (2.5,8):e += [c((x-.15,y-.15,7.27),(x+.15,y+.15,7.35),'brushed_steel')]
  e += [c((2.6,5.5,7.28),(4.5,6.2,7.36),'led_off'),c((10.7,5.2,7.28),(12.5,6.8,7.55),'rubber')]
  for x in (2.6,10.1):
   e += [c((x+.9,9.1,3.8),(x+1.5,10.5,4.6),'brushed_steel'),c((x,10.5,2.5),(x+4,11.4,7.1),'rubber'),
         c((x-.2,11.4,2.3),(x+4.2,13.9,7.3),'enamel'),c((x+.25,11.8,2.65),(x+3.75,13.95,6.95),'lens')]
   for v in (.4,1.3,2.2,3.1):e += [c((x+v,13.95,2.5),(x+v+.14,14.1,7.1),'brushed_steel')]
  e+=service()
 else:
  e=base()
  if name=='uv_exposure_unit':
   e += [c((1.3,2.2,1.5),(2.2,12.3,14.7),'enamel'),c((13.8,2.2,1.5),(14.7,12.3,14.7),'enamel'),c((2.2,2.2,1.5),(13.8,12.3,2.2),'enamel'),
         c((2.4,3.1,2.7),(13.6,3.8,13.9),'brushed_steel'),c((3.1,3.8,3.4),(12.9,4,13.2),'laminate')]
   e += [c((1.2,12.3,1.4),(14.8,13.2,14.8),'enamel'),c((3.1,13.2,3.2),(12.9,13.28,11.8),'rubber'),c((3.5,13.28,3.6),(12.5,13.34,11.4),'uv_detail')]
   for x in (1.8,13.2):e += [c((x,8.2,14.45),(x+.9,11.7,15.05),'brushed_steel')]
   e += [c((2.3,4.3,14.45),(13.7,11.8,14.65),'panel_seam'),c((3,5.1,14.65),(10.4,11.15,14.72),'uv_detail'),c((11.1,7,14.7),(12.8,9.6,14.77),'display_glass'),
         c((5.1,11.25,14.75),(10.2,11.7,15.25),'brushed_steel')]
   for x in (4,8,12):e += [c((x,2.3,14.7),(x+.5,3.7,15.0),'rubber')]
   e += ribs(2.6,5.3,1.25,2.5,4)
  elif name=='pcb_drill':
   e += [c((1.8,2.2,11.8),(3.3,13.7,14.8),'enamel'),c((12.7,2.2,11.8),(14.2,13.7,14.8),'enamel'),c((1.8,12.8,6.7),(14.2,14.5,14.8),'enamel')]
   for x in (3.6,12):e += [c((x,2.6,3),(x+.35,3.05,13.5),'brushed_steel')]
   e += [c((3,3.1,3.3),(13,3.9,13.4),'rubber'),c((3.8,3.9,4),(12.2,4.05,12.5),'laminate')]
   for x in (4.1,11.2):e += [c((x,4.05,4),(x+.7,4.5,5.5),'brushed_steel'),c((x,4.05,11.4),(x+.7,4.5,12.4),'brushed_steel')]
   for y in (5,7.2,9.4,11.6):e += [c((2.2,y,11.6),(2.9,y+.18,12.1),'brushed_steel'),c((13.1,y,11.6),(13.8,y+.18,12.1),'brushed_steel')]
   e += [c((4,12.15,7),(12,12.55,7.5),'brushed_steel'),c((1.7,4,1.7),(4.7,10.7,4.2),'enamel'),c((2,7,1.6),(4.4,9.8,1.7),'display_glass')]
   dynamic += [c((7,9.2,7),(9.5,13.4,10),'brushed_steel')]+round_vertical(8.25,7.7,8.5,1.02,1.5,'drill')+[c((8.12,4,8.37),(8.38,7.7,8.63),'drill')]
   for y in (4.8,5.4,6,6.6,7.2):dynamic += [c((8.06,y,8.3),(8.44,y+.12,8.7),'rubber')]
   for z in (5,5.5,6,6.5):e += [c((10.3,10.8,z),(11.6,11.8,z+.2),'rubber')]
  elif name=='pcb_etcher':
   e += [c((1.5,2.2,2),(14.5,3.2,14.5),'enamel'),c((1.5,3.2,2),(2.1,10.7,14.5),'brushed_steel'),c((13.9,3.2,2),(14.5,10.7,14.5),'brushed_steel'),c((2.1,3.2,13.9),(13.9,10.7,14.5),'brushed_steel'),
         c((2.1,3.2,2),(13.9,7.4,2.35),'panel_seam'),c((3,4.3,2.29),(12.8,6.7,2.36),'fluid_window'),c((2.2,6,2.4),(13.8,6.08,13.8),'chemical')]
   for x in (3,6,9,12):
    e += [c((x,3.3,4),(x+.18,9.8,13),'brushed_steel'),c((x+.22,6.1,5),(x+.39,9.5,12),'laminate')]
   e += [c((1.3,10.7,10.9),(14.7,12.7,14.7),'enamel'),c((2.1,12.7,11.6),(13.9,12.78,13.8),'vent')]
   # Visible pipe elbows, circulation pump and front dose cartridge.
   e += [c((.2,3,5),(.95,8.7,5.7),'steel_pipe'),c((.2,8,5),(3,8.7,5.7),'steel_pipe'),c((.2,3,5),(.95,3.7,10),'steel_pipe'),
         c((2.2,2.2,10),(4.8,4.8,13),'pump_paint'),c((9.4,7.6,2.25),(13.3,10.3,2.65),'display_glass')]
  elif name=='immersion_gold_line':
   # Three separately rimmed baths, staggered service plumbing and an overhead carrier.
   for x in (1.7,6.2,10.7):
    e += [c((x,2.2,3.6),(x+3.6,3.2,14.2),'enamel'),c((x,3.2,3.6),(x+.25,8.6,14.2),'brushed_steel'),c((x+3.35,3.2,3.6),(x+3.6,8.6,14.2),'brushed_steel'),
          c((x,3.2,3.6),(x+3.6,8.6,3.9),'enamel'),c((x,3.2,13.9),(x+3.6,8.6,14.2),'enamel'),c((x+.3,6.4,4),(x+3.3,6.48,13.8),'bath_gold' if x==6.2 else 'chemical')]
    for z in (3.45,14.2):e += [c((x-.13,8.6,z),(x+3.73,8.85,z+.18),'brushed_steel')]
    e += [c((x+.6,4.7,3.54),(x+3,7.2,3.61),'fluid_window'),c((x+.3,2.3,1.8),(x+3.3,4.5,3.2),'rubber')]
   for x in (.75,14.6):e += [c((x,2.2,12.1),(x+.65,15.15,12.8),'brushed_steel')]
   e += [c((.75,14.4,10.4),(15.25,15.1,12.8),'enamel'),c((1.4,13.95,10.6),(14.6,14.2,11),'brushed_steel'),
         c((12.8,10.1,1.8),(15.3,13.1,3.9),'enamel'),c((13.05,10.4,1.7),(15.05,12.8,1.8),'display_glass')]
   e += [c((6.9,12.7,10),(9.1,14.4,12.3),'pump_paint')]
   dynamic += [c((7.15,7,7),(7.45,12.7,11),'brushed_steel'),c((8.55,7,7),(8.85,12.7,11),'brushed_steel'),c((7.3,5.8,7.2),(8.7,9.3,7.35),'laminate')]
  else:
   # Feed bed, individually modeled coating rollers, guard rails and UV curing hood.
   e += [c((1.6,2.2,3),(14.4,3,14.5),'enamel'),c((2.3,3,3.5),(13.7,3.5,14),'rubber')]
   for z in (4,5,6,7,8,9,10,11,12,13):e += [c((2.5,3.5,z),(13.5,3.85,z+.35),'brushed_steel')]
   for x in (1.7,13.7):e += [c((x,3,5),(x+.6,11.6,11),'enamel')]
   e += [c((1.5,11.6,5),(14.5,12.5,11),'enamel'),c((2.8,11.3,8),(13.2,11.6,10.8),'uv_detail'),
         c((2.8,7.1,5.8),(13.2,8.4,6.8),'resin'),c((2.8,6.9,6.65),(13.2,7.1,6.8),'brushed_steel'),c((10.5,3.85,12),(12.3,4,13.7),'laminate')]
   e += round_vertical(4,12.5,6.9,1.25,2.3,'resin')+round_vertical(4,14.8,6.9,1.35,.35,'rubber')
   e += [c((11.7,6,1.7),(14.8,10.8,4),'enamel'),c((12,7,1.6),(14.5,10.5,1.7),'display_glass')]
  e+=service()
  for x in (1.6,14.4):
   for z in (1.6,14.4):e+=bolt(x,2.01,z)
 return e,dynamic
