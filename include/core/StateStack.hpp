#pragma once

#include "core/State.hpp"

#include <memory>
#include <vector>

namespace ss {

// Stack of game states. Changes requested while a state is running
// (push/pop/clear) are deferred until applyPendingChanges(), so a state
// is never destroyed while one of its own member functions is executing.
class StateStack {
public:
    void push(std::unique_ptr<State> state);
    void pop();
    void clear();

    void handleEvent(const sf::Event& event);
    void update(float dt);
    void render(sf::RenderTarget& target) const;
    void applyPendingChanges();

    bool empty() const { return m_states.empty(); }
    std::size_t size() const { return m_states.size(); }

private:
    enum class Action { Push, Pop, Clear };
    struct PendingChange {
        Action action;
        std::unique_ptr<State> state;
    };

    std::vector<std::unique_ptr<State>> m_states;
    std::vector<PendingChange> m_pending;
};

} // namespace ss
