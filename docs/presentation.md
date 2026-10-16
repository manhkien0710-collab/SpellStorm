# Presentation Plan & Defense Q&A Guide

This guide prepares the student for an **8 to 12-minute project defense** before an academic evaluation committee for the Object-Oriented Programming (OOP) course. It includes a structured time-budgeted outline and answers to likely instructor questions.

---

## 1. Presentation Outline (8–12 Minutes)

```text
[0:00 - 1:30]  1. Introduction & Problem Statement
               - Motivation: Why architecting 2D games with clean OOP is non-trivial.
               - Overview of Spell Storm: 2D arena action, magic elements, combinations.

[1:30 - 3:30]  2. High-Level Architecture & Clean Separation
               - Presentation (SFML) vs Domain Logic (Entities, Spells, Combat).
               - Decoupling StatusEffect & Character from graphics APIs.
               - Demonstrating the Mermaid UML class diagram.

[3:30 - 6:30]  3. Core OOP Principles in Action
               - Encapsulation: HealthComponent & ElementSet bitmask invariants.
               - Inheritance & Polymorphism: Entity -> Character -> Enemy; virtual onTick() and apply().
               - Composition over Inheritance: Composing HitEffects inside SpellDefinition.

[6:30 - 8:30]  4. Live Demo & Gameplay Showcase
               - Launching application, showcasing WASD movement, procedural rendering.
               - Elemental infusion (1:Fire, 2:Ice, 3:Lightning, 4:Wind, 5:Earth).
               - Casting dual combinations (Fire + Wind = Firestorm; Ice + Lightning = Frost Tempest).
               - Testing damage mitigation & healing HUD.

[8:30 - 10:00] 5. Memory Safety & Automated Verification
               - RAII, zero raw owning pointers, smart pointer ownership semantics.
               - 32/32 Unit test assertions passed.
               - AddressSanitizer & UndefinedBehaviorSanitizer zero-leak proof.

[10:00 - 12:00] 6. Conclusion & Transition to Defense Q&A
```

---

## 2. Instructor Defense Q&A (Frequently Asked Questions)

### Q1: Vì sao sử dụng kế thừa (Inheritance) trong dự án?
> **Trả lời:**
> Kế thừa được sử dụng độc quyền cho mối quan hệ bản thể học thực sự (**"is-a"**). 
> Cụ thể, `Entity` $\rightarrow$ `Character` $\rightarrow$ `Enemy`:
> - `Character` *là một* `Entity` vì nó có tọa độ, bán kính va chạm, và ID trong đấu trường, nhưng được chuyên biệt hóa thêm vận tốc và trạng thái.
> - `Enemy` và `PlayerWizard` *là các* `Character` vì đều chịu ảnh hưởng của máu, giáp và hiệu ứng làm chậm, nhưng mở rộng thêm logic điều khiển (bàn phím hoặc thuật toán AI).
> Kế thừa ở đây giúp tái sử dụng mã nguồn một cách tự nhiên và cho phép quản lý danh sách thực thể đa hình mà không cần lặp lại thuộc tính.

---

### Q2: Vì sao lại dùng Composition thay vì kế thừa cho `HealthComponent` và `SpellDefinition`?
> **Trả lời:**
> Áp dụng nguyên lý **"Composition over Inheritance"** mang lại hai lợi ích lớn:
> 1. **Tránh bùng nổ phân cấp lớp (Class Explosion)**: Nếu dùng kế thừa cho phép thuật, ta sẽ phải tạo hàng chục class như `FireBall`, `BouncingLightning`, `ExplodingFrost`. Thay vào đó, một class `SpellDefinition` duy nhất có thể cấu thành từ danh sách các chiến lược con `std::vector<std::unique_ptr<HitEffect>>`.
> 2. **Tách biệt trách nhiệm (SRP)**: `HealthComponent` chỉ quản lý tính toán máu, giáp và giảm sát thương. Nhờ vậy, ta có thể gắn `HealthComponent` vào các vật thể tĩnh (như trụ bảo vệ, rương gỗ) mà không bắt chúng phải kế thừa toàn bộ thuộc tính di chuyển hay phản lực của `Character`. Ngoài ra, `HealthComponent` có thể unit test 100% độc lập mà không cần môi trường đồ họa.

