// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <ostream>
class Object;
class Image;
struct Coord3D;struct ICoord2D;
namespace zhmc {
struct OverheadScope {Object* previous;float x,y,z;int sx,sy;bool valid,healthX;explicit OverheadScope(Object*,bool=false);~OverheadScope();};
void overheadProjection(const Coord3D*,const ICoord2D*);
bool captureOverheadImage(const Image*,int,int,int,int,unsigned int,int);
}
namespace zhmc { bool captureHealthBar(Object*,int,int,float,float,float,unsigned int,unsigned int);void healthBarState(std::ostream&); }
namespace zhmc { void captureFrame();void publishCursor();void cursorState(std::ostream& out); void beginGuiLayer(); void endGuiLayer(); void cursorChanged(int type, void* handle, float fps, int frames); }
namespace zhmc { unsigned int guiCostMs(); }
namespace zhmc { void cursorSource(void* handle,const char* file);void drawWorldIndicators(); }

namespace zhmc { bool preserveGuiClock(); }

namespace zhmc { void traceGuiClock(int percent); void guiClockState(std::ostream& out); }
namespace zhmc { bool mobCaptionPosition(unsigned int,int*,int*,int); }
