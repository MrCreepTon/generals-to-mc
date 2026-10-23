// SPDX-License-Identifier: GPL-3.0-or-later
// Export original, posed WW3D geometry. Assets remain in the user's private session.
#include "Precompiled/PreRTS.h"
#include "WorldScene.h"
#include "Bridge.h"
#include "GameClient/ParticleSys.h"
#include "W3DDevice/GameClient/W3DDisplay.h"
#include "W3DDevice/GameClient/W3DScene.h"
#include "WW3D2/lightenvironment.h"
#include "W3DDevice/GameClient/W3DAssetManager.h"
#include "W3DDevice/GameClient/W3DProjectedShadow.h"
#include "W3DDevice/GameClient/BaseHeightMap.h"
#include "W3DDevice/GameClient/W3DBibBuffer.h"
#include "GameLogic/PartitionManager.h"
#include "Common/PlayerList.h"
#include "Common/Player.h"
#include "Common/ThingTemplate.h"
#include "GameLogic/TerrainLogic.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Object.h"
#include "GameClient/Drawable.h"
#include "GameClient/GameClient.h"
#include "GameClient/InGameUI.h"
#include "W3DDevice/GameClient/W3DInGameUI.h"
#include "Common/DrawModule.h"
#include "W3DDevice/GameClient/Module/W3DModelDraw.h"
#include "W3DDevice/GameClient/Module/W3DLaserDraw.h"
#include "WW3D2/mesh.h"
#include "WW3D2/meshmdl.h"
#include "WW3D2/hlod.h"
#include "WW3D2/segline.h"
#include "WW3D2/line3d.h"
#include "W3DDevice/GameClient/W3DWaypointBuffer.h"
#include "W3DDevice/GameClient/W3DView.h"
#include "texture.h"
#include "WW3D2/mapper.h"
#include "WW3D2/vertmaterial.h"
#include "dx8wrapper.h"
#include <d3dx8tex.h>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <algorithm>
#include <cmath>
#include <cstdlib>

