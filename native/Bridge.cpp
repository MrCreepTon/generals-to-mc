// SPDX-License-Identifier: GPL-3.0-or-later
// Adapter linked to the original engine. No unit rules or asset replacements.
#include "Precompiled/PreRTS.h"
#include "Bridge.h"
#include "PagedWorld.h"
#include "WorldScene.h"
#include "GameLogic/Weapon.h"
#include "GameLogic/WeaponSet.h"
#include "GameLogic/PartitionManager.h"
#include "GameClient/FXList.h"
#include "FrameCapture.h"
#include "NativeRadar.h"
#include "NativeLockstep.h"
#include "Common/FramePacer.h"
#include "Common/ArchiveFileSystem.h"
#include "Common/GlobalData.h"
#include "Common/MessageStream.h"
#include "Common/Player.h"
#include "Common/Science.h"
#include "Common/Upgrade.h"
#include "GameLogic/Module/ProductionUpdate.h"
#include "GameLogic/RankInfo.h"
#include "Common/PlayerList.h"
#include "Common/PlayerTemplate.h"
#include "Common/MultiplayerSettings.h"
#include "Common/Money.h"
#include "Common/RandomValue.h"
#include "Common/ThingTemplate.h"
#include "Common/ThingFactory.h"
#include "Common/BuildAssistant.h"
#include "Common/ActionManager.h"
#include "GameLogic/Module/SupplyWarehouseDockUpdate.h"
#include "GameLogic/Module/AIUpdate.h"
#include "GameLogic/Module/DozerAIUpdate.h"
#include "GameLogic/Module/SpecialPowerModule.h"
#include "GameLogic/Locomotor.h"
#include "GameLogic/Module/SupplyTruckAIUpdate.h"
#include "GameLogic/Damage.h"
#include "Common/GameEngine.h"
#include "Common/GameAudio.h"
#include "Common/AudioAffect.h"
#include "Common/AudioEventRTS.h"
#include "Common/AudioHandleSpecialValues.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Object.h"
#include "GameLogic/Squad.h"
#include "GameLogic/AI.h"
#include "GameLogic/AIPlayer.h"
#include "GameLogic/SidesList.h"
#include "GameLogic/AIPathfind.h"
#include "GameLogic/TerrainLogic.h"
#include "GameLogic/Module/BodyModule.h"
#include "GameLogic/Module/PhysicsUpdate.h"
#include "GameLogic/Module/ActiveBody.h"
#include "Win32Device/GameClient/Win32Mouse.h"
#include "GameClient/InGameUI.h"
#include "GameClient/Drawable.h"
#include "GameClient/Shell.h"
#include "GameClient/View.h"
#include "GameClient/Display.h"
#include "Common/Radar.h"
#include "GameClient/GameWindowManager.h"
#include "GameClient/GameWindow.h"
#include "GameClient/GadgetPushButton.h"
#include "GameClient/ControlBar.h"
#include "GameNetwork/GameInfo.h"
#include <filesystem>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <string>
#include <cstdlib>
#include <algorithm>
#include <unordered_map>
#include <unordered_set>
#include <cmath>
#include <deque>

extern Win32Mouse* TheWin32Mouse;

