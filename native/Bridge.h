// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
class Drawable;
struct Coord3D;
struct ICoord2D;
struct Coord2D;
namespace zhmc {
void beforeUpdate();
void hostLogicTick();
void afterUpdate();
float worldUnitsPerBlock();
bool sandboxWorld();
bool rightHeld();
bool terrainHeight(float x, float y, float& height);
int terrainType(int cellX, int cellY);
bool terrainCellLoaded(int cellX,int cellY);
bool hostPathEdgeAllowed(int ax,int ay,int bx,int by);
bool buildSurfaceAllowed(float x,float y,float requiredHeight);
bool hostLineOfSight(const Coord3D& a,const Coord3D& b,bool& clear);
bool remoteInput();
bool popKey(unsigned char& code, bool& down, unsigned long& timestamp);
void traceMessage(int type);
void traceGuiProduction(int canMake);
void traceWindow(int message, int id, unsigned int state);
void traceDisposition(int type, unsigned int priority, int disposition);
void traceRawPointer(int type, int x, int y, bool locked);
bool hasWorldPointer();
Drawable* worldPointerDrawable();
bool worldPointerPosition(Coord3D* position);
bool worldPointerPosition(const ICoord2D* screen,Coord3D* position);
bool hasHostView();
int hostWorldToScreen(const Coord3D* position,ICoord2D* screen);
bool hostScreenToWorld(const ICoord2D* screen,Coord3D* position,float z);
void hostAngle(float radians);
bool hostLookAt(const Coord3D* position);
bool hostScroll(const Coord2D* delta);
}

namespace zhmc { Drawable* worldPointerDrawable(const ICoord2D* screen,int pickType);bool hostScreenToTerrain(const ICoord2D* screen,Coord3D* position);void hostCameraBasis(Coord3D& left,Coord3D& up); }

class Object;class DamageInfo;struct AICommandParms;
namespace zhmc {bool isExternalObject(unsigned int id);bool externalDamage(Object* object,DamageInfo* damage);void hostExplosion(const Coord3D* position,float radius,float amount,unsigned int source,int damageType);}
namespace zhmc {void hostPlayerCommand(Object* object,const AICommandParms* command);}
namespace zhmc {bool hostTerrainKnown(const Coord3D* position);}
