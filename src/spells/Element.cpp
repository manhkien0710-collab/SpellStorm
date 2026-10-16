#include "spells/Element.hpp"

#include <stdexcept>

namespace ss {

namespace {

const std::array<ElementInfo, kElementCount>& infoTable()
{
    // Function-local static: initialised on first use, no static-init-order issues.
    static const std::array<ElementInfo, kElementCount> table = {{
        {"Fire", "Burns targets over time", sf::Color(255, 120, 40)},
        {"Ice", "Slows targets", sf::Color(120, 210, 255)},
        {"Lightning", "Chains to nearby foes", sf::Color(255, 236, 90)},
        {"Wind", "Fast, piercing, knockback", sf::Color(150, 255, 190)},
        {"Earth", "Heavy hits, big knockback", sf::Color(196, 140, 84)},
    }};
    return table;
}

} // namespace

const ElementInfo& elementInfo(Element element)
{
    const std::size_t index = indexOf(element);
    if (index >= kElementCount) {
        throw std::out_of_range("elementInfo: invalid element");
    }
    return infoTable()[index];
}

Element elementFromIndex(std::size_t index)
{
    if (index >= kElementCount) {
        throw std::out_of_range("elementFromIndex: invalid index");
    }
    return static_cast<Element>(index);
}

std::array<Element, kElementCount> allElements()
{
    std::array<Element, kElementCount> result{};
    for (std::size_t i = 0; i < kElementCount; ++i) {
        result[i] = static_cast<Element>(i);
    }
    return result;
}

ElementSet::ElementSet(std::initializer_list<Element> elements)
{
    for (Element e : elements) {
        add(e);
    }
}

void ElementSet::add(Element element) { m_mask |= (1u << indexOf(element)); }
void ElementSet::remove(Element element) { m_mask &= ~(1u << indexOf(element)); }
bool ElementSet::contains(Element element) const { return (m_mask & (1u << indexOf(element))) != 0; }

int ElementSet::count() const
{
    int n = 0;
    for (std::uint32_t m = m_mask; m != 0; m &= m - 1) {
        ++n;
    }
    return n;
}

} // namespace ss
