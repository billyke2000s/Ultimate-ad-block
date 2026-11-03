from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
p=Path(__file__).resolve().parent
mono='/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
f=lambda n:ImageFont.truetype(mono,n)
b=lambda n:ImageFont.truetype(bold,n)
frames=[]
# A closed, orthogonal path: the snake loops without teleporting.
route=[(x,1) for x in range(2,28)]+[(27,y) for y in range(2,6)]+[(x,5) for x in range(26,1,-1)]+[(2,y) for y in range(4,1,-1)]
foods=[8,19,32,45]
for k in range(len(route)):
 im=Image.new('RGB',(1000,510),'#0b1110');d=ImageDraw.Draw(im)
 d.rounded_rectangle((1,1,998,508),radius=22,outline='#31443b',width=2)
 d.text((40,30),'OBSKYRA  /  ANDROID DNS FILTER',font=f(14),fill='#a9c5b1')
 d.text((40,66),'ULTIMATE AD BLOCK',font=b(49),fill='#eef7e9')
 d.text((42,132),'Small snake. Big appetite for ad domains.',font=f(20),fill='#b2d57e')
 # LCD game panel, with pixel grid.
 d.rounded_rectangle((40,184,960,422),radius=12,fill='#afbf8b')
 d.text((60,196),'S N A K E   /   A D  P A T R O L',font=f(14),fill='#2c4230')
 d.text((733,196),'DEMO LOOP',font=f(14),fill='#3d5136')
 ox,oy,cell=60,230,30
 for x in range(30):
  for y in range(6):d.rectangle((ox+x*cell+13,oy+y*cell+13,ox+x*cell+14,oy+y*cell+14),fill='#a2b27f')
 # Food disappears after the head reaches it; resets with the loop.
 for idx in foods:
  if k<idx:
   x,y=route[idx];px,py=ox+x*cell,oy+y*cell
   d.rectangle((px,py,px+26,py+26),outline='#31452d',width=2)
   d.text((px+3,py+5),'AD',font=f(14),fill='#253724')
 for age in range(10,-1,-1):
  x,y=route[(k-age)%len(route)];px,py=ox+x*cell,oy+y*cell
  d.rectangle((px+1,py+1,px+25,py+25),fill='#283e29' if age else '#162f20')
  if age==0:
   nx,ny=route[(k+1)%len(route)];dx,dy=nx-x,ny-y
   for ex,ey in ([(19,6),(19,18)] if dx>0 else [(5,6),(5,18)] if dx<0 else [(6,19),(18,19)] if dy>0 else [(6,5),(18,5)]):
    d.rectangle((px+ex,py+ey,px+ex+3,py+ey+3),fill='#cfdfa4')
 d.text((42,452),'LOCAL RULES',font=f(14),fill='#d3e5c5')
 d.text((343,452),'ENCRYPTED UPSTREAM',font=f(14),fill='#d3e5c5')
 d.text((737,452),'ON-DEVICE STATS',font=f(14),fill='#d3e5c5')
 frames.append(im)
frames[0].save(p/'ad-patrol.gif',save_all=True,append_images=frames[1:],duration=110,loop=0,optimize=True,disposal=1)
print('Frames:',len(frames),'GIF bytes:',(p/'docs/assets/ad-patrol.gif').stat().st_size)
