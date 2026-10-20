"""Isolated original-message/logic gates for Minecraft-hosted lockstep, inactive by default."""
def apply(root,addon,change):
    include='#include "'+addon.as_posix()+'/NativeLockstep.h"'
    engine=root/'GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp'
    change(engine,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    value=engine.read_text(encoding='utf-8-sig').replace('if (canUpdateGameLogic(FramePacer::IgnoreFrozenTime) && zhmc::allowLockstepTick())','if (canUpdateGameLogic(FramePacer::IgnoreFrozenTime))')
    engine.write_text(value,encoding='utf-8',newline='\n')
    change(engine,'if (canUpdateGameLogic(FramePacer::IgnoreFrozenTime))','for (int hostStep=0;hostStep<(zhmc::lockstepGameplayReady()?4:1)&&canUpdateGameLogic(FramePacer::IgnoreFrozenTime)&&zhmc::allowLockstepTick();++hostStep)')
    change(engine,'\tconst Int maxRenderFps = TheFramePacer->getActualFramesPerSecondLimit();','    if(zhmc::lockstepGameplayReady())return true; // Only committed frames advance; render pacing must not prevent catch-up.\n\tconst Int maxRenderFps = TheFramePacer->getActualFramesPerSecondLimit();')
    change(engine,'\t\t\tTheGameLogic->UPDATE();','\t\t\tTheGameLogic->UPDATE();\n            zhmc::finishLockstepTick();')
    stream=root/'Core/GameEngine/Source/Common/MessageStream.cpp'
    change(stream,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    change(stream,'void CommandList::appendMessageList( GameMessage *list )\n{\n\tGameMessage *msg, *next;\n\n\tfor( msg = list; msg; msg = next )\n\t{\n\t\tnext = msg->next();\n\t\tappendMessage( msg );',
        'void CommandList::appendMessageList( GameMessage *list )\n{\n\tGameMessage *msg, *next;\n\n\tfor( msg = list; msg; msg = next )\n\t{\n\t\tnext = msg->next();\n        if(zhmc::captureLockstepCommand(msg)){deleteInstance(msg);continue;}\n\t\tappendMessage( msg );')
    main=root/'GeneralsMD/Code/Main/WinMain.cpp'
    value=main.read_text(encoding='utf-8-sig').replace('if (!(GetEnvironmentVariableA("ZHMC_LOCKSTEP",nullptr,0)&&GetEnvironmentVariableA("ZHMC_BRIDGE_DIR",nullptr,0)) && !rts::ClientInstance::initialize())','if (!rts::ClientInstance::initialize())')
    value=value.replace('GetEnvironmentVariableA("ZHMC_LOCKSTEP",nullptr,0)&&GetEnvironmentVariableA("ZHMC_BRIDGE_DIR",nullptr,0)','GetEnvironmentVariableA("ZHMC_BRIDGE_DIR",nullptr,0)')
    main.write_text(value,encoding='utf-8',newline='\n')
    # Each adapter session is private, including preflight and non-network RTS.
    # A lingering/other local session must not trigger the retail single-instance
    # early shutdown (or foreground another hidden window).
    change(main,'if (!rts::ClientInstance::initialize())','if (GetEnvironmentVariableA("ZHMC_BRIDGE_DIR",nullptr,0)){rts::ClientInstance::setMultiInstance(true);rts::ClientInstance::skipPrimaryInstance();}\n        if (!rts::ClientInstance::initialize())')
    cmake=root/'GeneralsMD/Code/Main/CMakeLists.txt'
    value=cmake.read_text(encoding='utf-8-sig');entry='target_sources(z_generals PRIVATE "'+addon.as_posix()+'/NativeLockstep.cpp")'
    if entry not in value:cmake.write_text(value+'\n'+entry+'\n',encoding='utf-8',newline='\n')
    player=root/'GeneralsMD/Code/GameEngine/Include/Common/Player.h'
    change(player,'\tvirtual Bool getAiBaseCenter(Coord3D *pos);','\tAIPlayer* hostAI() { return m_ai; }\n\tvirtual Bool getAiBaseCenter(Coord3D *pos);')
    ai=root/'GeneralsMD/Code/GameEngine/Include/GameLogic/AIPlayer.h'
    change(ai,'\tBool getBaseCenter(Coord3D *pos) const','\tvoid hostRebase(const Coord3D& delta);\n\tBool getBaseCenter(Coord3D *pos) const')
    source=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/AI/AIPlayer.cpp'
    value=source.read_text(encoding='utf-8-sig')
    value=value.replace('p.z=TheTerrainLogic->getGroundHeight(p.x,p.y);info->setLocation(p);','info->setLocation(p);')
    if 'void AIPlayer::hostRebase(' not in value:
        value+='''
void AIPlayer::hostRebase(const Coord3D& delta){
    for(auto info=m_player->getBuildList();info;info=info->getNext()){
        Coord3D p=*info->getLocation();p.x+=delta.x;p.y+=delta.y;info->setLocation(p);
    }
    m_baseCenter.x+=delta.x;m_baseCenter.y+=delta.y;m_baseCenter.z=TheTerrainLogic->getGroundHeight(m_baseCenter.x,m_baseCenter.y);
    m_repairDozerOrigin.x+=delta.x;m_repairDozerOrigin.y+=delta.y;m_repairDozerOrigin.z+=delta.z;
}
'''
    source.write_text(value,encoding='utf-8',newline='\n')
    change(source,'#include "Common/GameMemory.h"','#include "'+addon.as_posix()+'/WorldScene.h"\n#include "Common/GameMemory.h"')
    # Supply plans must clear both original footprints and the original supply
    # exclusion border. The fixed three-cell offset can put the first priority
    # supply centre inside its warehouse; the dozer then rejects it forever.
    anchor='\t\t\tradius = bestSupplyWarehouse->getGeometryInfo().getBoundingCircleRadius();\n\t\t}\n\t\tlocation.x -= offset.x*radius;'
    change(source,anchor,anchor.replace('\t\tlocation.x', '\t\tif(zhmc::worldTransportActive()&&tTemplate->isKindOf(KINDOF_CANNOT_BUILD_NEAR_SUPPLIES))radius=std::max(radius,bestSupplyWarehouse->getGeometryInfo().getBoundingCircleRadius()+tTemplate->getTemplateGeometryInfo().getBoundingCircleRadius()+TheGlobalData->m_SupplyBuildBorder+PATHFIND_CELL_SIZE_F);\n\t\tlocation.x'))
    # The selected candidate is curPlan. bldgPlan is still null until after
    # prerequisite checking, preventing every automatic non-power building.
    skirmish=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/AI/AISkirmishPlayer.cpp'
    change(skirmish,'canMakeUnit(dozer, bldgPlan)','canMakeUnit(dozer, curPlan)')
