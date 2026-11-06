from PIL import Image
import glob,os
arr=[]
for f in glob.glob('app/src/main/res/drawable*/*'):
 try:
  im=Image.open(f); w,h=im.size; arr.append((w*h,w,h,os.path.getsize(f),f))
 except Exception: pass
for x in sorted(arr,reverse=True)[:30]: print(x)