namespace zhmc {
unsigned int pathSearchLimitCount();
float worldUnitsPerBlock(){static float value=[](){const char* t=std::getenv("ZHMC_WORLD_SCALE");float x=t?(float)std::atof(t):10.f;return std::isfinite(x)&&x>=4&&x<=32?x:10.f;}();return value;}
bool sandboxWorld(){return std::getenv("ZHMC_SANDBOX")||(!std::getenv("ZHMC_LOCKSTEP")&&remoteInput()&&!std::getenv("ZHMC_TEST"));}
namespace {
std::filesystem::path root;
unsigned long long consumed = 0;
DWORD published = 0;
DWORD updateStarted=0;
float fogFocusX=0,fogFocusY=0;DWORD fogPublished=0;
bool hostEmpVehicles=true;
float actualMusicVolume=-1,expectedMusicVolume=-1;std::string musicTrack;
bool hostAudioConfigured=false;float hostMusic=1,hostSound=1,hostSpeech=1;
bool hostFogEnabled=true;
unsigned int matchStartingCash=0;bool configuredStartingCash=false;
// Own STL strings only: original AsciiString pools/mutexes are destroyed before C++ statics.
std::vector<std::string> cachedMaps;
bool hostView=false;
Coord3D hostEye={},hostForward={},hostLeft={},hostUp={};
float hostTan=.57735027f,hostAspect=1;
unsigned long long cameraSerial=0,rotationSerial=0;float cameraAngle=0,angleBaseline=0;bool angleBaselineReady=false;
Coord3D cameraTarget={};
double scrollX=0,scrollY=0;
DWORD lastPublishCost=0;
std::string lastError;
std::deque<std::pair<unsigned int,int>> selectionProbes;
std::string selectionRegionProbe,selectionListResult;
struct Column { float height; int type; float clearance=10000; std::vector<std::pair<float,float>> obstacles; };
std::unordered_map<unsigned long long, Column> columns;
std::unordered_map<std::string,ObjectID> resourceSites;
std::unordered_map<std::string,ObjectID> worldSites;
struct ArmySlot{int kind,color,team;std::string faction;};
std::unordered_map<int,ArmySlot> armySlots;
std::unordered_map<int,std::string> armyNames;
std::unordered_set<unsigned int> forcedProjectiles,forcedOrders;
struct HostMob {ObjectID object;std::string label;};
std::unordered_map<std::string,HostMob> hostMobs;
std::unordered_map<unsigned int,std::string> hostMobKeys;
struct HostMove {Coord3D goal,position;unsigned int lastProgress;unsigned long long revision;unsigned int retries=0,lastRetryFrame=0;bool terrainDirty=false;};
std::unordered_map<unsigned int,HostMove> hostMoves;
bool retryingHostMove=false;
struct DrivenVehicle {Coord3D goal;float dx=0,dy=0;bool moving=false,fire=false;unsigned long long lease=0;};
std::unordered_map<unsigned int,DrivenVehicle> drivenVehicles;
std::unordered_map<unsigned int,unsigned long long> blockedDriveLeases;bool drivingCommand=false;

Coord3D requestedHostMove;bool hasRequestedHostMove=false;
struct HostCombatEvent {unsigned long long sequence;std::string target;unsigned int source;float amount,x,y,z,radius;int damageType;bool forcedAttack=false;unsigned int disabledFrames=0;};
std::deque<HostCombatEvent> hostCombatEvents;unsigned long long combatSequence=0;
float healthUnitsPerPoint(){auto t=TheThingFactory->findTemplate("AmericaInfantryRanger",false);if(!t)return 0;auto& modules=t->getBehaviorModuleInfo();for(int i=0;i<modules.getCount();++i)if(modules.getNthName(i)=="ActiveBody")return static_cast<const ActiveBodyModuleData*>(modules.getNthData(i))->m_maxHealth/20.f;return 0;}

bool originalResourcesRemoved=false;
SupplyWarehouseDockUpdate* supplyDock(Object* object){
    if(!object)return nullptr;
    for(auto modules=object->getBehaviorModules();modules&&*modules;++modules)
        if((*modules)->getModuleNameKey()==NAMEKEY("SupplyWarehouseDockUpdate"))return static_cast<SupplyWarehouseDockUpdate*>(*modules);
    return nullptr;
}
unsigned long long terrainRevision = 0;
struct KeyEvent { unsigned char code; bool down; DWORD timestamp; };
std::deque<KeyEvent> keys;
unsigned int rawDown = 0, rawUp = 0, productionMessages = 0;
unsigned int stopMessages=0,attackMessages=0;
int guiCanMake = -1;
int pointerX = 0, pointerY = 0;
int downWindow = -1, upWindow = -1;
unsigned int downState = 0, upState = 0;
unsigned int downPriority = 0, upPriority = 0;
int probeWindow = -1, probeTooltip = -1;
int rawX = -1, rawY = -1; bool rawLocked = false;
bool worldPointer = false;
DWORD lastLeftClick=0;int lastLeftX=-1000,lastLeftY=-1000;bool nextDoubleClick=false;unsigned int lastLeftWorldObject=0,pinnedLeftObject=0,pinnedLeftFrame=0;ICoord2D pinnedLeftScreen={};
struct PointerSample{Coord3D position;unsigned int object;DWORD stamp=timeGetTime();};
std::unordered_map<unsigned long long,PointerSample> pointerSamples;
unsigned long long pointerKey(int x,int y){return (static_cast<unsigned long long>(static_cast<unsigned int>(x))<<32)|static_cast<unsigned int>(y);}
bool hostInputSuspended=false,hostInputWasEnabled=true;unsigned int hostSuperweaponLimit=0;
bool rightButtonHeld=false,leftButtonHeld=false;bool pointerAnchor=false;DWORD anchorReleased=0;ICoord2D anchorScreen={};Coord3D anchorPosition={};
Coord3D worldPosition = {};
unsigned int worldObject = 0;
unsigned long long cellKey(int x, int y) {
    return (static_cast<unsigned long long>(static_cast<unsigned int>(x)) << 32) | static_cast<unsigned int>(y);
}
bool enabled() {
    static bool initialized = false;
    if (!initialized) {
        initialized = true;
        const char* value = std::getenv("ZHMC_BRIDGE_DIR");
        if (value && *value) root = value;
    }
    return !root.empty();
}
std::string json(const char* text,bool utf8=false) {
    std::ostringstream out; out << '"';
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); p && *p; ++p) {
        if (*p == '"' || *p == '\\') out << '\\' << char(*p);
        else if (*p < 32 || (!utf8 && *p >= 128)) out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << int(*p) << std::dec;
        else out << char(*p);
    }
    out << '"'; return out.str();
}
const CommandButton* windowCommand(GameWindow* window) {
    if(!window||!TheControlBar||window->winGetInputFunc()!=GadgetPushButtonInput)return nullptr;
    auto data=GadgetButtonGetData(window);
    for(auto command=TheControlBar->getCommandButtons();command;command=command->getNext())if(command==data)return command;
    return nullptr;
}
void controls(std::ostream& out,GameWindow* first,bool& comma,int depth=0) {
    if(depth>24)return;
    for(GameWindow* window=first;window;window=window->winGetNext()) {
        if(window->winIsHidden())continue;
        int x,y,w,h;window->winGetScreenPosition(&x,&y);window->winGetSize(&w,&h);
        auto input=window->winGetInputFunc();
        if(input&&input!=TheWindowManager->getDefaultInput()&&w>0&&h>0&&static_cast<long long>(w)*h<static_cast<long long>(TheDisplay->getWidth())*TheDisplay->getHeight()*3/4) {
            auto command=windowCommand(window);
            if(comma)out<<',';comma=true;
            out<<"{\"id\":"<<window->winGetWindowId()<<",\"x\":"<<x<<",\"y\":"<<y<<",\"width\":"<<w<<",\"height\":"<<h
                <<",\"enabled\":"<<(window->winGetEnabled()?"true":"false")<<",\"command\":"<<json(command?command->getName().str():"")<<'}';
        }
        controls(out,window->winGetChild(),comma,depth+1);
    }
}
std::string factionMusicPrefix(){
    auto player=ThePlayerList?ThePlayerList->getLocalPlayer():nullptr;if(!player)return {};
    std::string side=player->getBaseSide().str();return side=="China"?"Game_CHI_":side=="USA"?"Game_USA_":side=="GLA"?"Game_GLA_":"";
}
void nextHostMusic(){
    if(!TheAudio||!TheGlobalData->m_audioOn)return;
    auto prefix=factionMusicPrefix();if(prefix.empty()){TheAudio->nextMusicTrack();return;}
    int current=musicTrack.rfind(prefix,0)==0?std::atoi(musicTrack.c_str()+prefix.size()):0;
    for(int i=1;i<=11;++i){int index=(current+i-1)%11+1;char number[8];std::snprintf(number,sizeof(number),"%02d",index);auto name=prefix+number;
        if(!TheAudio->findAudioEventInfo(name.c_str()))continue;
        TheAudio->removeAudioEvent(AHSV_StopTheMusic);AudioEventRTS event(name.c_str());event.setPlayerIndex(ThePlayerList->getLocalPlayer()->getPlayerIndex());TheAudio->addAudioEvent(&event);return;
    }
}
void start(std::istringstream& in) {
    std::string map, faction, enemy;
    unsigned int seed, cash;
    if (!(in >> std::quoted(map) >> std::quoted(faction) >> std::quoted(enemy) >> seed >> cash)) {
        lastError = "Malformed START"; return;
    }
    int color=0;in>>color;if(color<0||!TheMultiplayerSettings||color>=TheMultiplayerSettings->getNumColors()){lastError="Unknown original team color";return;}
    std::replace(map.begin(), map.end(), '/', '\\');
    if (TheGameLogic->isInGame() && !TheGameLogic->isInShellGame()) {
        lastError = "A match is already active"; return;
    }
    int side = ThePlayerTemplateStore->getTemplateNumByName(faction.c_str());
    int other = ThePlayerTemplateStore->getTemplateNumByName(enemy.c_str());
    if (side < 0 || other < 0 || (!ThePlayerTemplateStore->getNthPlayerTemplate(side)->isPlayableSide()||ThePlayerTemplateStore->getNthPlayerTemplate(side)->getSide()=="Boss") || !TheArchiveFileSystem->doesFileExist(map.c_str())) {
        lastError = "Unknown original faction or map"; return;
    }
    if (!TheSkirmishGameInfo) TheSkirmishGameInfo = new SkirmishGameInfo;
    TheSkirmishGameInfo->init();
    TheSkirmishGameInfo->clearSlotList();
    TheSkirmishGameInfo->reset();
    TheSkirmishGameInfo->setLocalIP(0);
    TheSkirmishGameInfo->enterGame();
    GameSlot human; UnicodeString name; name.translate(armyNames.count(0)?armyNames.at(0).c_str():"Minecraft");
    human.setState(SLOT_PLAYER, name, 0);
    human.setColor(color); human.setPlayerTemplate(side); human.setStartPos(0);
    human.setTeamNumber(-1); human.setMapAvailability(true); human.setAccept();
    TheSkirmishGameInfo->setSlot(0, human);
    GameSlot ai;if(lockstepActive()){UnicodeString peer;peer.translate("Minecraft2");ai.setState(SLOT_PLAYER,peer,1);}else ai.setState(SLOT_EASY_AI);ai.setColor((color+1)%TheMultiplayerSettings->getNumColors());
    ai.setPlayerTemplate(other); ai.setStartPos(1); ai.setTeamNumber(-1);
    TheSkirmishGameInfo->setSlot(1, ai);
    for (int slot = 2; slot < MAX_SLOTS; ++slot) {
        GameSlot closed; closed.setState(SLOT_CLOSED); TheSkirmishGameInfo->setSlot(slot, closed);
    }
    if(!armySlots.empty()){
        for(int slot=0;slot<MAX_SLOTS;++slot){
            GameSlot entry;auto found=armySlots.find(slot);
            if(found==armySlots.end()){entry.setState(SLOT_CLOSED);TheSkirmishGameInfo->setSlot(slot,entry);continue;}
            const auto& c=found->second;int index=ThePlayerTemplateStore->getTemplateNumByName(c.faction.c_str());
            if(index<0||!ThePlayerTemplateStore->getNthPlayerTemplate(index)->isPlayableSide()||c.color<0||c.color>=TheMultiplayerSettings->getNumColors()){lastError="Invalid army faction or color";return;}
            UnicodeString label;label.translate(armyNames.count(slot)?armyNames.at(slot).c_str():("Minecraft"+std::to_string(slot+1)).c_str());
            entry.setState(c.kind==0?SLOT_PLAYER:c.kind==1?SLOT_EASY_AI:c.kind==2?SLOT_MED_AI:SLOT_BRUTAL_AI,label,slot);
            entry.setPlayerTemplate(index);entry.setColor(c.color);entry.setTeamNumber(c.team);entry.setStartPos(slot);entry.setMapAvailability(true);entry.setAccept();TheSkirmishGameInfo->setSlot(slot,entry);
        }
    }
    matchStartingCash=cash;configuredStartingCash=true;
    TheSkirmishGameInfo->setSuperweaponRestriction(hostSuperweaponLimit);
    TheSkirmishGameInfo->setSeed(seed);
    Money money; money.setStartingCash(cash); TheSkirmishGameInfo->setStartingCash(money);
    TheSkirmishGameInfo->setMap(map.c_str());
    TheWritableGlobalData->m_mapName = map.c_str();
    TheSkirmishGameInfo->startGame(0);
    InitRandom(seed);
    GameMessage* msg = TheMessageStream->appendMessage(GameMessage::MSG_NEW_GAME);
    msg->appendIntegerArgument(GAME_SKIRMISH);
    msg->appendIntegerArgument(DIFFICULTY_NORMAL);
    msg->appendIntegerArgument(0);
    msg->appendIntegerArgument(30);
    lastError.clear();
    hostInputSuspended=false;hostInputWasEnabled=true;
    forcedProjectiles.clear();forcedOrders.clear();columns.clear(); terrainRevision = 0; keys.clear();hostMoves.clear();drivenVehicles.clear();blockedDriveLeases.clear();
    resourceSites.clear();worldSites.clear();originalResourcesRemoved=false;hostMobs.clear();hostMobKeys.clear();hostCombatEvents.clear();combatSequence=0;
}
unsigned int mouseClick(unsigned int message,int x,int y){
    if(message!=WM_LBUTTONDOWN)return message;
    DWORD now=timeGetTime();bool twice=nextDoubleClick&&now-lastLeftClick<=GetDoubleClickTime()&&std::abs(x-lastLeftX)<=GetSystemMetrics(SM_CXDOUBLECLK)/2&&std::abs(y-lastLeftY)<=GetSystemMetrics(SM_CYDOUBLECLK)/2;
    lastLeftClick=now;lastLeftX=x;lastLeftY=y;nextDoubleClick=!twice;return twice?WM_LBUTTONDBLCLK:message;
}
void command(const std::string& text) {
    std::istringstream in(text); std::string verb; in >> verb;
    if(verb=="TEST_GRID"&&std::getenv("ZHMC_TEST")){
        int x,y,w,h;if(!(in>>x>>y>>w>>h)||w<1||h<1||w*h>16384){lastError="Invalid grid fixture";return;}
        std::ofstream out(root/"grid-test.json");out<<'[';bool comma=false;
        for(int dy=0;dy<h;++dy)for(int dx=0;dx<w;++dx)if(auto c=TheAI->pathfinder()->getCell(LAYER_GROUND,x+dx,y+dy)){
            if(comma)out<<',';comma=true;out<<'['<<x+dx<<','<<y+dy<<','<<c->getPosUnit()<<','<<c->getGoalUnit()<<','<<c->getObstacleID()<<']';
        }out<<']';return;
    }
    if(verb=="MATCH_NAME"){
        int slot;std::string name;if(TheGameLogic->isInGame()||!(in>>slot>>std::quoted(name))||slot<0||slot>=MAX_SLOTS||name.empty()||name.size()>256||name.find_first_of("\r\n")!=std::string::npos){lastError="Invalid army name";return;}
        armyNames[slot]=name;lastError.clear();return;
    }
    if(verb=="MATCH_SLOT"){
        int slot,kind,color,team;std::string faction;
        if(TheGameLogic->isInGame()||!(in>>slot>>kind>>color>>team>>std::quoted(faction))||slot<0||slot>=MAX_SLOTS||kind<0||kind>3||team< -1||team>=MAX_SLOTS||faction.size()>128){lastError="Invalid match slot";return;}
        armySlots[slot]={kind,color,team,faction};lastError.clear();return;
    }
    if(verb=="BEGIN_SHARED_AUTHORITY"){beginSharedAuthority();return;}
    if(verb=="HOST_EMP_RULES"){int value;if(in>>value)hostEmpVehicles=value!=0;return;}
    if(verb=="HOST_BATCH"){std::string line;int count=0;while(std::getline(in,line)&&count++<512)if(line.rfind("HOST_EMP_RULES ",0)==0||line.rfind("MOB_SYNC ",0)==0||line.rfind("MOB_DROP ",0)==0||line.rfind("MOB_KILLED ",0)==0||line.rfind("COMBAT_ACK ",0)==0||line.rfind("HOST_DAMAGE ",0)==0||line.rfind("HOST_CURRENT ",0)==0||line.rfind("HOST_DRIVE ",0)==0)command(line);return;}
    if(verb=="MATCH_RULES"){unsigned int limit;if(in>>limit&&limit<=1)hostSuperweaponLimit=limit;else lastError="Invalid superweapon limit";return;}
    if(verb=="DEBUG_MONEY"){
        unsigned int amount;if(!(in>>amount)||amount>1000000){lastError="Debug money must be 0..1000000";return;}
        if(auto player=ThePlayerList->getLocalPlayer())player->getMoney()->deposit(amount,false,false);return;
    }
    if(verb=="DEBUG_COOLDOWN"){
        unsigned int seconds;if(!(in>>seconds)||seconds>3600){lastError="Debug cooldown must be 0..3600 seconds";return;}
        for(auto object=TheGameLogic->getFirstObject();object;object=object->getNextObject())if(object->isLocallyControlled()){
            for(auto modules=object->getBehaviorModules();modules&&*modules;++modules)if(auto power=(*modules)->getSpecialPower())power->setReadyFrame(TheGameLogic->getFrame()+seconds*LOGICFRAMES_PER_SECOND);
        }return;
    }
    if(verb=="DEBUG_RANK"){if(auto player=ThePlayerList->getLocalPlayer())if(auto rank=TheRankInfoStore->getRankInfo(player->getRankLevel()+1))player->addSkillPoints(std::max(0,rank->m_skillPointsNeeded-player->getSkillPoints()));return;}
    if(verb=="DEBUG_SCIENCES"){
        if(auto player=ThePlayerList->getLocalPlayer()){
            for(int i=0;i<64;++i){auto rank=TheRankInfoStore->getRankInfo(player->getRankLevel()+1);if(!rank)break;int before=player->getRankLevel();player->addSkillPoints(std::max(0,rank->m_skillPointsNeeded-player->getSkillPoints()));if(player->getRankLevel()==before)break;}
            ScienceVec available,potential;
            for(int pass=0;pass<64;++pass){available.clear();potential.clear();TheScienceStore->getPurchasableSciences(player,available,potential);if(available.empty())break;for(auto science:available)player->grantScience(science);}
        }
        return;
    }
    if(verb=="DEBUG_HEAL"){
        if(auto selected=TheInGameUI->getAllSelectedDrawables())for(auto draw:*selected)if(auto object=draw->getObject())if(object->isLocallyControlled()&&!isExternalObject(object->getID()))object->attemptHealing(object->getBodyModule()->getMaxHealth(),nullptr);return;
    }
    if(verb=="HOST_FOG"){int enabled;if(in>>enabled)hostFogEnabled=enabled!=0;return;}
    if(verb=="HOST_AUDIO"){
        float music,sound,speech;if(!(in>>music>>sound>>speech)||!std::isfinite(music)||!std::isfinite(sound)||!std::isfinite(speech))return;
        hostMusic=std::clamp(music,0.f,1.f);hostSound=std::clamp(sound,0.f,1.f);hostSpeech=std::clamp(speech,0.f,1.f);hostAudioConfigured=true;
        if(TheAudio){TheAudio->unmuteAudio(AudioManager::MuteAudioReason_WindowFocus);TheAudio->setVolume(hostMusic,(AudioAffect)(AudioAffect_Music|AudioAffect_SystemSetting));TheAudio->setVolume(hostSound,(AudioAffect)(AudioAffect_Sound|AudioAffect_Sound3D|AudioAffect_SystemSetting));TheAudio->setVolume(hostSpeech,(AudioAffect)(AudioAffect_Speech|AudioAffect_SystemSetting));}
        return;
    }
    if(verb=="DEBUG_MUSIC"){nextHostMusic();return;}
    if(verb=="DEBUG_SOUND"){
        std::string name;if(in>>name&&name.size()<=128&&TheAudio){AudioEventRTS sound;sound.setEventName(name.c_str());TheAudio->addAudioEvent(&sound);}return;
    }
    if (verb == "QUIT") { TheGameEngine->setQuitting(true); return; }
    if (verb == "START") { start(in); return; }
    if(verb=="PLAYER_DEFEAT"){int index;if(!(in>>index)||index<2||index>=MAX_PLAYER_COUNT)return;if(auto player=ThePlayerList->getNthPlayer(index))if(player->isPlayerActive())player->killPlayer();return;}
    if (verb == "MOUSE") {
        worldPointer = false;pointerAnchor=false;leftButtonHeld=false;lastLeftWorldObject=0;pinnedLeftObject=0;
        unsigned int message, buttons; int x, y, wheel;
        if (!remoteInput() || !TheWin32Mouse || !(in >> message >> x >> y >> buttons >> wheel) ||
            x < 0 || x >= static_cast<int>(TheDisplay->getWidth()) || y < 0 || y >= static_cast<int>(TheDisplay->getHeight()) || buttons > 31 || abs(wheel) > 1200 ||
            (message != WM_MOUSEMOVE && message != WM_LBUTTONDOWN && message != WM_LBUTTONUP && message != WM_LBUTTONDBLCLK &&
             message != WM_RBUTTONDOWN && message != WM_RBUTTONUP && message != WM_MBUTTONDOWN &&
             message != WM_MBUTTONUP && message != WM_MOUSEWHEEL)) {
            lastError = "Invalid remote mouse event"; return;
        }
        if(message==WM_RBUTTONDOWN)rightButtonHeld=true;else if(message==WM_RBUTTONUP)rightButtonHeld=false;
        TheWin32Mouse->onCursorMovedInside();
        pointerX = x; pointerY = y;pointerSamples.erase(pointerKey(x,y));
        message=mouseClick(message,x,y);
        TheWin32Mouse->addWin32Event(message, MAKEWPARAM(buttons, wheel), MAKELPARAM(x, y), timeGetTime());
        lastError.clear(); return;
    }
    if (verb == "KEY") {
        int code, down;
        if (!remoteInput() || !(in >> code >> down) || code < 1 || code > 255 || (down != 0 && down != 1) || keys.size() >= 256) {
            lastError = "Invalid remote keyboard event"; return;
        }
        keys.push_back({static_cast<unsigned char>(code), down != 0, timeGetTime()});
        lastError.clear(); return;
    }
    if (!TheGameLogic->isInGame() || TheGameLogic->isInShellGame()) {
        lastError = "No active match"; return;
    }
    if(verb=="INIT_WORLD"&&lockstepActive()){
        // Attach the host terrain only after stock map initialization, also on a headless server.
        // Enabling paged partition cells during the original map loader corrupts its finite arrays.
        if(!worldTransportActive())setSceneRegion(0,0,1);
        for(auto o=TheGameLogic->getFirstObject();o;){auto next=o->getNextObject();auto player=o->getControllingPlayer();if(!player||player->getPlayerIndex()<2||o->isKindOf(KINDOF_SUPPLY_SOURCE))TheGameLogic->destroyObject(o);o=next;}
        for(int i=2;i<ThePlayerList->getPlayerCount();++i)ThePartitionManager->shroudMapForPlayer(i);
        originalResourcesRemoved=true;lastError.clear();return;
    }
    if(verb=="REBASE_PLAYER"&&lockstepActive()){
        int index;Coord3D target;if(!(in>>index>>target.x>>target.y>>target.z)||index<2||index>=ThePlayerList->getPlayerCount()||!std::isfinite(target.x)||!std::isfinite(target.y)||!std::isfinite(target.z)){lastError="Invalid army start";return;}
        auto player=ThePlayerList->getNthPlayer(index);Object* center=nullptr;
        for(auto o=TheGameLogic->getFirstObject();o;o=o->getNextObject())if(o->getControllingPlayer()==player&&o->isKindOf(KINDOF_COMMANDCENTER)){center=o;break;}
        if(!center){lastError="Army has no original command center";return;}
        Coord3D offset={target.x-center->getPosition()->x,target.y-center->getPosition()->y,target.z-center->getPosition()->z};
        std::vector<std::pair<Object*,Coord3D>> placements;
        for(auto o=TheGameLogic->getFirstObject();o;o=o->getNextObject())if(o->getControllingPlayer()==player&&!o->isContained()){
            Coord3D p=*o->getPosition();float oldFloor=TheTerrainLogic->getLayerHeight(p.x,p.y,LAYER_GROUND);p.x+=offset.x;p.y+=offset.y;
            float ground;if(!terrainHeight(p.x,p.y,ground)){lastError="Army start terrain is not loaded";return;}
            p.z=o->isKindOf(KINDOF_STRUCTURE)?ground:ground+(p.z-oldFloor);placements.emplace_back(o,p);
        }
        for(auto& item:placements)item.first->setPosition(&item.second);
        if(auto ai=player->hostAI())ai->hostRebase(offset);lastError.clear();return;
    }
    if(verb=="COMBAT_ACK"){unsigned long long ack;if(in>>ack)while(!hostCombatEvents.empty()&&hostCombatEvents.front().sequence<=ack){if(std::getenv("ZHMC_TEST")){auto& e=hostCombatEvents.front();std::ofstream log(root/"combat-test.jsonl",std::ios::app);log<<e.sequence<<" "<<e.source<<" "<<e.target<<" "<<e.amount<<" "<<e.x<<" "<<e.y<<" "<<e.z<<" "<<e.radius<<" "<<e.damageType<<"\n";}hostCombatEvents.pop_front();}return;}
    if(verb=="MOB_KILLED"){
        std::string key;unsigned killer;int points;if(!(in>>key>>killer>>points)||points<0||points>2000)return;
        auto found=hostMobs.find(key);if(found==hostMobs.end())return;
        auto source=TheGameLogic->findObjectByID(static_cast<ObjectID>(killer));
        if(source&&!isExternalObject(killer)&&source->getControllingPlayer()&&source->getControllingPlayer()->getPlayerIndex()>=2&&points>0)source->getControllingPlayer()->addSkillPoints(points);
        auto id=found->second.object;hostMobs.erase(found);hostMobKeys.erase(id);if(auto o=TheGameLogic->findObjectByID(id))TheGameLogic->destroyObject(o);return;
    }
    if(verb=="MOB_DROP"){std::string key;if(!(in>>key))return;auto m=hostMobs.find(key);if(m!=hostMobs.end()){auto id=m->second.object;hostMobKeys.erase(id);if(auto o=TheGameLogic->findObjectByID(id))TheGameLogic->destroyObject(o);hostMobs.erase(m);}return;}
    if(verb=="MOB_SYNC"){
        std::string key;Coord3D p;float hp,maxHp,width,height;int hostile,airborne=0;
        if(!(in>>key>>p.x>>p.y>>p.z>>hp>>maxHp>>width>>height>>hostile)||key.size()!=36||!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)||!std::isfinite(hp)||!std::isfinite(maxHp)||hp<=0||maxHp<=0||maxHp>100000||hp>maxHp||!std::isfinite(width)||!std::isfinite(height)||width<=0||height<=0||width>256||height>256)return;
        in>>airborne;std::string label;in>>std::quoted(label);if(label.size()>256)label.resize(256);float factor=healthUnitsPerPoint();if(factor<=0)return;auto found=hostMobs.find(key);Object* o=found==hostMobs.end()?nullptr:TheGameLogic->findObjectByID(found->second.object);
        auto hostilePlayer=ThePlayerList->getNthPlayer(1);
        if(hostile&&hostilePlayer)for(int i=2;i<ThePlayerList->getPlayerCount();++i){auto army=ThePlayerList->getNthPlayer(i);if(army){hostilePlayer->setPlayerRelationship(army,ENEMIES);army->setPlayerRelationship(hostilePlayer,ENEMIES);}}
        if(!o){auto t=TheThingFactory->findTemplate(airborne?"AmericaJetRaptor":"MogadishuMaleCivilian01",false);if(!t)return;auto player=hostile?hostilePlayer:ThePlayerList->getNeutralPlayer();if(!player)return;o=TheThingFactory->newObject(t,player->getDefaultTeam());hostMobs[key]={o->getID()};hostMobKeys[o->getID()]=key;}
        auto hostPlayer=hostile?hostilePlayer:ThePlayerList->getNeutralPlayer();
        if(hostPlayer&&o->getControllingPlayer()!=hostPlayer)o->setTeam(hostPlayer->getDefaultTeam());
        hostMobs[key].label=label;if(airborne)o->setStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_AIRBORNE_TARGET));else o->clearStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_AIRBORNE_TARGET));o->setSelectable(true);
        auto& geometry=o->getGeometryInfo();if(geometry.getGeomType()!=GEOMETRY_CYLINDER||!geometry.getIsSmall()||std::fabs(geometry.getMajorRadius()-width*.5f)>.001f||std::fabs(geometry.getMinorRadius()-width*.5f)>.001f||std::fabs(geometry.getMaxHeightAbovePosition()-height)>.001f)o->setGeometryInfo(GeometryInfo(GEOMETRY_CYLINDER,true,height,width*.5f,width*.5f));
        auto previous=o->getPosition();if(std::fabs(previous->x-p.x)>.0001f||std::fabs(previous->y-p.y)>.0001f||std::fabs(previous->z-p.z)>.0001f)o->setPosition(&p);
        auto body=o->getBodyModule();if(std::fabs(body->getMaxHealth()-maxHp*factor)>.001f)body->setMaxHealth(maxHp*factor,SAME_CURRENTHEALTH);float delta=hp*factor-body->getHealth();
        if(delta>0)o->attemptHealing(delta,nullptr);else if(delta<0){DamageInfo damage;damage.in.m_amount=-delta;damage.in.m_damageType=DAMAGE_UNRESISTABLE;body->attemptDamage(&damage);}
        // Geometry changes remove old COIs immediately. Rebuild only this imported object's
        // cell coverage before native weapon queries; keep its dirty entry for normal collision processing.
        if(auto partition=o->friend_getPartitionData())if(partition->isInNeedOfUpdatingCells())partition->friend_updateCellsTouched();return;
    }
    if(verb=="HOST_DRIVE"){
        unsigned int id;int active,fire;float dx,dy;Coord3D aim;
        if(!(in>>id>>active>>dx>>dy>>fire>>aim.x>>aim.y>>aim.z)||!std::isfinite(dx)||!std::isfinite(dy)||std::hypot(dx,dy)>1.001f||!std::isfinite(aim.x)||!std::isfinite(aim.y)||!std::isfinite(aim.z))return;
        unsigned int targetID=0;unsigned long long lease=0;in>>targetID;in>>lease;auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(!object||isExternalObject(id)||object->isContained()||object->getBodyModule()->getHealth()<=0||!object->isSelectable()||object->isKindOf(KINDOF_STRUCTURE)||!(object->isKindOf(KINDOF_VEHICLE)||object->isKindOf(KINDOF_AIRCRAFT)))return;
        auto blocked=blockedDriveLeases.find(id);if(active&&blocked!=blockedDriveLeases.end()&&lease<=blocked->second)return;
        auto ai=object->getAI();if(!ai)return;auto supply=ai->getSupplyTruckAIInterface();drivingCommand=true;
        auto found=drivenVehicles.find(id);
        if(!active){if(found!=drivenVehicles.end()&&found->second.lease==lease){ai->setTurretTargetPosition(TURRET_MAIN,nullptr);ai->setTurretTargetPosition(TURRET_ALT,nullptr);ai->aiIdle(CMD_FROM_PLAYER);drivenVehicles.erase(id);forcedOrders.erase(id);if(supply)supply->setForceBusyState(false);}drivingCommand=false;return;}
        if(supply)supply->setForceBusyState(true);
        bool first=found==drivenVehicles.end()||found->second.lease!=lease;auto& drive=drivenVehicles[id];if(first)drive.moving=false;drive.lease=lease;drive.fire=fire!=0;auto position=object->getPosition();bool moving=std::hypot(dx,dy)>.05f;
        if(first)ai->aiIdle(CMD_FROM_PLAYER);
        if(!object->isDisabled()){
            if(moving){if(!drive.moving||std::hypot(dx-drive.dx,dy-drive.dy)>.05f||std::hypot(position->x-drive.goal.x,position->y-drive.goal.y)<worldUnitsPerBlock()*3){drive.goal=*position;drive.goal.x+=dx*worldUnitsPerBlock()*8;drive.goal.y+=dy*worldUnitsPerBlock()*8;drive.dx=dx;drive.dy=dy;ai->aiMoveToPosition(&drive.goal,CMD_FROM_PLAYER);}}
            else if(drive.moving)ai->aiIdle(CMD_FROM_PLAYER);
            auto target=TheGameLogic->findObjectByID(static_cast<ObjectID>(targetID));if(target==object||target&&(target->isContained()||target->getBodyModule()->getHealth()<=0))target=nullptr;
            bool legal=!target||TheActionManager->getCanAttackObject(object,target,CMD_FROM_PLAYER,isExternalObject(targetID)?ATTACK_NEW_TARGET_FORCED:ATTACK_NEW_TARGET)==ATTACKRESULT_POSSIBLE;
            drive.fire=drive.fire&&legal;
            if(target&&legal){object->chooseBestWeaponForTarget(target,PREFER_MOST_DAMAGE,CMD_FROM_PLAYER);ai->setTurretTargetObject(TURRET_MAIN,target,isExternalObject(targetID));forcedOrders.erase(id);}
            else {ai->setTurretTargetPosition(TURRET_MAIN,&aim);forcedOrders.insert(id);}
            // Turret states aim and fire with their original turn rate and cooldown.
            // Fixed weapons have no turret state to fire them on the driver's behalf.
            if(drive.fire&&ai->getWhichTurretForCurWeapon()==TURRET_INVALID)if(auto weapon=object->getCurrentWeapon())if(weapon->getStatus()==READY_TO_FIRE){if(target&&weapon->isWithinAttackRange(object,target))weapon->fireWeapon(object,target);else if(!target&&weapon->isWithinAttackRange(object,&aim))weapon->fireWeapon(object,&aim);}
        }
        drive.moving=moving;drivingCommand=false;return;
    }
    if(verb=="HOST_CURRENT"){
        unsigned int id;float dx,dy;if(!(in>>id>>dx>>dy)||!std::isfinite(dx)||!std::isfinite(dy)||std::hypot(dx,dy)>worldUnitsPerBlock()*.04f)return;
        auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));
        if(!object||isExternalObject(id)||object->isKindOf(KINDOF_PROJECTILE)||object->isKindOf(KINDOF_STRUCTURE)||object->isKindOf(KINDOF_AIRCRAFT)||object->isContained()||object->isAirborneTarget()||object->isUsingAirborneLocomotor())return;
        auto physics=object->getPhysics();if(!physics)return;
        Coord3D force={dx*physics->getMass(),dy*physics->getMass(),0};physics->applyForce(&force);return;
    }
    if(verb=="HOST_DAMAGE"){
        unsigned int id;float amount;int explosive,type=11;if(!(in>>id>>amount>>explosive)||!std::isfinite(amount)||amount<=0||amount>1e6)return;in>>type;if(type!=6&&type!=12)type=11;auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(!o||isExternalObject(id)||o->isContained())return;DamageInfo damage;damage.in.m_amount=amount*healthUnitsPerPoint();damage.in.m_damageType=explosive?DAMAGE_EXPLOSION:static_cast<DamageType>(type);damage.in.m_deathType=type==6?DEATH_BURNED:DEATH_NORMAL;o->attemptDamage(&damage);return;
    }
    if(verb=="SITE_REMOVE"){
        std::string key;if(!(in>>key))return;auto found=worldSites.find(key);if(found!=worldSites.end()){if(auto object=TheGameLogic->findObjectByID(found->second))TheGameLogic->destroyObject(object);worldSites.erase(found);}return;
    }
    if(verb=="SITE"){
        std::string key,name;Coord3D p;float angle;
        if(!(in>>key>>name>>p.x>>p.y>>p.z>>angle)||key.size()>80||name.size()>128||!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)||!std::isfinite(angle))return;
        if(worldSites.count(key))return;auto t=TheThingFactory->findTemplate(name.c_str(),false);
        if(!t||!t->isKindOf(KINDOF_STRUCTURE)||t->isKindOf(KINDOF_PROJECTILE))return;
        auto neutral=ThePlayerList->getNeutralPlayer();
        if(TheBuildAssistant->isLocationLegalToBuild(&p,t,angle,BuildAssistant::TERRAIN_RESTRICTIONS|BuildAssistant::NO_OBJECT_OVERLAP,nullptr,neutral)!=LBC_OK)return;
        auto object=TheBuildAssistant->buildObjectNow(nullptr,t,&p,angle,neutral);
        if(object){object->clearStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_UNDER_CONSTRUCTION));object->setConstructionPercent(CONSTRUCTION_COMPLETE);worldSites.emplace(key,object->getID());}return;
    }
    if(verb=="RESOURCE_REMOVE"){
        std::string key;if(!(in>>key)||key.rfind("block_",0)!=0)return;
        auto found=resourceSites.find(key);if(found!=resourceSites.end()){if(auto object=TheGameLogic->findObjectByID(found->second))TheGameLogic->destroyObject(object);resourceSites.erase(found);}return;
    }
    if(verb=="RESOURCE"){
        std::string key,name;Coord3D p;int cash;
        if(!(in>>key>>name>>p.x>>p.y>>p.z>>cash)||key.size()>80||name!="SupplyDock"||cash< -1||cash>100000000||!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)) {lastError="Malformed resource site";return;}
        if(resourceSites.find(key)!=resourceSites.end())return;
        auto t=TheThingFactory->findTemplate(name.c_str(),false);if(!t)return;
        auto neutral=ThePlayerList->getNeutralPlayer();
        // Defer a site until its host columns and original footprint are usable. Retried idempotently by the host.
        auto legal=TheBuildAssistant->isLocationLegalToBuild(&p,t,0,BuildAssistant::TERRAIN_RESTRICTIONS|BuildAssistant::NO_OBJECT_OVERLAP,nullptr,neutral);
        if(legal!=LBC_OK)return;
        auto object=TheBuildAssistant->buildObjectNow(nullptr,t,&p,0,neutral);if(!object)return;object->setPosition(&p);
        object->clearStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_UNDER_CONSTRUCTION));object->setConstructionPercent(CONSTRUCTION_COMPLETE);
        auto dock=supplyDock(object);if(!dock){TheGameLogic->destroyObject(object);return;}
        if(cash>=0)dock->setCashValue(cash);
        resourceSites.emplace(key,object->getID());lastError.clear();return;
    }
    if(verb=="TEST_DOCK"&&std::getenv("ZHMC_TEST")){unsigned id,target;if(in>>id>>target){auto object=TheGameLogic->findObjectByID((ObjectID)id);auto dock=TheGameLogic->findObjectByID((ObjectID)target);if(object&&dock&&object->getAI())object->getAI()->aiDock(dock,CMD_FROM_PLAYER);}return;}
    if(verb=="TEST_SQUAD"&&std::getenv("ZHMC_TEST")){unsigned id;int group;if(in>>id>>group)if(group>=0&&group<10)if(auto object=TheGameLogic->findObjectByID((ObjectID)id))if(auto squad=object->getControllingPlayer()->getHotkeySquad(group))squad->addObject(object);return;}
    if(verb=="TEST_CONSTRUCT"&&std::getenv("ZHMC_TEST")){unsigned id;std::string name;Coord3D p;if(in>>id>>name>>p.x>>p.y>>p.z){auto builder=TheGameLogic->findObjectByID((ObjectID)id);auto type=TheThingFactory->findTemplate(name.c_str(),false);if(builder&&type&&builder->getAI())builder->getAI()->construct(type,&p,0,builder->getControllingPlayer(),false);}return;}
    if(verb=="TEST_CONSTRUCTION"&&std::getenv("ZHMC_TEST")){unsigned id;float percent;if(in>>id>>percent)if(std::isfinite(percent)&&percent>=-1&&percent<=100)if(auto object=TheGameLogic->findObjectByID((ObjectID)id)){if(percent>=0&&percent<100)object->setStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_UNDER_CONSTRUCTION));else object->clearStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_UNDER_CONSTRUCTION));object->setConstructionPercent(percent==100?CONSTRUCTION_COMPLETE:percent);object->clearModelConditionFlags(MAKE_MODELCONDITION_MASK3(MODELCONDITION_PARTIALLY_CONSTRUCTED,MODELCONDITION_ACTIVELY_BEING_CONSTRUCTED,MODELCONDITION_AWAITING_CONSTRUCTION));if(percent>=0&&percent<100)object->setModelConditionFlags(MAKE_MODELCONDITION_MASK2(MODELCONDITION_PARTIALLY_CONSTRUCTED,MODELCONDITION_ACTIVELY_BEING_CONSTRUCTED));}return;}
    if(verb=="TEST_SPAWN" && std::getenv("ZHMC_TEST")) {
        std::string name;Coord3D p;if(!(in>>name>>p.x>>p.y>>p.z)){lastError="Malformed test spawn";return;}
        auto t=TheThingFactory->findTemplate(name.c_str(),false);if(!t){lastError="Unknown test template";return;}
        auto owner=ThePlayerList->getLocalPlayer();int index;if(in>>index){if(index<0||index>=ThePlayerList->getPlayerCount()){lastError="Invalid fixture owner";return;}owner=ThePlayerList->getNthPlayer(index);}
        float angle=0;in>>angle;if(!std::isfinite(angle)){lastError="Invalid fixture orientation";return;}
        // Script construction must run original completion callbacks (notably supply-centre registration).
        auto o=t->isKindOf(KINDOF_STRUCTURE)?TheBuildAssistant->buildObjectNow(nullptr,t,&p,angle,owner):TheThingFactory->newObject(t,owner->getDefaultTeam());
        if(o){o->setPosition(&p);o->setOrientation(angle);if(t->isKindOf(KINDOF_STRUCTURE)){o->clearStatus(MAKE_OBJECT_STATUS_MASK(OBJECT_STATUS_UNDER_CONSTRUCTION));o->setConstructionPercent(CONSTRUCTION_COMPLETE);}}
        else {lastError="Test construction rejected";return;}lastError.clear();return;
    }
    if(verb=="TEST_TERRAIN_EXTREMES"&&std::getenv("ZHMC_TEST")){Coord3D a,b;float lo=0,hi=0;if(in>>a.x>>a.y>>a.z>>b.x>>b.y>>b.z){bool valid=ThePartitionManager->estimateTerrainExtremesAlongLine(a,b,&lo,&hi,nullptr,nullptr);std::ofstream out(root/"terrain-extremes-test.json");out<<"{\"valid\":"<<(valid?"true":"false")<<",\"min\":"<<lo<<",\"max\":"<<hi<<'}';}return;}
    if(verb=="TEST_ATTACK"&&std::getenv("ZHMC_TEST")){unsigned int id,target;int forced;if(in>>id>>target>>forced){auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));auto victim=TheGameLogic->findObjectByID(static_cast<ObjectID>(target));auto ai=object?object->getAI():nullptr;if(ai&&victim){if(forced)ai->aiForceAttackObject(victim,NO_MAX_SHOTS_LIMIT,CMD_FROM_PLAYER);else ai->aiAttackObject(victim,NO_MAX_SHOTS_LIMIT,CMD_FROM_PLAYER);}}return;}
    if(verb=="TEST_FIRE" && std::getenv("ZHMC_TEST")){unsigned int id;Coord3D p;if(in>>id>>p.x>>p.y>>p.z){auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(o&&o->getCurrentWeapon())o->getCurrentWeapon()->fireWeapon(o,&p);}return;}
    if(verb=="TEST_PROJECT"&&std::getenv("ZHMC_TEST")){ICoord2D screen;Coord3D p={},plane={};if(in>>screen.x>>screen.y){bool valid=hostScreenToTerrain(&screen,&p),zero=hostScreenToWorld(&screen,&plane,0);auto target=worldPointerDrawable(&screen,0);std::ofstream out(root/"project-test.json");out<<"{\"valid\":"<<(valid?"true":"false")<<",\"zeroPlane\":"<<(zero?"true":"false")<<",\"eyeZ\":"<<hostEye.z<<",\"picked\":"<<(target&&target->getObject()?target->getObject()->getID():0)<<",\"x\":"<<p.x<<",\"y\":"<<p.y<<",\"z\":"<<p.z<<'}';}return;}
    if(verb=="TEST_STALL" && std::getenv("ZHMC_TEST")){unsigned int id;if(in>>id){auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(o&&o->getAI())o->getAI()->aiIdle(CMD_FROM_AI);}return;}
    if(verb=="TEST_AI"&&std::getenv("ZHMC_TEST")){
        std::ofstream out(root/"ai-test.json");out<<"[";bool first=true;
        for(int i=2;i<ThePlayerList->getPlayerCount();++i){auto player=ThePlayerList->getNthPlayer(i);if(!player||!player->hostAI())continue;
            Object* dozer=nullptr;for(auto o=TheGameLogic->getFirstObject();o;o=o->getNextObject())if(o->getControllingPlayer()==player&&o->isKindOf(KINDOF_DOZER)){dozer=o;break;}
            for(auto info=player->getBuildList();info;info=info->getNext()){auto p=info->getLocation();Coord3D absolute=*p;absolute.z+=TheTerrainLogic->getGroundHeight(p->x,p->y);auto t=TheThingFactory->findTemplate(info->getTemplateName(),false);if(!first)out<<",";first=false;out<<"{\"player\":"<<i<<",\"name\":"<<json(info->getTemplateName().str())<<",\"automatic\":"<<info->isAutomaticBuild()<<",\"priority\":"<<info->isPriorityBuild()<<",\"buildable\":"<<info->isBuildable()<<",\"object\":"<<info->getObjectID()<<",\"cash\":"<<player->getMoney()->countMoney()<<",\"safe\":"<<player->hostAI()->isLocationSafe(p,t)<<",\"canBuildBase\":"<<player->getCanBuildBase()<<",\"enemyLegal\":"<<(t?TheBuildAssistant->isLocationLegalToBuild(&absolute,t,info->getAngle(),BuildAssistant::NO_ENEMY_OBJECT_OVERLAP,dozer,player):-1)<<",\"legal\":"<<(t?TheBuildAssistant->isLocationLegalToBuild(&absolute,t,info->getAngle(),BuildAssistant::TERRAIN_RESTRICTIONS|BuildAssistant::NO_OBJECT_OVERLAP,dozer,player):-1)<<",\"canMake\":"<<TheBuildAssistant->canMakeUnit(dozer,t)<<",\"x\":"<<p->x<<",\"y\":"<<p->y<<",\"z\":"<<p->z<<"}";}
        }out<<"]";return;
    }
    if(verb=="TEST_POWER_TARGET" && std::getenv("ZHMC_TEST")){unsigned int id;Coord3D p;if(in>>id>>p.x>>p.y>>p.z){auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(o)for(auto modules=o->getBehaviorModules();modules&&*modules;++modules)if(auto power=(*modules)->getSpecialPower()){bool allowed=TheActionManager->canDoSpecialPowerAtLocation(o,&p,CMD_FROM_PLAYER,power->getSpecialPowerTemplate(),nullptr,0,false);std::ofstream test(root/"power-target-test.json");test<<"{\"allowed\":"<<(allowed?"true":"false")<<"}";break;}}return;}
    if(verb=="TEST_PATH" && std::getenv("ZHMC_TEST")){
        unsigned int id;Coord3D p;if(!(in>>id>>p.x>>p.y>>p.z))return;auto o=TheGameLogic->findObjectByID((ObjectID)id);if(!o||!o->getAI())return;
        auto finder=TheAI->pathfinder();auto layer=TheTerrainLogic->getLayerForDestination(&p);auto start=finder->getCell(o->getLayer(),o->getPosition());auto end=finder->getCell(layer,&p);
        int probe=finder->hostPathProbe(o,&p);bool found=finder->slowDoesPathExist(o,o->getPosition(),&p,INVALID_ID);
        std::ofstream diagnostic(root/"path-diagnostic.json");diagnostic<<"{\"startType\":"<<(start?(int)start->getType():-1)<<",\"goalType\":"<<(end?(int)end->getType():-1)<<",\"startKnown\":"<<terrainCellLoaded((int)floor(o->getPosition()->x/PATHFIND_CELL_SIZE),(int)floor(o->getPosition()->y/PATHFIND_CELL_SIZE))<<",\"goalKnown\":"<<terrainCellLoaded((int)floor(p.x/PATHFIND_CELL_SIZE),(int)floor(p.y/PATHFIND_CELL_SIZE))<<",\"layer\":"<<(int)layer<<",\"probe\":"<<probe<<",\"found\":"<<found<<"}";return;
    }
    if(verb=="TEST_FX" && std::getenv("ZHMC_TEST")){
        std::string name;Coord3D p;if(!(in>>name>>p.x>>p.y>>p.z)){lastError="Malformed test FX";return;}auto fx=TheFXListStore->findFXList(name.c_str());if(!fx){lastError="Unknown original FX";return;}beginTestFX();FXList::doFXPos(fx,&p);endTestFX();lastError.clear();return;
    }
    if(verb=="TEST_DAMAGE" && std::getenv("ZHMC_TEST")) {
        unsigned int id;float amount;if(!(in>>id>>amount)||amount<=0||!std::isfinite(amount)){lastError="Malformed test damage";return;}
        auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));if(!o){lastError="Missing test target";return;}
        DamageInfo info;info.in.m_amount=amount;info.in.m_damageType=DAMAGE_UNRESISTABLE;o->attemptDamage(&info);lastError.clear();return;
    }
    if (verb == "HOST_VIEW") {
        float zoom;
        if (!(in>>hostEye.x>>hostEye.y>>hostEye.z>>hostForward.x>>hostForward.y>>hostForward.z
            >>hostLeft.x>>hostLeft.y>>hostLeft.z>>hostUp.x>>hostUp.y>>hostUp.z>>hostTan>>hostAspect>>zoom)
            || !std::isfinite(hostEye.x)||!std::isfinite(hostEye.y)||!std::isfinite(hostEye.z)
            ||hostTan<.1f||hostTan>3||hostAspect<.2f||hostAspect>5||!std::isfinite(zoom)) {lastError="Invalid host view";return;}
        for(auto vector:{hostEye,hostForward,hostLeft,hostUp})if(!std::isfinite(vector.x)||!std::isfinite(vector.y)||!std::isfinite(vector.z)){lastError="Invalid host view basis";return;}
        hostView=true;TheTacticalView->setZoom(std::clamp(zoom,.4f,3.0f));if(TheRadar)TheRadar->notifyViewChanged();lastError.clear();
    } else if (verb == "HOST_DRAG") {
        int active;IRegion2D region;
        if(!(in>>active>>region.lo.x>>region.lo.y>>region.hi.x>>region.hi.y)||(active!=0&&active!=1)){lastError="Invalid host drag";return;}
        TheInGameUI->setHostDragRegion(active!=0,region);lastError.clear();
    } else if (verb == "MODE") {
        int active;
        if (!(in >> active) || (active!=0 && active!=1)) { lastError="Invalid world mode";return; }
        if(!active){
            if(!hostInputSuspended){hostInputWasEnabled=TheInGameUI->getInputEnabled();hostInputSuspended=true;}
            // Use SAGE's cinematic/input-context reset: lost modifier releases must not
            // leave Alt waypoints, Ctrl attack or Shift selection latched after Minecraft.
            TheInGameUI->setInputEnabled(false);
            TheInGameUI->placeBuildAvailable(nullptr,nullptr);TheInGameUI->setGUICommand(nullptr);
            TheInGameUI->setScrolling(false);TheInGameUI->clearAttackMoveToMode();TheInGameUI->setRadiusCursorNone();
            IRegion2D empty={};TheInGameUI->setHostDragRegion(false,empty);
            if(TheWindowManager){if(auto captor=TheWindowManager->winGetCapture())TheWindowManager->winRelease(captor);TheWindowManager->winSetGrabWindow(nullptr);}
            for(unsigned char code:{0x2a,0x36,0x1d,0x9d,0x38,0xb8})keys.push_back({code,false,timeGetTime()});
        }else if(hostInputSuspended){TheInGameUI->setInputEnabled(hostInputWasEnabled);hostInputSuspended=false;}
        rightButtonHeld=leftButtonHeld=false;worldPointer=false;pointerAnchor=false;lastLeftWorldObject=pinnedLeftObject=0;nextDoubleClick=false;
        pointerSamples.clear();setWorldViewMode(active!=0);if(TheMouse)TheMouse->resetTooltipDelay();lastError.clear();
    } else if (verb == "WORLD_MOUSE") {
        unsigned int message, id, buttons; Coord3D p;
        if (!(in>>message>>p.x>>p.y>>p.z>>id>>buttons) || !std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)||buttons>31||
            (message!=WM_MOUSEMOVE&&message!=WM_LBUTTONDOWN&&message!=WM_LBUTTONUP&&message!=WM_LBUTTONDBLCLK&&message!=WM_RBUTTONDOWN&&message!=WM_RBUTTONUP&&message!=WM_MBUTTONDOWN&&message!=WM_MBUTTONUP)) { lastError="Invalid world mouse";return; }
        worldPointer=true;worldPosition=p;worldObject=id;pointerX=400;pointerY=200;
        int sx,sy;if(in>>sx>>sy){pointerX=std::clamp(sx,0,static_cast<int>(TheDisplay->getWidth())-1);pointerY=std::clamp(sy,0,static_cast<int>(TheDisplay->getHeight())-1);}
        if(message==WM_RBUTTONDOWN)rightButtonHeld=true;else if(message==WM_RBUTTONUP)rightButtonHeld=false;
        TheWin32Mouse->onCursorMovedInside();
        if(pointerSamples.size()>512)pointerSamples.clear();pointerSamples[pointerKey(pointerX,pointerY)]={p,id};
        bool sameDouble=message==WM_LBUTTONDOWN&&id!=0&&id==lastLeftWorldObject&&nextDoubleClick&&timeGetTime()-lastLeftClick<=GetDoubleClickTime()&&std::abs(pointerX-lastLeftX)<=std::max(4,static_cast<int>(TheDisplay->getWidth())/80)&&std::abs(pointerY-lastLeftY)<=std::max(4,static_cast<int>(TheDisplay->getHeight())/60);
        message=mouseClick(message,pointerX,pointerY);if(sameDouble){message=WM_LBUTTONDBLCLK;nextDoubleClick=false;}
        if(message==WM_LBUTTONDBLCLK&&id==0&&lastLeftWorldObject){auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(lastLeftWorldObject));if(object&&object->getDrawable()&&TheInGameUI->isDrawableSelected(object->getDrawable()->getID()))id=lastLeftWorldObject;}
        if(message==WM_LBUTTONDOWN||message==WM_LBUTTONDBLCLK){lastLeftWorldObject=id;pinnedLeftObject=id;pinnedLeftScreen={pointerX,pointerY};pinnedLeftFrame=TheGameLogic->getFrame()+3;worldObject=id;pointerSamples[pointerKey(pointerX,pointerY)].object=id;}
        if(message==WM_LBUTTONDOWN||message==WM_LBUTTONDBLCLK){pointerAnchor=true;anchorScreen={pointerX,pointerY};anchorPosition=p;}
        if(message==WM_LBUTTONDOWN||message==WM_LBUTTONDBLCLK)leftButtonHeld=true;else if(message==WM_LBUTTONUP){leftButtonHeld=false;anchorReleased=timeGetTime();}
        TheWin32Mouse->addWin32Event(message,buttons,MAKELPARAM(pointerX,pointerY),timeGetTime());lastError.clear();
    } else if (verb == "SCENE") {
        float x, y, radius;
        if (!(in >> x >> y >> radius) || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(radius) || radius < 0 || radius > worldUnitsPerBlock()*512) {
            lastError = "Invalid scene scope"; return;
        }
        fogFocusX=x;fogFocusY=y;setSceneRegion(x, y, radius); lastError.clear();
        if(radius>0&&!originalResourcesRemoved&&!lockstepActive()){
            originalResourcesRemoved=true;
            for(auto object=TheGameLogic->getFirstObject();object;){auto next=object->getNextObject();if(object->isKindOf(KINDOF_SUPPLY_SOURCE)||(sandboxWorld()&&!object->isLocallyControlled()))TheGameLogic->destroyObject(object);object=next;}
            // Skirmish startup can reveal the finite stock map through multiplayer settings.
            // Start host exploration once, preserving every active original unit looker.
            if(ThePartitionManager&&ThePlayerList&&ThePlayerList->getLocalPlayer())ThePartitionManager->shroudMapForPlayer(ThePlayerList->getLocalPlayer()->getPlayerIndex());
        }
    } else if (verb == "TERRAIN" || verb == "TERRAIN2" || verb=="TERRAIN3") {
        unsigned long long revision; unsigned int count;
        if (!(in >> revision >> count) || count > 1024 || revision <= terrainRevision) {
            lastError = "Malformed or stale TERRAIN"; return;
        }
        // Validate the complete delta before applying it. Never copy the explored world per tile.
        decltype(columns) changes;
        const ICoord2D* extent = TheAI->pathfinder()->getExtent();
        for (unsigned int n = 0; n < count; ++n) {
            int x, y, type; float height,clearance=10000;
            if (!(in >> x >> y >> height >> type) || (pagedWorld() ? (x < -60000000 || y < -60000000 || x > 60000000 || y > 60000000) : (x < 0 || y < 0 || x > (int)std::ceil(extent->x*PATHFIND_CELL_SIZE/worldUnitsPerBlock()) || y > (int)std::ceil(extent->y*PATHFIND_CELL_SIZE/worldUnitsPerBlock()))) ||
                !std::isfinite(height) || (type != -1 && type != 0 && type != 1 && type != 2 && type != 6)) {
                lastError = "Invalid terrain column"; return;
            }
            if(verb!="TERRAIN"&&(!(in>>clearance)||!std::isfinite(clearance)||clearance<0)){lastError="Invalid terrain clearance";return;}
            std::vector<std::pair<float,float>> obstacles;
            if(verb=="TERRAIN3"){unsigned count;if(!(in>>count)||count>64){lastError="Invalid collision count";return;}for(unsigned i=0;i<count;++i){float lo,hi;if(!(in>>lo>>hi)||!std::isfinite(lo)||!std::isfinite(hi)||hi<lo){lastError="Invalid collision span";return;}obstacles.emplace_back(lo,hi);}}
            changes[cellKey(x, y)] = {height, type,clearance,std::move(obstacles)};
        }
        // Wake only retained routes affected by a real collision/height change.
        // An unrelated streamed tile must not make every idle army re-path.
        for(auto& pending:hostMoves)if(auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(pending.first))){
            auto p=object->getPosition();auto& move=pending.second;
            float x0=std::min(p->x,move.goal.x)/worldUnitsPerBlock()-2,x1=std::max(p->x,move.goal.x)/worldUnitsPerBlock()+2;
            float y0=std::min(p->y,move.goal.y)/worldUnitsPerBlock()-2,y1=std::max(p->y,move.goal.y)/worldUnitsPerBlock()+2;
            for(const auto& entry:changes){
                if(entry.second.type==-1)continue;
                int x=static_cast<int>(static_cast<unsigned int>(entry.first>>32)),y=static_cast<int>(static_cast<unsigned int>(entry.first));
                if(x<x0||x>x1||y<y0||y>y1)continue;
                auto previous=columns.find(entry.first);
                if(previous==columns.end()||previous->second.height!=entry.second.height||previous->second.type!=entry.second.type||previous->second.clearance!=entry.second.clearance||previous->second.obstacles!=entry.second.obstacles){move.terrainDirty=true;break;}
            }
        }
        // Preserve each object's altitude relative to its prior surface, including stationary units.
        // Original placement updates weapons, exit bones, drawables and object footprints together.
        for (Object* object = TheGameLogic->getFirstObject(); object; object = object->getNextObject()) {
            if (isExternalObject(object->getID()) || !(object->isKindOf(KINDOF_SELECTABLE)||object->isKindOf(KINDOF_HULK)||object->isKindOf(KINDOF_CRATE)) || object->isContained() || object->getLayer() != LAYER_GROUND) continue;
            Coord3D position = *object->getPosition();
            auto column = changes.find(cellKey(static_cast<int>(std::floor(position.x/worldUnitsPerBlock())), static_cast<int>(std::floor(position.y/worldUnitsPerBlock()))));
            if (column != changes.end() && column->second.type != -1) {
                float oldHeight = TheTerrainLogic->getLayerHeight(position.x, position.y, LAYER_GROUND);
                float delta = column->second.height - oldHeight;
                if (std::fabs(delta) > .01f) { position.z = (object->isKindOf(KINDOF_STRUCTURE)||(!object->isKindOf(KINDOF_AIRCRAFT)&&!object->isAirborneTarget()&&!object->isUsingAirborneLocomotor())) ? column->second.height : position.z+delta; object->setPosition(&position); }
            }
        }
        for(auto& entry:changes)if(entry.second.type==-1)columns.erase(entry.first);else columns.insert_or_assign(entry.first,std::move(entry.second));
        terrainRevision = revision;
        // Paged path cells read the current imported terrain on access; their obstacle IDs and
        // live search state must survive an unrelated tile arriving elsewhere in the world.
        if(pagedWorld()){
            for(const auto& entry:changes){
                int bx=static_cast<int>(static_cast<unsigned int>(entry.first>>32)),by=static_cast<int>(static_cast<unsigned int>(entry.first));
                int x0=static_cast<int>(std::floor(bx*worldUnitsPerBlock()/PATHFIND_CELL_SIZE_F)),y0=static_cast<int>(std::floor(by*worldUnitsPerBlock()/PATHFIND_CELL_SIZE_F));
                int x1=static_cast<int>(std::ceil((bx+1)*worldUnitsPerBlock()/PATHFIND_CELL_SIZE_F)),y1=static_cast<int>(std::ceil((by+1)*worldUnitsPerBlock()/PATHFIND_CELL_SIZE_F));
                for(int x=x0;x<x1;++x)for(int y=y0;y<y1;++y)if(auto cell=TheAI->pathfinder()->getCell(LAYER_GROUND,x,y))if(cell->getObstacleID()==INVALID_ID){int type=terrainType(x,y);cell->setType(static_cast<PathfindCell::CellType>(type<0?6:type));cell->setZone(1);}
            }
        }else{
            TheAI->pathfinder()->forceMapRecalculation();
            for (Object* object = TheGameLogic->getFirstObject(); object; object = object->getNextObject())
                TheAI->pathfinder()->addObjectToPathfindMap(object);
        }
        lastError.clear();
    } else if (verb == "SELECT") {
        TheInGameUI->deselectAllDrawables();
        GameMessage* msg = TheMessageStream->appendMessage(GameMessage::MSG_CREATE_SELECTED_GROUP);
        msg->appendBooleanArgument(true);
        unsigned int id;
        while (in >> id) {
            Object* object = TheGameLogic->findObjectByID(static_cast<ObjectID>(id));
            if (object && object->isLocallyControlled() && !object->isContained()) {
                if(object->getDrawable())TheInGameUI->selectDrawable(object->getDrawable());
                msg->appendObjectIDArgument(object->getID());
            }
        }
        lastError.clear();
    } else if (verb == "MOVE") {
        Coord3D pos;
        if (!(in >> pos.x >> pos.y >> pos.z)) { lastError = "Malformed MOVE"; return; }
        GameMessage* msg = TheMessageStream->appendMessage(GameMessage::MSG_DO_MOVETO);
        msg->appendLocationArgument(pos); lastError.clear();
    } else { lastError = "Unsupported bridge command"; }
}
}
void synchronizedHostCommand(const std::string& text){command(text);}
void hostLogicTick() {
    // Imported tech sites are absent from retail map scripts. Give the stock AI one
    // ordinary capture mission; original research, travel and capture times still apply.
    if(TheGameLogic&&worldTransportActive()&&TheGameLogic->isInGame()&&TheGameLogic->getFrame()%90==0){
        for(int slot=2;slot<ThePlayerList->getPlayerCount();++slot){auto player=ThePlayerList->getNthPlayer(slot);if(!player||player->getPlayerType()!=PLAYER_COMPUTER)continue;
            Object* target=nullptr;Object* infantry=nullptr;float nearest=FLT_MAX;
            for(auto& entry:worldSites){auto site=TheGameLogic->findObjectByID(entry.second);if(!site||site->getBodyModule()->getHealth()<=0||site->getControllingPlayer()!=ThePlayerList->getNeutralPlayer()||std::string(site->getTemplate()->getName().str()).rfind("TechOil",0)!=0)continue;
                for(auto unit=TheGameLogic->getFirstObject();unit;unit=unit->getNextObject())if(unit->getControllingPlayer()==player&&!unit->isContained()&&!unit->isDisabled()&&unit->getBodyModule()->getHealth()>0&&unit->getAI()){
                    auto power=unit->findSpecialPowerModuleInterface(SPECIAL_INFANTRY_CAPTURE_BUILDING);if(!power)power=unit->findSpecialPowerModuleInterface(SPECIAL_BLACKLOTUS_CAPTURE_BUILDING);if(!power)continue;
                    auto a=unit->getPosition(),b=site->getPosition();float distance=std::hypot(a->x-b->x,a->y-b->y);if(distance<nearest){nearest=distance;target=site;infantry=unit;}
                }
            }
            if(!target||!infantry)continue;
            auto upgrade=TheUpgradeCenter->findUpgrade("Upgrade_InfantryCaptureBuilding");
            if(upgrade&&!player->hasUpgradeComplete(upgrade)&&!player->hasUpgradeInProduction(upgrade)&&!infantry->hasSpecialPower(SPECIAL_BLACKLOTUS_CAPTURE_BUILDING)){
                for(auto building=TheGameLogic->getFirstObject();building;building=building->getNextObject())if(building->getControllingPlayer()==player&&(building->getConstructionPercent()<0||building->getConstructionPercent()>=100)&&!building->isDisabled())if(auto production=building->getProductionUpdateInterface())if(building->canProduceUpgrade(upgrade)){production->queueUpgrade(upgrade);break;}
            }
            auto power=infantry->findSpecialPowerModuleInterface(SPECIAL_INFANTRY_CAPTURE_BUILDING);if(!power)power=infantry->findSpecialPowerModuleInterface(SPECIAL_BLACKLOTUS_CAPTURE_BUILDING);
            if(power&&infantry->getAI()->isIdle()&&TheActionManager->canDoSpecialPowerAtObject(infantry,target,CMD_FROM_AI,power->getSpecialPowerTemplate(),0))infantry->doSpecialPowerAtObject(power->getSpecialPowerTemplate(),target,0);
        }
    }

    for(auto it=forcedProjectiles.begin();it!=forcedProjectiles.end();)if(!TheGameLogic->findObjectByID(static_cast<ObjectID>(*it)))it=forcedProjectiles.erase(it);else ++it;
    // Loading a new host tile must allow the original AI to retry a failed move.
    if(TheGameLogic&&worldTransportActive()&&TheGameLogic->isInGame())for(auto it=hostMoves.begin();it!=hostMoves.end();){
        auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(it->first));auto ai=o?o->getAI():nullptr;
        if(!o||!ai||o->getBodyModule()->getHealth()<=0){it=hostMoves.erase(it);continue;}
        if(auto builder=ai->getDozerAIInterface())if(builder->getCurrentTask()!=DOZER_TASK_INVALID||builder->isAnyTaskPending()){it=hostMoves.erase(it);continue;}
        auto& move=it->second;auto p=o->getPosition();auto frame=TheGameLogic->getFrame();
        if(std::hypot(p->x-move.goal.x,p->y-move.goal.y)<worldUnitsPerBlock()){it=hostMoves.erase(it);continue;}
        if(std::hypot(p->x-move.position.x,p->y-move.position.y)>worldUnitsPerBlock()*.25f){move.position=*p;move.lastProgress=frame;move.retries=0;}
        unsigned int delay=move.terrainDirty?12u:(move.revision!=terrainRevision?90u:(90u<<std::min(move.retries,2u)));
        if(frame-move.lastProgress>delay&&!ai->isAttacking()&&!o->isDisabled()&&(ai->isIdle()||ai->isDoingGroundMovement())){
            move.terrainDirty=false;move.revision=terrainRevision;move.lastProgress=frame;move.lastRetryFrame=frame;++move.retries;retryingHostMove=true;ai->aiMoveToPosition(&move.goal,CMD_FROM_PLAYER);retryingHostMove=false;
        }++it;
    }
    if(TheGameLogic&&worldTransportActive()&&TheGameLogic->isInGame())for(auto o=TheGameLogic->getFirstObject();o;o=o->getNextObject()){
        if(!isExternalObject(o->getID())&&!o->isKindOf(KINDOF_AIRCRAFT)&&!o->isKindOf(KINDOF_PROJECTILE)&&!o->isContained()&&!o->isAirborneTarget()&&!o->isUsingAirborneLocomotor()&&o->getLayer()==LAYER_GROUND){
            float floor;if(terrainHeight(o->getPosition()->x,o->getPosition()->y,floor))if(auto physics=o->getPhysics())if(physics->isMotive()){physics->setStickToGround(true);physics->setAllowToFall(false);}
        }
        if(isExternalObject(o->getID())||!o->isKindOf(KINDOF_INFANTRY)||o->isKindOf(KINDOF_PROJECTILE))continue;
        // Original meshes retain their uniform scale. Host infantry collision fits a Minecraft player doorway.
        auto& g=o->getGeometryInfo();float radius=.3f*worldUnitsPerBlock(),height=1.8f*worldUnitsPerBlock();
        if(std::fabs(g.getMajorRadius()-radius)>.001f||std::fabs(g.getMinorRadius()-radius)>.001f||std::fabs(g.getMaxHeightAbovePosition()-height)>.001f){TheAI->pathfinder()->removeUnitFromPathfindMap(o);o->setGeometryInfo(GeometryInfo(GEOMETRY_CYLINDER,true,height,radius,radius));TheAI->pathfinder()->updatePos(o,o->getPosition());if(auto ai=o->getAI())if(!ai->isIdle())TheAI->pathfinder()->updateGoal(o,ai->getGoalPosition(),o->getDestinationLayer());}
    }
}
void beforeUpdate() {
    updateStarted=timeGetTime();
    if (!enabled()) return;
    if(pointerAnchor&&!leftButtonHeld&&TheGameLogic&&TheGameLogic->getFrame()>pinnedLeftFrame)pointerAnchor=false;
    for(auto it=pointerSamples.begin();it!=pointerSamples.end();)if(timeGetTime()-it->second.stamp>250)it=pointerSamples.erase(it);else ++it;
    // A forced JVM exit does not run Java shutdown hooks. Never leave an orphan match consuming CPU.
    struct HostParent {
        HANDLE handle=nullptr;bool required=false;
        HostParent(){const char* value=std::getenv("ZHMC_PARENT_PID");if(value&&*value){auto pid=std::strtoul(value,nullptr,10);if(pid){required=true;handle=OpenProcess(SYNCHRONIZE,FALSE,static_cast<DWORD>(pid));}}}
        ~HostParent(){if(handle)CloseHandle(handle);}
        bool exited()const{return required&&(!handle||WaitForSingleObject(handle,0)==WAIT_OBJECT_0);}
    };
    static HostParent parent;
    if(parent.exited()){TheGameEngine->setQuitting(true);return;}
    if(remoteInput()&&hostAudioConfigured&&TheAudio){
        TheAudio->unmuteAudio(AudioManager::MuteAudioReason_WindowFocus);
        TheAudio->setVolume(hostMusic,(AudioAffect)(AudioAffect_Music|AudioAffect_SystemSetting));
        TheAudio->setVolume(hostSound,(AudioAffect)(AudioAffect_Sound|AudioAffect_Sound3D|AudioAffect_SystemSetting));
        TheAudio->setVolume(hostSpeech,(AudioAffect)(AudioAffect_Speech|AudioAffect_SystemSetting));
    }
    if(remoteInput()&&hostAudioConfigured&&TheAudio&&TheGlobalData->m_audioOn&&TheGameLogic&&TheGameLogic->isInGame()&&!TheGameLogic->isLoadingMap()&&TheGameLogic->getFrame()>10){
        auto prefix=factionMusicPrefix();bool playing=TheAudio->isMusicPlaying();
        bool stock=musicTrack.rfind("Game_USA_",0)==0||musicTrack.rfind("Game_CHI_",0)==0||musicTrack.rfind("Game_GLA_",0)==0;
        if(!playing||(!prefix.empty()&&stock&&musicTrack.rfind(prefix,0)!=0))nextHostMusic();
    }
    if(!lockstepActive())hostLogicTick();
    if(TheGameLogic&&(TheGameLogic->isLoadingMap()||TheGameLogic->isLoadingSave()))return;
    // Immutable, atomically published files. A per-launch UUID prevents replaying old commands.
    for (int budget = 0; budget < 32; ++budget) {
        auto path = root / "commands" / (std::to_string(consumed + 1) + ".cmd");
        std::ifstream in(path, std::ios::binary);
        if (!in) break;
        std::string text((std::istreambuf_iterator<char>(in)), {});
        ++consumed;
        if (text.size() > 65536) { lastError = "Command too large"; continue; }
        command(text);
    }
}
void afterUpdate() {
    if (!enabled() || timeGetTime() - published < 33) return;
    published = timeGetTime();
    DWORD publishStarted=published;
    publishCursor();
    if(worldTransportActive()&&TheGameLogic->isInGame()&&ThePartitionManager&&ThePlayerList&&timeGetTime()-fogPublished>=150){
        fogPublished=timeGetTime();const unsigned int size=128;float step=2*worldUnitsPerBlock();float bounds[3]={std::floor(fogFocusX/step)*step-size*step*.5f,std::floor(fogFocusY/step)*step-size*step*.5f,size*step};
        unsigned int header[3]={0x464d485a,static_cast<unsigned int>(TheGameLogic->getFrame()),size};
        std::ofstream fog(root/"fog.tmp",std::ios::binary|std::ios::trunc);fog.write(reinterpret_cast<const char*>(header),sizeof(header));fog.write(reinterpret_cast<const char*>(bounds),sizeof(bounds));
        const unsigned char opacity[3]={static_cast<unsigned char>(255-TheGlobalData->m_clearAlpha),static_cast<unsigned char>(255-TheGlobalData->m_fogAlpha),static_cast<unsigned char>(255-TheGlobalData->m_shroudAlpha)};fog.write(reinterpret_cast<const char*>(opacity),sizeof(opacity));
        auto player=ThePlayerList->getLocalPlayer();for(unsigned int y=0;y<size;y++)for(unsigned int x=0;x<size;x++){Coord3D p={bounds[0]+(x+.5f)*step,bounds[1]+(y+.5f)*step,0};auto status=player?ThePartitionManager->getShroudStatusForPlayer(player->getPlayerIndex(),&p):CELLSHROUD_SHROUDED;unsigned char value=status==CELLSHROUD_CLEAR?0:status==CELLSHROUD_FOGGED?160:255;fog.write(reinterpret_cast<const char*>(&value),1);}
        fog.close();MoveFileExW((root/"fog.tmp").c_str(),(root/"fog.bin").c_str(),MOVEFILE_REPLACE_EXISTING);
    }
    std::ostringstream out;out<<std::setprecision(9);
    GameWindow* underPointer = TheWindowManager ? TheWindowManager->getWindowUnderCursor(pointerX, pointerY, false) : nullptr;
    if (TheWindowManager) {
        ICoord2D pos = {pointerX, pointerY}; GameWindow* tooltip = nullptr;
        GameWindow* probe = TheWindowManager->findWindowUnderMouse(tooltip, &pos, WIN_STATUS_ABOVE, WIN_STATUS_HIDDEN);
        if (!probe) probe = TheWindowManager->findWindowUnderMouse(tooltip, &pos, WIN_STATUS_NONE, WIN_STATUS_ABOVE | WIN_STATUS_BELOW | WIN_STATUS_HIDDEN);
        if (!probe) probe = TheWindowManager->findWindowUnderMouse(tooltip, &pos, WIN_STATUS_BELOW, WIN_STATUS_HIDDEN);
        probeWindow = probe ? probe->winGetWindowId() : -1; probeTooltip = tooltip ? tooltip->winGetWindowId() : -1;
    }
    const CommandButton* pointerCommand=windowCommand(underPointer);
    static unsigned int externalBlocked=0,nativeBlocked=0,terrainMismatches=0;
    static unsigned long long diagnosticRevision=~0ull;
    // Terrain diagnostics stay valid until the next terrain edit; avoid scanning every column at UI frequency.
    if (std::getenv("ZHMC_TEST")&&diagnosticRevision!=terrainRevision&&TheGameLogic->isInGame()&&!TheGameLogic->isInShellGame()) {
        diagnosticRevision=terrainRevision;externalBlocked=nativeBlocked=terrainMismatches=0;
        for (const auto& [key, column] : columns) {
            int x = static_cast<unsigned int>(key >> 32), y = static_cast<unsigned int>(key);
            PathfindCell* cell = TheAI->pathfinder()->getCell(LAYER_GROUND, x, y);
            if (column.type == 2 || column.type == 6) ++externalBlocked;
            if (cell && (cell->getType() == PathfindCell::CELL_CLIFF || cell->getType() == PathfindCell::CELL_IMPASSABLE)) ++nativeBlocked;
            if (!cell || (cell->getType() != column.type && cell->getType() != PathfindCell::CELL_OBSTACLE && cell->getType() != PathfindCell::CELL_IMPASSABLE)) ++terrainMismatches;
        }
    }
    const ICoord2D* extent = TheGameLogic->isInGame() && !TheGameLogic->isInShellGame() ? TheAI->pathfinder()->getExtent() : nullptr;
    out << "{\"protocol\":1,\"engine\":\"original-cpp\",\"pid\":" << GetCurrentProcessId()
        << ",\"frame\":" << TheGameLogic->getFrame()
        << ",\"inGame\":" << (TheGameLogic->isInGame() && !TheGameLogic->isInShellGame() && !TheGameLogic->isLoadingMap() && !TheGameLogic->isLoadingSave() ? "true" : "false")
        << ",\"consumed\":" << consumed << ",\"error\":" << json(lastError.c_str())
        << ",\"gamePaused\":" << (TheGameLogic->isGamePaused()?"true":"false")
        << ",\"timeFrozen\":" << (TheGameEngine->isTimeFrozen()?"true":"false")
        << ",\"terrainRevision\":" << terrainRevision << ",\"terrainColumns\":" << columns.size()
        << ",\"localCash\":" << (ThePlayerList->getLocalPlayer() ? ThePlayerList->getLocalPlayer()->getMoney()->countMoney() : 0)
        << ",\"superweaponLimit\":" << (TheSkirmishGameInfo?TheSkirmishGameInfo->getSuperweaponRestriction():hostSuperweaponLimit)
        << ",\"generalRank\":" << (ThePlayerList->getLocalPlayer()?ThePlayerList->getLocalPlayer()->getRankLevel():0)
        << ",\"generalExperience\":" << (ThePlayerList->getLocalPlayer()?ThePlayerList->getLocalPlayer()->getSkillPoints():0)
        << ",\"localPlayerColor\":" << (ThePlayerList->getLocalPlayer() ? (unsigned int)ThePlayerList->getLocalPlayer()->getPlayerColor() : 0)
        << ",\"localPlayerIndex\":" << (ThePlayerList->getLocalPlayer() ? ThePlayerList->getLocalPlayer()->getPlayerIndex() : -1)
        << ",\"terrainExtentX\":" << (extent ? extent->x : 0) << ",\"terrainExtentY\":" << (extent ? extent->y : 0)
        << ",\"rawDown\":" << rawDown << ",\"rawUp\":" << rawUp << ",\"productionMessages\":" << productionMessages << ",\"guiCanMake\":" << guiCanMake
        << ",\"stopMessages\":" << stopMessages
        << ",\"attackMessages\":" << attackMessages << ",\"forceAttack\":" << (TheInGameUI && TheInGameUI->isInForceAttackMode()?"true":"false")
        << ",\"engineUpdateMs\":" << (timeGetTime()-updateStarted) << ",\"guiCaptureMs\":" << guiCostMs()
        << ",\"pacerFps\":" << TheFramePacer->getUpdateFps() << ",\"renderLimit\":" << TheFramePacer->getActualFramesPerSecondLimit()
        << ",\"logicLimit\":" << TheFramePacer->getActualLogicTimeScaleFps()
        << ",\"publishMs\":" << lastPublishCost
        << ",\"uiSelected\":" << (TheInGameUI ? TheInGameUI->getSelectCount() : -1)
        << ",\"pendingCommand\":" << json(TheInGameUI && TheInGameUI->getGUICommand() ? TheInGameUI->getGUICommand()->getName().str() : "")
        << ",\"pendingBuild\":" << (TheInGameUI && TheInGameUI->getPendingPlaceType() ? "true" : "false")
        << ",\"pendingBuildType\":" << json(TheInGameUI && TheInGameUI->getPendingPlaceType() ? TheInGameUI->getPendingPlaceType()->getName().str() : "")
        << ",\"waypointMode\":" << (TheInGameUI && TheInGameUI->isInWaypointMode()?"true":"false")
        << ",\"uiInput\":" << (TheInGameUI && TheInGameUI->getInputEnabled() ? "true" : "false")
        << ",\"shellActive\":" << (TheShell && TheShell->isShellActive() ? "true" : "false")
        << ",\"mouseLocked\":" << (TheTacticalView && TheTacticalView->isMouseLocked() ? "true" : "false")
        << ",\"mouseX\":" << (TheMouse ? TheMouse->getMouseStatus()->pos.x : -1) << ",\"mouseY\":" << (TheMouse ? TheMouse->getMouseStatus()->pos.y : -1)
        << ",\"mouseCursor\":" << (TheMouse ? TheMouse->getMouseCursor() : -1)
        << ",\"pointerWindow\":" << (underPointer ? underPointer->winGetWindowId() : -1)
        << ",\"pointerCommand\":" << json(pointerCommand ? pointerCommand->getName().str() : "")
        << ",\"downWindow\":" << downWindow << ",\"upWindow\":" << upWindow << ",\"downState\":" << downState << ",\"upState\":" << upState
        << ",\"downPriority\":" << downPriority << ",\"upPriority\":" << upPriority
        << ",\"rawX\":" << rawX << ",\"rawY\":" << rawY << ",\"rawLocked\":" << (rawLocked ? "true" : "false")
        << ",\"probeWindow\":" << probeWindow << ",\"probeTooltip\":" << probeTooltip
        << ",\"externalBlocked\":" << externalBlocked << ",\"nativeBlocked\":" << nativeBlocked << ",\"terrainMismatches\":" << terrainMismatches
        << ",\"cameraSerial\":"<<cameraSerial<<",\"cameraTargetX\":"<<cameraTarget.x<<",\"cameraTargetY\":"<<cameraTarget.y
        << ",\"rotationSerial\":"<<rotationSerial<<",\"cameraAngle\":"<<cameraAngle
        << ",\"scrollX\":"<<scrollX<<",\"scrollY\":"<<scrollY<<",\"scrolling\":"<<(TheInGameUI&&TheInGameUI->isScrolling()?"true":"false");
    if(std::getenv("ZHMC_TEST")){auto o=TheGameLogic->findObjectByID((ObjectID)worldObject);auto d=o?o->getDrawable():nullptr;out<<",\"pointerObject\":"<<worldObject<<",\"pointerHidden\":"<<(d&&d->isDrawableEffectivelyHidden())<<",\"pointerShrouded\":"<<(d&&d->getFullyObscuredByShroud())<<",\"pointerMasked\":"<<(o&&o->getStatusBits().test(OBJECT_STATUS_MASKED))<<",\"pointerAttackResult\":"<<(o?TheInGameUI->getCanSelectedObjectsAttack(InGameUI::ACTIONTYPE_ATTACK_OBJECT,o,InGameUI::SELECTION_ANY,false):-1);}
    out<<",\"pagedWorld\":"<<(pagedWorld()?"true":"false")<<",\"pathPages\":"<<pathPageCount()<<",\"partitionPages\":"<<partitionPageCount()<<",\"pathSearchLimits\":"<<pathSearchLimitCount();
    radarState(out);cursorState(out);sceneState(out);guiClockState(out);lockstepState(out);
    int buildings[MAX_PLAYER_COUNT]={};for(auto object=TheGameLogic->getFirstObject();object;object=object->getNextObject())if(object->isKindOf(KINDOF_STRUCTURE)&&object->isKindOf(KINDOF_MP_COUNT_FOR_VICTORY)&&object->getBodyModule()->getHealth()>0){auto p=object->getControllingPlayer();if(p&&p->getPlayerIndex()>=0&&p->getPlayerIndex()<MAX_PLAYER_COUNT)++buildings[p->getPlayerIndex()];}
    out<<",\"armyStatus\":[";bool armyComma=false;for(int i=2;i<ThePlayerList->getPlayerCount();++i)if(auto p=ThePlayerList->getNthPlayer(i)){if(armyComma)out<<',';armyComma=true;out<<"{\"player\":"<<i<<",\"rank\":"<<p->getRankLevel()<<",\"experience\":"<<p->getSkillPoints()<<",\"buildings\":"<<buildings[i]<<'}';}out<<']';
    out<<",\"musicTrack\":"<<json(musicTrack.c_str())<<",\"expectedMusicVolume\":"<<expectedMusicVolume<<",\"actualMusicVolume\":"<<actualMusicVolume<<",\"hostAudioConfigured\":"<<(hostAudioConfigured?"true":"false")<<",\"musicPlaying\":"<<(TheAudio&&TheAudio->isMusicPlaying()?"true":"false")<<",\"musicVolume\":"<<(TheAudio?TheAudio->getVolume((AudioAffect)(AudioAffect_Music|AudioAffect_SystemSetting)):0)<<",\"soundVolume\":"<<(TheAudio?TheAudio->getVolume((AudioAffect)(AudioAffect_Sound|AudioAffect_SystemSetting)):0)<<",\"speechVolume\":"<<(TheAudio?TheAudio->getVolume((AudioAffect)(AudioAffect_Speech|AudioAffect_SystemSetting)):0);
    out<<",\"audioEnabled\":"<<(TheGlobalData->m_audioOn?"true":"false")<<",\"audio2DSamples\":"<<(TheAudio?TheAudio->getNum2DSamples():0)<<",\"audio3DSamples\":"<<(TheAudio?TheAudio->getNum3DSamples():0)<<",\"audio2DUsed\":"<<(TheAudio?TheAudio->getNum2DSamples()-TheAudio->getNumAvailable2DSamples():0)<<",\"audio3DUsed\":"<<(TheAudio?TheAudio->getNum3DSamples()-TheAudio->getNumAvailable3DSamples():0);
    out<<",\"specialPowers\":[";bool powerComma=false;
    for(auto object=TheGameLogic->getFirstObject();object;object=object->getNextObject())if(object->isLocallyControlled())for(auto modules=object->getBehaviorModules();modules&&*modules;++modules)if(auto power=(*modules)->getSpecialPower()){
        if(powerComma)out<<',';powerComma=true;out<<"{\"object\":"<<object->getID()<<",\"name\":"<<json(power->getPowerName().str())<<",\"readyFrame\":"<<power->getReadyFrame()<<",\"ready\":"<<(power->isReady()?"true":"false")<<'}';
    }out<<']';
    out<<",\"placementAngle\":"<<(TheInGameUI?TheInGameUI->getPlacementAngle():0)<<",\"placementAnchored\":"<<(TheInGameUI&&TheInGameUI->isPlacementAnchored()?"true":"false");
    out<<",\"resourceTemplate\":";
    auto resourceTemplate=TheThingFactory->findTemplate("SupplyDock",false);
    if(resourceTemplate){auto& geometry=resourceTemplate->getTemplateGeometryInfo();out<<"{\"name\":\"SupplyDock\",\"major\":"<<geometry.getMajorRadius()<<",\"minor\":"<<geometry.getMinorRadius()<<",\"height\":"<<geometry.getMaxHeightAbovePosition()<<'}';}else out<<"null";
    out<<",\"resources\":[";bool resourceComma=false;
    for(auto& site:resourceSites){auto object=TheGameLogic->findObjectByID(site.second);auto dock=supplyDock(object);if(resourceComma)out<<',';resourceComma=true;out<<"{\"key\":"<<json(site.first.c_str())<<",\"id\":"<<site.second<<",\"cash\":"<<(dock?dock->getBoxesStored()*TheGlobalData->m_baseValuePerSupplyBox:0)<<'}';}out<<']';
    out << ",\"userData\":" << json(TheGlobalData->getPath_UserData().str()) << ",\"factions\":[";
    for (int i = 0; i < ThePlayerTemplateStore->getPlayerTemplateCount(); ++i) {
        if (i) out << ',';
        out << json(ThePlayerTemplateStore->getNthPlayerTemplate(i)->getName().str());
    }
    out<<"],\"playableFactions\":[";bool playableComma=false;for(int i=0;i<ThePlayerTemplateStore->getPlayerTemplateCount();++i){auto t=ThePlayerTemplateStore->getNthPlayerTemplate(i);if(!t->isPlayableSide()||t->getSide()=="Boss")continue;if(playableComma)out<<',';playableComma=true;out<<json(t->getName().str());}
    out<<"],\"factionNames\":{";bool labelComma=false;for(int i=0;i<ThePlayerTemplateStore->getPlayerTemplateCount();++i){auto t=ThePlayerTemplateStore->getNthPlayerTemplate(i);if(!t->isPlayableSide()||t->getSide()=="Boss")continue;auto label=t->getDisplayName();int size=WideCharToMultiByte(CP_UTF8,0,reinterpret_cast<const wchar_t*>(label.str()),-1,nullptr,0,nullptr,nullptr);std::string text;if(size>0){text.resize(size);WideCharToMultiByte(CP_UTF8,0,reinterpret_cast<const wchar_t*>(label.str()),-1,text.data(),size,nullptr,nullptr);text.pop_back();}if(labelComma)out<<',';labelComma=true;out<<json(t->getName().str())<<':'<<json(text.c_str(),true);}
    out<<"},\"startRadii\":{";bool areaComma=false;for(int i=0;i<ThePlayerTemplateStore->getPlayerTemplateCount();++i){auto t=ThePlayerTemplateStore->getNthPlayerTemplate(i);if(!t->isPlayableSide()||t->getSide()=="Boss")continue;auto b=TheThingFactory->findTemplate(t->getStartingBuilding(),false);if(!b)continue;auto& g=b->getTemplateGeometryInfo();int r=std::min(24,(int)std::ceil(std::hypot(g.getMajorRadius(),g.getMinorRadius())/worldUnitsPerBlock())+3);if(areaComma)out<<',';areaComma=true;out<<json(t->getName().str())<<':'<<r;}

    out<<"},\"colors\":[";for(int i=0;i<TheMultiplayerSettings->getNumColors();++i){if(i)out<<',';auto c=TheMultiplayerSettings->getColor(i);out<<"{\"id\":"<<i<<",\"label\":"<<json(c->getTooltipName().str())<<",\"color\":"<<(unsigned int)c->getColor()<<'}';}out<<"],\"teamColor\":"<<(TheSkirmishGameInfo?TheSkirmishGameInfo->getConstSlot(0)->getColor():0);
    out << ",\"maps\":[";
    if(cachedMaps.empty()){FilenameList nativeMaps;TheArchiveFileSystem->getFileListInDirectory("Maps", "", "*.map", nativeMaps, true);for(const auto& map:nativeMaps)cachedMaps.emplace_back(map.str());}
    bool comma = false;
    for (const auto& map : cachedMaps) { if (comma) out << ','; comma = true; out << json(map.c_str()); }
    out << "],\"sites\":[";comma=false;for(auto& site:worldSites){if(comma)out<<',';comma=true;out<<"{\"key\":"<<json(site.first.c_str())<<",\"id\":"<<site.second<<",\"cash\":-1}";}
    out << "],\"objects\":["; comma = false;
    for (Object* object = TheGameLogic->getFirstObject(); object; object = object->getNextObject()) {
        if (comma) out << ','; comma = true;
        const Coord3D* p = object->getPosition();
        const Player* player = object->getControllingPlayer();
        out << "{\"id\":" << object->getID() << ",\"template\":" << json(object->getTemplate()->getName().str())
            << ",\"x\":" << p->x << ",\"y\":" << p->y << ",\"z\":" << p->z
            << ",\"owner\":" << (player ? player->getPlayerIndex() : -1)
            << ",\"health\":" << object->getBodyModule()->getHealth()
            << ",\"maxHealth\":" << object->getBodyModule()->getMaxHealth()
            << ",\"contained\":" << (object->isContained()?"true":"false")
            << ",\"constructionPercent\":" << object->getConstructionPercent()
            << ",\"orientation\":"<<object->getOrientation()<<",\"weaponRange\":"<<(object->getCurrentWeapon()?object->getCurrentWeapon()->getAttackRange(object):0);
        auto moving=object->getAI();auto hostMove=hostMoves.find(object->getID());const Coord3D* goal=hostMove!=hostMoves.end()?&hostMove->second.goal:moving?moving->getGoalPosition():nullptr;
        out<<",\"vehicle\":"<<((object->isKindOf(KINDOF_VEHICLE)||object->isKindOf(KINDOF_AIRCRAFT))?"true":"false")<<",\"aircraft\":"<<(object->isKindOf(KINDOF_AIRCRAFT)?"true":"false")<<",\"structure\":"<<(object->isKindOf(KINDOF_STRUCTURE)?"true":"false")<<",\"groundMovement\":"<<(hostMove!=hostMoves.end()||(moving&&!moving->isIdle()&&moving->isDoingGroundMovement())?"true":"false");
        out<<",\"moveRetries\":"<<(hostMove!=hostMoves.end()?hostMove->second.retries:0);
        out<<",\"moveRetryFrame\":"<<(hostMove!=hostMoves.end()?hostMove->second.lastRetryFrame:0);
        if(goal)out<<",\"goalX\":"<<goal->x<<",\"goalY\":"<<goal->y<<",\"goalZ\":"<<goal->z;
        if(std::getenv("ZHMC_TEST")){Real yaw=0,pitch=0;if(auto ai=object->getAI())if(ai->getTurretRotAndPitch(TURRET_MAIN,&yaw,&pitch))out<<",\"turretYaw\":"<<yaw<<",\"turretPitch\":"<<pitch;out<<",\"underConstruction\":"<<(object->getStatusBits().test(OBJECT_STATUS_UNDER_CONSTRUCTION)?"true":"false");}
        if(std::getenv("ZHMC_TEST")){auto ai=object->getAIUpdateInterface();auto supply=ai?ai->getSupplyTruckAIInterface():nullptr;out<<",\"weaponShot\":"<<(object->getCurrentWeapon()?object->getCurrentWeapon()->getLastShotFrame():0)<<",\"weaponStatus\":"<<(object->getCurrentWeapon()?object->getCurrentWeapon()->getStatus():-1)<<",\"aiID\":"<<(ai?ai->getCurrentStateID():-1)<<",\"ignoredObstacle\":"<<(ai?ai->getIgnoredObstacleID():0)<<",\"adjustsFlightDestination\":"<<(ai&&ai->isAircraftThatAdjustsDestination()?"true":"false")<<",\"aiState\":"<<json(ai?ai->getCurrentStateName().str():"")<<",\"disabled\":"<<(object->isDisabled()?"true":"false")<<",\"supplyBoxes\":"<<(supply?supply->getNumberBoxes():-1);}
        auto key=hostMobKeys.find(object->getID());auto& geometry=object->getGeometryInfo();out<<",\"externalKey\":"<<json(key==hostMobKeys.end()?"":key->second.c_str())<<",\"cloaked\":"<<(object->getStatusBits().test(OBJECT_STATUS_STEALTHED)&&!object->getStatusBits().test(OBJECT_STATUS_DETECTED)?"true":"false")<<",\"geometryMajor\":"<<geometry.getMajorRadius()<<",\"geometryMinor\":"<<geometry.getMinorRadius()<<",\"geometryHeight\":"<<geometry.getMaxHeightAbovePosition();
        out<<'}';
    }
    out<<"],\"healthUnitsPerPoint\":"<<healthUnitsPerPoint()<<",\"combatEvents\":[";comma=false;for(auto& e:hostCombatEvents){if(comma)out<<',';comma=true;out<<"{\"sequence\":"<<e.sequence<<",\"target\":"<<json(e.target.c_str())<<",\"source\":"<<e.source<<",\"amount\":"<<e.amount<<",\"x\":"<<e.x<<",\"y\":"<<e.y<<",\"z\":"<<e.z<<",\"radius\":"<<e.radius<<",\"damageType\":"<<e.damageType<<",\"disabledFrames\":"<<e.disabledFrames<<",\"forcedAttack\":"<<(e.forcedAttack?"true":"false")<<",\"explosive\":"<<(e.damageType==DAMAGE_EXPLOSION?"true":"false")<<'}';}
    out<<"]";healthBarState(out);out << ",\"selected\":[";comma=false;
    for (Object* object=TheGameLogic->getFirstObject();object;object=object->getNextObject()) {
        auto draw=object->getDrawable();if(!draw||!TheInGameUI->isDrawableSelected(draw->getID()))continue;
        if(comma)out<<',';comma=true;out<<object->getID();
    }
    out << "],\"controls\":[";comma=false;
    controls(out,TheWindowManager->winGetWindowList(),comma);
    out<<"]";auto cameo=TheWindowManager->winGetWindowFromId(nullptr,TheNameKeyGenerator->nameToKey("ControlBar.wnd:CameoWindow"));if(cameo&&!cameo->winIsHidden()){int x,y,w,h;cameo->winGetScreenPosition(&x,&y);cameo->winGetSize(&w,&h);out<<",\"portrait\":{\"id\":0,\"x\":"<<x<<",\"y\":"<<y<<",\"width\":"<<w<<",\"height\":"<<h<<'}';}
    if(std::getenv("ZHMC_TEST")){out<<",\"selectionListResult\":"<<json(selectionListResult.c_str())<<",\"selectionRegionProbe\":"<<json(selectionRegionProbe.c_str())<<",\"selectionProbes\":[";bool c=false;for(auto p:selectionProbes){if(c)out<<',';c=true;out<<'['<<p.first<<','<<p.second<<']';}out<<']';}
    out << "}";
    auto temporary = root / "state.tmp";
    { std::ofstream file(temporary, std::ios::binary | std::ios::trunc); file << out.str(); }
    // Reader opens FILE_SHARE_DELETE; failure leaves the previous complete snapshot valid.
    MoveFileExW(temporary.c_str(), (root / "state.json").c_str(), MOVEFILE_REPLACE_EXISTING);
    lastPublishCost=timeGetTime()-publishStarted;
}
bool terrainHeight(float x, float y, float& height) {
    auto it = columns.find(cellKey(static_cast<int>(std::floor(x / worldUnitsPerBlock())), static_cast<int>(std::floor(y / worldUnitsPerBlock()))));
    if (it == columns.end()) return false;
    height = it->second.height; return true;
}
bool terrainCellLoaded(int cellX,int cellY){
    int minX=(int)std::floor(cellX*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock()),minY=(int)std::floor(cellY*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock());
    int maxX=(int)std::ceil((cellX+1)*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock())-1,maxY=(int)std::ceil((cellY+1)*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock())-1;
    for(int x=minX;x<=maxX;++x)for(int y=minY;y<=maxY;++y)if(columns.find(cellKey(x,y))==columns.end())return false;return true;
}
int terrainType(int cellX,int cellY) {
    if(!worldTransportActive())return -1;
    int result=0,minX=(int)std::floor(cellX*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock()),minY=(int)std::floor(cellY*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock());
    int maxX=(int)std::ceil((cellX+1)*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock())-1,maxY=(int)std::ceil((cellY+1)*PATHFIND_CELL_SIZE_F/worldUnitsPerBlock())-1;
    for(int x=minX;x<=maxX;++x)for(int y=minY;y<=maxY;++y){auto c=columns.find(cellKey(x,y));if(c==columns.end()||c->second.type==6)return 6;if(c->second.type==2)result=2;else if(c->second.type==1&&result!=2)result=1;}
    return result;
}

