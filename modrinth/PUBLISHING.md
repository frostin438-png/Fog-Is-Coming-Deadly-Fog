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
| Gallery | `docs/screenshots/`: `fog_sh_s2_day.png`, `fog_sh_s3_night_lights.png`, `fog_sh_s3_day.png`, `fog_s0_countdown.png`, `fog_s3_day_exposed.png` (первый отметьте как featured) |

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
| Gallery | `docs/screenshots/`: `fog_oc_s0_morning.png`, `fog_oc_s0_sunset.png`, `fog_oc_s3_day.png`, `fog_oc_s3_night.png`, `fog_oc_s1_day.png` |

Версия: файл `FogIsComingShaders.zip`, номер `1.0.0`, Loaders: **Iris**, Game versions: **1.20.1**.
В «Dependencies» можно добавить мод `fog-is-coming` как **Optional**.

## 3. После отправки
Modrinth проверяет новые проекты вручную (обычно от нескольких часов до пары дней).
Если модератор попросит что-то поправить, это придёт в уведомления на сайте.
