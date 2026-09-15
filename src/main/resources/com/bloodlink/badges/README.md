# Badge assets

Everything about how a donor badge **looks** lives in this folder. You can
restyle or replace every badge here, by hand, in your IDE — **no Java changes,
no recompile of any controller, no code to read.**

What you cannot change here are the *thresholds* that decide which tier a donor
has earned. Those are deliberately kept in Java (`model/BadgeTier.java`:
NONE / BRONZE 1 / SILVER 3 / GOLD 6 / PLATINUM 10) because they are a rule about
real verified donations, not a piece of styling.

## `badges.json`

One entry per tier. The five keys must stay exactly `NONE`, `BRONZE`, `SILVER`,
`GOLD`, `PLATINUM` — they are matched to the `BadgeTier` enum by name.

```json
{
  "NONE":     { "label": "New Donor",      "icon": "none.svg",     "color": "#8A9A94" },
  "BRONZE":   { "label": "Bronze Donor",   "icon": "bronze.svg",   "color": "#B0703A" },
  "SILVER":   { "label": "Silver Donor",   "icon": "silver.svg",   "color": "#8C9AA6" },
  "GOLD":     { "label": "Gold Donor",     "icon": "gold.svg",     "color": "#C79A2E" },
  "PLATINUM": { "label": "Platinum Donor", "icon": "platinum.svg", "color": "#5C8AA0" }
}
```

| field   | meaning                                                                 |
|---------|-------------------------------------------------------------------------|
| `label` | The text shown next to the icon, e.g. "Gold Donor". Free text.           |
| `icon`  | A file **in this folder**. See "Icon files" below.                        |
| `color` | `#RGB` or `#RRGGBB`. Fills the icon and tints the label.                  |

## Icon files

Drop your own file in this folder and point `icon` at it. Two formats work:

1. **`.svg`** — a normal SVG file. Recommended: you can preview and edit it in
   your IDE or any vector editor.
2. **Anything else** (e.g. `.txt`, `.path`) — read as raw SVG *path data*, i.e.
   just the string that would go in a `d="..."` attribute.

### What the SVG reader supports

The app draws badges with JavaFX's native `SVGPath`, which understands path
geometry and nothing else. The reader pulls shapes out of your `.svg` and
converts them:

- `<path d="...">` — used as-is
- `<circle>`, `<ellipse>`, `<rect>` (including `rx`/`ry`), `<polygon>`,
  `<polyline>`, `<line>` — converted to equivalent path data

It **ignores** everything else, including:

- `transform` attributes (so flatten transforms before exporting)
- `fill`, `stroke`, `opacity`, gradients, filters — the whole icon is filled
  with the single `color` from `badges.json`
- `<text>`, `<image>`, `<use>`, `<defs>`, CSS classes

Draw on a **24×24 viewBox** (`viewBox="0 0 24 24"`). Other sizes still load, but
they are not rescaled, so a 100×100 icon will be clipped.

### Holes and knockouts

All shapes in a file are merged into one path drawn with the **even-odd** fill
rule, so an inner shape punches a hole through the outer one — that is how the
star in `silver.svg` and the ring in `gold.svg` are made.

Two consequences worth knowing:

- Put **all** your shapes in a single `<path fill-rule="evenodd" d="...">` if you
  want the file to look the same in a browser or IDE preview as it does in the
  app. Separate `<path>` elements each fill independently in an SVG viewer, but
  the app merges them — so a multi-`<path>` file can preview solid and then show
  holes once running. The bundled icons all use one combined path for this reason.
- Overlapping shapes cancel rather than union.

## If something is wrong with the file

The app never crashes over a bad badge file, because a broken icon is not a
reason to take down a blood-donation app. If `badges.json` is missing, malformed,
or an entry is incomplete, `util/BadgeRegistry.java` logs a warning and falls
back to the built-in defaults — which are the same values printed above, so the
app always looks finished out of the box. A single bad entry only falls back for
that tier; the others still use your file.