bool buildSurfaceAllowed(float x,float y,float requiredHeight){
    if(!worldTransportActive())return true;auto it=columns.find(cellKey(static_cast<int>(std::floor(x/worldUnitsPerBlock())),static_cast<int>(std::floor(y/worldUnitsPerBlock()))));
    return it!=columns.end()&&it->second.type==0&&it->second.clearance>=requiredHeight;
}
bool hostPathEdgeAllowed(int ax,int ay,int bx,int by){
    if(!worldTransportActive())return true;if(ax==bx&&ay==by)return true;
    auto height=[](int x,int y,float& h){return terrainHeight((x+.5f)*PATHFIND_CELL_SIZE_F,(y+.5f)*PATHFIND_CELL_SIZE_F,h);};
    float a,b;if(!height(ax,ay,a)||!height(bx,by,b)||std::fabs(a-b)>worldUnitsPerBlock()*1.001f)return false;
    // A diagonal must also fit through both cardinal edges; it cannot cut a tall corner.
    if(ax!=bx&&ay!=by){float c,d;if(!height(ax,by,c)||!height(bx,ay,d))return false;float step=worldUnitsPerBlock()*1.001f;if(std::fabs(a-c)>step||std::fabs(c-b)>step||std::fabs(a-d)>step||std::fabs(d-b)>step)return false;}
    return true;
}
bool hostTerrainKnown(const Coord3D* p){return p&&worldTransportActive()&&columns.count(cellKey(static_cast<int>(std::floor(p->x/worldUnitsPerBlock())),static_cast<int>(std::floor(p->y/worldUnitsPerBlock()))))!=0;}
bool hostPowerTarget(const Coord3D* p){return p&&worldTransportActive()&&std::isfinite(p->x)&&std::isfinite(p->y)&&std::isfinite(p->z)&&std::fabs(p->x)<=worldUnitsPerBlock()*60000000.f&&std::fabs(p->y)<=worldUnitsPerBlock()*60000000.f;}
bool hostUnshroudedTarget(const Coord3D* p){return !hostFogEnabled&&hostTerrainKnown(p);}
bool hostLineOfSight(const Coord3D& a,const Coord3D& b,bool& clear){
    if(!worldTransportActive())return false;clear=true;
    const int steps=std::max(1,static_cast<int>(std::ceil(std::max(std::fabs(b.x-a.x),std::fabs(b.y-a.y))/2.f)));
    for(int i=1;i<steps;++i){float t=static_cast<float>(i)/steps,x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t,z=a.z+(b.z-a.z)*t;
        auto it=columns.find(cellKey(static_cast<int>(std::floor(x/worldUnitsPerBlock())),static_cast<int>(std::floor(y/worldUnitsPerBlock()))));
        if(it==columns.end()){clear=false;return true;}
        if(z<it->second.height-.5f){clear=false;return true;}
        for(auto span:it->second.obstacles)if(z>=span.first&&z<=span.second){clear=false;return true;}
    }
    return true;
}
bool rightHeld(){return rightButtonHeld;}
bool remoteInput() { return GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0) > 0; }
bool hasWorldPointer() { return worldPointer; }
Drawable* worldPointerDrawable() { auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(worldObject));return object?object->getDrawable():nullptr; }
bool worldPointerPosition(Coord3D* position) { if(!worldPointer||!position)return false;*position=worldPosition;return true; }
static bool pinnedWorldSample(const ICoord2D* screen){return screen&&pointerAnchor&&TheGameLogic&&(leftButtonHeld||TheGameLogic->getFrame()<=pinnedLeftFrame)&&screen->x==anchorScreen.x&&screen->y==anchorScreen.y;}
Drawable* worldPointerDrawable(const ICoord2D* screen,int pickType){
    if(!screen)return nullptr;
    auto sample=pointerSamples.find(pointerKey(screen->x,screen->y));if(!pinnedWorldSample(screen)&&sample==pointerSamples.end())return nullptr;
    // Zero is a real captured target: clicking empty ground must stay a move,
    // even when a later hover at the same pixel points at a building or unit.
    unsigned int picked=pinnedWorldSample(screen)?pinnedLeftObject:sample->second.object;
    auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(picked));auto draw=object?object->getDrawable():nullptr;if(!draw||(!isExternalObject(picked)&&!hostSupplyTarget(object)&&(draw->isDrawableEffectivelyHidden()||draw->getFullyObscuredByShroud())))return nullptr;
    return draw;
}
bool worldPointerPosition(const ICoord2D* screen,Coord3D* position){
    if(!screen||!position)return false;
    if(pinnedWorldSample(screen)){*position=anchorPosition;return true;}
    if(worldPointer&&screen->x==pointerX&&screen->y==pointerY)return worldPointerPosition(position);
    auto sample=pointerSamples.find(pointerKey(screen->x,screen->y));if(sample!=pointerSamples.end()){*position=sample->second.position;return true;}return false;
}
bool hostDriverCanFire(const Object* object){auto it=object?drivenVehicles.find(object->getID()):drivenVehicles.end();return it==drivenVehicles.end()||it->second.fire;}
bool hostSupplyTarget(const Object* object){if(!object||!worldTransportActive())return false;for(auto& site:resourceSites)if(site.second==object->getID())return true;return false;}
void hostCameraBasis(Coord3D& left,Coord3D& up){left=hostLeft;up=hostUp;}
bool hostAudioView(Coord3D& pivot,Coord3D& eye,Coord3D& facing){
    if(!hasHostView())return false;ICoord2D center={static_cast<int>(TheDisplay->getWidth()/2),static_cast<int>(TheDisplay->getHeight()/2)};
    if(!hostScreenToTerrain(&center,&pivot))return false;eye=hostEye;facing=hostForward;facing.z=0;
    float length=std::hypot(facing.x,facing.y);if(length>.0001f){facing.x/=length;facing.y/=length;}return true;
}
bool hostScreenToTerrain(const ICoord2D* screen,Coord3D* position){
    if(worldPointerPosition(screen,position))return true;
    if(!screen||!position||!hasHostView())return false;
    // The host can be below native z=0. Intersecting that fixed plane would be behind
    // the camera and must never fall back to the stock map's unrelated camera.
    float sx=-(2.f*screen->x/TheDisplay->getWidth()-1)*hostTan*hostAspect,sy=(1-2.f*screen->y/TheDisplay->getHeight())*hostTan;
    Coord3D ray={hostForward.x+hostLeft.x*sx+hostUp.x*sy,hostForward.y+hostLeft.y*sx+hostUp.y*sy,hostForward.z+hostLeft.z*sx+hostUp.z*sy};
    float length=std::sqrt(ray.x*ray.x+ray.y*ray.y+ray.z*ray.z);if(length<.001f)return false;
    float step=worldUnitsPerBlock()*.25f/length;
    for(float t=0;t<=step*1024;t+=step){
        Coord3D p={hostEye.x+ray.x*t,hostEye.y+ray.y*t,hostEye.z+ray.z*t};float height;
        if(!terrainHeight(p.x,p.y,height)||p.z>height)continue;
        if(!hostScreenToWorld(screen,position,height))return false;
        float actual;if(terrainHeight(position->x,position->y,actual)&&std::fabs(actual-height)<.01f)return true;
    }return false;
}
bool popKey(unsigned char& code, bool& down, unsigned long& timestamp) {
    if (keys.empty()) return false;
    auto key = keys.front(); keys.pop_front();
    code = key.code; down = key.down; timestamp = key.timestamp; return true;
}
void traceMessage(int type) {
    if (type == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN) ++rawDown;
    if (type == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP) ++rawUp;
    if (type == GameMessage::MSG_QUEUE_UNIT_CREATE) ++productionMessages;
    if (type == GameMessage::MSG_DO_STOP) ++stopMessages;
    if (type == GameMessage::MSG_DO_ATTACK_OBJECT || type == GameMessage::MSG_DO_FORCE_ATTACK_OBJECT || type == GameMessage::MSG_DO_FORCE_ATTACK_GROUND) ++attackMessages;
}
void traceGuiProduction(int canMake) { guiCanMake = canMake; }
void traceWindow(int message, int id, unsigned int state) {
    if (message == GWM_LEFT_DOWN) { downWindow = id; downState = state; }
    if (message == GWM_LEFT_UP) { upWindow = id; upState = state; }
}
void traceDisposition(int type, unsigned int priority, int disposition) {
    if (disposition != DESTROY_MESSAGE) return;
    if (type == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN) downPriority = priority;
    if (type == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP) upPriority = priority;
}
void traceRawPointer(int type, int x, int y, bool locked) {
    if (type != GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN && type != GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP) return;
    rawX = x; rawY = y; rawLocked = locked;
}
bool hasHostView(){return hostView&&worldInterfaceActive();}
bool hostScreenToWorld(const ICoord2D* screen,Coord3D* p,float z){
    if(!screen||!p||!hasHostView())return false;
    float x=-(2.f*screen->x/TheDisplay->getWidth()-1)*hostTan*hostAspect,y=(1-2.f*screen->y/TheDisplay->getHeight())*hostTan;
    Coord3D ray={hostForward.x+hostLeft.x*x+hostUp.x*y,hostForward.y+hostLeft.y*x+hostUp.y*y,hostForward.z+hostLeft.z*x+hostUp.z*y};
    if(std::fabs(ray.z)<1e-5f)return false;float distance=(z-hostEye.z)/ray.z;if(distance<0)return false;
    p->x=hostEye.x+distance*ray.x;p->y=hostEye.y+distance*ray.y;p->z=z;return true;
}
void hostAngle(float radians){if(!hasHostView())return;if(!angleBaselineReady){angleBaseline=TheTacticalView->getAngle();angleBaselineReady=true;}cameraAngle=radians-angleBaseline;++rotationSerial;}
bool hostLookAt(const Coord3D* p){if(!p||!hasHostView())return false;cameraTarget=*p;++cameraSerial;return true;}
bool hostScroll(const Coord2D* delta){
    if(!hasHostView())return false;if(!delta)return true;
    // Original LookAtTranslator owns rates, anchor, input modes and direction.
    // Use its 250-pixel scroll scale with the host camera's view plane.
    float x=delta->x*180.f/TheDisplay->getWidth()*hostTan*hostAspect,y=delta->y*180.f/TheDisplay->getHeight()*hostTan;
    float horizontal=std::hypot(hostForward.x,hostForward.y);
    if(horizontal>.001f){scrollX+=-hostLeft.x*x-hostForward.x/horizontal*y;scrollY+=-hostLeft.y*x-hostForward.y/horizontal*y;}
    return true;
}
int hostWorldToScreen(const Coord3D* p,ICoord2D* screen) {
    if(!p||!screen||!hasHostView())return 2;
    float dx=p->x-hostEye.x,dy=p->y-hostEye.y,dz=p->z-hostEye.z;
    float depth=dx*hostForward.x+dy*hostForward.y+dz*hostForward.z;
    if(depth<=.01f)return 2;
    float x=(1-(dx*hostLeft.x+dy*hostLeft.y+dz*hostLeft.z)/depth/hostTan/hostAspect)*TheDisplay->getWidth()/2;
    float y=(1-(dx*hostUp.x+dy*hostUp.y+dz*hostUp.z)/depth/hostTan)*TheDisplay->getHeight()/2;
    screen->x=static_cast<int>(std::clamp(x,-100000.f,100000.f));screen->y=static_cast<int>(std::clamp(y,-100000.f,100000.f));
    overheadProjection(p,screen);
    return x>=0&&y>=0&&x<TheDisplay->getWidth()&&y<TheDisplay->getHeight()?0:1;
}
}


