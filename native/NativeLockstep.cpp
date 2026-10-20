// SPDX-License-Identifier: GPL-3.0-or-later
// The Minecraft server orders frames; every participant keeps the original UI and simulation.
#include "Precompiled/PreRTS.h"
#include "NativeLockstep.h"
#include "Bridge.h"
#include "Common/MessageStream.h"
#include "Common/PlayerList.h"
#include "Common/Player.h"
#include "GameLogic/GameLogic.h"
#include "GameClient/ControlBar.h"
#include <filesystem>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <vector>
#include <cmath>
#include <cstdlib>

namespace zhmc {
namespace {
unsigned long long outgoing=0;std::string problem;unsigned int appliedFrame=~0u;
unsigned int fingerprintFrame=~0u,fingerprint=0;
bool sharedAuthority=false;
std::filesystem::path directory(){const char* value=std::getenv("ZHMC_BRIDGE_DIR");return value?std::filesystem::path(value):std::filesystem::path();}
void encode(std::ostream& out,const GameMessage* message){
    out<<message->getType()<<' '<<static_cast<int>(message->getArgumentCount())<<std::setprecision(9);
    for(int i=0;i<message->getArgumentCount();++i){auto type=message->getArgumentDataType(i);auto a=message->getArgument(i);out<<' '<<type;
        switch(type){
        case ARGUMENTDATATYPE_REAL:out<<' '<<a->real;break;
        case ARGUMENTDATATYPE_LOCATION:out<<' '<<a->location.x<<' '<<a->location.y<<' '<<a->location.z;break;
        case ARGUMENTDATATYPE_PIXEL:out<<' '<<a->pixel.x<<' '<<a->pixel.y;break;
        case ARGUMENTDATATYPE_PIXELREGION:out<<' '<<a->pixelRegion.lo.x<<' '<<a->pixelRegion.lo.y<<' '<<a->pixelRegion.hi.x<<' '<<a->pixelRegion.hi.y;break;
        case ARGUMENTDATATYPE_BOOLEAN:out<<' '<<(a->boolean?1:0);break;
        case ARGUMENTDATATYPE_WIDECHAR:out<<' '<<static_cast<unsigned int>(a->wChar);break;
        default:out<<' '<<a->integer;break;
        }
    }
}
GameMessage* decode(std::istream& in){
    int player,type,count;if(!(in>>player>>type>>count)||player<2||player>=16||type<=GameMessage::MSG_BEGIN_NETWORK_MESSAGES||type>=GameMessage::MSG_BEGIN_DEBUG_NETWORK_MESSAGES||count<0||count>255)return nullptr;
    auto p=ThePlayerList->getNthPlayer(player);if(!p||!p->isPlayerActive())return nullptr;
    auto message=newInstance(GameMessage)(static_cast<GameMessage::Type>(type));message->friend_setPlayerIndex(player);
    for(int i=0;i<count;++i){int tag;int integer;float real;Coord3D location;ICoord2D pixel;IRegion2D region;if(!(in>>tag))goto invalid;
        switch(tag){
        case ARGUMENTDATATYPE_REAL:if(!(in>>real)||!std::isfinite(real))goto invalid;message->appendRealArgument(real);break;
        case ARGUMENTDATATYPE_LOCATION:if(!(in>>location.x>>location.y>>location.z)||!std::isfinite(location.x)||!std::isfinite(location.y)||!std::isfinite(location.z))goto invalid;message->appendLocationArgument(location);break;
        case ARGUMENTDATATYPE_PIXEL:if(!(in>>pixel.x>>pixel.y))goto invalid;message->appendPixelArgument(pixel);break;
        case ARGUMENTDATATYPE_PIXELREGION:if(!(in>>region.lo.x>>region.lo.y>>region.hi.x>>region.hi.y))goto invalid;message->appendPixelRegionArgument(region);break;
        default:
            if(!(in>>integer))goto invalid;
            switch(tag){
            case ARGUMENTDATATYPE_INTEGER:message->appendIntegerArgument(integer);break;
            case ARGUMENTDATATYPE_BOOLEAN:if(integer!=0&&integer!=1)goto invalid;message->appendBooleanArgument(integer!=0);break;
            case ARGUMENTDATATYPE_OBJECTID:message->appendObjectIDArgument(static_cast<ObjectID>(integer));break;
            case ARGUMENTDATATYPE_DRAWABLEID:message->appendDrawableIDArgument(static_cast<DrawableID>(integer));break;
            case ARGUMENTDATATYPE_TEAMID:message->appendTeamIDArgument(static_cast<unsigned int>(integer));break;
            case ARGUMENTDATATYPE_TIMESTAMP:message->appendTimestampArgument(static_cast<unsigned int>(integer));break;
            case ARGUMENTDATATYPE_WIDECHAR:if(integer<0||integer>65535)goto invalid;message->appendWideCharArgument(static_cast<WideChar>(integer));break;
            default:goto invalid;
            }
        }
    }
    {std::string extra;if(in>>extra)goto invalid;}return message;
invalid:deleteInstance(message);return nullptr;
}
}
bool lockstepActive(){static const bool active=std::getenv("ZHMC_LOCKSTEP")!=nullptr;return active&&!sharedAuthority;}
void beginSharedAuthority(){if(std::getenv("ZHMC_SHARED_AUTHORITY"))sharedAuthority=true;}
bool lockstepGameplayReady(){return lockstepActive()&&TheGameLogic&&TheGameLogic->isInGame()&&!TheGameLogic->isInShellGame()&&!TheGameLogic->isLoadingMap()&&!TheGameLogic->isLoadingSave()&&TheGameLogic->getFirstObject();}
bool captureLockstepCommand(const GameMessage* message){
    if(!lockstepActive()||!TheGameLogic->isInGame()||TheGameLogic->isInShellGame()||message->getType()<=GameMessage::MSG_BEGIN_NETWORK_MESSAGES||message->getType()>=GameMessage::MSG_END_NETWORK_MESSAGES)return false;
    // Debug network commands are never sent by participants.
    if(message->getType()>=GameMessage::MSG_BEGIN_DEBUG_NETWORK_MESSAGES)return true;
    if(message->getType()==GameMessage::MSG_SET_REPLAY_CAMERA)return true;
    if(std::getenv("ZHMC_AUTHORITY"))return true;
    auto folder=directory()/"outbox";std::filesystem::create_directories(folder);auto path=folder/(std::to_string(++outgoing)+".tmp");
    {std::ofstream out(path);encode(out,message);out<<'\n';}
    auto published=path;published.replace_extension("msg");MoveFileExW(path.c_str(),published.c_str(),MOVEFILE_REPLACE_EXISTING);return true;
}
bool allowLockstepTick(){
    if(!lockstepActive()||!TheGameLogic->isInGame()||TheGameLogic->isInShellGame())return true;
    // MSG_NEW_GAME marks the game active before its synchronous map initialization runs.
    if(TheGameLogic->getFrame()==0&&!TheGameLogic->getFirstObject())return true;
    if(!problem.empty())return false;
    if(appliedFrame==TheGameLogic->getFrame())return false;
    const char* local=std::getenv("ZHMC_LOCAL_PLAYER");int index=local?std::atoi(local):2;
    if(auto player=ThePlayerList->getNthPlayer(index))if(ThePlayerList->getLocalPlayer()!=player){ThePlayerList->setLocalPlayer(player);if(TheControlBar)TheControlBar->setControlBarSchemeByPlayer(player);}
    auto file=directory()/"frames"/(std::to_string(TheGameLogic->getFrame())+".frame");std::ifstream in(file);if(!in)return false;
    std::string marker;unsigned int frame;if(!(in>>marker>>frame)||marker!="FRAME"||frame!=TheGameLogic->getFrame()){problem="Invalid synchronized frame header";return false;}
    std::vector<GameMessage*> messages;std::vector<std::string> world;std::string line;std::getline(in,line);unsigned int bytes=0;
    while(std::getline(in,line)){
        bytes+=static_cast<unsigned int>(line.size());if(bytes>4*1024*1024||messages.size()>1024||world.size()>1024){problem="Synchronized frame exceeds limits";break;}
        if(line.empty())continue;
        if(line.rfind("C ",0)==0){std::istringstream command(line.substr(2));auto message=decode(command);if(!message){problem="Malformed synchronized game command";break;}messages.push_back(message);}
        else if(line.rfind("W ",0)==0)world.push_back(line.substr(2));else{problem="Unknown synchronized record";break;}
    }
    if(!problem.empty()){for(auto message:messages)deleteInstance(message);return false;}
    for(auto& command:world)synchronizedHostCommand(command);
    hostLogicTick();
    for(auto message:messages)TheCommandList->appendMessage(message);
    appliedFrame=frame;
    return true;
}
void lockstepState(std::ostream& out){
    out<<",\"lockstep\":"<<(lockstepActive()?"true":"false");if(!lockstepActive())return;
    finishLockstepTick();
    out<<",\"lockstepCrc\":"<<fingerprint<<",\"lockstepCrcFrame\":"<<static_cast<int>(fingerprintFrame)<<",\"lockstepAppliedFrame\":"<<appliedFrame<<",\"lockstepOutgoing\":"<<outgoing<<",\"lockstepError\":\""<<problem<<'"';
}
void finishLockstepTick(){
    if(!lockstepGameplayReady()||fingerprintFrame==TheGameLogic->getFrame())return;
    // Retail checks every100 ticks. Half-second checkpoints bound detection time
    // without recalculating every unit/AI/script CRC on every simulation tick.
    if(fingerprintFrame!=~0u&&TheGameLogic->getFrame()%15!=0)return;
    fingerprintFrame=TheGameLogic->getFrame();fingerprint=TheGameLogic->getCRC(CRC_RECALC);
    auto file=directory()/"checks.bin";if(!std::filesystem::exists(file)){std::ofstream init(file,std::ios::binary);char empty[4096]={};init.write(empty,sizeof(empty));}
    std::fstream out(file,std::ios::binary|std::ios::in|std::ios::out);unsigned int record[4]={fingerprintFrame,fingerprint,fingerprintFrame,0};out.seekp((fingerprintFrame%256)*sizeof(record));out.write(reinterpret_cast<const char*>(record),sizeof(record));
}
}
