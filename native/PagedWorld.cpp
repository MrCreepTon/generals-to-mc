// SPDX-License-Identifier: GPL-3.0-or-later
#include "Precompiled/PreRTS.h"
#include "PagedWorld.h"
#include "Bridge.h"
#include "WorldScene.h"
#include "GameLogic/Pathfinder/PathfindCell.h"
#include "GameLogic/PartitionManager.h"
#include <unordered_map>
#include <memory>
#include <cstdlib>
namespace zhmc {
namespace {
constexpr int SIZE=32;
int pageIndex(int coordinate){return coordinate>=0?coordinate/SIZE:(coordinate-(SIZE-1))/SIZE;}
unsigned long long pageKey(int x,int y){return (static_cast<unsigned long long>(static_cast<unsigned int>(x))<<32)|static_cast<unsigned int>(y);}
std::unordered_map<unsigned long long,std::unique_ptr<PathfindCell[]>> paths;
std::unordered_map<unsigned long long,std::unique_ptr<PartitionCell[]>> partitions;
}
bool pagedWorld(){static const bool enabled=std::getenv("ZHMC_PAGED")!=nullptr;return worldTransportActive()&&enabled;}
PathfindCell* pagedPathCell(int x,int y){
    if(x<-200000000||y<-200000000||x>200000000||y>200000000)return nullptr;
    int px=pageIndex(x),py=pageIndex(y);auto& page=paths[pageKey(px,py)];
    if(!page){page=std::make_unique<PathfindCell[]>(SIZE*SIZE);for(int i=0;i<SIZE*SIZE;i++){page[i].setLayer(LAYER_GROUND);page[i].setType(PathfindCell::CELL_IMPASSABLE);page[i].setZone(1);}}
    auto cell=&page[(y-py*SIZE)*SIZE+(x-px*SIZE)];
    if(cell->getObstacleID()==INVALID_ID){int type=terrainType(x,y);cell->setType(static_cast<PathfindCell::CellType>(type<0?6:type));}
    return cell;
}
PartitionCell* pagedPartitionCell(int x,int y){
    if(x<-200000000||y<-200000000||x>200000000||y>200000000)return nullptr;
    int px=pageIndex(x),py=pageIndex(y);auto& page=partitions[pageKey(px,py)];
    if(!page){page=std::make_unique<PartitionCell[]>(SIZE*SIZE);for(int iy=0;iy<SIZE;iy++)for(int ix=0;ix<SIZE;ix++){
#ifdef PM_CACHE_TERRAIN_HEIGHT
        page[iy*SIZE+ix].init(px*SIZE+ix,py*SIZE+iy,-4096,4096);
#else
        page[iy*SIZE+ix].init(px*SIZE+ix,py*SIZE+iy);
#endif
    }}
    return &page[(y-py*SIZE)*SIZE+(x-px*SIZE)];
}
void resetPagedPath(){paths.clear();}
void resetPagedPartition(){partitions.clear();}
unsigned int pathPageCount(){return static_cast<unsigned int>(paths.size());}
unsigned int partitionPageCount(){return static_cast<unsigned int>(partitions.size());}
}
