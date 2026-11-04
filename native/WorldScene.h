// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <ostream>
class RenderObjClass;
class Image;
namespace zhmc {unsigned int overheadTexture(const Image*);}
class SegmentedLineClass;class Vector3;
namespace zhmc {bool captureWaypointLine(SegmentedLineClass*,const Vector3*,int);bool captureWaypointNode(RenderObjClass*);}
namespace zhmc {
void rememberModel(const void* module,RenderObjClass* model);
void setSceneRegion(float x, float y, float radius);
bool sceneEnabled();
bool worldInterfaceActive();
bool worldTransportActive();
bool suppressNativeWorld();
void setWorldViewMode(bool active);
void captureScene();
}

namespace zhmc { void sceneState(std::ostream& out); }

namespace zhmc { void beginTestFX();void endTestFX(); }
