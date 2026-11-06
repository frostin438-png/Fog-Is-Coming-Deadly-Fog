# Публикация на Modrinth: пошагово

Всё, что нужно вставить в поля, уже готово. Загрузка делается из вашего аккаунта на modrinth.com.

## 0. Перед публикацией (обязательно)
1. Поставьте `fogiscoming-1.0.0.jar` в **обычную** сборку Forge 1.20.1 (47.x) через Prism Launcher,
   CurseForge или официальный лаунчер. Создайте мир и проверьте: отсчёт, `/fog setstage 3`,
   урон без света, защиту факелом и домом.
2. Добавьте в ту же сборку Oculus и Embeddium, включите `FogIsComingShaders.zip` и посмотрите туман.
3. По желанию — зайдите на сервер с модом.

## 1. Мод
**Create a project → Project type: Mod**

| Поле | Значение |
|---|---|
| Name | `Fog Is Coming: Deadly Fog` |
| URL (slug) | `fog-is-coming` (на 2026-11-06 был свободен) |
| Summary | `A deadly fog slowly swallows the world. Survive the countdown, then hide near light or inside a sealed shelter, or the fog will kill you.` |
| Icon | `modrinth/icon.png` |
| Description | содержимое `modrinth/DESCRIPTION_mod.md` |
| Categories | **Adventure**, **Game Mechanics** |
| Client side | **Required** |
| Server side | **Required** |
| License | **MIT** |
| Source code | `https://github.com/frostin438-png/Fog-Is-Coming-Deadly-Fog` |
| Issues | `https://github.com/frostin438-png/Fog-Is-Coming-Deadly-Fog/issues` |
| Gallery | все файлы из `modrinth/gallery/mod/` по порядку; `01_night_cabin.jpg` отметьте как **Featured** |

**Upload version**

| Поле | Значение |
|---|---|
| File | `fogiscoming-1.0.0.jar` |
| Version number | `1.0.0` |
| Version title | `1.0.0 — First release` |
| Release channel | **Release** (или **Beta**, если хотите сначала собрать отзывы) |
| Loaders | **Forge** |
| Game versions | **1.20.1** |
| Dependencies | нет |
| Changelog | `First public release: four fog stages, light and shelter protection, countdown with a ticking clock, built-in volumetric fog shaders, commands and configs.` |

## 2. Шейдерпак (отдельный проект)
**Create a project → Project type: Shader**

| Поле | Значение |
|---|---|
| Name | `Fog Is Coming Shaders` |
| URL (slug) | `fog-is-coming-shaders` (был свободен) |
| Summary | `Moody semi-realistic shaders for Oculus/Iris: soft shadows, waving plants, water, and volumetric fog driven by the Fog Is Coming mod.` |
| Icon | `modrinth/shaderpack_icon.png` |
| Description | содержимое `modrinth/DESCRIPTION_shaderpack.md` |
| Categories / features | Atmosphere, Shadows, Foliage, Reflections, Bloom; стиль **Semi-realistic**; производительность **Medium** |
| License | **MIT** |
| Source code | `https://github.com/frostin438-png/Fog-Is-Coming-Deadly-Fog` (папка `shaderpack/`) |
| Gallery | все файлы из `modrinth/gallery/shaderpack/` по порядку; `01_sunset_lake.jpg` отметьте как **Featured** |

Версия: файл `FogIsComingShaders.zip`, номер `1.0.0`, Loaders: **Iris**, Game versions: **1.20.1**.
В «Dependencies» можно добавить мод `fog-is-coming` как **Optional**.

## Подписи к кадрам галереи (Title / Description)

Мод:
1. `01_night_cabin` — **A safe place** — Lanterns and a campfire keep the deadly fog away from the cabin.
2. `02_countdown` — **The calm** — One day left. Somewhere a clock is ticking.
3. `03_stage1_taiga` — **Stage I: The Arrival** — A light, cold fog creeps between the trees.
4. `04_stage2_title` — **Stage II: The Thickening** — The world grows quiet. The distance is gone.
5. `05_stage2_forest` — **Lost in the woods** — Dense grey fog swallows the forest.
6. `06_stage3_village` — **Stage III: The Deadly Fog** — Even at noon the village sinks into darkness.
7. `07_exposed_night` — **You are exposed** — A few seconds to reach the light.
8. `08_stage3_sheltered_by_light` — **Sheltered by light** — Stay close to a lamp and the fog can't hurt you.

Шейдерпак:
1. `01_sunset_lake` — **Sunset** — Sun glint on the water and a warm sky.
2. `02_morning_valley` — **Morning** — Soft shadows over a valley.
3. `03_cherry_hill` — **Cherry hill** — Waving leaves and grass.
4. `04_fog_taiga` — **The fog arrives** — With the Fog Is Coming mod: volumetric fog in a taiga.
5. `05_fog_forest` — **Into the fog** — A dark forest disappearing into the fog.
6. `06_deadly_fog_village` — **Deadly fog** — The sun is gone and lamps glow through the fog.
7. `07_cabin_night` — **Night** — Warm lantern light and a soul lantern.

## 3. После отправки
Modrinth проверяет новые проекты вручную (обычно от нескольких часов до пары дней).
Если модератор попросит что-то поправить, это придёт в уведомления на сайте.