namespace zhmc {
namespace {
float centerX = 0, centerY = 0, radius = 0;
DWORD last = 0;
DWORD worldStarted = 0;
bool worldViewMode = true;
std::unordered_map<TextureClass*, unsigned int> textures;
std::unordered_map<unsigned long long,unsigned int> maskedTextures;
unsigned int nextTextureId=0;
std::unordered_map<TextureMapperClass*,Matrix4x4> uvMatrices;
struct ModelCapture{RenderObjClass* model;unsigned int generation;};
std::unordered_map<const void*,ModelCapture> models;
unsigned int modelGeneration=0;
unsigned int exportedParticles=0,exportedDecals=0,repairedUV=0,testFXParticles=0;
std::unordered_set<unsigned int> priorSystems,testSystems;
template<class T> void put(std::ostream& out, const T& value) { out.write(reinterpret_cast<const char*>(&value), sizeof(value)); }

unsigned int textureId(TextureClass* texture, const std::filesystem::path& root) {
    if (!texture) return 0;
    auto found = textures.find(texture);
    if (found != textures.end()) return found->second;
    auto device = DX8Wrapper::_Get_D3D_Device8();
    if (!device) return 0;
    if(!texture->Peek_D3D_Texture())texture->Init(); // New construction assets need loading even with the native world pass suppressed.
    if(!texture->Peek_D3D_Texture())return 0;
    IDirect3DSurface8* source = texture->Get_D3D_Surface_Level();
    if (!source) return 0;
    D3DSURFACE_DESC desc; source->GetDesc(&desc);
    if (desc.Width > 2048 || desc.Height > 2048) { source->Release(); return 0; }
    IDirect3DSurface8* copy = nullptr;
    HRESULT result = device->CreateImageSurface(desc.Width, desc.Height, D3DFMT_A8R8G8B8, &copy);
    if (SUCCEEDED(result)) result = D3DXLoadSurfaceFromSurface(copy, nullptr, nullptr, source, nullptr, nullptr, D3DX_FILTER_NONE, 0);
    source->Release();
    if (FAILED(result)) { if (copy) copy->Release(); return 0; }
    D3DLOCKED_RECT pixels;
    if (FAILED(copy->LockRect(&pixels, nullptr, D3DLOCK_READONLY))) { copy->Release(); return 0; }
    unsigned int id = ++nextTextureId;
    std::filesystem::create_directories(root / "textures");
    auto temp = root / "textures" / (std::to_string(id) + ".tmp");
    {
        std::ofstream file(temp, std::ios::binary | std::ios::trunc);
        put(file, desc.Width); put(file, desc.Height);
        for (UINT row = 0; row < desc.Height; ++row)
            file.write(static_cast<const char*>(pixels.pBits) + row * pixels.Pitch, desc.Width * 4);
    }
    copy->UnlockRect(); copy->Release();
    if (!MoveFileExW(temp.c_str(), (root / "textures" / (std::to_string(id) + ".rgba")).c_str(), MOVEFILE_REPLACE_EXISTING)) return 0;
    // Keep identities alive: an allocator may otherwise reuse a deleted texture address.
    texture->Add_Ref();textures.emplace(texture, id);
    if(std::getenv("ZHMC_TEST")){std::ofstream log(root/"texture-names.txt",std::ios::app);log<<id<<" "<<static_cast<const char*>(texture->Get_Texture_Name())<<"\n";}
    return id;
}

bool matchingTextureCoordinates(MeshModelClass* model,int pass,int count){
    auto first=model->Get_UV_Array(pass,0),second=model->Get_UV_Array(pass,1);if(!first||!second||std::memcmp(first,second,count*sizeof(Vector2))!=0)return false;
    for(int vertex=0;vertex<count;vertex++){auto material=model->Peek_Material(vertex,pass);if(!material)continue;Matrix4x4 a,b;a.Make_Identity();b.Make_Identity();auto m0=material->Peek_Mapper(0),m1=material->Peek_Mapper(1);if((m0&&m0->Needs_Normals())||(m1&&m1->Needs_Normals()))return false;if(m0)m0->Calculate_Texture_Matrix(a);if(m1)m1->Calculate_Texture_Matrix(b);if(std::memcmp(&a,&b,sizeof(a))!=0)return false;}return true;
}

// Fixed-function WW3D can take coverage from stage1 while stage0 supplies team colour.
// Bake the static, shared-UV alpha operation once; never infer coverage from RGB black.
unsigned int maskedTextureId(TextureClass* base,TextureClass* mask,int operation,const std::filesystem::path& root){
    unsigned int first=textureId(base,root),second=textureId(mask,root);if(!first||!second)return first;
    unsigned long long key=(static_cast<unsigned long long>(first)<<32)|(static_cast<unsigned long long>(second)<<2)|operation;
    auto found=maskedTextures.find(key);if(found!=maskedTextures.end())return found->second;
    auto read=[&](unsigned int id,unsigned int& w,unsigned int& h,std::vector<unsigned char>& pixels){std::ifstream f(root/"textures"/(std::to_string(id)+".rgba"),std::ios::binary);f.read(reinterpret_cast<char*>(&w),4);f.read(reinterpret_cast<char*>(&h),4);if(!f||!w||!h||w>2048||h>2048)return false;pixels.resize(w*h*4);return !!f.read(reinterpret_cast<char*>(pixels.data()),pixels.size());};
    unsigned int w,h,mw,mh;std::vector<unsigned char> pixels,coverage;if(!read(first,w,h,pixels)||!read(second,mw,mh,coverage))return first;
    for(unsigned int y=0;y<h;y++)for(unsigned int x=0;x<w;x++){auto& a=pixels[(y*w+x)*4+3];unsigned int b=coverage[((y*mh/h)*mw+x*mw/w)*4+3];if(operation==ShaderClass::DETAILALPHA_DETAIL)a=b;else if(operation==ShaderClass::DETAILALPHA_SCALE)a=(a*b+127)/255;else if(operation==ShaderClass::DETAILALPHA_INVSCALE)a=255-((255-a)*(255-b)+127)/255;}
    unsigned int id=++nextTextureId;auto temp=root/"textures"/(std::to_string(id)+".tmp");{std::ofstream f(temp,std::ios::binary);put(f,w);put(f,h);f.write(reinterpret_cast<const char*>(pixels.data()),pixels.size());if(!f)return first;}
    if(!MoveFileExW(temp.c_str(),(root/"textures"/(std::to_string(id)+".rgba")).c_str(),MOVEFILE_REPLACE_EXISTING))return first;
    maskedTextures.emplace(key,id);if(std::getenv("ZHMC_TEST")){std::ofstream log(root/"texture-names.txt",std::ios::app);log<<id<<" alpha-stage "<<first<<" "<<second<<" "<<operation<<"\n";}return id;
}

// Transport WW3D blend/depth policy; Minecraft supplies world light to ordinary meshes.
unsigned int materialMode(const ShaderClass& shader,bool lit){return 0x30000u|(shader.Get_Depth_Compare()<<12)|(shader.Get_Color_Mask()<<10)|shader.Get_Src_Blend_Func()|(shader.Get_Dst_Blend_Func()<<4)|(shader.Get_Alpha_Test()<<8)|(shader.Get_Depth_Mask()<<9)|(lit?0x800u:0);}
unsigned int alphaMode(bool lit){return 0x10000u|ShaderClass::SRCBLEND_SRC_ALPHA|(ShaderClass::DSTBLEND_ONE_MINUS_SRC_ALPHA<<4)|(lit?0x800u:0);}
unsigned int color(MeshModelClass* model,int vertex,int pass,float alpha,const Vector3* tint) {
    Vector3 diffuse(1,1,1);float opacity=alpha;auto material=model->Peek_Material(vertex,pass);
    if(material){material->Get_Diffuse(&diffuse);opacity*=material->Get_Opacity();}
    if(auto dcg=model->Get_DCG_Array(pass)){auto c=dcg[vertex];diffuse.Set(((c>>16)&255)/255.f,((c>>8)&255)/255.f,(c&255)/255.f);opacity*=((c>>24)&255)/255.f;}
    // Preserve per-drawable highlight without baking the native desert sun into Minecraft.
    if(tint)diffuse.Set(diffuse.X*(1+tint->X),diffuse.Y*(1+tint->Y),diffuse.Z*(1+tint->Z));
    auto byte=[](float f){return static_cast<unsigned int>(std::clamp(f,0.f,1.f)*255+.5f);};
    return byte(opacity)<<24|byte(diffuse.X)<<16|byte(diffuse.Y)<<8|byte(diffuse.Z);
}

void mesh(std::ostream& out, MeshClass* object, unsigned int owner, const std::filesystem::path& root, unsigned int& batches, unsigned int& total,unsigned long long part,const Vector3* tint) {
    MeshModelClass* model = object->Peek_Model();
    int count = model->Get_Vertex_Count(), faces = model->Get_Polygon_Count();
    if (count <= 0 || count > 65536 || faces <= 0 || total + faces * 3 > 300000) return;
    std::vector<Vector3> vertices(count), normals(count);
    if (model->Get_Flag(MeshGeometryClass::SKIN)) {
        if (!object->Get_Container() || !object->Get_Container()->Get_HTree()) return;
        object->Get_Deformed_Vertices(vertices.data(), normals.data()); // Original bone evaluation, already in world coordinates.
    } else {
        auto position = model->Get_Vertex_Array(); auto normal = model->Get_Vertex_Normal_Array();
        const Matrix3D& transform = object->Get_Transform();
        Matrix3D rotate = transform; rotate.Set_Translation(Vector3(0, 0, 0));
        for (int v = 0; v < count; ++v) {
            Matrix3D::Transform_Vector(transform, position[v], &vertices[v]);
            Matrix3D::Transform_Vector(rotate, normal[v], &normals[v]);
        }
    }
    auto triangles=model->Get_Polygon_Array();
    for(int pass=0;pass<model->Get_Pass_Count();++pass){auto uv=model->Get_UV_Array(pass,0);
    for (int face = 0; face < faces;) {
        TextureClass* texture = model->Peek_Texture(face,pass,0);

        int end = face + 1;
        while (end < faces && model->Peek_Texture(end,pass,0)==texture&&model->Peek_Texture(end,pass,1)==model->Peek_Texture(face,pass,1)&&model->Get_Shader(end,pass)==model->Get_Shader(face,pass)) ++end;
        unsigned int id = textureId(texture, root), n = (end - face) * 3;
        auto shader=model->Get_Shader(face,pass);auto mask=model->Peek_Texture(face,pass,1);
        if(mask&&shader.Get_Post_Detail_Alpha_Func()!=ShaderClass::DETAILALPHA_DISABLE&&shader.Get_Post_Detail_Color_Func()==ShaderClass::DETAILCOLOR_DISABLE&&matchingTextureCoordinates(model,pass,count))
            id=maskedTextureId(texture,mask,shader.Get_Post_Detail_Alpha_Func(),root);
        if(total+n>300000)return;auto key=(part^static_cast<unsigned int>(face)^(static_cast<unsigned long long>(pass)<<48))*1099511628211ULL;
        put(out, owner); put(out, id); put(out, n);put(out,key);unsigned int mode=materialMode(model->Get_Shader(face,pass),true);put(out,mode);
        for (; face < end; ++face) for (int corner = 0; corner < 3; ++corner) {
            int v = triangles[face][corner];
            put(out, vertices[v].X); put(out, vertices[v].Y); put(out, vertices[v].Z);
            put(out, normals[v].X); put(out, normals[v].Y); put(out, normals[v].Z);
            float u = uv ? uv[v].X : 0, t = uv ? uv[v].Y : 0; if(!std::isfinite(u)){u=0;++repairedUV;}if(!std::isfinite(t)){t=0;++repairedUV;}auto material=model->Peek_Material(v,pass);auto mapper=material?material->Peek_Mapper(0):nullptr;
            if(mapper&&!mapper->Needs_Normals()){auto found=uvMatrices.find(mapper);if(found==uvMatrices.end()){Matrix4x4 matrix;mapper->Calculate_Texture_Matrix(matrix);found=uvMatrices.emplace(mapper,matrix).first;}auto& m=found->second;float mappedU=m[0].X*u+m[0].Y*t+m[0].Z,mappedV=m[1].X*u+m[1].Y*t+m[1].Z;if(std::isfinite(mappedU)&&std::isfinite(mappedV)){u=mappedU;t=mappedV;}}
            put(out, u); put(out, t);
            unsigned int c = color(model,v,pass,object->Get_Alpha_Override(),tint); put(out, c);
        }
        ++batches;total+=n;
    }}
}

void walk(std::ostream& out, RenderObjClass* object, unsigned int owner, const std::filesystem::path& root, unsigned int& batches, unsigned int& total,unsigned long long part, int depth = 0,const Vector3* tint=nullptr) {
    if (!object || depth > 16 || object->Is_Hidden() || object->Is_Animation_Hidden()) return;
    object->Get_Transform(); // Validate animated hierarchy transforms through the original engine.
    for(auto name=object->Get_Name();name&&*name;++name)part=(part^static_cast<unsigned char>(*name))*1099511628211ULL;
    if(object->Class_ID()==RenderObjClass::CLASSID_LINE3D){
        auto line=static_cast<Line3DClass*>(object);if(total+36>300000)return;
        unsigned int n=36,texture=0,mode=materialMode(line->hostShader(),false);
        put(out,owner);put(out,texture);put(out,n);put(out,part);put(out,mode);
        auto c=line->hostColor();auto byte=[](float v){return static_cast<unsigned int>(std::clamp(v,0.f,1.f)*255+.5f);};
        unsigned int color=byte(c.W)<<24|byte(c.X)<<16|byte(c.Y)<<8|byte(c.Z);
        for(int i=0;i<36;++i){Vector3 p;Matrix3D::Transform_Vector(line->Get_Transform(),line->hostVertices()[line->hostIndices()[i]],&p);put(out,p.X);put(out,p.Y);put(out,p.Z);float zero=0,one=1;put(out,zero);put(out,zero);put(out,one);put(out,zero);put(out,zero);put(out,color);}
        ++batches;total+=36;return;
    }
    if (object->Class_ID() == RenderObjClass::CLASSID_MESH) { mesh(out, static_cast<MeshClass*>(object), owner, root, batches, total,part,tint); return; }
    if (object->Class_ID() == RenderObjClass::CLASSID_HLOD) {
        auto lod = static_cast<HLodClass*>(object);
        lod->updateForHost(); // Original animation advancement/hierarchy evaluation, independent of hover or hidden native rendering.
        // Native camera culling/NULL LOD must not hide models seen by Minecraft's camera.
        if (lod->Get_Lod_Count() < 1) return;
        int level = lod->Get_Lod_Count() - 1;
        for (int i = 0; i < lod->Get_Lod_Model_Count(level); ++i) walk(out, lod->Peek_Lod_Model(level, i), owner, root, batches, total,(part^i)*1099511628211ULL, depth + 1,tint);
        for (int i = 0; i < lod->Get_Additional_Model_Count(); ++i) walk(out, lod->Peek_Additional_Model(i), owner, root, batches, total,(part^(0x10000u+i))*1099511628211ULL, depth + 1,tint);
    } else for (int i = 0; i < object->Get_Num_Sub_Objects(); ++i) {
        auto child = object->Get_Sub_Object(i);
        walk(out, child, owner, root, batches, total,(part^i)*1099511628211ULL, depth + 1,tint);
        if (child) child->Release_Ref();
    }
}
}

namespace {
struct EffectExport {std::ostream& out;const std::filesystem::path& root;unsigned int& batches;unsigned int& total;};
EffectExport* waypointExport=nullptr;
unsigned int rgba(float r,float g,float b,float a){auto byte=[](float v){return static_cast<unsigned int>(std::clamp(v,0.f,1.f)*255+.5f);};return byte(a)<<24|byte(r)<<16|byte(g)<<8|byte(b);}
void effectVertex(std::ostream& out,const Vector3& p,float u,float v,unsigned int c){put(out,p.X);put(out,p.Y);put(out,p.Z);float zero=0,one=1;put(out,zero);put(out,zero);put(out,one);put(out,u);put(out,v);put(out,c);}
bool visibleEffect(float x,float y,float z){
    if(std::hypot(x-centerX,y-centerY)>radius)return false;
    auto player=ThePlayerList->getLocalPlayer();Coord3D p={x,y,z};
    if(hostTerrainKnown(&p))return true;
    return player&&ThePartitionManager->getShroudStatusForPlayer(player->getPlayerIndex(),&p)==CELLSHROUD_CLEAR;
}
void decal(void* data,TextureClass* texture,const Matrix3D& transform,float sx,float sy,float ou,float ov,unsigned int c,int type,const void* identity){
    auto& e=*static_cast<EffectExport*>(data);auto center=transform.Get_Translation();
    if((!worldInterfaceActive()&&identity==TheInGameUI->hostCursorShadow())||!visibleEffect(center.X,center.Y,center.Z)||sx<=0||sy<=0||!std::isfinite(sx)||!std::isfinite(sy))return;
    int nx=std::clamp(static_cast<int>(std::ceil(sx/worldUnitsPerBlock())),1,128),ny=std::clamp(static_cast<int>(std::ceil(sy/worldUnitsPerBlock())),1,128);unsigned int n=nx*ny*6;
    if(e.total+n>300000)return;
    // Use the same preset as W3DProjectedShadowManager::flushDecals.
    auto shader=type==SHADOW_ADDITIVE_DECAL?ShaderClass::_PresetAdditiveShader:type==SHADOW_DECAL?ShaderClass::_PresetMultiplicativeShader:ShaderClass::_PresetAlphaShader;
    auto id=textureId(texture,e.root);unsigned int owner=0xd0000000,mode=materialMode(shader,false)&~0x200u;
    if(type&SHADOW_ADDITIVE_DECAL)c|=0xff000000;
    auto key=0xd000000000000000ULL^static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(identity));
    put(e.out,owner);put(e.out,id);put(e.out,n);put(e.out,key);put(e.out,mode);
    auto uAxis=transform.Get_X_Vector();uAxis.Z=0;if(uAxis.Length2()<.0001f)uAxis.Set(1,0,0);uAxis.Normalize();Vector3 vAxis(uAxis.Y,-uAxis.X,0);
    const int triangles[]={0,1,2,0,2,3};
    for(int y=0;y<ny;++y)for(int x=0;x<nx;++x){float us[]={float(x)/nx,float(x+1)/nx,float(x+1)/nx,float(x)/nx},vs[]={float(y)/ny,float(y)/ny,float(y+1)/ny,float(y+1)/ny};Vector3 points[4];
        for(int k=0;k<4;++k){points[k]=center+uAxis*((us[k]-.5f-ou)*sx)+vAxis*((vs[k]-.5f-ov)*sy);float height;if(!terrainHeight(points[k].X,points[k].Y,height))height=TheTerrainLogic->getGroundHeight(points[k].X,points[k].Y);points[k].Z=height+1;}
        for(int k:triangles)effectVertex(e.out,points[k],us[k],vs[k],c);
    }++e.batches;e.total+=n;++exportedDecals;
}
void particles(EffectExport& e){
    if(!TheParticleSystemManager||!worldTransportActive())return;
    Coord3D l,u;hostCameraBasis(l,u);Vector3 right(-l.x,-l.y,-l.z),up(u.x,u.y,u.z);
    for(auto sys:TheParticleSystemManager->getAllParticleSystems()){
        if(!sys||sys->isUsingDrawables()||sys->isUsingSmudge())continue;
        TextureClass* texture=nullptr;unsigned int id=0;
        for(auto p=sys->getFirstParticle();p;p=p->m_systemNext){
            auto pos=p->getPosition();float size=p->getSize();
            if(!visibleEffect(pos->x,pos->y,pos->z)||p->isInvisible()||!std::isfinite(size)||size<=0||e.total+6>300000)continue;
            if(!texture){texture=W3DDisplay::m_assetManager->Get_Texture(sys->getParticleTypeName().str());id=textureId(texture,e.root);}
            unsigned int owner=0xe0000000,n=6,mode=alphaMode(false);
            switch(sys->getShaderType()){case ParticleSystemInfo::ADDITIVE:mode=materialMode(ShaderClass::_PresetAdditiveSpriteShader,false);break;case ParticleSystemInfo::MULTIPLY:mode=materialMode(ShaderClass::_PresetMultiplicativeSpriteShader,false);break;case ParticleSystemInfo::ALPHA_TEST:mode=materialMode(ShaderClass::_PresetATestSpriteShader,false);break;default:break;}
            auto key=0xe000000000000000ULL^(static_cast<unsigned long long>(sys->getSystemID())<<32)^static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(p));
            put(e.out,owner);put(e.out,id);put(e.out,n);put(e.out,key);put(e.out,mode);
            auto color=p->getColor();auto c=rgba(color->red,color->green,color->blue,p->getAlpha());
            Vector3 a=right,b=up;if(sys->getParticleAlignment()==ParticleSystemInfo::PARTICLE_ALIGNMENT_XYPLANAR){a.Set(1,0,0);b.Set(0,1,0);}
            float angle=std::floor(p->getAngle()*255/(2*3.14159265f))*(2*3.14159265f/256),co=std::cos(angle),si=std::sin(angle);Vector3 ax=a*co+b*si,ay=b*co-a*si,center(pos->x,pos->y,pos->z);
            Vector3 v[]={center-ax*(size*.5f)+ay*(size*.5f),center-ax*(size*.5f)-ay*(size*.5f),center+ax*(size*.5f)-ay*(size*.5f),center+ax*(size*.5f)+ay*(size*.5f)};
            const int order[]={0,1,2,0,2,3};float us[]={0,0,1,1},vs[]={0,1,1,0};for(int k:order)effectVertex(e.out,v[k],us[k],vs[k],c);
            ++e.batches;e.total+=6;++exportedParticles;if(testSystems.count(sys->getSystemID()))++testFXParticles;
        }if(texture)texture->Release_Ref();
    }
}
void bib(void* data,TextureClass* texture,const Vector3* corners,unsigned int c,const void* identity,Bool preview){
    auto& e=*static_cast<EffectExport*>(data);Vector3 center=(corners[0]+corners[1]+corners[2]+corners[3])*.25f;
    if((preview&&!worldInterfaceActive())||!visibleEffect(center.X,center.Y,center.Z))return;
    auto a=corners[1]-corners[0],b=corners[3]-corners[0];int nx=std::clamp((int)std::ceil(a.Length()/worldUnitsPerBlock()),1,128),ny=std::clamp((int)std::ceil(b.Length()/worldUnitsPerBlock()),1,128);unsigned int n=nx*ny*6;
    if(e.total+n>300000)return;
    unsigned int owner=0xc0000000,id=textureId(texture,e.root),mode=alphaMode(true);auto key=0xc000000000000000ULL^static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(identity));
    put(e.out,owner);put(e.out,id);put(e.out,n);put(e.out,key);put(e.out,mode);
    const int order[]={0,1,2,0,2,3};
    for(int y=0;y<ny;++y)for(int x=0;x<nx;++x){float us[]={float(x)/nx,float(x+1)/nx,float(x+1)/nx,float(x)/nx},vs[]={float(y)/ny,float(y)/ny,float(y+1)/ny,float(y+1)/ny};Vector3 p[4];
        for(int k=0;k<4;++k){p[k]=corners[0]*(1-us[k])*(1-vs[k])+corners[1]*us[k]*(1-vs[k])+corners[2]*us[k]*vs[k]+corners[3]*(1-us[k])*vs[k];float h;if(!terrainHeight(p[k].X,p[k].Y,h))h=TheTerrainLogic->getGroundHeight(p[k].X,p[k].Y);p[k].Z=h+1.1f;}
        for(int k:order)effectVertex(e.out,p[k],us[k],1-vs[k],c);
    }++e.batches;e.total+=n;
}
}
bool captureWaypointNode(RenderObjClass* model){if(!waypointExport)return false;auto& e=*waypointExport;walk(e.out,model,0xf1000000u,e.root,e.batches,e.total,0xf1000000u+e.batches);return true;}
bool captureWaypointLine(SegmentedLineClass* line,const Vector3* points,int count){
    if(!waypointExport)return false;if(!line||!points||count<2)return true;auto& e=*waypointExport;auto tex=line->Get_Texture();unsigned int id=textureId(tex,e.root);if(tex)tex->Release_Ref();Vector3 tint;line->Get_Color(tint);unsigned int color=rgba(tint.X,tint.Y,tint.Z,line->Get_Opacity());Coord3D left,up;hostCameraBasis(left,up);Vector3 normal;Vector3::Cross_Product(Vector3(left.x,left.y,left.z),Vector3(up.x,up.y,up.z),&normal);
    for(int i=1;i<count;++i){Vector3 along=points[i]-points[i-1];Vector3 edge;Vector3::Cross_Product(along,normal,&edge);float length=edge.Length();if(length<.001f)continue;edge*=line->Get_Width()*.5f/length;Vector3 a=points[i-1]-edge,b=points[i-1]+edge,c=points[i]+edge,d=points[i]-edge;unsigned int owner=0xf2000000u,n=6,mode=materialMode(line->Get_Shader(),false);unsigned long long part=0xf2000000u+e.batches;put(e.out,owner);put(e.out,id);put(e.out,n);put(e.out,part);put(e.out,mode);effectVertex(e.out,a,0,0,color);effectVertex(e.out,b,0,1,color);effectVertex(e.out,c,1,1,color);effectVertex(e.out,a,0,0,color);effectVertex(e.out,c,1,1,color);effectVertex(e.out,d,1,0,color);++e.batches;e.total+=6;}return true;
}
unsigned int exportedLasers=0;
void emitLaser(void* context,SegmentedLineClass* line){
    if(!line||line->Is_Hidden()||line->Is_Animation_Hidden()||line->Get_Num_Points()<2)return;
    auto& e=*static_cast<EffectExport*>(context);auto texture=line->Get_Texture();unsigned int tex=textureId(texture,e.root);if(texture)texture->Release_Ref();
    Vector3 tint;line->Get_Color(tint);unsigned int color=rgba(tint.X,tint.Y,tint.Z,line->Get_Opacity());Coord3D left,up;hostCameraBasis(left,up);Vector3 normal;Vector3::Cross_Product(Vector3(left.x,left.y,left.z),Vector3(up.x,up.y,up.z),&normal);
    float tile=line->Get_Texture_Tile_Factor();if(!std::isfinite(tile)||tile<=0)tile=1;auto rate=line->Get_UV_Offset_Rate();float time=TheGameLogic->getFrame()/30.f;int count=line->Get_Num_Points();
    for(int i=1;i<count&&e.total+6<=300000;++i){Vector3 a,b,localA,localB;line->Get_Point_Location(i-1,localA);line->Get_Point_Location(i,localB);Matrix3D::Transform_Vector(line->Get_Transform(),localA,&a);Matrix3D::Transform_Vector(line->Get_Transform(),localB,&b);Vector3 edge;Vector3::Cross_Product(b-a,normal,&edge);float length=edge.Length();if(length<.001f)continue;edge*=line->Get_Width()*.5f/length;
        unsigned int owner=0xf3000000u,n=6,mode=materialMode(line->Get_Shader(),false);unsigned long long part=0xf300000000000000ULL^reinterpret_cast<uintptr_t>(line)^static_cast<unsigned long long>(i);put(e.out,owner);put(e.out,tex);put(e.out,n);put(e.out,part);put(e.out,mode);
        float v0=float(i-1)/(count-1)*tile+time*rate.Y,v1=float(i)/(count-1)*tile+time*rate.Y,u=time*rate.X;
        effectVertex(e.out,a-edge,u,v0,color);effectVertex(e.out,a+edge,1+u,v0,color);effectVertex(e.out,b+edge,1+u,v1,color);effectVertex(e.out,a-edge,u,v0,color);effectVertex(e.out,b+edge,1+u,v1,color);effectVertex(e.out,b-edge,u,v1,color);++e.batches;e.total+=6;++exportedLasers;
    }
}
void sceneState(std::ostream& out){out<<",\"exportedParticles\":"<<exportedParticles<<",\"exportedDecals\":"<<exportedDecals<<",\"repairedUV\":"<<repairedUV<<",\"testFXParticles\":"<<testFXParticles<<",\"exportedLasers\":"<<exportedLasers;}
void beginTestFX(){priorSystems.clear();testSystems.clear();for(auto s:TheParticleSystemManager->getAllParticleSystems())if(s)priorSystems.insert(s->getSystemID());}
void endTestFX(){for(auto s:TheParticleSystemManager->getAllParticleSystems())if(s&&!priorSystems.count(s->getSystemID()))testSystems.insert(s->getSystemID());priorSystems.clear();}
void setSceneRegion(float x, float y, float r) { if(radius<=0 && r>0)worldStarted=timeGetTime();centerX = x; centerY = y; radius = r; }
void rememberModel(const void* module,RenderObjClass* model){if(model){auto found=models.find(module);if(found==models.end()||found->second.model!=model)models[module]={model,++modelGeneration};}else models.erase(module);}
bool worldTransportActive() { return radius > 0; }
bool sceneEnabled() { return radius > 0; }
bool worldInterfaceActive() { return radius > 0 && worldViewMode; }
bool hostMinecraftEffects(const Coord3D* p){return p&&!worldInterfaceActive()&&std::hypot(p->x-centerX,p->y-centerY)<=radius&&hostTerrainKnown(p);}
bool suppressNativeWorld() { return radius > 0 && timeGetTime()-worldStarted>2000; }
void setWorldViewMode(bool active) { worldViewMode=active; }
void captureScene() {
    const char* path = std::getenv("ZHMC_BRIDGE_DIR");
    static unsigned int lastFrame=~0u;
    if (!path || !sceneEnabled() || !TheGameLogic->isInGame() || TheGameLogic->isInShellGame() || lastFrame==TheGameLogic->getFrame()) return;
    lastFrame=TheGameLogic->getFrame();
    last = timeGetTime(); std::filesystem::path root(path);
    uvMatrices.clear();
    unsigned int magic = 0x354d485a, frame = TheGameLogic->getFrame(), batches = 0, total = 0;
    std::ostringstream out(std::ios::out | std::ios::binary); put(out, magic); put(out, frame); put(out, batches);
    exportedLasers=0;EffectExport effects{out,root,batches,total};
    for (Drawable* draw = TheGameClient->firstDrawable(); draw; draw=draw->getNextDrawable()) {
        auto object=draw->getObject();
        if(object&&isExternalObject(object->getID()))continue;
        bool laser=false;for(auto module=draw->getDrawModules();*module;++module)if((*module)->getLaserDrawInterface())laser=true;
        if(object&&!laser&&!object->isKindOf(KINDOF_SELECTABLE)&&!object->isKindOf(KINDOF_PROJECTILE)&&!object->isKindOf(KINDOF_HULK))continue;
        auto p = draw->getPosition();
        if (std::hypot(p->x - centerX, p->y - centerY) > radius) continue;
        const bool resource=object&&(object->isKindOf(KINDOF_SUPPLY_SOURCE)||object->getTemplate()->getName()=="TechOilDerrick");
        if(resource)draw->setFullyObscuredByShroud(false);
        if((draw->getFullyObscuredByShroud()&&!resource&&worldInterfaceActive())||(TheInGameUI->hostIsPlacementDrawable(draw)&&!worldInterfaceActive()))continue;
        // Shroud controls the RTS commander, never first-person Minecraft rendering.
        struct HostVisibilityScope {Drawable* draw;bool restore;~HostVisibilityScope(){if(restore)draw->setFullyObscuredByShroud(true);}} visibility{draw,!worldInterfaceActive()&&draw->getFullyObscuredByShroud()};
        if(visibility.restore)draw->setFullyObscuredByShroud(false);
        draw->draw(); // Preserve original model-state transitions and draw-module animation control.
        unsigned int id=object&&object->isKindOf(KINDOF_SELECTABLE)?object->getID():0x80000000u+draw->getID();
        Vector3 tint(0,0,0);if(auto c=draw->getTintColor())tint+=*c;if(auto c=draw->getSelectionColor())tint+=*c;
        for (DrawModule** module = draw->getDrawModules(); *module; ++module) {
            if((*module)->getLaserDrawInterface())static_cast<W3DLaserDraw*>(*module)->hostLines(&effects,emitLaser);
            auto model=models.find(*module);if(model!=models.end())walk(out,model->second.model,id,root,batches,total,model->second.generation,0,&tint);
        }
    }
    if(worldInterfaceActive()){
        auto ui=static_cast<W3DInGameUI*>(TheInGameUI);
        for(int i=0;i<ui->hostFeedbackCount();++i){auto model=ui->hostFeedback(i);if(model&&model->Peek_Scene())walk(out,model,0xf0000000u+i,root,batches,total,0xf0000000u+i);}
    }
    exportedParticles=exportedDecals=testFXParticles=0;
    if(worldInterfaceActive()&&TheTerrainRenderObject&&TheTerrainRenderObject->hostWaypoints()){waypointExport=&effects;RenderInfoClass info(*static_cast<W3DView*>(TheTacticalView)->get3DCamera());TheTerrainRenderObject->hostWaypoints()->drawWaypoints(info);waypointExport=nullptr;}
    if(TheW3DProjectedShadowManager)TheW3DProjectedShadowManager->hostDecals(decal,&effects);
    if(TheTerrainRenderObject&&TheTerrainRenderObject->hostBibBuffer())TheTerrainRenderObject->hostBibBuffer()->hostBibs(bib,&effects);
    particles(effects);
    out.seekp(8); put(out, batches);
    auto bytes = out.str(); auto temp = root / "scene.tmp";
    { std::ofstream file(temp, std::ios::binary | std::ios::trunc); file.write(bytes.data(), bytes.size()); }
    MoveFileExW(temp.c_str(), (root / "scene.bin").c_str(), MOVEFILE_REPLACE_EXISTING);
}
}