namespace zhmc {
void selectionListProbe(unsigned int id,int reason){selectionListResult=std::to_string(id)+","+std::to_string(reason);}
void hostSelectionRegion(bool point,unsigned int drawable,int width,int height,int matched){std::ostringstream s;s<<point<<","<<drawable<<","<<width<<","<<height<<","<<matched;selectionRegionProbe=s.str();}
void selectionProbe(unsigned int id,int reason){if(!std::getenv("ZHMC_TEST"))return;selectionProbes.emplace_back(id,reason);while(selectionProbes.size()>32)selectionProbes.pop_front();}
std::string hostMobLabel(unsigned int){return "";}
bool isExternalObject(unsigned int id){return hostMobKeys.count(id)!=0;}
bool externalDamage(Object* object,DamageInfo* damage){auto key=hostMobKeys.find(object->getID());if(key==hostMobKeys.end())return false;if(damage->in.m_damageType==DAMAGE_SUBDUAL_MISSILE||damage->in.m_damageType==DAMAGE_STATUS||damage->in.m_damageType==DAMAGE_DISARM||damage->in.m_damageType==DAMAGE_HAZARD_CLEANUP)return false;if(damage->in.m_amount>0&&std::isfinite(damage->in.m_amount)){auto p=object->getPosition();hostCombatEvents.push_back({++combatSequence,key->second,(unsigned int)damage->in.m_sourceID,damage->in.m_amount,p->x,p->y,p->z,0,(int)damage->in.m_damageType});}return true;}
bool forcedSource(unsigned int id){
    if(forcedProjectiles.count(id))return true;auto o=TheGameLogic->findObjectByID(static_cast<ObjectID>(id));auto ai=o?o->getAIUpdateInterface():nullptr;
    return forcedOrders.count(id)&&ai&&(ai->getCurrentStateID()==AI_FORCE_ATTACK_OBJECT||ai->getCurrentStateID()==AI_ATTACK_POSITION);
}
namespace {std::unordered_map<unsigned int,unsigned int> hostProjectileSteps;bool resolvingBlockImpact=false;}
void hostProjectile(Object* projectile,const Object* source){if(!worldTransportActive()||!projectile||!source)return;for(auto it=hostProjectileSteps.begin();it!=hostProjectileSteps.end();)if(!TheGameLogic->findObjectByID(static_cast<ObjectID>(it->first)))it=hostProjectileSteps.erase(it);else ++it;hostProjectileSteps[projectile->getID()]=0;if(forcedSource(source->getID()))forcedProjectiles.insert(projectile->getID());}
bool hostBlockImpact(){return resolvingBlockImpact;}
bool hostStartingCash(unsigned int& cash){if(!configuredStartingCash)return false;cash=matchStartingCash;return true;}
void hostAudioMeter(float value){actualMusicVolume=value;}
void hostTrack(const char* name,float volume){musicTrack=name?name:"";expectedMusicVolume=volume;}
const char* hostFactionTrack(const char* name){
    if(!name||!remoteInput()||!TheGameLogic||!TheGameLogic->isInGame()||!ThePlayerList)return nullptr;
    std::string track(name);if(track.rfind("Game_USA_",0)!=0&&track.rfind("Game_CHI_",0)!=0&&track.rfind("Game_GLA_",0)!=0)return nullptr;
    auto player=ThePlayerList->getLocalPlayer();if(!player)return nullptr;
    std::string side=player->getBaseSide().str(),prefix;
    if(side=="China")prefix="Game_CHI_";else if(side=="USA")prefix="Game_USA_";else if(side=="GLA")prefix="Game_GLA_";else return nullptr;
    static std::string mapped;mapped=prefix+track.substr(9);
    if(mapped==track)return nullptr;
    if(!TheAudio->findAudioEventInfo(mapped.c_str()))mapped=prefix+"01";
    return TheAudio->findAudioEventInfo(mapped.c_str())?mapped.c_str():nullptr;
}

bool hostProjectileMoved(Object* object,const Coord3D* previous){
    if(resolvingBlockImpact||!worldTransportActive()||!object||!previous||!object->getBehaviorModules()||object->getStatusBits().test(OBJECT_STATUS_NO_COLLISIONS))return false;
    if(isExternalObject(object->getID())||(object->isKindOf(KINDOF_SELECTABLE)&&!object->isKindOf(KINDOF_PROJECTILE)))return false;
    auto projectile=object->getProjectileUpdateInterface();
    if(!projectile&&!object->isKindOf(KINDOF_PROJECTILE))return false;
    if(projectile&&!projectile->projectileIsArmed())return false;
    auto tracked=hostProjectileSteps.find(object->getID());
    // Superweapon delivery objects are created by special-power modules, outside Weapon.cpp.
    if(tracked==hostProjectileSteps.end())tracked=hostProjectileSteps.emplace(object->getID(),0).first;
    auto end=*object->getPosition();float dx=end.x-previous->x,dy=end.y-previous->y,dz=end.z-previous->z;
    if(std::fabs(dx)+std::fabs(dy)+std::fabs(dz)<.0001f)return false;
    if(tracked->second++==0)return false; // Initial placement is not a projectile flight segment.
    int steps=std::min(2048,std::max(1,(int)std::ceil(std::max({std::fabs(dx),std::fabs(dy),std::fabs(dz)})/(worldUnitsPerBlock()*.125f))));
    for(int i=1;i<=steps;++i){float t=(float)i/steps;Coord3D p={previous->x+dx*t,previous->y+dy*t,previous->z+dz*t};auto c=columns.find(cellKey((int)std::floor(p.x/worldUnitsPerBlock()),(int)std::floor(p.y/worldUnitsPerBlock())));if(c==columns.end())continue;
        bool solid=p.z<c->second.height-.01f;for(auto span:c->second.obstacles)if(p.z>=span.first&&p.z<=span.second)solid=true;
        if(!solid)continue;
        // Dispatch the original projectile collision: its own detonation, weapon, FX and splash.
        resolvingBlockImpact=true;object->setPosition(&p);Coord3D normal={0,0,1};if(projectile&&!object->getPhysics())projectile->projectileHandleCollision(nullptr);else object->onCollide(nullptr,&p,&normal);resolvingBlockImpact=false;
        return true;
    }return false;
}

void hostExplosion(const Coord3D* p,float radius,float amount,unsigned int source,int type){
    if(!worldTransportActive()||!p||!std::isfinite(radius)||!std::isfinite(amount)||radius<=0||amount<=0||(type!=DAMAGE_EXPLOSION&&type!=DAMAGE_ARMOR_PIERCING&&type!=DAMAGE_INFANTRY_MISSILE&&type!=DAMAGE_AURORA_BOMB&&type!=DAMAGE_LAND_MINE&&type!=DAMAGE_JET_MISSILES&&type!=DAMAGE_STEALTHJET_MISSILES&&type!=DAMAGE_FLAME&&type!=DAMAGE_MOLOTOV_COCKTAIL&&type!=DAMAGE_PARTICLE_BEAM&&type!=DAMAGE_SUBDUAL_MISSILE&&type!=DAMAGE_MICROWAVE))return;
    // Residual beam fire keeps its original entity damage. Terrain pulses come from the main cannon,
    // rather than dozens of overlapping trail remnants exploding the same Minecraft blocks again.
    if(type==DAMAGE_PARTICLE_BEAM)if(auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(source)))if(std::string(object->getTemplate()->getName().str()).find("ParticleUplinkCannonTrailRemnant")!=std::string::npos)return;
    hostCombatEvents.push_back({++combatSequence,"",source,amount,p->x,p->y,p->z,radius,type,forcedSource(source)});
    // Private fixture diagnostics only: compare the original blast with imported victim geometry.
    if(std::getenv("ZHMC_TEST")&&type==DAMAGE_EXPLOSION&&amount>=300&&ThePartitionManager){
        std::ofstream log(root/"area-test.txt",std::ios::app);
        auto projectile=TheGameLogic->findObjectByID(static_cast<ObjectID>(source));
        log<<"AREA "<<TheGameLogic->getFrame()<<" "<<combatSequence<<" "<<source<<" "<<(projectile?(unsigned int)projectile->getProducerID():0)<<" "<<amount<<" "<<radius<<" "<<p->x<<" "<<p->y<<" "<<p->z<<"\n";
        for(auto& entry:hostMobKeys)if(auto object=TheGameLogic->findObjectByID(static_cast<ObjectID>(entry.first))){
            float distance=ThePartitionManager->getDistanceSquared(object,p,FROM_BOUNDINGSPHERE_3D);
            if(distance>(radius+40)*(radius+40))continue;
            auto position=object->getPosition();auto partition=object->friend_getPartitionData();
            log<<"MOB "<<entry.first<<" "<<entry.second<<" "<<position->x<<" "<<position->y<<" "<<position->z<<" "<<distance<<" "<<object->getBodyModule()->getHealth()<<" "<<(partition&&partition->isInNeedOfUpdatingCells()?1:0)<<" "<<(partition?partition->friend_getCoiInUseCount():0)<<"\n";
        }
    }
}
void hostStrategicExplosion(const Coord3D* p,float radius,float amount,unsigned int source,int type){auto before=combatSequence;hostExplosion(p,radius,amount,source,type);if(combatSequence>before)hostCombatEvents.back().forcedAttack=true;}
void hostPlayerCommand(Object* object,const AICommandParms* command){
    if(retryingHostMove||!worldTransportActive()||!object||!command||command->m_cmdSource!=CMD_FROM_PLAYER)return;
    if(!drivingCommand){auto driven=drivenVehicles.find(object->getID());if(driven!=drivenVehicles.end()){blockedDriveLeases[object->getID()]=driven->second.lease;if(auto ai=object->getAI()){ai->setTurretTargetPosition(TURRET_MAIN,nullptr);ai->setTurretTargetPosition(TURRET_ALT,nullptr);if(auto supply=ai->getSupplyTruckAIInterface())supply->setForceBusyState(false);}drivenVehicles.erase(driven);}}
    if(command->m_cmd==AICMD_FORCE_ATTACK_OBJECT||command->m_cmd==AICMD_ATTACK_POSITION)forcedOrders.insert(object->getID());else forcedOrders.erase(object->getID());
    if(command->m_cmd==AICMD_MOVE_TO_POSITION&&!object->isKindOf(KINDOF_AIRCRAFT)&&!isExternalObject(object->getID())){Coord3D goal=command->m_pos;if(hasRequestedHostMove&&!hostTerrainKnown(&requestedHostMove))goal=requestedHostMove;hostMoves[object->getID()]={goal,*object->getPosition(),TheGameLogic->getFrame(),terrainRevision};}
    else hostMoves.erase(object->getID());
}
void hostRequestedGroupMove(const Coord3D* goal){hasRequestedHostMove=goal&&worldTransportActive();if(hasRequestedHostMove)requestedHostMove=*goal;}
}

namespace zhmc {
bool hostEmpAffectsNative(){return !worldTransportActive()||hostEmpVehicles;}
void hostEMP(const Coord3D* p,float radius,unsigned int frames,unsigned int source){
 if(!worldTransportActive()||!p||radius<=0||!std::isfinite(radius)||!frames)return;
 hostCombatEvents.push_back({++combatSequence,"",source,0,p->x,p->y,p->z,radius,-1,false,frames});
}
}