---

### Q3: Virtual function hoạt động thế nào trong C++?
> **Trả lời:**
> Trong C++, khi một class có ít nhất một hàm `virtual`, compiler sẽ tạo ra một **bảng hàm ảo (vtable - virtual method table)** cho class đó, chứa các con trỏ hàm trỏ đến các cài đặt tương ứng. Mỗi đối tượng của class đó sẽ chứa một con trỏ ẩn gọi là **vptr** trỏ tới vtable của kiểu thực tế lúc runtime.
> Khi gọi phương thức qua con trỏ hoặc tham chiếu lớp cơ sở (ví dụ `effect->apply(context)`), chương trình sẽ tra cứu gián tiếp qua vptr đến vtable để nhảy đến đúng hàm của lớp con (`DamageEffect::apply` hoặc `AreaEffect::apply`). Cơ chế này gọi là **dynamic dispatch (liên kết muộn)**.

---

### Q4: Khi nào cần Virtual Destructor? Trong code lớp nào có virtual destructor?
> **Trả lời:**
> Virtual destructor là **bắt buộc** khi một class đóng vai trò là polymorphic base class và đối tượng có thể bị giải phóng (delete) thông qua con trỏ của lớp cơ sở. Nếu không có virtual destructor, compiler chỉ gọi destructor của lớp cơ sở, dẫn đến rò rỉ tài nguyên (resource leak) hoặc hành vi không xác định (Undefined Behavior) đối với các thành phần của lớp dẫn xuất.
> Trong dự án:
> - `Entity::~Entity()`
> - `StatusEffect::~StatusEffect()`
> - `HitEffect::~HitEffect()`
> - `State::~State()`
> Tất cả đều được khai báo `virtual ~ClassName() = default;`.

---

### Q5: Quản lý bộ nhớ ra sao? Làm thế nào tránh Memory Leak và Dangling Pointer?
> **Trả lời:**
> 1. **Nguyên tắc RAII (Resource Acquisition Is Initialization)**: Không sử dụng `new` hoặc `delete` thủ công trong toàn bộ codebase.
> 2. **Sở hữu độc quyền (`std::unique_ptr`)**: Sử dụng khi một đối tượng thuộc về duy nhất một chủ sở hữu (ví dụ: `Character` sở hữu danh sách `StatusEffect`, `StateStack` sở hữu các `State`).
> 3. **Sở hữu chia sẻ bất biến (`std::shared_ptr<const SpellDefinition>`)**: Định nghĩa phép thuật được đăng ký trong `SpellRegistry` và chia sẻ an toàn cho nhiều đạn bay hoặc slot trang bị.
> 4. **Tránh Dangling Pointer trong Combat**: Trong [`ChainEffect::apply`](../src/spells/HitEffect.cpp), khi nhảy tia sét giữa các quái vật, mã nguồn sao chép `const EntityId nextId = next->id()` trước khi gọi hàm sát thương. Nhờ đó, nếu quái vật chết và bị hủy ngay lập tức, vòng lặp không bao giờ truy cập vào vùng nhớ đã giải phóng.
> 5. **Kiểm chứng thực tế**: Đã chạy toàn bộ bộ test với **AddressSanitizer (ASan)** và ghi nhận 0 byte rò rỉ.

---

