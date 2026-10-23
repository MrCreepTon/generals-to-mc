"""Sparse adapters around the original SAGE path and partition algorithms."""
import re

def apply(root,addon,change):
    include='#include "'+addon.as_posix()+'/PagedWorld.h"'
    constants=root/'Core/GameEngine/Include/GameLogic/Pathfinder/PathfindConstants.h'
    change(constants,'constexpr const Real PATHFIND_CELL_SIZE_F = 10.0f;','constexpr const Real PATHFIND_CELL_SIZE_F = 4.0f;')
    change(constants,'constexpr const UnsignedInt PATHFIND_CELL_SIZE = 10;','constexpr const UnsignedInt PATHFIND_CELL_SIZE = 4;')
    path_header=root/'Core/GameEngine/Include/GameLogic/AIPathfind.h'
    change(path_header,'#pragma once','#pragma once\n'+include)
    change(path_header,'inline PathfindCell *Pathfinder::getCell( PathfindLayerEnum layer, Int x, Int y )\n{','inline PathfindCell *Pathfinder::getCell( PathfindLayerEnum layer, Int x, Int y )\n{\n    if(zhmc::pagedWorld()&&layer==LAYER_GROUND&&(x<m_extent.lo.x||y<m_extent.lo.y||x>m_extent.hi.x||y>m_extent.hi.y))return zhmc::pagedPathCell(x,y);')
    change(path_header,'\tBool overflow = false;','\tif(zhmc::pagedWorld())return false;\n\tBool overflow = false;')
    change(path_header,'protected:\n\tvirtual Path *internalFindPath','    Int hostPathProbe(Object* object,const Coord3D* goal);\nprotected:\n\tvirtual Path *internalFindPath')
    path=root/'Core/GameEngine/Source/GameLogic/AI/AIPathfind.cpp'
    change(path,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    # The finite retail map/hierarchy bounded a failed search implicitly. Sparse
    # Minecraft terrain has neither bound. Count expanded nodes, not wall time,
    # so every lockstep participant takes the same partial-path/failure branch.
    budget='''
namespace zhmc {
static unsigned int limitedPathSearches=0;
unsigned int pathSearchLimitCount(){return limitedPathSearches;}
static bool pathSearchStep(unsigned int& visited){
    if(!pagedWorld()||++visited<=4096)return true;
    ++limitedPathSearches;return false;
}
}
'''
    value=path.read_text(encoding='utf-8-sig').replace('\nnamespace zhmc {bool worldTransportActive();}','')
    if 'unsigned int pathSearchLimitCount()' not in value:
        value=value.replace(include,include+budget,1)
    value=value.replace('while( !m_openList.empty() )',
        'for(unsigned int hostVisited=0; !m_openList.empty() && zhmc::pathSearchStep(hostVisited); )')
    # Retail's straight-line shortcut repeats the entire goal ray at every A*
    # node. On unknown/blocked sparse destinations that becomes quadratic work.
    # Keep the normal original eight-neighbour expansion on imported terrain.
    value=value.replace('if (attackDistance==NO_ATTACK && !m_isTunneling && !locomotorSet.isDownhillOnly() && goalCell)',
        'if (!zhmc::pagedWorld() && attackDistance==NO_ATTACK && !m_isTunneling && !locomotorSet.isDownhillOnly() && goalCell)')
    path.write_text(value,encoding='utf-8',newline='\n')
    value=path.read_text(encoding='utf-8-sig').replace('floor(from->x/10)','floor(from->x/PATHFIND_CELL_SIZE)').replace('floor(from->y/10)','floor(from->y/PATHFIND_CELL_SIZE)')
    path.write_text(value,encoding='utf-8',newline='\n')
    if 'Int Pathfinder::hostPathProbe(' not in value:
        value+='''
Int Pathfinder::hostPathProbe(Object* obj,const Coord3D* goal){
    Int radius;Bool center;getRadiusAndCenter(obj,radius,center);ICoord2D cell;worldToCell(goal,&cell);auto layer=TheTerrainLogic->getLayerForDestination(goal);auto& loco=obj->getAI()->getLocomotorSet();Int result=m_isMapReady?1:0;
    if(checkDestination(obj,cell.x,cell.y,layer,radius,center))result|=2;
    if(validMovementPosition(false,layer,loco,goal))result|=4;
    if(validMovementPosition(false,obj->getLayer(),loco,obj->getPosition()))result|=8;
    TCheckMovementInfo info;info.cell=cell;info.layer=layer;info.radius=radius;info.centerInCell=center;info.considerTransient=false;info.acceptableSurfaces=loco.getValidSurfaces();if(checkForMovement(obj,info))result|=16;
    if(!checkCellOutsideExtents(cell))result|=32;
    if(layer==LAYER_GROUND)result|=64;
    return result;
}
'''
        path.write_text(value,encoding='utf-8',newline='\n')
    # Signed host coordinates are valid. Retail's >=0 guards leak unit and goal
    # reservations behind every unit outside the stock map. Keep the unset value
    # outside the supported host range, including the real cell (-1,-1).
    value=path.read_text(encoding='utf-8-sig')
    for name in ('curCell','goalCell'):
        old=f'{name}.x>=0 && {name}.y>=0'
        new=f'(zhmc::pagedWorld()?({name}.x!=-2147483647&&{name}.y!=-2147483647):({old}))'
        if new not in value:value=value.replace(old,new)
    old='if (cell.x<0 || cell.y<0) return false;'
    new='if (zhmc::pagedWorld()?(cell.x==-2147483647||cell.y==-2147483647):(cell.x<0||cell.y<0)) return false;'
    value=value.replace(old,new).replace('newCell.x = newCell.y = -1;','newCell.x = newCell.y = -2147483647;')
    path.write_text(value,encoding='utf-8',newline='\n')
    # Keep original allocation bounds; only footprint access becomes sparse.
    value=path.read_text(encoding='utf-8-sig')
    value=re.sub(r'm_map\[([a-z]+)\]\[([a-z]+)\]\.',r'getCell(LAYER_GROUND,\1,\2)->',value)
    value=value.replace('&m_map[i][j]','getCell(LAYER_GROUND,i,j)')
    for condition in ('cx >= 0 && cy >= 0 && cx < m_extent.hi.x && cy < m_extent.hi.y','i >= 0 && j >= 0 && i < m_extent.hi.x && j < m_extent.hi.y'):
        value=value.replace('if ('+condition+')','if (zhmc::pagedWorld() || ('+condition+'))')
    value=value.replace('\tcellBounds.intersectWith(m_extent);','\tif(!zhmc::pagedWorld())cellBounds.intersectWith(m_extent);')
    value=value.replace('\tm_zoneManager.updateZonesForModify(m_map, m_layers, cellBounds, m_extent);','\tif(!zhmc::pagedWorld())m_zoneManager.updateZonesForModify(m_map, m_layers, cellBounds, m_extent);')
    path.write_text(value,encoding='utf-8',newline='\n')
    change(path,include,include+'\nnamespace zhmc {bool worldTransportActive();}')
    # Minecraft columns already classify collision/steps; SAGE cliff dilation closes one-block doorways.
    text=path.read_text(encoding='utf-8-sig').replace('zhmc::terrainType(i,j) < 0 && (getCell','!zhmc::worldTransportActive() && (getCell')
    path.write_text(text,encoding='utf-8',newline='\n')
    old='if (getCell(LAYER_GROUND,i,j)->getType() & PathfindCell::CELL_CLIFF) {'
    new='if (!zhmc::worldTransportActive() && (getCell(LAYER_GROUND,i,j)->getType() & PathfindCell::CELL_CLIFF)) {'
    if text.count(old) not in (0,2): raise RuntimeError('Unexpected cliff dilation integration points')
    if old in text: path.write_text(text.replace(old,new),encoding='utf-8',newline='\n')
    change(path,'enum {MAX_RADIUS = 2};','enum {MAX_RADIUS = (50 + PATHFIND_CELL_SIZE - 1) / PATHFIND_CELL_SIZE / 2};')
    change(path,'void Pathfinder::reset()\n{','void Pathfinder::reset()\n{\n    zhmc::resetPagedPath();')
    change(path,'Pathfinder::~Pathfinder()\n{','Pathfinder::~Pathfinder()\n{\n    zhmc::resetPagedPath();')
    change(path,'void Pathfinder::clip( Coord3D *from, Coord3D *to )\n{','void Pathfinder::clip( Coord3D *from, Coord3D *to )\n{\n    if(zhmc::pagedWorld())return;')
    change(path,'\tm_logicalExtent = bounds;','\tm_logicalExtent = bounds;\n    if(zhmc::pagedWorld()){m_logicalExtent.lo.x=m_logicalExtent.lo.y=-200000000;m_logicalExtent.hi.x=m_logicalExtent.hi.y=200000000;}')
    signature='Path *Pathfinder::findPath( Object *obj, const LocomotorSet& locomotorSet, const Coord3D *from,\n\t\t\t\t\t\t\t\t\t\t\t\t\t const Coord3D *rawTo)\n{'
    change(path,signature,signature+'\n    if(zhmc::pagedWorld())return internalFindPath(obj,locomotorSet,from,rawTo);')
    signature='Bool Pathfinder::checkForMovement(const Object *obj, TCheckMovementInfo &info)\n{\n\tinfo.allyFixedCount = 0;\n\tinfo.allyMoving = false;\n\tinfo.allyGoal = false;\n\tinfo.enemyFixed = false;'
    change(path,signature,signature+'\n    if(zhmc::pagedWorld()&&!zhmc::terrainCellLoaded(info.cell.x,info.cell.y))return false;')
    value=path.read_text(encoding='utf-8-sig')
    signature=re.search(r'Path \*Pathfinder::findClosestHierarchicalPath\([^)]*\)\n\{',value).group()
    change(path,signature,signature+'\n    if(zhmc::pagedWorld())return nullptr;')
    # Group movement calls findGroundPath directly, bypassing findPath's sparse guard.
    # Its stock hierarchical prepass dereferences map[x][y] and the finite zone blocks.
    # Return "no hierarchy" at the shared entry; findGroundPath retains its original
    # cell-by-cell fallback and original locomotors on the loaded sparse cells.
    value=path.read_text(encoding='utf-8-sig')
    signature=re.search(r'Path \*Pathfinder::internal_findHierarchicalPath\([^)]*\)\n\{',value).group()
    change(path,signature,signature+'\n    if(zhmc::pagedWorld())return nullptr;')
    value=path.read_text(encoding='utf-8-sig')
    for name in ('findClosestPath','internalFindPath'):
        signature=re.search(r'Path \*Pathfinder::'+name+r'\([^)]*\)\n\{',value).group()
        change(path,signature,signature+'\n    if(zhmc::pagedWorld()&&!zhmc::terrainCellLoaded((int)floor(from->x/PATHFIND_CELL_SIZE),(int)floor(from->y/PATHFIND_CELL_SIZE)))return nullptr;')
    signature='\t\t\tnewCell = getCell(parentCell->getLayer(), newCellCoord.x, newCellCoord.y );'
    guard='\n            if(zhmc::pagedWorld()&&!zhmc::terrainCellLoaded(newCellCoord.x,newCellCoord.y))continue;'
    value=path.read_text(encoding='utf-8-sig')
    value=value.replace(guard,'')
    if value.count(signature)!=3:raise RuntimeError('Expected all three original neighbor loops')
    path.write_text(value.replace(signature,signature+guard),encoding='utf-8',newline='\n')
    transition='\n            if(zhmc::pagedWorld()&&parentCell->getLayer()==LAYER_GROUND&&!(locomotorSet.getValidSurfaces()&LOCOMOTORSURFACE_CLIFF)&&!zhmc::hostPathEdgeAllowed(parentCell->getXIndex(),parentCell->getYIndex(),newCellCoord.x,newCellCoord.y))continue;'
    ground_transition=transition.replace('&&!(locomotorSet.getValidSurfaces()&LOCOMOTORSURFACE_CLIFF)','')
    value=path.read_text(encoding='utf-8-sig').replace(transition,'').replace(ground_transition,'')
    value=value.replace(signature+guard,signature+guard+transition)
    begin=value.index('Path *Pathfinder::findGroundPath(');end=value.index('Path *Pathfinder::findHierarchicalPath(',begin)
    value=value[:begin]+value[begin:end].replace(transition,ground_transition)+value[end:]
    path.write_text(value,encoding='utf-8',newline='\n')
    value=path.read_text(encoding='utf-8-sig')
    anchor=re.search(r'Bool Pathfinder::validMovementPosition\( Bool isCrusher, LocomotorSurfaceTypeMask acceptableSurfaces,\s*PathfindCell \*toCell, PathfindCell \*fromCell \)\n\{',value).group()
    # Cell coordinates live in optional search info: a fresh neighbour and line
    # callbacks need not have it. Never ask them for getXIndex/getYIndex here.
    value=path.read_text(encoding='utf-8-sig')
    value=value.replace('\n    if(zhmc::pagedWorld()&&toCell&&fromCell&&fromCell->getLayer()==LAYER_GROUND&&toCell->getLayer()==LAYER_GROUND&&!(acceptableSurfaces&LOCOMOTORSURFACE_CLIFF)&&!zhmc::hostPathEdgeAllowed(fromCell->getXIndex(),fromCell->getYIndex(),toCell->getXIndex(),toCell->getYIndex()))return false;','')
    value=value.replace('\n    if(zhmc::pagedWorld()&&from&&from->getLayer()==LAYER_GROUND&&to->getLayer()==LAYER_GROUND&&!zhmc::hostPathEdgeAllowed(from->getXIndex(),from->getYIndex(),to_x,to_y))return 1;','')
    path.write_text(value,encoding='utf-8',newline='\n')
    for struct,callback in (('LinePassableStruct','linePassableCallback'),('GroundPathPassableStruct','groundPathPassableCallback')):
        change(path,'struct '+struct+'\n{','struct '+struct+'\n{\n    ICoord2D hostPrevious;')
        declaration='\tconst '+struct+'* d = (const '+struct+'*)userData;'
        surfaces='&&!(d->acceptableSurfaces&LOCOMOTORSURFACE_CLIFF)' if struct=='LinePassableStruct' else ''
        replacement='\t'+struct+'* d = ('+struct+'*)userData;\n    if(zhmc::pagedWorld()&&from&&from->getLayer()==LAYER_GROUND&&to->getLayer()==LAYER_GROUND'+surfaces+'&&!zhmc::hostPathEdgeAllowed(d->hostPrevious.x,d->hostPrevious.y,to_x,to_y))return 1;\n    d->hostPrevious.x=to_x;d->hostPrevious.y=to_y;'
        change(path,declaration,replacement)
        change(path,'\t'+struct+' info;','\t'+struct+' info;\n    worldToCell(&startWorld,&info.hostPrevious);')
    cell=root/'Core/GameEngine/Include/GameLogic/Pathfinder/PathfindCell.h'
    for axis in ('X','Y'):change(cell,'UnsignedShort get'+axis+'Index()','Int get'+axis+'Index()')
    info=root/'Core/GameEngine/Include/GameLogic/Pathfinder/PathfindCellInfo.h'
    change(info,'UnsignedShort m_totalCost, m_costSoFar;','UnsignedInt m_totalCost, m_costSoFar;')
    zone=root/'Core/GameEngine/Source/GameLogic/AI/Pathfinder/PathfindZoneManager.cpp'
    change(zone,'#include "GameLogic/Pathfinder/PathfindZoneManager.h"','#include "GameLogic/Pathfinder/PathfindZoneManager.h"\n'+include)
    for signature,statement in (
        ('Bool PathfindZoneManager::isPassable(Int cellX, Int cellY) const','return true;'),
        ('Bool PathfindZoneManager::clipIsPassable(Int cellX, Int cellY) const','return true;'),
        ('zoneStorageType PathfindZoneManager::getEffectiveTerrainZone(zoneStorageType zone) const','return 1;')):
        change(zone,signature+'\n{',signature+'\n{\n    if(zhmc::pagedWorld())'+statement)
    value=zone.read_text(encoding='utf-8-sig')
    signature=re.search(r'zoneStorageType PathfindZoneManager::getEffectiveZone\([^)]*\) const\n\{',value).group()
    change(zone,signature,signature+'\n    if(zhmc::pagedWorld())return 1;')
    part_header=root/'GeneralsMD/Code/GameEngine/Include/GameLogic/PartitionManager.h'
    change(part_header,'#pragma once','#pragma once\n'+include)
    value=part_header.read_text(encoding='utf-8-sig')
    value=re.sub(r'\bShort(\s+m_cell[XY];)',r'Int\1',value)
    old='return (x < 0 || y < 0 || x >= m_cellCountX || y >= m_cellCountY) ? nullptr : &m_cells[y * m_cellCountX + x];'
    new='return (x < 0 || y < 0 || x >= m_cellCountX || y >= m_cellCountY) ? (zhmc::pagedWorld()?zhmc::pagedPartitionCell(x,y):nullptr) : &m_cells[y * m_cellCountX + x];'
    value=value.replace(old,new);part_header.write_text(value,encoding='utf-8',newline='\n')
    part=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/PartitionManager.cpp'
    change(part,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    # Original shroud/threat/value row operations otherwise bypass getCellAt with dense pointer arithmetic.
    value=part.read_text(encoding='utf-8-sig')
    for match in list(re.finditer(r'void hLine\w+\(Int x1, Int x2, Int y, void \*\w+\)\n\{',value))[::-1]:
        begin=match.end();end=value.index('\n}',begin);body=value[begin:end]
        if 'ThePartitionManager->getCellAt(x,y)' in body:continue
        condition='y < 0 || y >= ThePartitionManager->m_cellCountY || x1 >= ThePartitionManager->m_cellCountX || x2 < 0'
        body=body.replace('if ('+condition+')','if (!zhmc::pagedWorld()&&('+condition+'))')
        body=re.sub(r'\tPartitionCell\* cell = [^\n]+\n','',body)
        body=body.replace('for (Int x = x1; x <= x2; ++x, ++cell)','for (Int x = x1; x <= x2; ++x)')
        condition='x < 0 || x >= ThePartitionManager->m_cellCountX'
        body=body.replace('if ('+condition+')','if (!zhmc::pagedWorld()&&('+condition+'))')
        body=body.replace('\t\t\tcontinue;','\t\t\tcontinue;\n        auto cell=ThePartitionManager->getCellAt(x,y);if(!cell)continue;',1)
        value=value[:begin]+body+value[end:]
    part.write_text(value,encoding='utf-8',newline='\n')
    # Shutdown has already detached native objects from their cells at this point.
    change(part,'\tdelete [] m_cells;','\tzhmc::resetPagedPartition();\n\tdelete [] m_cells;')
    terrain=root/'GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameLogic/W3DTerrainLogic.cpp'
    change(terrain,'#include "Common/GameMemory.h"','#include "Common/GameMemory.h"\n'+include)
    signature='void W3DTerrainLogic::getMaximumPathfindExtent( Region3D *extent ) const\n{'
    change(terrain,signature,signature+'\n    if(zhmc::pagedWorld()){extent->lo.x=extent->lo.y=-2000000000.f;extent->hi.x=extent->hi.y=2000000000.f;extent->lo.z=-4096;extent->hi.z=4096;return;}')
    group=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/AI/AIGroup.cpp'
    change(group,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    change(group,include,include+'\nnamespace zhmc {void hostRequestedGroupMove(const Coord3D* goal);}')
    signature='void AIGroup::groupMoveToPosition( const Coord3D *p_posIn, Bool addWaypoint, CommandSourceType cmdSource )\n{'
    change(group,signature,signature+'''
    // Keep the logical click when the stock group prepass chooses a temporary
    // reachable endpoint on the currently loaded terrain. Known destinations
    // retain the original per-unit formation goals unchanged.
    struct HostRequestedMoveScope {
        HostRequestedMoveScope(const Coord3D* goal){zhmc::hostRequestedGroupMove(goal);}
        ~HostRequestedMoveScope(){zhmc::hostRequestedGroupMove(nullptr);}
    } hostRequestedMoveScope(cmdSource==CMD_FROM_PLAYER&&!addWaypoint?p_posIn:nullptr);
''')
    value=group.read_text(encoding='utf-8-sig')
    signature=re.search(r'static void clampToMap\([^)]*\)[^{]*\{',value).group()
    change(group,signature,signature+'\n    if(zhmc::pagedWorld())return;')
    signature='void clampWaypointPosition( Coord3D &position, Int margin )\n{'
    change(group,signature,signature+'\n    if(zhmc::pagedWorld())return;')
    ai=root/'GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate.cpp'
    change(ai,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    value=ai.read_text(encoding='utf-8-sig')
    value=re.sub(r'(m_pathfind(?:Goal|Cur)Cell\.[xy](?: = m_pathfind(?:Goal|Cur)Cell\.[xy])? = )-1;',r'\g<1>-2147483647;',value)
    old='m_pathfindGoalCell.x >= 0 && m_pathfindGoalCell.y >= 0'
    new='(zhmc::pagedWorld()?(m_pathfindGoalCell.x!=-2147483647&&m_pathfindGoalCell.y!=-2147483647):('+old+'))'
    if new not in value:value=value.replace(old,new)
    old='if (goalCell.x>0 && goalCell.y>0) {'
    new='if (zhmc::pagedWorld()?(goalCell.x!=-2147483647&&goalCell.y!=-2147483647):(goalCell.x>0 && goalCell.y>0)) {'
    value=value.replace(old,new)
    # A nearby structure need not have an AI update module.
    value=value.replace('if (!aiOther->isDoingGroundMovement()) {','if (aiOther && !aiOther->isDoingGroundMovement()) {')
    ai.write_text(value,encoding='utf-8',newline='\n')
    signature='void AIUpdateInterface::setGoalPositionClipped(const Coord3D* in, CommandSourceType cmdSource)\n{'
    change(ai,signature,signature+'\n    if(zhmc::pagedWorld()){getStateMachine()->setGoalPosition(in);return;}')
    change(ai,'if (cmdSource==CMD_FROM_PLAYER) {\n\t\t// Clip to playable area.','if (cmdSource==CMD_FROM_PLAYER && !zhmc::pagedWorld()) {\n\t\t// Clip to playable area.')
    build=root/'GeneralsMD/Code/GameEngine/Source/Common/System/BuildAssistant.cpp'
    change(build,'#include "PreRTS.h"','#include "PreRTS.h"\n'+include)
    for target in ('terrainExtent','sampleData.mapRegion'):
        old='TheTerrainLogic->getExtent( &'+target+' );'
        change(build,old,'if(zhmc::pagedWorld())TheTerrainLogic->getMaximumPathfindExtent(&'+target+');else '+old)
    cmake=root/'GeneralsMD/Code/Main/CMakeLists.txt'
    value=cmake.read_text(encoding='utf-8-sig')
    legacy=' "'+addon.as_posix()+'/PagedWorld.cpp")'
    value='\n'.join(line.replace(legacy,')') if '/WorldScene.cpp' in line else line for line in value.split('\n'))
    value=value.replace('\ntarget_sources(z_generals PRIVATE)\n','\n')
    entry='target_sources(z_generals PRIVATE "'+addon.as_posix()+'/PagedWorld.cpp")'
    if entry not in value:value+='\n'+entry+'\n'
    cmake.write_text(value,encoding='utf-8',newline='\n')
