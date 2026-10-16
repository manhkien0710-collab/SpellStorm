#pragma once

#include "core/GameOptions.hpp"

#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/Window/Event.hpp>

namespace sf {
class RenderWindow;
}

namespace ss {

class ResourceManager;
class StateStack;

// Shared services every state may use. All members are non-owning
// references to objects owned by Game, which outlives every state.
struct StateContext {
    sf::RenderWindow& window;
    ResourceManager& resources;
    StateStack& stack;
    const GameOptions& options;
};

// Abstract base for every screen (menu, gameplay, pause, ...).
// The StateStack only calls update()/handleEvent() on the top state,
// which is how Pause and Level Up really freeze the gameplay below them.
class State {
public:
    explicit State(StateContext context) : m_context(context) {}
    virtual ~State() = default;

    State(const State&) = delete;
    State& operator=(const State&) = delete;

    virtual void handleEvent(const sf::Event& event) = 0;
    virtual void update(float dt) = 0;
    virtual void render(sf::RenderTarget& target) const = 0;

    // Overlay states are drawn on top of the state below them.
    virtual bool isOverlay() const { return false; }

protected:
    const StateContext& context() const { return m_context; }

private:
    StateContext m_context;
};

} // namespace ss
