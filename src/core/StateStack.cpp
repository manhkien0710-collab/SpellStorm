#include "core/StateStack.hpp"

#include <stdexcept>

namespace ss {

void StateStack::push(std::unique_ptr<State> state)
{
    if (!state) {
        throw std::invalid_argument("StateStack::push: null state");
    }
    m_pending.push_back({Action::Push, std::move(state)});
}

void StateStack::pop()
{
    m_pending.push_back({Action::Pop, nullptr});
}

void StateStack::clear()
{
    m_pending.push_back({Action::Clear, nullptr});
}

void StateStack::handleEvent(const sf::Event& event)
{
    if (!m_states.empty()) {
        m_states.back()->handleEvent(event);
    }
}

void StateStack::update(float dt)
{
    if (!m_states.empty()) {
        m_states.back()->update(dt);
    }
}

void StateStack::render(sf::RenderTarget& target) const
{
    if (m_states.empty()) {
        return;
    }
    // Find the lowest state that must be visible: walk down while overlays.
    std::size_t first = m_states.size() - 1;
    while (first > 0 && m_states[first]->isOverlay()) {
        --first;
    }
    for (std::size_t i = first; i < m_states.size(); ++i) {
        m_states[i]->render(target);
    }
}

void StateStack::applyPendingChanges()
{
    // Move the queue out first: constructors/destructors of states must not
    // see a half-processed queue.
    std::vector<PendingChange> changes;
    changes.swap(m_pending);

    for (PendingChange& change : changes) {
        switch (change.action) {
        case Action::Push:
            m_states.push_back(std::move(change.state));
            break;
        case Action::Pop:
            if (!m_states.empty()) {
                m_states.pop_back();
            }
            break;
        case Action::Clear:
            // Destroy from the top so overlays die before the state they reference.
            while (!m_states.empty()) {
                m_states.pop_back();
            }
            break;
        }
    }
}

} // namespace ss
