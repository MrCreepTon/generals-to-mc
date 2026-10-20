// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <ostream>
class Object;
namespace zhmc { bool captureHealthBar(Object*,int,int,float,float,float,unsigned int,unsigned int);void healthBarState(std::ostream&); }
namespace zhmc { void captureFrame();void publishCursor();void cursorState(std::ostream& out); void beginGuiLayer(); void endGuiLayer(); void cursorChanged(int type, void* handle, float fps, int frames); }
namespace zhmc { unsigned int guiCostMs(); }
namespace zhmc { void cursorSource(void* handle,const char* file);void drawWorldIndicators(); }

namespace zhmc { bool preserveGuiClock(); }

namespace zhmc { void traceGuiClock(int percent); void guiClockState(std::ostream& out); }
namespace zhmc { bool mobCaptionPosition(unsigned int,int*,int*,int); }
