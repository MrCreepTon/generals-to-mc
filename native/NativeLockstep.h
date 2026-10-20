// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <iosfwd>
#include <string>
class GameMessage;
namespace zhmc {
bool lockstepActive();
bool lockstepGameplayReady();
void beginSharedAuthority();
bool captureLockstepCommand(const GameMessage* message);
bool allowLockstepTick();
void finishLockstepTick();
void lockstepState(std::ostream& out);
void synchronizedHostCommand(const std::string& command);
}
