import re, sys, numpy as np, moderngl, math
from PIL import Image
src=open('/home/claude/visuals-mod/src/main/java/dev/visuals/effects/PuddleShader.java',encoding='utf-8').read()
VERT=re.search(r'VERTEX = """\n(.*?)""";',src,re.S).group(1)
FRAG=re.search(r'FRAGMENT = """\n(.*?)""";',src,re.S).group(1)
W,H=960,540
fov=math.radians(70); a=W/H; near,far=0.05,512.0
f=1/math.tan(fov/2)
P=np.array([[f/a,0,0,0],[0,f,0,0],[0,0,(far+near)/(near-far),2*far*near/(near-far)],[0,0,-1,0]],dtype=np.float64)
pitch=math.radians(14)
fwd=np.array([0,-math.sin(pitch),-math.cos(pitch)]); right=np.array([1.0,0,0]); up=np.cross(right,fwd); zv=-fwd
R=np.stack([right,up,zv]); C=np.array([0.3,1.7,0.0])
ys,xs=np.mgrid[0:H,0:W]
ndcx=(xs+0.5)/W*2-1; ndcy=1-(ys+0.5)/H*2
vd=np.stack([ndcx*a/f, ndcy/f, -np.ones_like(ndcx)],-1); vd/=np.linalg.norm(vd,axis=-1,keepdims=True)
d=vd@R
color=np.zeros((H,W,3)); tbest=np.full((H,W),1e9)
yy=np.clip(d[...,1],0,1)
color[...]=np.stack([0.45+0.25*yy,0.62+0.2*yy,0.95+0*yy],-1)
def hit(t,col):
    global color,tbest
    m=(t>0)&(t<tbest); tbest=np.where(m,t,tbest)
    col=np.asarray(col)
    for k in range(3):
        c=col[...,k] if col.ndim==3 else col[k]
        color[...,k]=np.where(m,c,color[...,k])
with np.errstate(divide='ignore',invalid='ignore'):
    tg=np.where(d[...,1]<-1e-6,(0-C[1])/d[...,1],-1)
gp=C+d*tg[...,None]
chk=((np.floor(gp[...,0])+np.floor(gp[...,2]))%2).astype(float)
hit(tg,np.stack([0.30+0.10*chk,0.50+0.10*chk,0.20+0.05*chk],-1))
def box(lo,hi,col):
    with np.errstate(divide='ignore',invalid='ignore'):
        t1=(np.array(lo)-C)/d; t2=(np.array(hi)-C)/d
    tn=np.minimum(t1,t2); tf=np.maximum(t1,t2)
    tmin=np.max(tn,axis=-1); tmax=np.min(tf,axis=-1)
    hit(np.where((tmax>=tmin)&(tmax>0),tmin,-1),col)
box([-0.5,0,-6.5],[0.5,3,-5.5],[0.85,0.1,0.1])
box([3.0,0,-8.0],[6.0,4,-5.0],[0.9,0.85,0.7])
box([-6.0,0,-9.0],[-4.0,5,-7.0],[0.2,0.35,0.8])
hp=C+d*np.where(tbest<1e8,tbest,1)[...,None]
vpos=(hp-C)@R.T
clip=np.concatenate([vpos,np.ones((H,W,1))],-1)@P.T
depth=np.where(tbest<1e8,clip[...,2]/clip[...,3]*0.5+0.5,1.0)
inp=(np.clip(color,0,1)*255).astype('u1')
ctx=moderngl.create_standalone_context(backend='egl')
prog=ctx.program(vertex_shader=VERT,fragment_shader=FRAG)
ct=ctx.texture((W,H),3,np.ascontiguousarray(inp[::-1]).tobytes()); ct.filter=(moderngl.NEAREST,)*2
dt=ctx.texture((W,H),1,np.ascontiguousarray(depth[::-1]).astype('f4').tobytes(),dtype='f4'); dt.filter=(moderngl.NEAREST,)*2
ct.use(0); dt.use(1)
def setu(n,v):
    if n in prog: prog[n].value=v
setu('ColorTex',0); setu('DepthTex',1)
prog['Proj'].write(P.astype('f4').T.tobytes()); prog['InvProj'].write(np.linalg.inv(P).astype('f4').T.tobytes())
prog['ViewRot'].write(R.astype('f4').T.tobytes())
cov=float(sys.argv[1]); refl=float(sys.argv[2]); damp=float(sys.argv[3]); q=float(sys.argv[4])
setu('CamPos',tuple(C)); setu('OutSize',(W,H)); setu('Params',(cov,refl,1.0,q)); setu('Env',(0.55,0.72,1.0,0.0)); setu('Time',1.0); setu('Damp',damp)
fbo=ctx.framebuffer(ctx.texture((W,H),4)); fbo.use(); ctx.viewport=(0,0,W,H)
ctx.vertex_array(prog,[]).render(moderngl.TRIANGLES,vertices=3)
out=np.frombuffer(fbo.read(components=4),dtype='u1').reshape(H,W,4)[::-1,:,:3]
Image.fromarray(np.concatenate([inp,out],0)).resize((W//2,H)).save(sys.argv[5])
diff=np.abs(out.astype(int)-inp.astype(int)).sum(-1)
print(sys.argv[5],"- veränderte Pixel:",round(100*(diff>10).mean(),1),"%")
