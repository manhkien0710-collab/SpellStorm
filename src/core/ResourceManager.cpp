#include "core/ResourceManager.hpp"

#include <iostream>
#include <stdexcept>
#include <vector>

namespace ss {

std::vector<std::string> ResourceManager::candidatePaths(const std::string& subDir, const std::string& fileName,
                                                         const std::string& fallback)
{
    std::vector<std::string> paths;

    // Direct path if already relative to project or absolute
    if (!fileName.empty() && (fileName[0] == '/' || fileName.rfind("assets/", 0) == 0)) {
        paths.push_back(fileName);
    }

    const std::string relPrefix = subDir.empty() ? "" : (subDir + "/");

    paths.push_back("assets/" + relPrefix + fileName);
    paths.push_back("../assets/" + relPrefix + fileName);
    paths.push_back("../../assets/" + relPrefix + fileName);

#ifdef SPELLSTORM_ASSET_DIR
    paths.push_back(std::string(SPELLSTORM_ASSET_DIR) + "/" + relPrefix + fileName);
#endif

    if (!fallback.empty()) {
        paths.push_back(fallback);
    }

    return paths;
}

// --- Fonts ---

bool ResourceManager::loadFont(FontId id, const std::string& fileName, const std::string& systemFallback)
{
    // Avoid re-loading if already loaded
    if (m_fonts.find(id) != m_fonts.end()) {
        return true;
    }

    auto font = std::make_unique<sf::Font>();
    for (const std::string& path : candidatePaths("fonts", fileName, systemFallback)) {
        if (font->loadFromFile(path)) {
            m_fonts[id] = std::move(font);
            return true;
        }
    }
    return false;
}

const sf::Font& ResourceManager::font(FontId id) const
{
    const auto it = m_fonts.find(id);
    if (it == m_fonts.end()) {
        throw std::out_of_range("ResourceManager: font not loaded");
    }
    return *it->second;
}

const sf::Font* ResourceManager::getFont(FontId id) const noexcept
{
    const auto it = m_fonts.find(id);
    if (it == m_fonts.end()) {
        return nullptr;
    }
    return it->second.get();
}

bool ResourceManager::hasFont(FontId id) const noexcept
{
    return m_fonts.count(id) > 0;
}

// --- Textures ---

bool ResourceManager::loadTexture(TextureId id, const std::string& relativePath)
{
    if (m_textures.find(id) != m_textures.end()) {
        return true;
    }

    auto tex = std::make_unique<sf::Texture>();

    // Search under textures/ subdirectory first, then general assets/
    std::vector<std::string> paths = candidatePaths("textures", relativePath);
    for (const std::string& p : candidatePaths("", relativePath)) {
        paths.push_back(p);
    }

    for (const std::string& path : paths) {
        if (tex->loadFromFile(path)) {
            // Enable smooth nearest-neighbor filtering for crisp pixel art
            tex->setSmooth(false);
            m_textures[id] = std::move(tex);
            return true;
        }
    }

    std::cerr << "[ResourceManager] Notice: Texture not found: " << relativePath
              << " (will use procedural visual fallback)\n";
    return false;
}

bool ResourceManager::loadTexture(const std::string& name, const std::string& relativePath)
{
    if (m_namedTextures.find(name) != m_namedTextures.end()) {
        return true;
    }

    auto tex = std::make_unique<sf::Texture>();
    std::vector<std::string> paths = candidatePaths("textures", relativePath);
    for (const std::string& p : candidatePaths("", relativePath)) {
        paths.push_back(p);
    }

    for (const std::string& path : paths) {
        if (tex->loadFromFile(path)) {
            tex->setSmooth(false);
            m_namedTextures[name] = std::move(tex);
            return true;
        }
    }

    std::cerr << "[ResourceManager] Notice: Named texture not found: " << relativePath
              << " (will use procedural visual fallback)\n";
    return false;
}

const sf::Texture& ResourceManager::texture(TextureId id) const
{
    const auto it = m_textures.find(id);
    if (it == m_textures.end()) {
        throw std::out_of_range("ResourceManager: texture not loaded");
    }
    return *it->second;
}

const sf::Texture* ResourceManager::getTexture(TextureId id) const noexcept
{
    const auto it = m_textures.find(id);
    if (it == m_textures.end()) {
        return nullptr;
    }
    return it->second.get();
}

const sf::Texture* ResourceManager::getTexture(const std::string& name) const noexcept
{
    const auto it = m_namedTextures.find(name);
    if (it == m_namedTextures.end()) {
        return nullptr;
    }
    return it->second.get();
}

bool ResourceManager::hasTexture(TextureId id) const noexcept
{
    return m_textures.count(id) > 0;
}

bool ResourceManager::hasTexture(const std::string& name) const noexcept
{
    return m_namedTextures.count(name) > 0;
}

// --- Sprite Sheets ---

void ResourceManager::registerSpriteSheet(TextureId id, int frameWidth, int frameHeight,
                                          int columns, int rows, int totalFrames)
{
    const sf::Texture* tex = getTexture(id);
    SpriteSheet sheet;
    sheet.texture = tex;
    sheet.frameWidth = frameWidth;
    sheet.frameHeight = frameHeight;
    sheet.columns = columns;
    sheet.rows = rows;
    sheet.totalFrames = totalFrames;
    m_spriteSheets[id] = sheet;
}

const SpriteSheet* ResourceManager::getSpriteSheet(TextureId id) const noexcept
{
    const auto it = m_spriteSheets.find(id);
    if (it == m_spriteSheets.end()) {
        return nullptr;
    }
    return &it->second;
}

sf::IntRect ResourceManager::getFrameRect(TextureId id, int frameIndex) const
{
    const SpriteSheet* sheet = getSpriteSheet(id);
    if (sheet) {
        return sheet->getFrameRect(frameIndex);
    }
    const sf::Texture* tex = getTexture(id);
    if (tex) {
        return sf::IntRect(0, 0, static_cast<int>(tex->getSize().x), static_cast<int>(tex->getSize().y));
    }
    return sf::IntRect();
}

// --- Default Assets Initializer ---

bool ResourceManager::loadDefaultAssets()
{
    // 1. Fonts
    loadFont(FontId::Body, "DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
    loadFont(FontId::Bold, "DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
    loadFont(FontId::Title, "DejaVuSerif-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf");

    // 2. Character & Enemy Textures
    loadTexture(TextureId::Player, "characters/wizard.png");
    loadTexture(TextureId::MeleeEnemy, "enemies/melee_fiend.png");
    loadTexture(TextureId::RangedEnemy, "enemies/ranged_cultist.png");
    loadTexture(TextureId::TankEnemy, "enemies/tank_golem.png");

    // 3. Projectiles
    loadTexture(TextureId::ProjectileFire, "projectiles/spell_fire.png");
    loadTexture(TextureId::ProjectileIce, "projectiles/spell_ice.png");
    loadTexture(TextureId::ProjectileLightning, "projectiles/spell_lightning.png");
    loadTexture(TextureId::ProjectileWind, "projectiles/spell_wind.png");
    loadTexture(TextureId::ProjectileEarth, "projectiles/spell_earth.png");
    loadTexture(TextureId::ProjectileEnemy, "projectiles/projectile_enemy.png");

    // 4. Pickups
    loadTexture(TextureId::GemCyan, "pickups/gem_cyan.png");
    loadTexture(TextureId::GemEmerald, "pickups/gem_emerald.png");
    loadTexture(TextureId::GemGold, "pickups/gem_gold.png");

    // 5. Environment
    loadTexture(TextureId::ArenaFloor, "environment/floor_tile.png");

    // Register sprite sheets
    if (hasTexture(TextureId::Player)) {
        registerSpriteSheet(TextureId::Player, 32, 32, 2, 1, 2);
    }
    if (hasTexture(TextureId::MeleeEnemy)) {
        registerSpriteSheet(TextureId::MeleeEnemy, 32, 32, 1, 1, 1);
    }
    if (hasTexture(TextureId::RangedEnemy)) {
        registerSpriteSheet(TextureId::RangedEnemy, 32, 32, 1, 1, 1);
    }
    if (hasTexture(TextureId::TankEnemy)) {
        registerSpriteSheet(TextureId::TankEnemy, 48, 48, 1, 1, 1);
    }

    return hasFont(FontId::Body);
}

} // namespace ss
