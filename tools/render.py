"""простий софтверний рендер DFF з текстурами (перевірка без гри)."""
import sys, os, math, glob, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gtaLib import dff, txd
from PIL import Image
def load_txd(p):
    t=txd.txd(); t.load_file(p); out={}
    for n in t.native_textures:
        w,h=n.width,n.height; px=bytes(n.pixels[0])
        a=np.frombuffer(px,dtype=np.uint8).reshape(h,w,4)[:,:,[2,1,0,3]]
        out[n.name]=a.astype(np.float32)/255
    return out
def render(models, texs, cam, look, out, W=1280, H=720, fov=60):
    img=np.zeros((H,W,3),np.float32); img[:]=np.array([0.62,0.74,0.86]); zb=np.full((H,W),1e9,np.float32)
    cam=np.array(cam,float); look=np.array(look,float); f=look-cam; f/=np.linalg.norm(f)
    r=np.cross(f,[0,0,1]); r/=np.linalg.norm(r); u=np.cross(r,f); fl=(W/2)/math.tan(math.radians(fov/2))
    sun=np.array([0.4,0.3,0.85]); sun/=np.linalg.norm(sun)
    for path,off in models:
        d=dff.dff(); d.load_file(path); g=d.clumps[0].geometry_list[0]
        V=np.array([[v.x,v.y,v.z] for v in g.vertices])+np.array(off)
        N=np.array([[n.x,n.y,n.z] for n in g.normals]) if g.normals else np.zeros_like(V)
        UV=np.array([[t.u,t.v] for t in g.uv_layers[0]])
        rel=V-cam; zc=rel@f; xs=W/2+fl*(rel@r)/np.maximum(zc,1e-3); ys=H/2-fl*(rel@u)/np.maximum(zc,1e-3)
        for t in g.triangles:
            ids=[t.a,t.b,t.c]
            if (zc[ids]<0.5).any(): continue
            tex=texs.get(g.materials[t.material].textures[0].name)
            x=xs[ids]; y=ys[ids]
            x0,x1=int(max(0,np.floor(x.min()))),int(min(W-1,np.ceil(x.max()))); y0,y1=int(max(0,np.floor(y.min()))),int(min(H-1,np.ceil(y.max())))
            if x0>x1 or y0>y1: continue
            gx,gy=np.meshgrid(np.arange(x0,x1+1)+0.5,np.arange(y0,y1+1)+0.5)
            den=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2])
            if abs(den)<1e-9: continue
            w0=((y[1]-y[2])*(gx-x[2])+(x[2]-x[1])*(gy-y[2]))/den; w1=((y[2]-y[0])*(gx-x[2])+(x[0]-x[2])*(gy-y[2]))/den; w2=1-w0-w1
            m=(w0>=0)&(w1>=0)&(w2>=0)
            if not m.any(): continue
            iz=w0/zc[ids[0]]+w1/zc[ids[1]]+w2/zc[ids[2]]; z=1/iz
            sub=zb[y0:y1+1,x0:x1+1]; m&=z<sub
            if not m.any(): continue
            uv=(w0[...,None]*UV[ids[0]]/zc[ids[0]]+w1[...,None]*UV[ids[1]]/zc[ids[1]]+w2[...,None]*UV[ids[2]]/zc[ids[2]])*z[...,None]
            if tex is None: c=np.ones(gx.shape+(3,))*0.7
            else:
                th,tw=tex.shape[:2]; tu=((uv[...,0]%1)*tw).astype(int)%tw; tv=((uv[...,1]%1)*th).astype(int)%th
                c=tex[tv,tu,:3]
            n=N[ids].mean(0); lam=0.55+0.45*max(0,float(np.dot(n,sun)))
            sub[m]=z[m]; img[y0:y1+1,x0:x1+1][m]=c[m]*lam
    Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).save(out)
if __name__=='__main__':
    pass
