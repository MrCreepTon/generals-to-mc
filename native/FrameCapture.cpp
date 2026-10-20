// SPDX-License-Identifier: GPL-3.0-or-later
// Original WW3D framebuffer transport proof. World/depth fusion is a separate stage.
#include "Precompiled/PreRTS.h"
#include "FrameCapture.h"
#include "WorldScene.h"
#include "Bridge.h"
#include "dx8wrapper.h"
#include "GameClient/InGameUI.h"
#include "GameClient/Drawable.h"
#include "GameClient/Mouse.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Object.h"
#include "Common/PlayerList.h"
#include "Common/Player.h"
#include "GameClient/GameWindowManager.h"
#include "GameClient/GameWindow.h"
#include "Common/NameKeyGenerator.h"
#include <filesystem>
#include <fstream>
#include <cstdlib>
#include <vector>
#include <algorithm>
#include <unordered_map>

namespace zhmc {
std::string hostMobLabel(unsigned int);
namespace {
IDirect3DSurface8* gui = nullptr;
IDirect3DSurface8* previousTarget = nullptr;
IDirect3DSurface8* previousDepth = nullptr;
DWORD lastGui = 0;
DWORD guiStarted=0,lastGuiCost=0;
bool guiReplay = false;
struct HealthBar{unsigned int id;float x,y,z,dx,dy,width,height,ratio;unsigned int color,outline;};
std::vector<HealthBar> healthBars;
int guiClockCount=0,guiClockPercent=-1;
HCURSOR cursorHandle=nullptr;
int cursorType=0,cursorFrames=1;
float cursorFps=12;
DWORD lastCursor=0;
DWORD cursorStarted=0;
struct Ani {int steps=1;std::vector<unsigned int> rates={6};};
std::unordered_map<HCURSOR,Ani> animations;
int cursorStep=0;
struct CursorResource {HCURSOR handle=nullptr;float fps=12;int frames=1;};
std::unordered_map<int,CursorResource> cursorResourcesByType;
void captureCursor() {
    const char* root=std::getenv("ZHMC_BRIDGE_DIR");
    if(!root||!cursorHandle||!worldInterfaceActive()||timeGetTime()-lastCursor<33)return;
    lastCursor=timeGetTime();ICONINFO info={};if(!GetIconInfo(cursorHandle,&info))return;
    BITMAP bitmap={};GetObject(info.hbmColor?info.hbmColor:info.hbmMask,sizeof(bitmap),&bitmap);
    int w=bitmap.bmWidth,h=info.hbmColor?bitmap.bmHeight:bitmap.bmHeight/2;
    if(info.hbmColor)DeleteObject(info.hbmColor);if(info.hbmMask)DeleteObject(info.hbmMask);
    if(w<1||h<1||w>128||h>128)return;
    BITMAPINFO bmi={};bmi.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);bmi.bmiHeader.biWidth=w;bmi.bmiHeader.biHeight=-h;bmi.bmiHeader.biPlanes=1;bmi.bmiHeader.biBitCount=32;bmi.bmiHeader.biCompression=BI_RGB;
    HDC dc=CreateCompatibleDC(nullptr);void* pixels=nullptr;HBITMAP dib=CreateDIBSection(dc,&bmi,DIB_RGB_COLORS,&pixels,nullptr,0);
    if(!dib){DeleteDC(dc);return;}auto old=SelectObject(dc,dib);
    auto ani=animations.find(cursorHandle);
    int frame=0;
    if(ani!=animations.end()) {
        auto& a=ani->second;unsigned int length=0;for(auto rate:a.rates)length+=rate;
        unsigned int phase=(static_cast<unsigned long long>(timeGetTime()-cursorStarted)*60/1000)%std::max(1u,length);
        while(frame<a.steps-1&&phase>=a.rates[frame]){phase-=a.rates[frame];++frame;}
        cursorFrames=a.steps;
    }else frame=static_cast<int>((timeGetTime()-cursorStarted)*std::max(1.0f,cursorFps)/1000)%std::max(1,cursorFrames);
    cursorStep=frame;
    memset(pixels,0,w*h*4);DrawIconEx(dc,0,0,cursorHandle,w,h,frame,nullptr,DI_NORMAL);GdiFlush();
    std::vector<unsigned int> colors(static_cast<unsigned int*>(pixels),static_cast<unsigned int*>(pixels)+w*h);
    memset(pixels,255,w*h*4);DrawIconEx(dc,0,0,cursorHandle,w,h,frame,nullptr,DI_NORMAL);GdiFlush();
    auto white=static_cast<unsigned int*>(pixels);
    for(int i=0;i<w*h;i++) {
        auto b=colors[i],v=white[i];int delta=(int((v>>16)&255)-int((b>>16)&255)+int((v>>8)&255)-int((b>>8)&255)+int(v&255)-int(b&255))/3;
        unsigned int alpha=std::clamp(255-delta,0,255),color=alpha<<24;
        if(alpha)for(int shift=0;shift<=16;shift+=8)color|=std::min(255u,((b>>shift)&255)*255/alpha)<<shift;
        colors[i]=color;
    }
    SelectObject(dc,old);DeleteObject(dib);DeleteDC(dc);
    std::filesystem::path directory(root);auto temp=directory/"cursor.tmp";
    {std::ofstream file(temp,std::ios::binary|std::ios::trunc);int header[]={w,h,int(info.xHotspot),int(info.yHotspot),cursorType,frame,cursorFrames};file.write(reinterpret_cast<char*>(header),sizeof(header));file.write(reinterpret_cast<char*>(colors.data()),colors.size()*4);}
    MoveFileExW(temp.c_str(),(directory/"cursor.rgba").c_str(),MOVEFILE_REPLACE_EXISTING);
}
}
void cursorChanged(int type, void* handle, float fps, int frames) {cursorResourcesByType[type]={static_cast<HCURSOR>(handle),fps,frames};}
void cursorState(std::ostream& out){int desired=TheMouse?TheMouse->getMouseCursor():-1;auto found=cursorResourcesByType.find(desired);out<<",\"cursorDesired\":"<<desired<<",\"cursorReported\":"<<cursorType<<",\"cursorResourceFound\":"<<(found!=cursorResourcesByType.end()?"true":"false")<<",\"cursorResourceLoaded\":"<<(found!=cursorResourcesByType.end()&&found->second.handle?"true":"false")<<",\"cursorResourceCount\":"<<cursorResourcesByType.size();}
void publishCursor(){
    if(!TheMouse)return;int type=TheMouse->getMouseCursor();auto found=cursorResourcesByType.find(type);
    if(found==cursorResourcesByType.end()||!found->second.handle){
        const char* root=std::getenv("ZHMC_BRIDGE_DIR");if(!root||!worldInterfaceActive())return;
        cursorType=type;cursorHandle=nullptr;cursorFrames=1;std::filesystem::path path(root);auto temp=path/"cursor.tmp";
        {std::ofstream out(temp,std::ios::binary|std::ios::trunc);int header[]={1,1,0,0,type,0,1};unsigned int pixel=0;out.write(reinterpret_cast<char*>(header),sizeof(header));out.write(reinterpret_cast<char*>(&pixel),4);}
        MoveFileExW(temp.c_str(),(path/"cursor.rgba").c_str(),MOVEFILE_REPLACE_EXISTING);return;
    }
    auto resource=found->second;if(type!=cursorType||resource.handle!=cursorHandle)cursorStarted=timeGetTime();
    cursorType=type;cursorHandle=resource.handle;cursorFps=resource.fps;cursorFrames=resource.frames;captureCursor();
}
void cursorSource(void* handle,const char* file) {
    if(!handle||!file||!std::getenv("ZHMC_CAPTURE"))return;
    std::ifstream input(file,std::ios::binary);if(!input)return;
    char magic[12];if(!input.read(magic,12)||memcmp(magic,"RIFF",4)||memcmp(magic+8,"ACON",4))return;
    Ani ani;unsigned int defaultRate=6;std::vector<unsigned int> rates;
    for(int chunk=0;chunk<1024&&input;++chunk) {
        char name[4];unsigned int size;if(!input.read(name,4)||!input.read(reinterpret_cast<char*>(&size),4)||size>4*1024*1024)break;
        std::vector<char> data(size);if(!input.read(data.data(),size))break;if(size&1)input.get();
        if(!memcmp(name,"anih",4)&&size>=36){unsigned int header[9];memcpy(header,data.data(),36);ani.steps=static_cast<int>(header[2]?header[2]:header[1]);defaultRate=std::clamp(header[7],1u,600u);}
        if(!memcmp(name,"rate",4)&&size%4==0){rates.resize(size/4);memcpy(rates.data(),data.data(),size);}
    }
    ani.steps=std::clamp(ani.steps,1,256);ani.rates.assign(ani.steps,defaultRate);
    for(int i=0;i<ani.steps&&i<static_cast<int>(rates.size());++i)ani.rates[i]=std::clamp(rates[i],1u,600u);
    animations[static_cast<HCURSOR>(handle)]=std::move(ani);
}
void drawWorldIndicators() {
    if(!hasHostView())return;
    if(!guiReplay)healthBars.clear();
    for(Object* object=TheGameLogic->getFirstObject();object;object=object->getNextObject()) {
        auto draw=object->getDrawable();
        if(!draw||!ThePlayerList->getLocalPlayer()||(!isExternalObject(object->getID())&&(draw->isDrawableEffectivelyHidden()||object->getShroudedStatus(ThePlayerList->getLocalPlayer()->getPlayerIndex())>OBJECTSHROUD_PARTIAL_CLEAR)))continue;
        if(isExternalObject(object->getID()))draw->setCaptionText(UnicodeString());
        draw->drawIconUI();draw->drawUIText();
    }
}
void drawSelectedMobCaption(){
    if(!hasHostView()||!TheInGameUI||TheInGameUI->getSelectCount()!=1)return;
    for(Object* object=TheGameLogic->getFirstObject();object;object=object->getNextObject()){
        auto draw=object->getDrawable();if(!draw||!draw->isSelected()||!isExternalObject(object->getID()))continue;
        auto label=hostMobLabel(object->getID());if(label.empty())return;int length=MultiByteToWideChar(CP_UTF8,0,label.c_str(),-1,nullptr,0);if(length<=0)return;
        std::vector<wchar_t> text(length);MultiByteToWideChar(CP_UTF8,0,label.c_str(),-1,text.data(),length);UnicodeString caption;caption.set(reinterpret_cast<const WideChar*>(text.data()));
        draw->setCaptionText(caption);draw->drawIconUI();draw->setCaptionText(UnicodeString());return;
    }
}
bool mobCaptionPosition(unsigned int id,int* x,int* y,int height){
    if(!isExternalObject(id))return false;
    auto window=TheWindowManager->winGetWindowFromId(nullptr,TheNameKeyGenerator->nameToKey("ControlBar.wnd:CameoWindow"));
    if(!window||window->winIsHidden()){*x=-10000;*y=-10000;return true;}int left,top,width,size;window->winGetScreenPosition(&left,&top);window->winGetSize(&width,&size);*x=left+width/2;*y=top+size-height-2;return true;
}
bool captureHealthBar(Object* object,int x,int y,float width,float height,float ratio,unsigned int color,unsigned int outline){
    if(!hasHostView()||!object)return false;
    if(!guiReplay){Coord3D health;object->getHealthBoxPosition(health);ICoord2D screen;hostWorldToScreen(&health,&screen);healthBars.push_back({(unsigned int)object->getID(),health.x,health.y,health.z,float(x-screen.x),float(y-screen.y),width,height,ratio,color,outline});}return true;
}
void healthBarState(std::ostream& out){out<<",\"healthBars\":[";bool comma=false;for(auto& b:healthBars){if(comma)out<<',';comma=true;out<<"{\"id\":"<<b.id<<",\"x\":"<<b.x<<",\"y\":"<<b.y<<",\"z\":"<<b.z<<",\"dx\":"<<b.dx<<",\"dy\":"<<b.dy<<",\"width\":"<<b.width<<",\"height\":"<<b.height<<",\"ratio\":"<<b.ratio<<",\"color\":"<<b.color<<",\"outline\":"<<b.outline<<'}';}out<<']';}
void traceGuiClock(int percent){if(previousTarget&&!guiReplay){++guiClockCount;guiClockPercent=percent;}}
void guiClockState(std::ostream& out){out<<",\"guiClockCount\":"<<guiClockCount<<",\"guiClockPercent\":"<<guiClockPercent;}
bool preserveGuiClock(){return previousTarget && !guiReplay;}
unsigned int guiCostMs(){return lastGuiCost;}
void beginGuiLayer() {
    if (guiReplay || !worldInterfaceActive() || !std::getenv("ZHMC_CAPTURE") || timeGetTime() - lastGui < 33 || previousTarget) return;
    auto device = DX8Wrapper::_Get_D3D_Device8(); if (!device) return;
    IDirect3DSurface8* target = nullptr;
    if (FAILED(device->GetRenderTarget(&target))) return;
    D3DSURFACE_DESC desc; target->GetDesc(&desc);
    if (!gui && FAILED(device->CreateRenderTarget(desc.Width, desc.Height, D3DFMT_A8R8G8B8, D3DMULTISAMPLE_NONE, true, &gui))) { target->Release(); return; }
    device->GetDepthStencilSurface(&previousDepth);
    if (FAILED(device->SetRenderTarget(gui, nullptr))) { target->Release(); if(previousDepth)previousDepth->Release(); previousDepth=nullptr; return; }
    guiClockCount=0;guiClockPercent=-1;previousTarget = target; lastGui = timeGetTime();guiStarted=lastGui;
    device->Clear(0, nullptr, D3DCLEAR_TARGET, 0, 1, 0);
}
void endGuiLayer() {
    if (guiReplay || !previousTarget) return;
    auto device = DX8Wrapper::_Get_D3D_Device8();
    drawSelectedMobCaption();
    D3DLOCKED_RECT pixels;
    D3DSURFACE_DESC desc; gui->GetDesc(&desc);
    std::vector<unsigned int> black(desc.Width * desc.Height);
    if (SUCCEEDED(gui->LockRect(&pixels, nullptr, D3DLOCK_READONLY))) {
        for (UINT row = 0; row < desc.Height; ++row) memcpy(black.data() + row * desc.Width, static_cast<const char*>(pixels.pBits) + row * pixels.Pitch, desc.Width * 4);
        gui->UnlockRect();
        // Original D3D8 GUI shaders do not preserve destination alpha. Recover coverage
        // from the same original GUI drawn against black and white, not a chroma key.
        device->Clear(0, nullptr, D3DCLEAR_TARGET, 0xffffffff, 1, 0);
        guiReplay = true; TheInGameUI->draw(); drawSelectedMobCaption(); guiReplay = false;
        if (SUCCEEDED(gui->LockRect(&pixels, nullptr, D3DLOCK_READONLY))) {
            for (UINT row = 0; row < desc.Height; ++row) {
                auto white = reinterpret_cast<const unsigned int*>(static_cast<const char*>(pixels.pBits) + row * pixels.Pitch);
                for (UINT x = 0; x < desc.Width; ++x) {
                    unsigned int b = black[row * desc.Width + x], w = white[x];
                    // Most original GUI pixels are either opaque or completely uncovered.
                    // Preserve the exact two-background alpha reconstruction at the edges.
                    if((b&0xffffffu)==(w&0xffffffu)){black[row*desc.Width+x]=b|0xff000000u;continue;}
                    if(!(b&0xffffffu)&&(w&0xffffffu)==0xffffffu){black[row*desc.Width+x]=0;continue;}
                    int delta = (int((w >> 16) & 255) - int((b >> 16) & 255) + int((w >> 8) & 255) - int((b >> 8) & 255) + int(w & 255) - int(b & 255)) / 3;
                    unsigned int alpha = std::clamp(255 - delta, 0, 255), color = alpha << 24;
                    if (alpha) for (int shift = 0; shift <= 16; shift += 8) color |= std::min(255u, ((b >> shift) & 255) * 255 / alpha) << shift;
                    black[row * desc.Width + x] = color;
                }
            }
            gui->UnlockRect();
            std::filesystem::path directory(std::getenv("ZHMC_BRIDGE_DIR")); auto temp = directory / "gui.tmp";
            { std::ofstream file(temp, std::ios::binary | std::ios::trunc); file.write(reinterpret_cast<const char*>(&desc.Width), 4); file.write(reinterpret_cast<const char*>(&desc.Height), 4); file.write(reinterpret_cast<const char*>(black.data()), black.size() * 4); }
            MoveFileExW(temp.c_str(), (directory / "gui.rgba").c_str(), MOVEFILE_REPLACE_EXISTING);
        }
    }
    device->SetRenderTarget(previousTarget, previousDepth);
    previousTarget->Release(); previousTarget = nullptr;
    if (previousDepth) previousDepth->Release(); previousDepth = nullptr;
    lastGuiCost=timeGetTime()-guiStarted;
}
void captureFrame() {
    captureScene();

    // The Minecraft world route needs geometry and GUI, not a second terrain framebuffer.
    if (worldTransportActive()) return;
    const char* enabled = std::getenv("ZHMC_CAPTURE");
    const char* root = std::getenv("ZHMC_BRIDGE_DIR");
    if (!enabled || !root) return;
    static DWORD last = 0;
    if (timeGetTime() - last < 100) return;
    last = timeGetTime();
    IDirect3DDevice8* device = DX8Wrapper::_Get_D3D_Device8();
    if (!device) return;
    IDirect3DSurface8* source = nullptr;
    IDirect3DSurface8* copy = nullptr;
    if (FAILED(device->GetRenderTarget(&source))) return;
    D3DSURFACE_DESC desc;
    source->GetDesc(&desc);
    if (desc.Format != D3DFMT_X8R8G8B8 && desc.Format != D3DFMT_A8R8G8B8) { source->Release(); return; }
    HRESULT result = device->CreateImageSurface(desc.Width, desc.Height, desc.Format, &copy);
    if (SUCCEEDED(result)) result = device->CopyRects(source, nullptr, 0, copy, nullptr);
    source->Release();
    if (FAILED(result)) { if (copy) copy->Release(); return; }
    D3DLOCKED_RECT pixels;
    result = copy->LockRect(&pixels, nullptr, D3DLOCK_READONLY);
    if (SUCCEEDED(result)) {
        std::filesystem::path directory(root);
        auto temp = directory / "frame.tmp";
        BITMAPFILEHEADER header = {};
        BITMAPINFOHEADER info = {};
        header.bfType = 0x4d42; header.bfOffBits = sizeof(header) + sizeof(info);
        header.bfSize = header.bfOffBits + desc.Width * desc.Height * 4;
        info.biSize = sizeof(info); info.biWidth = desc.Width; info.biHeight = -static_cast<LONG>(desc.Height);
        info.biPlanes = 1; info.biBitCount = 32; info.biCompression = BI_RGB;
        {
            std::ofstream file(temp, std::ios::binary | std::ios::trunc);
            file.write(reinterpret_cast<const char*>(&header), sizeof(header));
            file.write(reinterpret_cast<const char*>(&info), sizeof(info));
            for (UINT row = 0; row < desc.Height; ++row)
                file.write(static_cast<const char*>(pixels.pBits) + row * pixels.Pitch, desc.Width * 4);
        }
        copy->UnlockRect();
        MoveFileExW(temp.c_str(), (directory / "frame.bmp").c_str(), MOVEFILE_REPLACE_EXISTING);
    }
    copy->Release();
}
}
