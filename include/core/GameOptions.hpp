#pragma once

#include <string>

namespace ss {

// Runtime options parsed from the command line (see main.cpp).
struct GameOptions {
    float matchDuration = 300.f;   // seconds the player must survive to win
    unsigned seed = 0;             // 0 = random
    bool autoplay = false;         // a bot plays the game (used for testing/demo)
    std::string screenshotDir;     // if not empty, screenshots are saved here periodically
    float screenshotInterval = 4.f;
    float quitAfter = 0.f;         // > 0: close the game automatically after N seconds
};

} // namespace ss
