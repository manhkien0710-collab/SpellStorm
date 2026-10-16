#pragma once

#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/Texture.hpp>

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace ss {

enum class FontId {
    Body,
    Bold,
    Title
};

enum class TextureId {
    Player,
    MeleeEnemy,
    RangedEnemy,
    TankEnemy,
    ProjectileFire,
    ProjectileIce,
    ProjectileLightning,
    ProjectileWind,
    ProjectileEarth,
    ProjectileEnemy,
    GemCyan,
    GemEmerald,
    GemGold,
    ArenaFloor,
    // Milestone 6 additions:
    TileGrass,
    TileDirt,
    TileStone,
    TileWater,
    TileSand,
    TileBridgeH,
    TileBridgeV,
    DecoTreeOak,
    DecoTreePine,
    DecoRock,
    DecoRockLarge,
    DecoBush,
    DecoLog,
    DecoRuins,
    DecoTent,
    DecoCampfire,
    PlayerDown,
    PlayerUp,
    PlayerLeft,
    PlayerRight,
    ProjectilePlayer
};

// Lightweight sprite sheet descriptor for grid-based multi-frame animation.
struct SpriteSheet {
    const sf::Texture* texture = nullptr;
    int frameWidth = 0;
    int frameHeight = 0;
    int columns = 1;
    int rows = 1;
    int totalFrames = 1;

    sf::IntRect getFrameRect(int frameIndex) const {
        if (!texture || frameWidth <= 0 || frameHeight <= 0) {
            return sf::IntRect();
        }
        if (frameIndex < 0) {
            frameIndex = 0;
        }
        if (totalFrames > 0) {
            frameIndex = frameIndex % totalFrames;
        }
        const int col = (columns > 0) ? (frameIndex % columns) : 0;
        const int row = (columns > 0) ? (frameIndex / columns) : 0;
        return sf::IntRect(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }
};

// Owns all loaded graphical resources (fonts and textures) via strict RAII.
// Handles multi-directory path candidate resolution, prevents duplicate loading,
// and guarantees that shared assets outlive any sprites referencing them.
class ResourceManager {
public:
    ResourceManager() = default;
    ~ResourceManager() = default;

    ResourceManager(const ResourceManager&) = delete;
    ResourceManager& operator=(const ResourceManager&) = delete;
    ResourceManager(ResourceManager&&) noexcept = default;
    ResourceManager& operator=(ResourceManager&&) noexcept = default;

    // --- Fonts ---
    // Tries assets/fonts/<file> in candidate directories, then the given fallback.
    bool loadFont(FontId id, const std::string& fileName, const std::string& systemFallback = "");
    const sf::Font& font(FontId id) const; // throws std::out_of_range if not loaded
    const sf::Font* getFont(FontId id) const noexcept;
    bool hasFont(FontId id) const noexcept;

    // --- Textures ---
    // Searches candidate directories for texture files.
    bool loadTexture(TextureId id, const std::string& relativePath);
    bool loadTexture(const std::string& name, const std::string& relativePath);

    const sf::Texture& texture(TextureId id) const; // throws std::out_of_range if not loaded
    const sf::Texture* getTexture(TextureId id) const noexcept;
    const sf::Texture* getTexture(const std::string& name) const noexcept;
    bool hasTexture(TextureId id) const noexcept;
    bool hasTexture(const std::string& name) const noexcept;

    // --- Sprite Sheets ---
    void registerSpriteSheet(TextureId id, int frameWidth, int frameHeight, int columns, int rows, int totalFrames);
    const SpriteSheet* getSpriteSheet(TextureId id) const noexcept;
    sf::IntRect getFrameRect(TextureId id, int frameIndex) const;

    // High-level initialization helper that loads standard bundled fonts and textures.
    // Missing individual textures log a notice and do not throw or crash.
    bool loadDefaultAssets();

    // Resolves a relative subpath against standard candidate asset root directories.
    static std::vector<std::string> candidatePaths(const std::string& subDir, const std::string& fileName,
                                                   const std::string& fallback = "");

private:
    std::map<FontId, std::unique_ptr<sf::Font>> m_fonts;
    std::map<TextureId, std::unique_ptr<sf::Texture>> m_textures;
    std::map<std::string, std::unique_ptr<sf::Texture>> m_namedTextures;
    std::map<TextureId, SpriteSheet> m_spriteSheets;
};

} // namespace ss
