// SPDX-License-Identifier: GPL-3.0-or-later
// Feed Minecraft map colours to the original radar, retaining its icons and UI.
#include "Precompiled/PreRTS.h"
#include "NativeRadar.h"
#include "W3DDevice/Common/W3DRadar.h"
#include "GameLogic/PartitionManager.h"
#include "Common/PlayerList.h"
#include "Common/Player.h"
#include "texture.h"
#include "surfaceclass.h"
#include "GameClient/Image.h"
#include <filesystem>
#include <fstream>
#include <vector>
#include <cmath>
#include <cstdlib>

namespace { unsigned long long revision=0; float bounds[4]={}; std::filesystem::file_time_type lastStamp; }
namespace zhmc {
void radarState(std::ostream& out) { out<<",\"radarRevision\":"<<revision<<",\"radarMinX\":"<<bounds[0]<<",\"radarMinY\":"<<bounds[1]<<",\"radarSpan\":"<<bounds[2]; }
}
void W3DRadar::updateHostTerrain() {
    const char* root=std::getenv("ZHMC_BRIDGE_DIR");
    if(!root||!m_terrainTexture||!m_shroudTexture)return;
    std::error_code error;auto path=std::filesystem::path(root)/"radar.bin";auto stamp=std::filesystem::last_write_time(path,error);
    if(error||stamp==lastStamp)return;
    std::ifstream file(path,std::ios::binary);unsigned int header[3];float nextBounds[4];unsigned long long nextRevision;
    if(!file.read(reinterpret_cast<char*>(header),sizeof(header))||header[0]!=0x524d485a||header[1]!=128||header[2]!=128)return;
    if(!file.read(reinterpret_cast<char*>(nextBounds),sizeof(nextBounds))||!file.read(reinterpret_cast<char*>(&nextRevision),8))return;
    for(float value:nextBounds)if(!std::isfinite(value))return;
    if(nextBounds[2]<10||nextBounds[3]<10||nextBounds[2]>100000||nextBounds[3]>100000)return;
    std::vector<unsigned int> pixels(128*128);
    if(!file.read(reinterpret_cast<char*>(pixels.data()),pixels.size()*4)||file.peek()!=EOF)return;
    file.close(); // Release the snapshot before the producer atomically replaces it.
    auto surface=m_terrainTexture->Get_Surface_Level();if(!surface)return;
    SurfaceClass::SurfaceDescription desc;surface->Get_Description(desc);int pitch;void* bits=surface->Lock(&pitch);
    if(!bits){surface->Release_Ref();return;}
    for(int y=0;y<m_textureHeight;++y)for(int x=0;x<m_textureWidth;++x) {
        auto color=pixels[(y*128/m_textureHeight)*128+x*128/m_textureWidth];
        surface->Draw_Pixel(x,y,ARGB_Color_To_WW3D_Color(desc.Format,color),Get_Bytes_Per_Pixel(desc.Format),bits,pitch);
    }
    surface->Unlock();surface->Release_Ref();
    m_mapExtent.lo.x=nextBounds[0];m_mapExtent.lo.y=nextBounds[1];m_mapExtent.hi.x=nextBounds[0]+nextBounds[2];m_mapExtent.hi.y=nextBounds[1]+nextBounds[3];
    m_xSample=nextBounds[2]/static_cast<float>(RADAR_CELL_WIDTH);m_ySample=nextBounds[3]/static_cast<float>(RADAR_CELL_HEIGHT);
    // Rebuild fog against the moving window using the original partition visibility.
    surface=m_shroudTexture->Get_Surface_Level();
    if(surface){surface->Get_Description(desc);bits=surface->Lock(&pitch);
        if(bits){auto player=ThePlayerList->getLocalPlayer();
            for(int y=0;y<m_textureHeight;++y)for(int x=0;x<m_textureWidth;++x){
                Coord3D p={nextBounds[0]+x*nextBounds[2]/m_textureWidth,nextBounds[1]+y*nextBounds[3]/m_textureHeight,0};
                auto status=player&&ThePartitionManager?ThePartitionManager->getShroudStatusForPlayer(player->getPlayerIndex(),&p):CELLSHROUD_SHROUDED;
                int alpha=status==CELLSHROUD_SHROUDED?148:status==CELLSHROUD_FOGGED?92:0;
                surface->Draw_Pixel(x,y,ARGB_Color_To_WW3D_Color(desc.Format,static_cast<unsigned int>(alpha)<<24),Get_Bytes_Per_Pixel(desc.Format),bits,pitch);
            }
            surface->Unlock();
        }surface->Release_Ref();
    }
    Region2D uv;uv.lo.x=1;uv.hi.x=0;uv.lo.y=1;uv.hi.y=0;
    m_terrainImage->setUV(&uv);m_overlayImage->setUV(&uv);m_shroudImage->setUV(&uv);
    m_reconstructViewBox=TRUE;updateObjectTexture(m_overlayTexture);
    revision=nextRevision;std::copy(nextBounds,nextBounds+4,bounds);lastStamp=stamp;
}
