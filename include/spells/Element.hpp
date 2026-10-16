#pragma once

#include <SFML/Graphics/Color.hpp>

#include <array>
#include <cstddef>
#include <cstdint>
#include <initializer_list>

namespace ss {

// To add a new element: add an enum value before Count, add a row to the
// info table in Element.cpp, and register its spell in SpellLibrary.cpp.
// Player, HUD, upgrades and key bindings pick it up automatically.
enum class Element : std::uint8_t { Fire = 0, Ice, Lightning, Wind, Earth, Count };

constexpr std::size_t kElementCount = static_cast<std::size_t>(Element::Count);

struct ElementInfo {
    const char* name;
    const char* trait;
    sf::Color color;
};

const ElementInfo& elementInfo(Element element);
constexpr std::size_t indexOf(Element element) { return static_cast<std::size_t>(element); }
Element elementFromIndex(std::size_t index); // throws std::out_of_range
std::array<Element, kElementCount> allElements();

// Compact, order-independent set of elements (bit mask). Used as the key
// of combination recipes, so {Fire, Wind} == {Wind, Fire}.
class ElementSet {
public:
    ElementSet() = default;
    ElementSet(std::initializer_list<Element> elements);

    void add(Element element);
    void remove(Element element);
    bool contains(Element element) const;
    int count() const;
    std::uint32_t mask() const { return m_mask; }

    bool operator==(const ElementSet& other) const { return m_mask == other.m_mask; }
    bool operator!=(const ElementSet& other) const { return m_mask != other.m_mask; }

private:
    std::uint32_t m_mask = 0;
};

} // namespace ss
