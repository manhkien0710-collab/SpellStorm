#include "TestHelper.hpp"
#include <iostream>

void runCombatTests();
void runSpellsTests();
void runSystemsTests();
void runSimulationTests();
void runProgressionTests();
void runGameOverTests();
void runShootingModeTests();
void runAssetPipelineTests();

int main()
{
    std::cout << "Running SpellStorm Test Suite..." << std::endl;

    runCombatTests();
    runSpellsTests();
    runSystemsTests();
    runSimulationTests();
    runProgressionTests();
    runGameOverTests();
    runShootingModeTests();
    runAssetPipelineTests();

    std::cout << "Tests Passed: " << ss::test::g_testsPassed << std::endl;
    std::cout << "Tests Failed: " << ss::test::g_testsFailed << std::endl;

    if (ss::test::g_testsFailed > 0) {
        std::cerr << "SOME TESTS FAILED!" << std::endl;
        return 1;
    }

    std::cout << "ALL TESTS PASSED!" << std::endl;
    return 0;
}
