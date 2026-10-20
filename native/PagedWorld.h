// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
class PathfindCell;
class PartitionCell;
namespace zhmc {
bool pagedWorld();
PathfindCell* pagedPathCell(int x,int y);
PartitionCell* pagedPartitionCell(int x,int y);
void resetPagedPath();
void resetPagedPartition();
unsigned int pathPageCount();
unsigned int partitionPageCount();
}
