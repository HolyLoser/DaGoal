from PIL import Image,ImageDraw
import glob,os,math
fs=glob.glob(r'tmp/pdfs/review-*.png')
ims=[]
for f in fs:
 im=Image.open(f).convert('RGB'); im.thumbnail((300,390)); ims.append((f,im.copy()))
w=630; h=math.ceil(len(ims)/2)*420
out=Image.new('RGB',(w,h),'#aaaaaa'); d=ImageDraw.Draw(out)
for i,(f,im) in enumerate(ims):
 x=(i%2)*315+7; y=(i//2)*420+20
 out.paste(im,(x,y)); d.text((x,y-15),os.path.basename(f),fill='black')
out.save(r'tmp/pdfs/contact.png')
print('saved',out.size)
