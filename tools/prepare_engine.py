"""Apply only integration patches to an external, pinned engine checkout."""
from pathlib import Path
import argparse
import subprocess
import re

PIN = 'f746af7e626c96e693cb74a032da47a602c1a039'

def change(path: Path, old: str, new: str):
    text = path.read_text(encoding='utf-8-sig')
    if new in text:
        return
    # Other adapters can insert an include between this anchor and our include.
    # Presence of the requested include is enough; do not grow duplicate blocks.
    if old.startswith('#include ') and new.startswith(old + '\n'):
        added = new[len(old):].strip().splitlines()
        if added and all(line in text for line in added):
            return
    if text.count(old) != 1:
        raise RuntimeError(f'Expected exactly one integration point in {path}: {old[:70]}')
    path.write_text(text.replace(old, new), encoding='utf-8', newline='\n')

def main():
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    args = p.parse_args()
    root = args.source.resolve()
    addon = Path(__file__).resolve().parents[1] / 'native'
    sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    if PIN != 'PENDING' and sha != PIN:
        raise RuntimeError(f'Unreviewed source revision {sha}, expected {PIN}')
    change(root / 'GeneralsMD/Code/Main/RTS.RC', '#include "afxres.h"', '#include "winres.h"')
    shroud=root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DShroud.cpp'
    change(shroud,'if (x < m_numCellsX && y < m_numCellsY)',
           'if (x >= 0 && y >= 0 && x < m_numCellsX && y < m_numCellsY) // Finite visual shroud excludes signed host pages.')
    # Completely separate saves/configs. This hook runs during static initialization.
    change(root / 'GeneralsMD/Code/GameEngine/Source/Common/GlobalData.cpp',
           'AsciiString GlobalData::BuildUserDataPathFromRegistry()\n{',
           '''AsciiString GlobalData::BuildUserDataPathFromRegistry()
{
    char zhmcUserData[MAX_PATH];
    DWORD zhmcLength = GetEnvironmentVariableA("ZHMC_USER_DATA", zhmcUserData, MAX_PATH);
    if (zhmcLength > 0 && zhmcLength < MAX_PATH) {
        AsciiString path = zhmcUserData;
        if (!path.endsWith("\\\\")) path.concat('\\\\');
        return path;
    }''')
    engine = root / 'GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp'
    change(root / 'Core/GameEngineDevice/Source/Win32Device/Common/Win32BIGFileSystem.cpp',
           '\tloadBigFilesFromDirectory("", "*.big");',
           '''\tloadBigFilesFromDirectory("", "*.big");
    // Shared Core is compiled without RTS_ZEROHOUR. Mount the explicit private base here.
    char zhmcExplicitBase[MAX_PATH];
    DWORD zhmcExplicitLength = GetEnvironmentVariableA("ZHMC_BASE_DATA", zhmcExplicitBase, MAX_PATH);
    if (zhmcExplicitLength > 0 && zhmcExplicitLength < MAX_PATH) {
        AsciiString zhmcMount = zhmcExplicitBase;
        if (!zhmcMount.endsWith("\\\\")) zhmcMount.concat('\\\\');
        loadBigFilesFromDirectory(zhmcMount, "*.big");
        return;
    }''')
    change(root / 'Core/GameEngineDevice/Source/Win32Device/Common/Win32BIGFileSystem.cpp',
           '    GetStringFromGeneralsRegistry("", "InstallPath", installPath );',
           '''    char zhmcBase[MAX_PATH];
    DWORD zhmcBaseLength = GetEnvironmentVariableA("ZHMC_BASE_DATA", zhmcBase, MAX_PATH);
    if (zhmcBaseLength > 0 && zhmcBaseLength < MAX_PATH) installPath = zhmcBase;
    else GetStringFromGeneralsRegistry("", "InstallPath", installPath );''')
    change(engine, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(engine, 'void GameEngine::update()\n{', 'void GameEngine::update()\n{\n    zhmc::beforeUpdate();')
    change(engine, '\n\t\t\tTheFramePacer->update();', '\n            zhmc::afterUpdate();\n\t\t\tTheFramePacer->update();')
    obj = root / 'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Object.cpp'
    context=root/'Core/GameEngine/Source/GameClient/MessageStream/CommandXlat.cpp'
    change(context,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(context,'\tCanAttackResult result = ATTACKRESULT_NOT_POSSIBLE;','''    // A Minecraft neutral is inspectable with an empty selection, and explicitly
    // attackable by an armed selection without requiring the force-fire modifier.
    // Keep original weapon eligibility, hints, voice, dispatcher and lockstep.
    if(!command && draw && zhmc::isExternalObject(obj->getID())){
        CanAttackResult hostResult=canAnyForceAttack(TheInGameUI->getAllSelectedDrawables(),obj,pos);
        if(hostResult==ATTACKRESULT_POSSIBLE || hostResult==ATTACKRESULT_POSSIBLE_AFTER_MOVING)
            return evaluateForceAttack(draw,pos,type);
    }
\tCanAttackResult result = ATTACKRESULT_NOT_POSSIBLE;''')
    change(obj, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(obj,'//USE_PERF_TIMER(Object_reactToTransformChange)','//USE_PERF_TIMER(Object_reactToTransformChange)\n    if(zhmc::hostProjectileMoved(this,oldPos))return;')
    change(obj, 'void Object::attemptDamage( DamageInfo *damageInfo )\n{', 'void Object::attemptDamage( DamageInfo *damageInfo )\n{\n    if (zhmc::externalDamage(this,damageInfo)) return;')
    aiupdate = root / 'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate.cpp'
    change(aiupdate, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(aiupdate, 'UpdateSleepTime AIUpdateInterface::update()\n{', 'UpdateSleepTime AIUpdateInterface::update()\n{\n    if (zhmc::isExternalObject(getObject()->getID())) return UPDATE_SLEEP_NONE;')
    for relative,signature in (
        ('GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/PhysicsUpdate.cpp','UpdateSleepTime PhysicsBehavior::update()\n{'),
        ('GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate/JetAIUpdate.cpp','UpdateSleepTime JetAIUpdate::update()\n{')):
        imported=root/relative
        change(imported,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
        change(imported,signature,signature+'\n    if(zhmc::isExternalObject(getObject()->getID()))return UPDATE_SLEEP_NONE;')
    missile=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate/MissileAIUpdate.cpp'
    change(missile,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(missile,'#include \"'+addon.as_posix()+'/Bridge.h\"','#include \"'+addon.as_posix()+'/Bridge.h\"\nnamespace zhmc {bool worldTransportActive();}')
    text=missile.read_text(encoding='utf-8-sig').replace('zhmc::worldTransportActive() ? -4096.f : 0.f','zhmc::worldTransportActive() ? -32768.f : 0.f');missile.write_text(text,encoding='utf-8',newline='\n')
    change(missile,'if (newPos.z < 0)','if (newPos.z < (zhmc::worldTransportActive() ? -32768.f : 0.f))')
    emp=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/EMPUpdate.cpp'
    change(emp,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    value=emp.read_text(encoding='utf-8-sig')
    value=value.replace('dr->setInstanceScale( m_currentScale );','if(dr)dr->setInstanceScale( m_currentScale );') if 'if(dr)dr->setInstanceScale' not in value else value
    value=value.replace('dr->colorTint( &start );','if(dr)dr->colorTint( &start );') if 'if(dr)dr->colorTint' not in value else value
    value=value.replace('dr->colorFlash( &end, 0, m_tintEnvFadeFrames, ~0u );','if(dr)dr->colorFlash( &end, 0, m_tintEnvFadeFrames, ~0u );') if 'if(dr)dr->colorFlash' not in value else value
    emp.write_text(value,encoding='utf-8',newline='\n')
    change(emp,'Real radius = data->m_effectRadius;','Real radius = data->m_effectRadius;\n    zhmc::hostEMP(object->getPosition(),radius,data->m_disabledDuration,(unsigned int)object->getProducerID());\n    if(!zhmc::hostEmpAffectsNative())return;')
    change(missile,'if (other==nullptr) {','if (other==nullptr && !zhmc::hostBlockImpact()) {')
    miles=root/'Core/GameEngineDevice/Source/MilesAudioDevice/MilesAudioManager.cpp'
    change(miles,'#include "MilesAudioDevice/MilesAudioManager.h"','#include "MilesAudioDevice/MilesAudioManager.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(miles,'return findActiveMusic() != nullptr;','auto playing=findActiveMusic();if(playing){F32 measured=0;AIL_stream_volume_pan(playing->m_stream,&measured,nullptr);zhmc::hostAudioMeter(measured);}return playing != nullptr;')
    # Minecraft uses its own visibility; SAGE must create FX before the scene exporter can see them.
    fx=root/'Core/GameEngine/Source/GameClient/FXList.cpp'
    particles=root/'Core/GameEngine/Source/GameClient/System/ParticleSys.cpp'
    for source in (fx,particles):
        change(source,'#include "PreRTS.h"','#include "PreRTS.h"\nstruct Coord3D;namespace zhmc {bool hostMinecraftEffects(const Coord3D*);}')
    change(fx,'if (ThePartitionManager->getShroudStatusForPlayer(playerIndex, primary) != CELLSHROUD_CLEAR)',
           'if (!zhmc::hostMinecraftEffects(primary) && ThePartitionManager->getShroudStatusForPlayer(playerIndex, primary) != CELLSHROUD_CLEAR)')
    change(fx,'if (primary && primary->getShroudedStatus(playerIndex) > OBJECTSHROUD_PARTIAL_CLEAR)',
           'if (primary && !zhmc::hostMinecraftEffects(primary->getPosition()) && primary->getShroudedStatus(playerIndex) > OBJECTSHROUD_PARTIAL_CLEAR)')
    change(particles,'visibilityState.isShrouded = attachedTo->getFullyObscuredByShroud();',
           'visibilityState.isShrouded = attachedTo->getFullyObscuredByShroud() && !zhmc::hostMinecraftEffects(attachedTo->getPosition());')
    change(particles,'visibilityState.isShrouded = objectAttachedTo->getShroudedStatus(localPlayerIndex) >= OBJECTSHROUD_FOGGED;',
           'visibilityState.isShrouded = objectAttachedTo->getShroudedStatus(localPlayerIndex) >= OBJECTSHROUD_FOGGED && !zhmc::hostMinecraftEffects(objectAttachedTo->getPosition());')
    weapon = root / 'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Weapon.cpp'
    change(weapon, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(weapon,'#include "PreRTS.h"','#include "PreRTS.h"\nnamespace zhmc {void hostProjectile(Object*,const Object*);}')
    change(weapon,'projectile->setProducer(sourceObj);','projectile->setProducer(sourceObj);\n    zhmc::hostProjectile(projectile,sourceObj);')
    change(weapon, '\t\tReal radius = max(primaryRadius, secondaryRadius);', '\t\tReal radius = max(primaryRadius, secondaryRadius);\n        zhmc::hostExplosion(pos,radius,max(primaryDamage,secondaryDamage),(unsigned int)sourceID,(int)damageType);')
    # These effects deal area damage directly, without going through WeaponTemplate.
    neutron=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/NeutronMissileSlowDeathUpdate.cpp'
    firestorm=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/FirestormDynamicGeometryInfoUpdate.cpp'
    for effect in (neutron,firestorm):
        change(effect,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(neutron,'\tdamageInfo.in.m_sourceID = missile->getID();','\tdamageInfo.in.m_sourceID = missile->getID();\n    zhmc::hostExplosion(missilePos,blastInfo->outerRadius,max(blastInfo->maxDamage,blastInfo->minDamage),(unsigned int)missile->getID(),DAMAGE_EXPLOSION);')
    change(firestorm,'\tReal boundingCircle = firestorm->getGeometryInfo().getBoundingCircleRadius();','\tReal boundingCircle = firestorm->getGeometryInfo().getBoundingCircleRadius();\n    zhmc::hostExplosion(firestormPos,boundingCircle,modData->m_damageAmount,(unsigned int)firestorm->getID(),DAMAGE_FLAME);')
    change(aiupdate,'\tif (!isAllowedToRespondToAiCommands(parms))\n\t\treturn;','\tif (!isAllowedToRespondToAiCommands(parms))\n\t\treturn;\n    zhmc::hostPlayerCommand(getObject(),parms);')
    action=root/'GeneralsMD/Code/GameEngine/Source/Common/RTS/ActionManager.cpp'
    change(action,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    action_text=action.read_text(encoding='utf-8-sig');action_text=action_text.replace('return zhmc::hostTerrainKnown(loc) ||','return zhmc::hostUnshroudedTarget(loc) ||');action.write_text(action_text,encoding='utf-8',newline='\n')
    change(action, '//Don\'t allow "damaging" special powers in shrouded areas, but Fogged are okay.\n\t\t\t\treturn ThePartitionManager->getShroudStatusForPlayer( obj->getControllingPlayer()->getPlayerIndex(), loc ) != CELLSHROUD_SHROUDED;', '//Don\'t allow "damaging" special powers in shrouded areas, but Fogged are okay.\n\t\t\t\treturn zhmc::hostUnshroudedTarget(loc) || ThePartitionManager->getShroudStatusForPlayer( obj->getControllingPlayer()->getPlayerIndex(), loc ) != CELLSHROUD_SHROUDED;')
    change(action,'inline Bool isPointOnMap( const Coord3D  *testPos )\n{','inline Bool isPointOnMap( const Coord3D  *testPos )\n{\n    if(zhmc::hostTerrainKnown(testPos))return TRUE;')
    change(action,'#include "'+addon.as_posix()+'/Bridge.h"','#include "'+addon.as_posix()+'/Bridge.h"\nnamespace zhmc {bool hostUnshroudedTarget(const Coord3D*);}')
    change(action,'return zhmc::hostTerrainKnown(loc) || ThePartitionManager->getShroudStatusForPlayer','return zhmc::hostUnshroudedTarget(loc) || ThePartitionManager->getShroudStatusForPlayer')
    # Imported mobs keep Minecraft altitude even above excavated soil; the proxy's ground scatter is not their body.
    anchor='\tif (getProjectileTemplate() == nullptr || isProjectileDetonation)\n\t{\n\t\t// see if we need to be called back at a later point to deal the damage.'
    change(weapon,anchor,'    if(victimObj && zhmc::isExternalObject(victimObj->getID())){\n        Coord3D hostCenter;victimObj->getGeometryInfo().getCenterPosition(*victimObj->getPosition(),hostCenter);\n        projectileDestination.z=hostCenter.z;victimPos=&projectileDestination;\n    }\n'+anchor)
    drawable = root / 'GeneralsMD/Code/GameEngine/Source/GameClient/Drawable.cpp'
    change(drawable, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/FrameCapture.h"')
    change(drawable,'screen.x -= (m_captionDisplayString->getWidth() / 2);','Int hostCaptionWidth,hostCaptionHeight;m_captionDisplayString->getSize(&hostCaptionWidth,&hostCaptionHeight);\n    if(getObject())zhmc::mobCaptionPosition((unsigned int)getObject()->getID(),&screen.x,&screen.y,hostCaptionHeight);\n\tscreen.x -= (m_captionDisplayString->getWidth() / 2);')
    change(drawable, '\t\t// draw the health box outline', '\t\tif(zhmc::captureHealthBar(obj,healthBarRegion->lo.x,healthBarRegion->lo.y,healthBoxWidth,healthBoxHeight,healthRatio,color,outlineColor))return;\n\t\t// draw the health box outline')
    # Original terrain-extrema caches contain stock-map heights; sparse cells use broad placeholders.
    # Projectile Bezier control points must query the actual loaded Minecraft floor instead.
    partition = root / 'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/PartitionManager.cpp'
    change(partition,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(partition,'#include "'+addon.as_posix()+'/Bridge.h"','#include "'+addon.as_posix()+'/Bridge.h"\n#include "'+addon.as_posix()+'/WorldScene.h"')
    signature='Bool PartitionManager::estimateTerrainExtremesAlongLine(const Coord3D& pos, const Coord3D& posOther, Real* minZ, Real* maxZ, Coord2D* minZPos, Coord2D* maxZPos)\n{'
    change(partition,signature,signature+'''
    if(zhmc::worldTransportActive()){
        if(minZ)*minZ=HUGE_DIST;if(maxZ)*maxZ=-HUGE_DIST;
        Real dx=posOther.x-pos.x,dy=posOther.y-pos.y;
        Int steps=MAX(1,(Int)ceil(sqrt(dx*dx+dy*dy)/(zhmc::worldUnitsPerBlock()*.5f)));
        if(steps>8192)return false;
        for(Int i=0;i<=steps;++i){Real t=(Real)i/steps,x=pos.x+dx*t,y=pos.y+dy*t,height;
            if(!zhmc::terrainHeight(x,y,height))return false;
            if(minZ&&height<*minZ){*minZ=height;if(minZPos){minZPos->x=x;minZPos->y=y;}}
            if(maxZ&&height>*maxZ){*maxZ=height;if(maxZPos){maxZPos->x=x;maxZPos->y=y;}}
        }return true;
    }''')
    heightmap = root / 'Core/GameEngineDevice/Include/W3DDevice/GameClient/BaseHeightMap.h'
    change(heightmap,'\tvirtual ~BaseHeightMapRenderObjClass() override;','\tW3DWaypointBuffer* hostWaypoints() const { return m_waypointBuffer; }\n\tvirtual ~BaseHeightMapRenderObjClass() override;')
    waypoint = root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3dWaypointBuffer.cpp'
    change(waypoint,'#include "W3DDevice/GameClient/W3DWaypointBuffer.h"','#include "W3DDevice/GameClient/W3DWaypointBuffer.h"\n#include "'+addon.as_posix()+'/WorldScene.h"')
    # All line/node sites share the same original decisions, points, width, texture and color.
    wptext=waypoint.read_text(encoding='utf-8-sig')
    for old,new in [('m_line->Render( localRinfo );','if(!zhmc::captureWaypointLine(m_line,points,numPoints))m_line->Render( localRinfo );'),('WW3D::Render(*m_waypointNodeRobj,localRinfo);','if(!zhmc::captureWaypointNode(m_waypointNodeRobj))WW3D::Render(*m_waypointNodeRobj,localRinfo);')]:
        if new not in wptext:wptext=wptext.replace(old,new)
    waypoint.write_text(wptext,encoding='utf-8',newline='\n')
    portraits=root / 'Core/GameEngine/Source/GameClient/GUI/ControlBar/ControlBar.cpp'
    change(portraits,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "Common/PlayerTemplate.h"')
    change(portraits,'const Image* portrait = thing->getSelectedPortraitImage();','const Image* portrait = thing->getSelectedPortraitImage();\n        if(zhmc::hasHostView()&&obj->isKindOf(KINDOF_COMMANDCENTER)&&player&&player->getPlayerTemplate()->getGeneralImage())portrait=player->getPlayerTemplate()->getGeneralImage();')
    change(portraits,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    portrait_source=portraits.read_text(encoding='utf-8-sig')
    if 'm_rightHUDCameoWindow->winClearStatus(WIN_STATUS_IMAGE);for(int i=0;i<MAX_UPGRADE_CAMEO_UPGRADES;++i)' in portrait_source:
        change(portraits,'m_rightHUDCameoWindow->winClearStatus(WIN_STATUS_IMAGE);for(int i=0;i<MAX_UPGRADE_CAMEO_UPGRADES;++i)','m_rightHUDWindow->winClearStatus(WIN_STATUS_IMAGE);m_rightHUDCameoWindow->winSetEnabledImage(0,nullptr);GadgetButtonDrawOverlayImage(m_rightHUDCameoWindow,nullptr);m_rightHUDCameoWindow->winSetStatus(WIN_STATUS_IMAGE);for(int i=0;i<MAX_UPGRADE_CAMEO_UPGRADES;++i)')
    change(portraits,'void ControlBar::setPortraitByObject( Object *obj )\n{','void ControlBar::setPortraitByObject( Object *obj )\n{\n    if(obj&&zhmc::isExternalObject(obj->getID())){m_rightHUDUnitSelectParent->winHide(FALSE);m_rightHUDWindow->winClearStatus(WIN_STATUS_IMAGE);m_rightHUDCameoWindow->winSetEnabledImage(0,nullptr);GadgetButtonDrawOverlayImage(m_rightHUDCameoWindow,nullptr);m_rightHUDCameoWindow->winSetStatus(WIN_STATUS_IMAGE);for(int i=0;i<MAX_UPGRADE_CAMEO_UPGRADES;++i)m_rightHUDUpgradeCameos[i]->winHide(TRUE);return;}')
    hints=root / 'GeneralsMD/Code/GameEngine/Source/GameClient/InGameUI.cpp'
    change(hints,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(hints,'if( draw && (t == GameMessage::MSG_DO_ATTACK_OBJECT_HINT || t == GameMessage::MSG_DO_ATTACK_OBJECT_AFTER_MOVING_HINT) )','if( draw && !zhmc::isExternalObject(draw->getObject()->getID()) && (t == GameMessage::MSG_DO_ATTACK_OBJECT_HINT || t == GameMessage::MSG_DO_ATTACK_OBJECT_AFTER_MOVING_HINT) )')
    selection=root / 'Core/GameEngine/Source/GameClient/MessageStream/SelectionXlat.cpp'
    change(selection,'#include "PreRTS.h"','#include "PreRTS.h"\nnamespace zhmc {void selectionProbe(unsigned int,int);}')
    text=selection.read_text(encoding='utf-8-sig');begin=text.index('Bool CanSelectDrawable(');end=text.index('\n//---',begin+20)
    body=text[begin:end]
    if 'zhmc::selectionProbe' not in body:
        body=body.replace('const Object *obj = draw->getObject();','const Object *obj = draw->getObject();\n    zhmc::selectionProbe((unsigned int)obj->getID(),0);')
        # Reasons follow the original gates, without changing their return values.
        pos=0;reason=1
        while True:
            at=body.find('return FALSE;',pos)
            if at<0:break
            replacement='zhmc::selectionProbe(draw&&draw->getObject()?(unsigned int)draw->getObject()->getID():0,'+str(reason)+');return FALSE;'
            body=body[:at]+replacement+body[at+len('return FALSE;'):];pos=at+len(replacement);reason+=1
        body=body.replace('return false;','zhmc::selectionProbe((unsigned int)obj->getID(),20);return false;').replace('return TRUE;','zhmc::selectionProbe((unsigned int)obj->getID(),99);return TRUE;')
        selection.write_text(text[:begin]+body+text[end:],encoding='utf-8',newline='\n')
    selection_list=root / 'Core/GameEngine/Source/GameClient/SelectionInfo.cpp'
    change(selection_list,'#include "PreRTS.h"','#include "PreRTS.h"\nnamespace zhmc {void selectionListProbe(unsigned int,int);}')
    text=selection_list.read_text(encoding='utf-8-sig');begin=text.index('Bool addDrawableToList(')
    body=text[begin:]
    if 'zhmc::selectionListProbe' not in body:
        reason=0
        def trace_return(match):
            nonlocal reason
            reason+=1
            return '(zhmc::selectionListProbe(draw&&draw->getObject()?(unsigned int)draw->getObject()->getID():0,'+str(reason)+'),'+match[0]+')'
        body=re.sub(r'(?<=return )(?:TRUE|FALSE)(?=;)',trace_return,body)
        selection_list.write_text(text[:begin]+body,encoding='utf-8',newline='\n')
    change(selection,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(selection,'if( draw->isDrawableEffectivelyHidden() )','if( draw->isDrawableEffectivelyHidden() && !zhmc::isExternalObject(obj->getID()) )')
    change(selection_list,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(selection_list,'PickDrawableStruct *pds = (PickDrawableStruct *) userData;','PickDrawableStruct *pds = (PickDrawableStruct *) userData;\n    const Bool hostMob=draw&&draw->getObject()&&zhmc::isExternalObject(draw->getObject()->getID());')
    change(selection_list,'if (!draw->getTemplate()->isAnyKindOf(pds->kindofsToMatch))','if (!hostMob && !draw->getTemplate()->isAnyKindOf(pds->kindofsToMatch))')
    change(selection_list,'if (draw->getFullyObscuredByShroud())','if (!hostMob && draw->getFullyObscuredByShroud())')
    change(selection_list,'if (draw->isDrawableEffectivelyHidden())','if (!hostMob && draw->isDrawableEffectivelyHidden())')
    # Older patch runs inserted the same probe declaration across a later include.
    for path,declaration in ((selection,'namespace zhmc {void selectionProbe(unsigned int,int);}'),(selection_list,'namespace zhmc {void selectionListProbe(unsigned int,int);}')):
        text=path.read_text(encoding='utf-8-sig')
        while text.count(declaration)>1:
            text=text.replace(declaration+'\n','',1)
        path.write_text(text,encoding='utf-8',newline='\n')

    pathfinder = root / 'Core/GameEngine/Source/GameLogic/AI/AIPathfind.cpp'
    change(pathfinder, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(pathfinder, '\tif (hasObstacle) {',
           '\tint zhmcType = zhmc::terrainType(i, j);\n'
           '\tif (zhmcType >= 0) type = static_cast<PathfindCell::CellType>(zhmcType);\n\tif (hasObstacle) {')
    terrain = root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameLogic/W3DTerrainLogic.cpp'
    change(terrain, '#include "Common/GameMemory.h"', '#include "Common/GameMemory.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(terrain, 'Real W3DTerrainLogic::getGroundHeight( Real x, Real y, Coord3D* normal ) const\n{',
           '''Real W3DTerrainLogic::getGroundHeight( Real x, Real y, Coord3D* normal ) const
{
    float zhmcHeight;
    if (zhmc::terrainHeight(x, y, zhmcHeight)) {
        if (normal) { normal->x = 0; normal->y = 0; normal->z = 1; }
        return zhmcHeight;
    }''')
    change(terrain, 'Real W3DTerrainLogic::getLayerHeight( Real x, Real y, PathfindLayerEnum layer, Coord3D* normal, Bool clip ) const\n{',
           '''Real W3DTerrainLogic::getLayerHeight( Real x, Real y, PathfindLayerEnum layer, Coord3D* normal, Bool clip ) const
{
    float zhmcHeight;
    if (layer == LAYER_GROUND && zhmc::terrainHeight(x, y, zhmcHeight)) {
        if (normal) { normal->x = 0; normal->y = 0; normal->z = 1; }
        return zhmcHeight;
    }''')
    text=terrain.read_text(encoding='utf-8-sig').replace('floor(x / 10)), static_cast<int>(floor(y / 10))','floor(x / PATHFIND_CELL_SIZE)), static_cast<int>(floor(y / PATHFIND_CELL_SIZE))')
    terrain.write_text(text,encoding='utf-8',newline='\n')
    change(terrain, 'Bool W3DTerrainLogic::isCliffCell( Real x, Real y) const\n{',
           '''Bool W3DTerrainLogic::isCliffCell( Real x, Real y) const
{
    int zhmcType = zhmc::terrainType(static_cast<int>(floor(x / PATHFIND_CELL_SIZE)), static_cast<int>(floor(y / PATHFIND_CELL_SIZE)));
    if (zhmcType >= 0) return zhmcType == 2 || zhmcType == 6;''')
    cmake = root / 'GeneralsMD/Code/Main/CMakeLists.txt'
    if '/FrameCapture.cpp"' not in cmake.read_text(encoding='utf-8'):
        change(cmake, 'add_executable(z_generals WIN32)',
               'add_executable(z_generals WIN32)\ntarget_sources(z_generals PRIVATE "' + addon.as_posix() + '/Bridge.cpp")')
    if '/WorldScene.cpp"' not in cmake.read_text(encoding='utf-8'):
        change(cmake, 'target_sources(z_generals PRIVATE "' + addon.as_posix() + '/Bridge.cpp")',
               'target_sources(z_generals PRIVATE "' + addon.as_posix() + '/Bridge.cpp" "' + addon.as_posix() + '/FrameCapture.cpp")\n'
               'target_include_directories(z_generals PRIVATE "${CMAKE_SOURCE_DIR}/Core/Libraries/Source/WWVegas/WW3D2")')
    change(cmake, 'target_sources(z_generals PRIVATE "' + addon.as_posix() + '/Bridge.cpp" "' + addon.as_posix() + '/FrameCapture.cpp")',
           'target_sources(z_generals PRIVATE "' + addon.as_posix() + '/Bridge.cpp" "' + addon.as_posix() + '/FrameCapture.cpp" "' + addon.as_posix() + '/WorldScene.cpp")')
    renderer = root / 'Core/Libraries/Source/WWVegas/WW3D2/dx8wrapper.cpp'
    change(renderer, '#include "dx8wrapper.h"', '#include "dx8wrapper.h"\n#include "' + addon.as_posix() + '/FrameCapture.h"')
    change(renderer, '\tDX8CALL(EndScene());', '\tDX8CALL(EndScene());\n    zhmc::captureFrame();')
    change(renderer, '\t\t\thr=_Get_D3D_Device8()->Present(nullptr, nullptr, nullptr, nullptr);',
           '\t\t\thr=GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0) ? D3D_OK : _Get_D3D_Device8()->Present(nullptr, nullptr, nullptr, nullptr);')
    display = root / 'Core/GameEngineDevice/Source/W3DDevice/GameClient/W3DDisplay.cpp'
    change(display, '#include "WW3D2/sortingrenderer.h"', '#include "WW3D2/sortingrenderer.h"\n#include "' + addon.as_posix() + '/WorldScene.h"')
    change(display, '\t\t\t\tdrawViews();\n\n\t\t\t\t// draw the user interface',
           '\t\t\t\tif (!zhmc::suppressNativeWorld()) drawViews();\n\n\t\t\t\t// draw the user interface')
    view = root / 'Core/GameEngineDevice/Source/W3DDevice/GameClient/W3DView.cpp'
    change(view, '#include "Lib/BaseType.h"', '#include "Lib/BaseType.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(view, 'View::WorldToScreenReturn W3DView::worldToScreenTriReturn( const Coord3D *w, ICoord2D *s )\n{',
           'View::WorldToScreenReturn W3DView::worldToScreenTriReturn( const Coord3D *w, ICoord2D *s )\n{\n    if (zhmc::hasHostView()) return static_cast<View::WorldToScreenReturn>(zhmc::hostWorldToScreen(w,s));')
    # Normalize previous integration prefixes too: stacked legacy hooks bypassed screen arguments.
    def view_prefix(signature, hook):
        value=view.read_text(encoding='utf-8-sig');start=value.index(signature+'\n{')+len(signature+'\n{')
        tail=value[start:];lines=tail.splitlines(keepends=True);used=0
        for line in lines:
            if not line.strip():used+=len(line);continue
            if line.lstrip().startswith('if (zhmc::'):used+=len(line);continue
            break
        value=value[:start]+'\n    '+hook+'\n'+tail[used:]
        view.write_text(value,encoding='utf-8',newline='\n')
    view_prefix('Drawable *W3DView::pickDrawable( const ICoord2D *screen, Bool forceAttack, PickType pickType )',
                'if (zhmc::hasHostView()) return zhmc::worldPointerDrawable(screen,static_cast<int>(pickType));')
    view_prefix('Bool W3DView::screenToTerrain( const ICoord2D *screen, Coord3D *world )',
                'if (zhmc::hasHostView()) return zhmc::hostScreenToTerrain(screen,world);')
    view_prefix('PlaneClass::IntersectionResType W3DView::screenToWorldAtZ( const ICoord2D *screen, Coord3D *world, Real z )',
                'if (zhmc::hasHostView()) return zhmc::hostScreenToWorld(screen,world,z) ? PlaneClass::INSIDE_SEGMENT : PlaneClass::NO_INTERSECTION;')
    change(view,'void W3DView::lookAt( const Coord3D *o )\n{','void W3DView::lookAt( const Coord3D *o )\n{\n    if (zhmc::hostLookAt(o)) return;')
    change(view,'void W3DView::scrollBy( const Coord2D *delta )\n{','void W3DView::scrollBy( const Coord2D *delta )\n{\n    if (zhmc::hostScroll(delta)) return;')
    radar=root / 'Core/GameEngineDevice/Source/W3DDevice/Common/System/W3DRadar.cpp'
    change(radar,'#include "Common/PlayerList.h"','#include "Common/PlayerList.h"\n#include "Common/ThingTemplate.h"')
    change(radar,'if (obj->getShroudedStatus(playerIndex) > OBJECTSHROUD_PARTIAL_CLEAR)',
           'if (!(GetEnvironmentVariableA("ZHMC_CAPTURE",nullptr,0)&&(obj->isKindOf(KINDOF_SUPPLY_SOURCE)||obj->getTemplate()->getName()=="TechOilDerrick")) && obj->getShroudedStatus(playerIndex) > OBJECTSHROUD_PARTIAL_CLEAR)')
    radar_header=root / 'Core/GameEngineDevice/Include/W3DDevice/Common/W3DRadar.h'
    change(radar_header,'\tvirtual void clearShroud() override;','\tvoid updateHostTerrain();\n\tvirtual void clearShroud() override;')
    change(radar,'void W3DRadar::draw( Int pixelX, Int pixelY, Int width, Int height )\n{','void W3DRadar::draw( Int pixelX, Int pixelY, Int width, Int height )\n{\n    updateHostTerrain();')
    for axis in ('x','y'):
        extent='width' if axis=='x' else 'height'
        cell='WIDTH' if axis=='x' else 'HEIGHT'
        for expression in (f'world[ i ].{axis}',f'ulWorld.{axis}'):
            old=f'{expression} / (m_mapExtent.{extent}() / RADAR_CELL_{cell})'
            change(radar,old,f'({expression} - m_mapExtent.lo.{axis}) / (m_mapExtent.{extent}() / RADAR_CELL_{cell})')
        old=f'pos->{axis} / (m_mapExtent.{extent}() / RADAR_CELL_{cell})'
        new=f'(pos->{axis} - m_mapExtent.lo.{axis}) / (m_mapExtent.{extent}() / RADAR_CELL_{cell})'
        text=radar.read_text(encoding='utf-8-sig')
        if old in text:
            if text.count(old)!=3:raise RuntimeError('Unexpected radar coordinate sites')
            radar.write_text(text.replace(old,new),encoding='utf-8',newline='\n')
    common_radar=root / 'Core/GameEngine/Source/Common/System/Radar.cpp'
    for axis in ('x','y'):
        change(common_radar,f'world->{axis} = {axis} * m_{axis}Sample;',f'world->{axis} = m_mapExtent.lo.{axis} + {axis} * m_{axis}Sample;')
        change(common_radar,f'radar->{axis} = world->{axis} / m_{axis}Sample;',f'radar->{axis} = (world->{axis} - m_mapExtent.lo.{axis}) / m_{axis}Sample;')
    if '/NativeRadar.cpp"' not in cmake.read_text(encoding='utf-8'):
        anchor='target_sources(z_generals PRIVATE "'+addon.as_posix()+'/Bridge.cpp" "'+addon.as_posix()+'/FrameCapture.cpp" "'+addon.as_posix()+'/WorldScene.cpp")'
        change(cmake,anchor,anchor+'\ntarget_sources(z_generals PRIVATE "'+addon.as_posix()+'/NativeRadar.cpp")')
    change(radar,'pixel->x = (radar->x * radarWidth / RADAR_CELL_WIDTH) + radarUpperLeftX;',
           'pixel->x = ((GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0) ? RADAR_CELL_WIDTH-1-radar->x : radar->x) * radarWidth / RADAR_CELL_WIDTH) + radarUpperLeftX;')
    # Native radar textures already flip V. Match the host camera's -X screen-right basis.
    change(radar,'\t\tconst unsigned int pixelColor = ARGB_Color_To_WW3D_Color(surfaceDesc.Format, argbColor);\n\n\t\t// draw the blip, but make sure the points are legal','\t\tconst unsigned int pixelColor = ARGB_Color_To_WW3D_Color(surfaceDesc.Format, argbColor);\n        if(GetEnvironmentVariableA("ZHMC_CAPTURE",nullptr,0)){\n            unsigned int outline=ARGB_Color_To_WW3D_Color(surfaceDesc.Format,argbColor&0xff000000);\n            for(int y=-1;y<=2;++y)for(int x=-1;x<=2;++x)if(legalRadarPoint(radarPoint.x+x,radarPoint.y+y))surface->Draw_Pixel(radarPoint.x+x,radarPoint.y+y,outline,bytesPerPixel,pBits,pitch);\n        }\n\n\t\t// draw the blip, but make sure the points are legal')
    common_text=common_radar.read_text(encoding='utf-8-sig')
    begin=common_text.index('Bool Radar::localPixelToRadar(')
    end=common_text.index('\n}',begin)
    segment=common_text[begin:end]
    if 'RADAR_CELL_WIDTH-1-radar->x' not in segment:
        segment=segment.replace('\treturn TRUE;','\tif (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) radar->x=RADAR_CELL_WIDTH-1-radar->x;\n\treturn TRUE;')
        common_radar.write_text(common_text[:begin]+segment+common_text[end:],encoding='utf-8',newline='\n')
    client_ui=root / 'GeneralsMD/Code/GameEngine/Source/GameClient/InGameUI.cpp'
    change(client_ui,'void InGameUI::postWindowDraw()\n{','void InGameUI::postWindowDraw()\n{\n    if (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) { m_renderFpsPointSize=0;m_systemTimePointSize=0;m_gameTimePointSize=0; }')
    lod = root / 'GeneralsMD/Code/GameEngine/Source/Common/GameLOD.cpp'
    change(lod, '\t\tTheWritableGlobalData->m_textureReductionFactor = requestedTextureReduction;',
           '\t\tif (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) requestedTextureReduction=0;\n\t\tTheWritableGlobalData->m_textureReductionFactor = requestedTextureReduction;')
    ui = root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DInGameUI.cpp'
    change(ui, '#include "W3DDevice/GameClient/W3DInGameUI.h"',
           '#include "W3DDevice/GameClient/W3DInGameUI.h"\n#include "' + addon.as_posix() + '/FrameCapture.h"')
    change(ui, 'void W3DInGameUI::draw()\n{', 'void W3DInGameUI::draw()\n{\n    zhmc::beginGuiLayer();')
    change(client_ui,'if (TheRecorder->isMultiplayer() && player->isPlayableSide())','if ((TheRecorder->isMultiplayer() || zhmc::hasHostView()) && player->isPlayableSide())')
    change(ui, '\t// repaint all our windows', '\tzhmc::drawWorldIndicators();\n\t// repaint all our windows')
    change(ui, '\tTheDisplay->endBatch();', '\tTheDisplay->endBatch();\n    zhmc::endGuiLayer();')
    winmain = root / 'GeneralsMD/Code/Main/WinMain.cpp'
    change(winmain,'\twindowStyle =  WS_POPUP|WS_VISIBLE;','\twindowStyle = WS_POPUP | (GetEnvironmentVariableA("ZHMC_CAPTURE",nullptr,0) ? 0 : WS_VISIBLE);')
    change(winmain,'HWND hWnd = CreateWindow( TEXT("Game Window"),','HWND hWnd = CreateWindowEx(GetEnvironmentVariableA("ZHMC_CAPTURE",nullptr,0) ? WS_EX_TOOLWINDOW|WS_EX_NOACTIVATE : 0, TEXT("Game Window"),')
    radius_decal=root/'Core/GameEngine/Source/GameClient/RadiusDecal.cpp'
    change(radius_decal,'\tresult.clear();','\tresult.clear();\n    // Headless authorities have no projected-shadow manager; this effect is visual only.\n    if(!TheProjectedShadowManager)return;')
    laser_header=root/'Core/GameEngineDevice/Include/W3DDevice/GameClient/Module/W3DLaserDraw.h'
    change(laser_header,'\tvirtual Real getLaserTemplateWidth() const override;','\tvirtual Real getLaserTemplateWidth() const override;\n    void hostLines(void* context,void (*emit)(void*,SegmentedLineClass*)) { auto data=getW3DLaserDrawModuleData();for(unsigned int i=0;i<data->m_numBeams*data->m_segments;++i)if(m_line3D[i])emit(context,m_line3D[i]); }')
    change(winmain, '\tif (!runWindowed)\n\t{\tSetWindowPos',
           '\tif (!GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) {\n\tif (!runWindowed)\n\t{\tSetWindowPos')
    change(winmain, '\tUpdateWindow( hWnd );', '\tUpdateWindow( hWnd );\n    }')
    w3d_mouse=root / 'Core/GameEngineDevice/Source/W3DDevice/GameClient/W3DMouse.cpp'
    change(w3d_mouse,'if (m_drawing)\t//only allow cursor to change when drawing the cursor (once per frame) to fix flickering.',
           'if (m_drawing || GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) // Host cursor export must track logic even when the native world draw is suppressed.')
    mouse = root / 'Core/GameEngineDevice/Source/Win32Device/GameClient/Win32Mouse.cpp'
    change(mouse, '#include "Win32Device/GameClient/Win32Mouse.h"', '#include "Win32Device/GameClient/Win32Mouse.h"\n#include "' + addon.as_posix() + '/FrameCapture.h"')
    change(mouse, '\t\t\t\t\t\tcursorResources[cursor][direction]=LoadCursorFromFile(fname.str());',
           '\t\t\t\t\t\tcursorResources[cursor][direction]=LoadCursorFromFile(fname.str());\n                        zhmc::cursorSource(cursorResources[cursor][direction],fname.str());')
    if 'zhmc::cursorSource(cursorResources[cursor][direction],resourcePath);' not in mouse.read_text(encoding='utf-8-sig'):
        change(mouse, '\t\t\t\tif (!loaded)\n\t\t\t\t\tcursorResources[cursor][direction]=LoadCursorFromFile(resourcePath);',
               '\t\t\t\tif (!loaded) {\n\t\t\t\t\tcursorResources[cursor][direction]=LoadCursorFromFile(resourcePath);\n                    zhmc::cursorSource(cursorResources[cursor][direction],resourcePath);\n                }')
    cursor_load=root / 'Core/GameEngineDevice/Source/Win32Device/GameClient/Win32Mouse.cpp'
    for filename in ('resourcePath','fname.str()'):
        line='zhmc::cursorSource(cursorResources[cursor][direction],'+filename+');'
        change(cursor_load,line,line+'\n                        zhmc::cursorChanged(cursor,cursorResources[cursor][direction],m_cursorInfo[cursor].fps,m_cursorInfo[cursor].numFrames);')
    terrain_los=root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameLogic/W3DTerrainLogic.cpp'
    change(terrain_los, 'Bool W3DTerrainLogic::isClearLineOfSight(const Coord3D& pos, const Coord3D& posOther) const\n{',
           'Bool W3DTerrainLogic::isClearLineOfSight(const Coord3D& pos, const Coord3D& posOther) const\n{\n    bool hostClear;if(zhmc::hostLineOfSight(pos,posOther,hostClear))return hostClear;')
    ui_header=root / 'GeneralsMD/Code/GameEngine/Include/GameClient/InGameUI.h'
    change(ui_header, '\tenum SelectionRules', '\tvoid setHostDragRegion(Bool active,const IRegion2D& region) { m_isDragSelecting=active;m_dragSelectRegion=region; }\n\tenum SelectionRules')
    hlod_header=root / 'GeneralsMD/Code/Libraries/Source/WWVegas/WW3D2/hlod.h'
    change(hlod_header, '\tHLodClass(const HLodClass & src);',
           '    void updateForHost() { Update_Sub_Object_Transforms(); }\n\tHLodClass(const HLodClass & src);')
    # Keep the original virtual tables intact; register actual W3DModelDraw objects.
    for header,legacy in [('GeneralsMD/Code/GameEngine/Include/Common/DrawModule.h','\tvirtual RenderObjClass* hostRenderObject() { return nullptr; }\n'),('Core/GameEngineDevice/Include/W3DDevice/GameClient/Module/W3DModelDraw.h','\tRenderObjClass* hostRenderObject() override { return m_renderObject; }\n')]:
        path=root/header;text=path.read_text(encoding='utf-8-sig')
        if legacy in text:path.write_text(text.replace(legacy,''),encoding='utf-8',newline='\n')
    model_source=root / 'Core/GameEngineDevice/Source/W3DDevice/GameClient/Drawable/Draw/W3DModelDraw.cpp'
    change(model_source,'#include "W3DDevice/GameClient/Module/W3DModelDraw.h"','#include "W3DDevice/GameClient/Module/W3DModelDraw.h"\n#include "'+addon.as_posix()+'/WorldScene.h"')
    change(model_source,'void W3DModelDraw::doDrawModule(const Matrix3D* transformMtx)\n{','void W3DModelDraw::doDrawModule(const Matrix3D* transformMtx)\n{\n    struct HostModelCapture { W3DModelDraw* module; ~HostModelCapture(){zhmc::rememberModel(static_cast<DrawModule*>(module),module->getRenderObject());} } hostModelCapture{this};')
    change(model_source,'W3DModelDraw::~W3DModelDraw()\n{','W3DModelDraw::~W3DModelDraw()\n{\n    zhmc::rememberModel(static_cast<DrawModule*>(this),nullptr);')
    # Lazily validated model barrels can precede their recoil vector after a state change.
    # Crash dump 20261013-120820 faulted on the empty vector in handleWeaponFireFX.
    change(model_source,'specificBarrelToUse > wbvec.size()','specificBarrelToUse >= static_cast<Int>(wbvec.size())')
    change(model_source,'WeaponRecoilInfo& recoil = m_weaponRecoilInfoVec[wslot][specificBarrelToUse];','if(m_weaponRecoilInfoVec[wslot].size()!=wbvec.size())rebuildWeaponRecoilInfo(m_curState);\n\t\tWeaponRecoilInfo& recoil = m_weaponRecoilInfoVec[wslot][specificBarrelToUse];')

    tracer=root / 'Core/GameEngineDevice/Source/W3DDevice/GameClient/Drawable/Draw/W3DTracerDraw.cpp'
    change(tracer,'#include "WW3D2/line3d.h"','#include "WW3D2/line3d.h"\n#include "'+addon.as_posix()+'/WorldScene.h"')
    change(tracer,'void W3DTracerDraw::doDrawModule(const Matrix3D* transformMtx)\n{','void W3DTracerDraw::doDrawModule(const Matrix3D* transformMtx)\n{\n    struct HostTracerCapture {const void* module;Line3DClass*& line;~HostTracerCapture(){zhmc::rememberModel(module,line);} } hostTracerCapture{static_cast<DrawModule*>(this),m_theTracer};')
    change(tracer,'W3DTracerDraw::~W3DTracerDraw()\n{','W3DTracerDraw::~W3DTracerDraw()\n{\n    zhmc::rememberModel(static_cast<DrawModule*>(this),nullptr);')
    line=root / 'Core/Libraries/Source/WWVegas/WW3D2/line3d.h'
    change(line,'\tprotected:', '\tconst Vector3* hostVertices() const {return vert;}\n\tconst Vector4& hostColor() const {return Color;}\n\tconst ShaderClass& hostShader() const {return Shader;}\n\tconst unsigned short* hostIndices() const;\n\tprotected:')
    line_source=root / 'Core/Libraries/Source/WWVegas/WW3D2/line3d.cpp'
    change(line_source,'void Line3DClass::Render(RenderInfoClass & rinfo)','const unsigned short* Line3DClass::hostIndices() const {return Indices;}\nvoid Line3DClass::Render(RenderInfoClass & rinfo)')
    w3d_ui_header=root / 'GeneralsMD/Code/GameEngineDevice/Include/W3DDevice/GameClient/W3DInGameUI.h'
    change(w3d_ui_header,'\tvirtual void draw() override; ///< Render the in-game user interface',
           '\tRenderObjClass* hostFeedback(int i) { if(i<MAX_MOVE_HINTS)return m_moveHintRenderObj[i];return i==MAX_MOVE_HINTS?m_buildingPlacementAnchor:m_buildingPlacementArrow; }\n\tint hostFeedbackCount() const { return MAX_MOVE_HINTS+2; }\n\tvirtual void draw() override; ///< Render the in-game user interface')
    for method in ('capture', 'releaseCapture'):
        anchor = 'void Win32Mouse::' + method + '()\n{'
        change(mouse, anchor, anchor + '\n    if (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) return;')
    cursor_hook = '\tMouse::setCursor( cursor );\n    if (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) {\n' \
           '        if (cursor >= FIRST_CURSOR && cursor < NUM_MOUSE_CURSORS) zhmc::cursorChanged(cursor, cursorResources[cursor][m_directionFrame], m_cursorInfo[cursor].fps, m_cursorInfo[cursor].numFrames);\n'
    cursor_hook += '        return;\n    }'
    if cursor_hook not in mouse.read_text(encoding='utf-8-sig'):
        legacy_cursor = '\tMouse::setCursor( cursor );\n    if (GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) return;'
        change(mouse, legacy_cursor if legacy_cursor in mouse.read_text(encoding='utf-8-sig') else '\tMouse::setCursor( cursor );', cursor_hook)
    change(mouse, '\t\t\tScreenToClient( ApplicationHWnd, &p );',
           '\t\t\tif (!GetEnvironmentVariableA("ZHMC_CAPTURE", nullptr, 0)) ScreenToClient( ApplicationHWnd, &p );')
    keyboard = root / 'Core/GameEngineDevice/Source/Win32Device/GameClient/Win32DIKeyboard.cpp'
    change(keyboard, '#include "WinMain.h"', '#include "WinMain.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(keyboard, 'void DirectInputKeyboard::openKeyboard()\n{',
           'void DirectInputKeyboard::openKeyboard()\n{\n    if (zhmc::remoteInput()) return;')
    change(keyboard, 'void DirectInputKeyboard::getKey( KeyboardIO *key )\n{',
           '''void DirectInputKeyboard::getKey( KeyboardIO *key )
{
    if (zhmc::remoteInput()) {
        unsigned char code; bool down; unsigned long timestamp;
        key->key = KEY_NONE;
        if (zhmc::popKey(code, down, timestamp)) {
            key->key = code; key->state = down ? KEY_STATE_DOWN : KEY_STATE_UP;
            key->keyDownTimeMsec = timestamp; key->status = KeyboardIO::STATUS_UNUSED;
        }
        return;
    }''')
    change(keyboard, 'Bool DirectInputKeyboard::getCapsState()\n{',
           'Bool DirectInputKeyboard::getCapsState()\n{\n    if (zhmc::remoteInput()) return false;')
    stream = root / 'Core/GameEngine/Source/Common/MessageStream.cpp'
    change(stream, '#include "Common/MessageStream.h"', '#include "Common/MessageStream.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(stream, 'GameMessage *MessageStream::appendMessage( GameMessage::Type type )\n{',
           'GameMessage *MessageStream::appendMessage( GameMessage::Type type )\n{\n    zhmc::traceMessage(type);')
    change(stream, '\t\t\t\tGameMessageDisposition disp = ss->m_translator->translateGameMessage(msg);',
           '\t\t\t\tGameMessageDisposition disp = ss->m_translator->translateGameMessage(msg);\n'
           '                zhmc::traceDisposition(msg->getType(), ss->m_priority, disp);')
    controlbar = root / 'Core/GameEngine/Source/GameClient/GUI/ControlBar/ControlBarCommandProcessing.cpp'
    change(controlbar, '#include "PreRTS.h"', '#include "PreRTS.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(controlbar, '\t\t\tCanMakeType cmt = TheBuildAssistant->canMakeUnit(factory, whatToBuild);',
           '\t\t\tCanMakeType cmt = TheBuildAssistant->canMakeUnit(factory, whatToBuild);\n            zhmc::traceGuiProduction(cmt);')
    windows = root / 'Core/GameEngine/Source/GameClient/GUI/GameWindowManager.cpp'
    change(windows, '#include "GameClient/GameWindowManager.h"', '#include "GameClient/GameWindowManager.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(windows, '\treturn window->m_input( window, msg, mData1, mData2 );',
           '\tWindowMsgHandledType zhmcResult = window->m_input( window, msg, mData1, mData2 );\n'
           '    zhmc::traceWindow(msg, window->winGetWindowId(), window->winGetInstanceData()->getState());\n'
           '    return zhmcResult;')
    translator = root / 'Core/GameEngine/Source/GameClient/MessageStream/WindowXlat.cpp'
    change(translator, '#include "GameClient/WindowXlat.h"', '#include "GameClient/WindowXlat.h"\n#include "' + addon.as_posix() + '/Bridge.h"')
    change(translator, 'GameMessageDisposition WindowTranslator::translateGameMessage(const GameMessage *msg)\n{',
           '''GameMessageDisposition WindowTranslator::translateGameMessage(const GameMessage *msg)
{
    if (msg->getType() == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN || msg->getType() == GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP)
        zhmc::traceRawPointer(msg->getType(), msg->getArgument(0)->pixel.x, msg->getArgument(0)->pixel.y,
            TheTacticalView && TheTacticalView->isMouseLocked());''')
    builds=root / 'GeneralsMD/Code/GameEngine/Source/Common/System/BuildAssistant.cpp'
    change(builds,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(builds,'struct SampleBuildData\n{','struct SampleBuildData\n{\n    Real hostRequiredHeight=0;')
    change(builds,'SampleBuildData *sampleData = (SampleBuildData *)userData;','SampleBuildData *sampleData = (SampleBuildData *)userData;\n    if (!zhmc::buildSurfaceAllowed(samplePoint->x,samplePoint->y,sampleData->hostRequiredHeight)) sampleData->terrainRestricted=TRUE;')
    change(builds,'SampleBuildData sampleData;','SampleBuildData sampleData;\n        sampleData.hostRequiredHeight=build->getTemplateGeometryInfo().getMaxHeightAbovePosition();')
    # Host camera projection must also serve original rectangle/similar-type selection.
    change(view,'#include "W3DDevice/GameClient/W3DView.h"','#include "W3DDevice/GameClient/W3DView.h"\nnamespace zhmc {void hostSelectionRegion(bool,unsigned int,int,int,int);}')
    old_region_tail='        }return matched;'
    traced_region_tail='        }zhmc::hostSelectionRegion(point,picked?(unsigned int)picked->getID():0,screenRegion?screenRegion->width():-1,screenRegion?screenRegion->height():-1,matched);return matched;'
    if old_region_tail in view.read_text(encoding='utf-8-sig'):
        change(view,old_region_tail,traced_region_tail)
    region_anchor='\tBool inside = FALSE;\n\tInt count = 0;\n\tDrawable *draw;'
    change(view,region_anchor,"""    if(zhmc::hasHostView()) {
        int matched=0;
        bool point=screenRegion&&screenRegion->width()==0&&screenRegion->height()==0;
        auto picked=point?pickDrawable(&screenRegion->lo,TRUE,PICK_TYPE_ALL_DRAWABLES):nullptr;
        for(auto d=TheGameClient->firstDrawable();d;d=d->getNextDrawable()) {
            if(point){if(d!=picked)continue;}
            else if(screenRegion){ICoord2D p;if(zhmc::hostWorldToScreen(d->getPosition(),&p)!=0||p.x<screenRegion->lo.x||p.x>screenRegion->hi.x||p.y<screenRegion->lo.y||p.y>screenRegion->hi.y)continue;}
            if(callback(d,userData))++matched;
        }zhmc::hostSelectionRegion(point,picked?(unsigned int)picked->getID():0,screenRegion?screenRegion->width():-1,screenRegion?screenRegion->height():-1,matched);return matched;
    }
"""+region_anchor)
    # Push-button clock flags are consumed on repaint. Keep them for both matte draws.
    for relative in ('Core/GameEngineDevice/Source/W3DDevice/GameClient/GUI/Gadget/W3DPushButton.cpp','Core/GameEngineDevice/Source/W3DDevice/GameClient/GUI/GUICallbacks/W3DMainMenu.cpp'):
        target=root / relative
        change(target,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/FrameCapture.h"') if '#include "PreRTS.h"' in target.read_text(encoding='utf-8-sig') else None
        value=target.read_text(encoding='utf-8-sig')
        if '/FrameCapture.h"' not in value:
            value='#include "'+addon.as_posix()+'/FrameCapture.h"\n'+value
        value=value.replace('pData->drawClock = NO_CLOCK;','if (!zhmc::preserveGuiClock()) pData->drawClock = NO_CLOCK;') if 'if (!zhmc::preserveGuiClock()) pData->drawClock' not in value else value
        target.write_text(value,encoding='utf-8',newline='\n')
    shadow_header=root / 'GeneralsMD/Code/GameEngineDevice/Include/W3DDevice/GameClient/W3DProjectedShadow.h'
    change(shadow_header,'\t\tvoid queueDecal(W3DProjectedShadow *shadow);',
           '\t\tvoid hostDecals(void (*emit)(void*,TextureClass*,const Matrix3D&,float,float,float,float,unsigned int,int,const void*),void* data);\n\t\tvoid queueDecal(W3DProjectedShadow *shadow);')
    shadow_source=root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/Shadow/W3DProjectedShadow.cpp'
    value=shadow_source.read_text(encoding='utf-8-sig')
    if 'void W3DProjectedShadowManager::hostDecals(' not in value:
        value+='\nvoid W3DProjectedShadowManager::hostDecals(void (*emit)(void*,TextureClass*,const Matrix3D&,float,float,float,float,unsigned int,int,const void*),void* data) {\n'
        value+='    for(auto s=m_decalList;s;s=s->m_next){if(!s->m_isEnabled||s->m_isInvisibleEnabled||!s->m_opacity||!s->m_shadowTexture[0])continue;Matrix3D t(true);if(s->m_robj)t=s->m_robj->Get_Transform();else{t.Rotate_Z(s->m_localAngle);t.Set_Translation(Vector3(s->m_x,s->m_y,s->m_z));}emit(data,s->m_shadowTexture[0]->getTexture(),t,s->m_decalSizeX,s->m_decalSizeY,s->m_decalOffsetU,s->m_decalOffsetV,s->m_diffuse,s->m_type,s);}\n}\n'
        shadow_source.write_text(value,encoding='utf-8',newline='\n')
    for name in ('drawRectClock','drawRemainingRectClock'):
        signature='void W3DDisplay::'+name+'(Int startX, Int startY, Int width, Int height, Int percent, UnsignedInt color)\n{'
        change(display,signature,signature+'\n    zhmc::traceGuiClock(percent);')
    # The supplied EA source has free placement rotation; the fork's 45-degree tweak is not retail behaviour.
    ui_source=root / 'GeneralsMD/Code/GameEngine/Source/GameClient/InGameUI.cpp'
    change(ui_source,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(ui_source,'\t\t\t\t\tif (isInForceAttackMode())\n\t\t\t\t\t{\n\t\t\t\t\t\tconst Real snapRadians',
           '\t\t\t\t\tif (isInForceAttackMode() && !zhmc::remoteInput())\n\t\t\t\t\t{\n\t\t\t\t\t\tconst Real snapRadians')
    change(display,'#include "'+addon.as_posix()+'/WorldScene.h"','#include "'+addon.as_posix()+'/WorldScene.h"\n#include "'+addon.as_posix()+'/FrameCapture.h"')
    radius_header=root / 'Core/GameEngine/Include/GameClient/RadiusDecal.h'
    change(radius_header,'\tBool isEmpty() const { return m_empty; }','\tShadow* hostShadow() const { return m_decal; }\n\tBool isEmpty() const { return m_empty; }')
    ui_header=root / 'GeneralsMD/Code/GameEngine/Include/GameClient/InGameUI.h'
    change(ui_header,'\tvirtual Real getPlacementAngle();','\tShadow* hostCursorShadow() const { return m_curRadiusCursor.hostShadow(); }\n\tBool hostIsPlacementDrawable(const Drawable* draw) const;\n\tvirtual Real getPlacementAngle();')
    value=ui_source.read_text(encoding='utf-8-sig')
    if 'Bool InGameUI::hostIsPlacementDrawable(' not in value:
        value+='\nBool InGameUI::hostIsPlacementDrawable(const Drawable* draw) const {for(int i=0;i<TheGlobalData->m_maxLineBuildObjects;++i)if(m_placeIcon[i]==draw)return TRUE;return FALSE;}\n'
        ui_source.write_text(value,encoding='utf-8',newline='\n')
    bib_header=root / 'GeneralsMD/Code/GameEngineDevice/Include/W3DDevice/GameClient/W3DBibBuffer.h'
    change(bib_header,'\tvoid renderBibs();','\tvoid hostBibs(void (*emit)(void*,TextureClass*,const Vector3*,unsigned int,const void*,Bool),void* data);\n\tvoid renderBibs();')
    terrain_header=root / 'Core/GameEngineDevice/Include/W3DDevice/GameClient/BaseHeightMap.h'
    change(terrain_header,'\tBaseHeightMapRenderObjClass();','\tW3DBibBuffer* hostBibBuffer() const { return m_bibBuffer; }\n\tBaseHeightMapRenderObjClass();')
    bib_source=root / 'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DBibBuffer.cpp'
    value=bib_source.read_text(encoding='utf-8-sig')
    if 'void W3DBibBuffer::hostBibs(' not in value:
        value+='''
void W3DBibBuffer::hostBibs(void (*emit)(void*,TextureClass*,const Vector3*,unsigned int,const void*,Bool),void* data) {
    if(!m_initialized)return;
    unsigned int color=0xffffffff; // The host lightmap supplies terrain lighting.
    for(int i=0;i<m_numBibs;++i)if(!m_bibs[i].m_unused)emit(data,m_bibs[i].m_highlight?m_highlightBibTexture:m_bibTexture,m_bibs[i].m_corners,color,&m_bibs[i],m_bibs[i].m_drawableID!=INVALID_DRAWABLE_ID);
}
'''
        bib_source.write_text(value,encoding='utf-8',newline='\n')
    value=bib_source.read_text(encoding='utf-8-sig')
    begin=value.index('void W3DBibBuffer::hostBibs(');body=value[begin:]
    body=re.sub(r'    auto a=TheGlobalData->m_terrainAmbient\[0\];auto d=TheGlobalData->m_terrainDiffuse\[0\];\n    unsigned int color=[^\n]+;', '    unsigned int color=0xffffffff; // The host lightmap supplies terrain lighting.',body)
    bib_source.write_text(value[:begin]+body,encoding='utf-8',newline='\n')
    for relative,signature in (
        ('GeneralsMD/Code/GameEngine/Source/GameLogic/AI/AIPlayer.cpp','void AIPlayer::update()\n{'),
        ('GeneralsMD/Code/GameEngine/Source/GameLogic/ScriptEngine/VictoryConditions.cpp','void VictoryConditions::update()\n{')):
        target=root / relative
        change(target,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
        change(target,signature,signature+'\n    if(zhmc::sandboxWorld())return;')
    change(view,'void W3DView::setAngle( Real radians )\n{','void W3DView::setAngle( Real radians )\n{\n    zhmc::hostAngle(radians);')
    value=view.read_text(encoding='utf-8-sig').replace('    zhmc::hostAngle(TheGlobalData->m_cameraAngle);\n','')
    view.write_text(value,encoding='utf-8',newline='\n')
    change(view,'\tView::setAngleToDefault();','\tView::setAngleToDefault();\n    zhmc::hostAngle(getAngle());')
    look=root / 'Core/GameEngine/Source/GameClient/MessageStream/LookAtXlat.cpp'
    change(look,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"')
    change(look,'if (TheInGameUI->isInForceAttackMode())\n\t\t\t\t{\n\t\t\t\t\tconst Real snapRadians','if (TheInGameUI->isInForceAttackMode() && !zhmc::remoteInput())\n\t\t\t\t{\n\t\t\t\t\tconst Real snapRadians')
    change(look,'\t\tcase GameMessage::MSG_FRAME_TICK:\n\t\t{','\t\tcase GameMessage::MSG_FRAME_TICK:\n\t\t{\n            if(zhmc::remoteInput()&&m_isScrolling&&m_scrollType==SCROLL_RMB&&!zhmc::rightHeld())stopScrolling();')
    audio=root/'Core/GameEngine/Source/Common/Audio/GameAudio.cpp'
    change(audio,'#include "PreRTS.h"','#include "PreRTS.h"\n#include "'+addon.as_posix()+'/Bridge.h"\nnamespace zhmc {bool hostAudioView(Coord3D&,Coord3D&,Coord3D&);}')
    change(audio,'void AudioManager::muteAudio( MuteAudioReason reason )\n{','void AudioManager::muteAudio( MuteAudioReason reason )\n{\n    if(reason==MuteAudioReason_WindowFocus&&zhmc::remoteInput())return;')
    change(audio,'\tCoord3D cameraPivot = TheTacticalView->getPosition();','\tCoord3D cameraPivot = TheTacticalView->getPosition();\n    Coord3D hostCameraPos,hostFacing;Bool hostAudio=zhmc::hostAudioView(cameraPivot,hostCameraPos,hostFacing);')
    change(audio,'\tlookTo.set(forward.X, forward.Y, forward.Z);','\tlookTo.set(forward.X, forward.Y, forward.Z);\n    if(hostAudio)lookTo=hostFacing;')
    change(audio,'\tCoord3D cameraPos = TheTacticalView->get3DCameraPosition();','\tCoord3D cameraPos = hostAudio?hostCameraPos:TheTacticalView->get3DCameraPosition();')
    from patch_paged_world import apply as patch_paged
    patch_paged(root,addon,change)
    from patch_native_lockstep import apply as patch_lockstep
    patch_lockstep(root,addon,change)
    # Canonical order also upgrades checkouts patched by earlier adapter versions.
    value=partition.read_text(encoding='utf-8-sig')
    for header in ('Bridge.h','PagedWorld.h','WorldScene.h'):
        value=re.sub(r'^#include "'+re.escape(addon.as_posix()+'/'+header)+r'"[^\n]*\n','',value,flags=re.M)
    value=value.replace('#include "PreRTS.h"','#include "PreRTS.h"\n'+''.join('#include "'+addon.as_posix()+'/'+h+'"\n' for h in ('Bridge.h','PagedWorld.h','WorldScene.h')).rstrip())
    partition.write_text(value,encoding='utf-8',newline='\n')
    print(f'Prepared original engine adapter at {sha}')

if __name__ == '__main__':
    main()