### Q6: Game Loop hoạt động thế nào?
> **Trả lời:**
> Vòng lặp trò chơi trong `main.cpp` tuân theo mô hình chuẩn của game engine 2D:
> ```text
> [Restart Clock] -> [Poll Events & Input] -> [Update Domain Logic (dt)] -> [Clear Screen] -> [Render Graphics] -> [Display]
> ```
> 1. **Input**: Đọc sự kiện bàn phím/chuột qua SFML `pollEvent`.
> 2. **Update**: Cập nhật vị trí nhân vật, thời gian hiệu lực trạng thái, và hệ thống hạt đồ họa theo bước thời gian `dt`.
> 3. **Render**: Vẽ nền lưới, bóng nhân vật, cơ thể nhân vật, hiệu ứng hạt và giao diện HUD theo thứ tự lớp (z-order).

---

### Q7: Delta time (`dt`) giải quyết vấn đề gì?
> **Trả lời:**
> Delta time là khoảng thời gian (tính bằng giây) trôi qua giữa hai frame liên tiếp (`clock.restart().asSeconds()`).
> Nếu cập nhật vị trí bằng cách cộng một hằng số mỗi frame (`pos.x += speed`), tốc độ của game sẽ phụ thuộc vào số khung hình/giây (FPS): máy 144Hz sẽ chạy nhanh hơn máy 60Hz gấp 2.4 lần.
> Bằng cách nhân với `dt` (`pos += velocity * dt`), vận tốc được chuẩn hóa theo đơn vị **pixels/giây**. Dù game chạy ở 30 FPS, 60 FPS hay 144 FPS, nhân vật vẫn di chuyển cùng một quãng đường trong thế giới thực, đảm bảo tính nhất quán (frame-rate independence).

---

### Q8: Làm thế nào để thêm một loại Enemy mới vào hệ thống?
> **Trả lời:**
> Nhờ tính mở rộng của kiến trúc:
> 1. Tạo một class kế thừa từ [`Enemy`](../include/entities/Enemy.hpp) (ví dụ: `class RangedEnemy : public Enemy`).
> 2. Cài đặt các hàm thuần ảo:
>    - `EnemyType type() const override { return EnemyType::Ranged; }`
>    - `void updateAI(float dt, const Player& player) override`: viết thuật toán giữ khoảng cách và bắn đạn.
>    - `void draw(sf::RenderTarget& target) const override`: tự vẽ sprite hoặc hình học đặc trưng.
> 3. **Không cần sửa đổi** `Character`, `HealthComponent`, `SpellDefinition` hay `EffectSystem` (Tuân thủ triệt để nguyên lý Open/Closed Principle).

---

### Q9: Làm thế nào để thêm một nguyên tố phép thuật mới (ví dụ nguyên tố Tối - Shadow)?
> **Trả lời:**
> 1. Thêm enum `Shadow` vào [`Element`](../include/spells/Element.hpp).
> 2. Thêm một dòng thông tin (tên, màu sắc, đặc tính) vào bảng dữ liệu trong [`Element.cpp`](../src/spells/Element.cpp).
> 3. Đăng ký phép cơ bản và công thức kết hợp mới trong [`SpellRegistry`](../include/spells/SpellRegistry.hpp).
> `ElementSet` sử dụng bitmask nên tự động hỗ trợ tới 32 nguyên tố mà không cần sửa cấu trúc dữ liệu.

---

### Q10: Vì sao kiến trúc hiện tại phù hợp và đạt điểm cao trong môn OOP?
> **Trả lời:**
> 1. **Không có God Object**: Trách nhiệm được chia nhỏ thành các class có nhiệm vụ độc lập (`HealthComponent`, `EffectSystem`, `SpellRegistry`).
> 2. **Tách biệt Presentation và Domain**: Tầng chiến đấu không bị trói chặt vào SFML Graphics, cho phép chạy test simulation không cần màn hình.
> 3. **Mã nguồn sạch và an toàn**: Sử dụng C++17, RAII, const-correctness, 0 con trỏ sở hữu trần (raw owning pointers), đạt 0 cảnh báo dưới `-Wall -Wextra -Wpedantic` và 0 lỗi dưới AddressSanitizer.
