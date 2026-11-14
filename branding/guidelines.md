# Hotaru logo

A firefly's light trail writes an "h" and ends in its glow.

| File | Use |
| --- | --- |
| `hotaru-symbol.svg`, `hotaru-wordmark.svg` | On dark backgrounds; the default. |
| `*-on-light.svg` | On light backgrounds, with a deeper orange so the dot still shows. |
| `*-black.svg`, `*-white.svg` | One colour only. |
| `hotaru-app-icon.svg` | The app icon; the launcher drawables in `app/quest` are drawn from it. |

## Colours

| Name | Hex | Use |
| --- | --- | --- |
| Glow | `#FFBE7A` | The dot, and the app's accent. |
| Glow on light | `#F2913A` | The dot on light backgrounds. |
| Paper | `#FFF6E8` | The letter on dark. |
| Night | `#17110E` | Icon background. |
| Ink | `#1B1512` | The letter on light. |

## Rules

- Keep clear space of one dot's width around the mark.
- No smaller than 16 px for the symbol and 20 px tall for the wordmark.
- Only the dot takes colour. Don't recolour, outline, tilt or add effects.
- The wordmark's letters are strokes; expand them to outlines before sending to print or cutting.

## In the app

- Glow marks what is on, chosen, playing or watched: switches, the selected track and profile, progress and the played tick.
- Red is for favorites, errors and destructive actions. Everything else is white on glass.
