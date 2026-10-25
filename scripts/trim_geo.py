import sys
def varint(b,i):
    r=s=0
    while True:
        c=b[i];i+=1;r|=(c&0x7f)<<s;s+=7
        if c<0x80:return r,i
def entries(b):
    i=0
    while i<len(b):
        st=i;k,i=varint(b,i);assert k==0x0a;l,i=varint(b,i);yield b[st:i+l],b[i:i+l];i+=l
def code(e):
    k,i=varint(e,0);l,i=varint(e,i);return e[i:i+l].decode().lower()
src,dst,keep=sys.argv[1],sys.argv[2],set(sys.argv[3].lower().split(','))
b=open(src,'rb').read();out=bytearray();found=set()
for raw,e in entries(b):
    c=code(e)
    if c in keep: out+=raw;found.add(c)
open(dst,'wb').write(out);print(dst,len(b),'->',len(out),'missing:',keep-found)
