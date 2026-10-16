#pragma once

#include <iostream>
#include <string>
#include <cstdlib>

namespace ss::test {

inline int g_testsPassed = 0;
inline int g_testsFailed = 0;

inline void report(bool condition, const std::string& expr, const char* file, int line) {
    if (condition) {
        ++g_testsPassed;
    } else {
        ++g_testsFailed;
        std::cerr << "[FAIL] " << file << ":" << line << " - Condition: " << expr << std::endl;
    }
}

} // namespace ss::test

#define EXPECT_TRUE(cond) ss::test::report((cond), #cond, __FILE__, __LINE__)
#define EXPECT_FALSE(cond) ss::test::report(!(cond), "!(" #cond ")", __FILE__, __LINE__)
#define EXPECT_EQ(a, b) ss::test::report((a) == (b), #a " == " #b, __FILE__, __LINE__)
#define EXPECT_NEAR(a, b, eps) ss::test::report(std::abs((a) - (b)) <= (eps), "|" #a " - " #b "| <= " #eps, __FILE__, __LINE__)
#define EXPECT_GE(a, b) ss::test::report((a) >= (b), #a " >= " #b, __FILE__, __LINE__)
#define EXPECT_LE(a, b) ss::test::report((a) <= (b), #a " <= " #b, __FILE__, __LINE__)
#define EXPECT_GT(a, b) ss::test::report((a) > (b), #a " > " #b, __FILE__, __LINE__)
#define EXPECT_LT(a, b) ss::test::report((a) < (b), #a " < " #b, __FILE__, __LINE__)
#define EXPECT_NE(a, b) ss::test::report((a) != (b), #a " != " #b, __FILE__, __LINE__)

