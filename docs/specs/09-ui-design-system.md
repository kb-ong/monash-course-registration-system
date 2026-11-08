# 09 — UI Design System ("Fancy University")

**Owner:** coder-frontend · **Enforced by:** reviewer (visual + code review)
**Goal:** two different developers, given the same requirements, produce the *same* look. Every value
below is a decision, not a suggestion. If a value is missing, use the nearest token in §2 — never invent
a new colour or a new radius.

Stack: React 18 + TypeScript + Vite + Tailwind 3.4 + framer-motion + lucide-react. Fonts from Google
Fonts: **Fraunces** (display, optical size axis) and **Inter** (UI/body).

---

## 1. Design principles

| # | Principle | Consequence |
| --- | --- | --- |
| P1 | **Academic prestige, not startup playfulness** | serif display, navy depth, gold hairlines, generous whitespace |
| P2 | **Calm authority** | one primary action per view; no competing accent colours |
| P3 | **Data is the hero** | seats, credits and times are set in tabular numerals with clear labels |
| P4 | **Depth through light, not lines** | elevation + hairline borders; avoid heavy 2 px outlines except the gold rule |
| P5 | **Motion explains, never decorates** | 120–320 ms, transform/opacity only, always reducible to zero |
| P6 | **Nothing is only a colour** | every status has text + icon + colour |

---

## 2. Tokens

### 2.1 Brand palette (frozen)

| Token | Hex | Use |
| --- | --- | --- |
| `monash-blue` | `#006DAE` | primary brand, links, focus, info |
| `monash-navy` | `#002B49` | headers, primary buttons, hero base, footer |
| `monash-ink` | `#0A1A2F` | body text, headings on light |
| `monash-gold` | `#C9A227` | rules, seals, "limit reached", accents only |
| `monash-mist` | `#F5F7FA` | page canvas, input wells, hover surfaces |

### 2.2 Extended scale (derived, permitted)

| Token | Hex | Use |
| --- | --- | --- |
| `navy-900` | `#00182A` | hero gradient end, deepest surface |
| `navy-700` | `#003A63` | primary button hover |
| `blue-600` | `#006DAE` | = brand |
| `blue-500` | `#1E86C4` | link hover, chart accent |
| `blue-100` | `#CFE4F2` | selected chip border |
| `blue-50` | `#EAF3F9` | info tint, ghost hover |
| `gold-600` | `#C9A227` | = brand |
| `gold-100` | `#F7EFD2` | gold tint fill |
| `ink-900` | `#0A1A2F` | headings |
| `ink-700` | `#40536B` | secondary text (7.9:1 on white) |
| `ink-500` | `#5B6B80` | tertiary text (5.4:1 on white) |
| `ink-400` | `#64748B` | placeholder, meta (4.8:1 on white) |
| `line-200` | `#E3E7EC` | hairline borders |
| `line-300` | `#CBD5E0` | input borders, stronger dividers |
| `surface` | `#FFFFFF` | cards |
| `canvas` | `#F5F7FA` | page background |
| `success-600` | `#0F7B4F` | success text/icon (5.3:1) |
| `success-50` | `#E8F5EE` | success tint |
| `warning-700` | `#8A5A00` | warning text |
| `warning-50` | `#FFF6E0` | warning tint |
| `danger-600` | `#B3261E` | errors, destructive (6.5:1) |
| `danger-50` | `#FDECEA` | error tint |

**Forbidden:** any other hue. No purple, no teal, no "AI gradient". Gradients are navy→navy only.

### 2.3 Contrast (verified, WCAG 2.1 AA)

| Pair | Ratio | Verdict |
| --- | --- | --- |
| `ink-900` on `surface` | 16.9:1 | AAA |
| `ink-700` on `surface` | 7.9:1 | AAA |
| `ink-500` on `surface` | 5.4:1 | AA |
| `ink-400` on `surface` | 4.8:1 | AA (min only) |
| white on `navy` | 13.4:1 | AAA |
| white on `monash-blue` | 5.5:1 | AA |
| `ink-900` on `gold` | 7.0:1 | AAA |
| `danger-600` on `surface` | 6.5:1 | AA |
| `success-600` on `surface` | 5.3:1 | AA |
| `gold` on `surface` | 2.4:1 | **decorative only — never text on white** |

Rule: gold text is allowed **only** on navy (`#002B49`), where it reaches 5.6:1.

### 2.4 Typography

```css
--font-display: 'Fraunces', Georgia, 'Times New Roman', serif; /* 600/700, opsz 9..144 */
--font-sans:    'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;
--font-mono:    ui-monospace, 'SF Mono', 'Cascadia Mono', Menlo, monospace;
```

| Token | Font | Size / line-height | Weight | Tracking | Use |
| --- | --- | --- | --- | --- | --- |
| `display-1` | Fraunces | 56 / 60 | 600 | −0.02em | login hero, 404 |
| `display-2` | Fraunces | 44 / 48 | 600 | −0.02em | dashboard greeting |
| `h1` | Fraunces | 36 / 42 | 600 | −0.015em | page titles |
| `h2` | Fraunces | 28 / 34 | 600 | −0.01em | section titles |
| `h3` | Fraunces | 22 / 28 | 600 | 0 | card title (detail) |
| `h4` | Inter | 18 / 26 | 600 | 0 | card title (grid), dialog title (alt) |
| `body-lg` | Inter | 17 / 28 | 400 | 0 | leads, descriptions |
| `body` | Inter | 15 / 24 | 400 | 0 | default |
| `body-sm` | Inter | 13 / 20 | 400 | 0 | meta, helper |
| `label` | Inter | 13 / 16 | 600 | 0.01em | field labels |
| `overline` | Inter | 11 / 16 | 600 | 0.08em, uppercase | eyebrows, card codes |
| `button` | Inter | 15 / 16 | 600 | 0.01em | buttons (sm: 13/16) |

Numbers (credits, seats, times, codes) always `font-variant-numeric: tabular-nums`.

Max reading width: `68ch` for prose; course description capped at `72ch`.

### 2.5 Spacing (4 px base)

`0,1=4,2=8,3=12,4=16,5=20,6=24,8=32,10=40,12=48,16=64,20=80,24=96`

| Context | Value |
| --- | --- |
| Page gutter (desktop / mobile) | 24 / 16 |
| Section vertical rhythm | 64 desktop, 40 mobile |
| Card padding | 24 (grid card), 32 (detail panel) |
| Stack between form fields | 16 |
| Inline icon-to-text | 8 |
| Grid gap (cards) | 24 desktop, 16 mobile |
| Header height | 72 desktop, 64 mobile |
| Content max width | 1200 (`max-w-[1200px]`), prose 68ch |

### 2.6 Radii

| Token | Value | Use |
| --- | --- | --- |
| `xs` | 4 | badges (tight), checkbox |
| `sm` | 6 | chips, small buttons |
| `md` | 10 | buttons, inputs, toasts |
| `lg` | 14 | cards |
| `xl` | 20 | dialogs, hero panels |
| `2xl` | 28 | login card (desktop) |
| `pill` | 999 | status pills, avatar, seat meter |

### 2.7 Borders

| Token | Value |
| --- | --- |
| `hairline` | `1px solid #E3E7EC` |
| `strong` | `1px solid #CBD5E0` |
| `focus` | `2px solid #006DAE` + `outline-offset: 2px` |
| `focus-on-navy` | `2px solid #C9A227` + `outline-offset: 2px` |
| `gold-rule` | `2px solid #C9A227`, width 56 px (short) or full-bleed (hero divider) |
| `error` | `1px solid #B3261E` |

### 2.8 Elevation

| Token | Shadow | Use |
| --- | --- | --- |
| `e0` | none | flat surfaces |
| `e1` | `0 1px 2px rgba(10,26,47,.06), 0 1px 3px rgba(10,26,47,.04)` | cards at rest |
| `e2` | `0 4px 12px rgba(10,26,47,.08)` | card hover, dropdown |
| `e3` | `0 12px 32px rgba(10,26,47,.12)` | toast, popover |
| `e4` | `0 24px 64px rgba(2,43,73,.18)` | dialog |

Never combine `e3`+ with a strong border. Never use a coloured shadow except the hero's soft navy glow
(`0 40px 120px rgba(0,43,73,.35)`).

### 2.9 Motion

| Token | Value | Use |
| --- | --- | --- |
| `dur-1` | 120 ms | hover, focus, colour changes |
| `dur-2` | 200 ms | dialogs, toasts, row removal |
| `dur-3` | 320 ms | seat meter fill, card enter |
| `dur-4` | 560 ms | hero content entrance (once, on login/dashboard) |
| `ease-standard` | `cubic-bezier(.2,.8,.2,1)` | most transitions |
| `ease-enter` | `cubic-bezier(.16,1,.3,1)` | elements appearing |
| `ease-exit` | `cubic-bezier(.4,0,1,1)` | elements leaving |
| `stagger` | 40 ms per item, max 6 items | card grid entrance |

Rules:

- Only `transform` and `opacity` animate. Never animate `height`, `width`, `top`, `box-shadow` on scroll.
- No parallax, no autoplay video, no looping background animation.
- `prefers-reduced-motion: reduce` → all durations become `1ms`, transforms removed, skeleton shimmer
  becomes a static tint.

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 1ms !important;
    scroll-behavior: auto !important;
  }
}
```

---

## 3. Texture & atmosphere (the "fancy" part)

Applied only to **hero** surfaces (login left panel, dashboard hero, dialog header band):

```css
.hero-navy {
  background:
    radial-gradient(1200px 600px at 15% -10%, rgba(0,109,174,.55), transparent 60%),
    radial-gradient(900px 500px at 90% 110%, rgba(201,162,39,.16), transparent 55%),
    linear-gradient(160deg, #002B49 0%, #00182A 100%);
}
.hero-grain::after {
  content: ''; position: absolute; inset: 0; pointer-events: none; opacity: .05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)' opacity='.6'/%3E%3C/svg%3E");
}
```

- Grain is allowed **only** on navy surfaces, opacity ≤ 0.06.
- Glass card on navy: `background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14);
  backdrop-filter: blur(12px)`; text `#E6EEF5`.
- Gold rule: 2 px, 56 px wide, 24 px below the eyebrow. Exactly one per hero section.

---

## 4. Breakpoints & responsive rules

| Name | Min width | Grid | Nav |
| --- | --- | --- | --- |
| `xs` | 0 | 1 col, gutter 16 | burger → slide-over |
| `sm` | 640 | 1 col, gutter 20 | burger |
| `md` | 768 | 2 cols (cards), gutter 24 | burger until 1024 |
| `lg` | 1024 | 2 cols + filter rail | inline nav |
| `xl` | 1280 | 3 cols, container 1200 | inline nav |

Behaviour:

- **360 px must work.** No horizontal scroll on any page; test at 360 × 640.
- Touch targets ≥ 44 × 44 px (buttons use `min-h-[44px]` on mobile).
- Tables become stacked cards below `md` (Schedule list, CSV preview).
- The week grid is horizontally scrollable below `lg` with a sticky day header; it is never shrunk to
  illegibility.
- Filter rail becomes a `Filters` button opening a bottom sheet below `lg`.
- Dialog becomes a bottom sheet below `sm`: full width, `border-radius: 20px 20px 0 0`, slide up 200 ms.
- Font sizes never scale below 13 px; `display-1` drops to 34/38 on mobile, `display-2` to 30/34.

---

## 5. Component inventory

Each component lives in `src/components/ui/`. Variants are props, not copy-pasted classes.

### 5.1 Button

| Size | Height | Pad-x | Font | Radius |
| --- | --- | --- | --- | --- |
| `sm` | 32 | 12 | `button` 13/16 | `sm` (6) |
| `md` | 40 | 16 | `button` 15/16 | `md` (10) |
| `lg` | 48 | 24 | `button` 15/16 | `md` (10) |

| Variant | Rest | Hover | Active | Disabled |
| --- | --- | --- | --- | --- |
| `primary` | bg `navy`, text white | bg `navy-700` | bg `navy-900`, `scale(.99)` | bg `line-200`, text `ink-400`, `cursor-not-allowed` |
| `secondary` | bg white, border `strong`, text `ink-900` | bg `mist` | bg `line-200` | border `line-200`, text `ink-400` |
| `gold` | bg `gold`, text `ink-900` | bg `#B8911F` | `#A5821B` | bg `gold-100`, text `ink-400` |
| `ghost` | text `blue-600` | bg `blue-50` | bg `blue-100` | text `ink-400` |
| `danger` | bg `danger-600`, text white | `#8F1D17` | `#7A1811` | bg `danger-50`, text `#C99` |
| `link` | text `blue-600`, underline offset 3 | underline | — | text `ink-400` |

Shared: `inline-flex items-center gap-2 font-semibold transition-colors duration-[120ms]`,
focus ring per §2.7, `disabled:pointer-events-none`.
Loading: leading `Loader2` icon (16 px) spinning at 1s linear, label unchanged plus `aria-busy="true"`.
Icon-only: 40 × 40, `aria-label` required, radius `md`.

### 5.2 Input / Select / Field

```
Username / label 13px 600 ink-700, 6px gap
┌──────────────────────────────────────────┐  height 44 (48 on login), radius 10
│ placeholder 15px ink-400                 │  border line-300, bg white
└──────────────────────────────────────────┘
helper 13px ink-500 · error 13px danger-600 with AlertCircle 14
```

| State | Border | Ring | Notes |
| --- | --- | --- | --- |
| rest | `line-300` | none | |
| hover | `ink-400` | none | |
| focus | `blue-600` | `0 0 0 3px rgba(0,109,174,.22)` | ring replaces outline |
| error | `danger-600` | `0 0 0 3px rgba(179,38,30,.16)` | `aria-invalid="true"`, message `aria-describedby` |
| disabled | `line-200`, bg `mist` | none | text `ink-400` |

Select uses the same shell plus a `ChevronDown` 16 px at right, `appearance: none`.
Search input has a leading `Search` 18 px icon (`pl-11`) and an `X` clear button when non-empty.
Checkbox/radio: 18 px, radius 4, accent `blue-600`, label 15/24.

### 5.3 CourseCard

```
┌──────────────────────────────────────────────┐  radius 14, bg white, border hairline, e1
│ OVERLINE: FIT2004              [ 6 CP ]      │  code blue-600 · credit badge neutral
│ Algorithms and Data Structures               │  h4 Inter 600 18/26, 2-line clamp
│ Information Technology · Dr Wei Chen         │  body-sm ink-500
│ ┌ Clock  TUE 10:00–12:00 ─────────────────┐  │  body-sm, tabular nums
│ └ Clayton, Building 25, Rm 125 ───────────┘  │  truncate 1 line
│ SEATS ▓▓▓▓▓▓▓▓░░░░░░  62 of 80 available     │  SeatMeter
│ ──────────────────────────────────────────── │  hairline divider, 16px above
│ [   Register   ]              View details → │  primary md + ghost link
└──────────────────────────────────────────────┘
```

- Padding 24, min-height 232, `display:flex; flex-direction:column; gap:12px`.
- Hover: `translateY(-2px)`, `e2`, gold rule appears under the code (2 px, animate width 0→40 px, 200 ms).
- Full course: `FULL` badge in the top-right replaces the credit badge; card border `danger-50`; the
  Register button is disabled with label `Full`.
- Registered: success badge `Registered`, button replaced by ghost link `View in schedule`.
- Multi-slot: show the first slot + `+1 more` in `ink-400`.
- Grid: `grid gap-6 md:grid-cols-2 xl:grid-cols-3`.

### 5.4 SeatMeter

```
SEATS                                   62 of 80 available
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░                        (6px track, radius pill)
```

| Property | Value |
| --- | --- |
| Track | height 6, radius pill, bg `#E9EEF4` |
| Fill | width `= availableSeats / capacity * 100%`, radius pill, `transition: width 320ms ease-standard` |
| Fill colour | >50 % free `success-600`; 10–50 % `gold`; 1–10 % `warning-700`; 0 % `danger-600` |
| Label left | `overline` 11/16 `ink-500` — the word `SEATS` |
| Label right | `body-sm` tabular — `62 of 80 available` |
| Tight state | `Only 3 left` replaces the right label, colour `warning-700`, `font-weight 600` |
| Full | right label `No seats available` in `danger-600`; fill width 0 but track gets a 1 px `danger-50` inset |

ARIA: `role="meter" aria-valuemin="0" aria-valuemax="{capacity}" aria-valuenow="{availableSeats}"
aria-label="Seats available"`. Colour is never the only signal — the text always states the number.

### 5.5 Badges & pills

Height 22, pad-x 8, radius pill, `overline` 11/16, gap 6, optional 12 px icon.

| Variant | Bg | Text | Icon |
| --- | --- | --- | --- |
| `neutral` | `mist` | `ink-700` | — |
| `credit` | `#EEF2F6` | `ink-700` | — (text `6 CP`) |
| `registered` | `success-50` | `success-600` | `Check` |
| `open` | `success-50` | `success-600` | `Dot` |
| `closing` | `warning-50` | `warning-700` | `Clock` |
| `full` | `danger-50` | `danger-600` | `Ban` |
| `faculty` | white + `strong` border | `ink-700` | — |

### 5.6 StatusBanner (registration period)

Full-width strip, height 44 (desktop) / auto (mobile), radius `md`, padding 12/16, left 3 px accent bar,
`body-sm` 600 text, right-aligned relative time. Variants map to FR7 §6: open (success), opening-soon
(gold), not-yet (navy tint), closed (danger), unknown (neutral + Retry).

### 5.7 Modal / Dialog

```
        ┌────────────────────────────────────────────┐  width 480, radius 20, e4
        │  Register for FIT2004?                  ✕  │  h2 24 Fraunces, close 32×32 ghost
        │  ──────── gold rule 56px                   │
        │                                            │
        │  Algorithms and Data Structures            │  body-lg
        │  6 credit points · Tue 10:00–12:00         │  body-sm ink-500
        │                                            │
        │  This will bring you to 12 of 24 credits.  │  body, ink-700
        │                                            │
        │              [ Cancel ]  [ Confirm ]       │  right-aligned, gap 12
        └────────────────────────────────────────────┘
```

- Overlay `rgba(2,43,73,.55)` + `backdrop-filter: blur(2px)`; click-outside closes (except while pending).
- Entry: overlay opacity 0→1 (160 ms), panel opacity 0→1 + `scale(.98)→1` + `translateY(8px)→0` (200 ms).
- Focus: first focusable element is the **safe** action (`Cancel` / `Keep it`); focus trap; `Esc` closes
  unless pending; on close focus returns to the trigger.
- `role="dialog" aria-modal="true" aria-labelledby` the title.
- Destructive variant: confirm button is `danger`, and the panel gets a 3 px `danger-600` left bar.

### 5.8 Toast

```
                                   ┌───────────────────────────────┐
                                   │ ▌ Check  Registered for FIT2004│  ✕
                                   │          Seat 62 of 80         │
                                   └───────────────────────────────┘
```

- Position: bottom-right desktop (24 px inset), top-centre mobile (12 px), `z-50`.
- Width 360 (max `calc(100vw - 32px)`), radius 12, bg white, e3, 3 px left accent (semantic).
- Title `body` 600, description `body-sm` `ink-500`, dismiss `X` 16 px.
- Auto-dismiss 5 s (success/info), 8 s (error); pause on hover/focus; max 3 stacked, oldest drops.
- `role="status" aria-live="polite"` for success/info, `role="alert" aria-live="assertive"` for errors.
- Entry: `translateY(8px)→0` + fade 200 ms; exit 160 ms.

### 5.9 Skeleton

- Block: `bg-[#EEF2F6]` with shimmer `linear-gradient(90deg,#EEF2F6 0%,#F7F9FB 50%,#EEF2F6 100%)`,
  `background-size: 200% 100%`, 1.2 s linear infinite.
- Shapes mirror the real content exactly (card skeleton = same height/padding as `CourseCard`).
- Never a full-page spinner. Never skeleton text lines inside a button.

### 5.10 EmptyState

```
              ╭─────╮
              │  ⌕  │   64px circle, mist bg, 28px lucide icon ink-400
              ╰─────╯
        No courses match "algo"
   Try a different code or clear the search.
            [ Clear search ]
```

Centred, max-width 420, padding 48/24. Title `h4`, body `body` `ink-500`, one primary or secondary CTA.

### 5.11 Header / Nav

- Height 72 desktop / 64 mobile, sticky top, `z-40`.
- Light pages: `rgba(255,255,255,.86)` + `backdrop-filter: blur(12px)` + bottom hairline.
- Navy pages (login/dashboard hero overlap): transparent over hero, text white, gold focus ring.
- Left: `MONASH` wordmark (Inter 700, 14 px, tracking .18em) + hairline divider + `Course Registration`
  (`body-sm` `ink-500`).
- Centre/right (≥1024): `Catalogue`, `My schedule` — `body` 500; active = `ink-900` + 2 px gold underline
  offset 22 px.
- Right: avatar 32 px circle (`navy` bg, white initials 12 px 600), name (hidden < `lg`), `LogOut` icon
  button with `aria-label="Sign out"`.
- Mobile: `Menu` icon → slide-over from right (280 px, navy, white links, 200 ms).

### 5.12 Footer

- Navy band, padding 48/24, top gold rule 2 px.
- 3 columns ≥`md`, stacked below: (1) wordmark + one-line description, (2) `Semester 2 2026 · 16 courses`,
  (3) `Privacy · Accessibility · Contact` as `body-sm` `#A9BDCE` links.
- Bottom line: `© 2026 Monash University` + `Phase 1 demonstration build` in `overline`, `#7E97AC`.
- Never sticky. Never contains actions.

### 5.13 CreditMeter (dashboard/schedule)

Circular ring 96 px, stroke 8, track `#E9EEF4`, fill `blue-600` (gold at 24/24), tabular centre label
`18 / 24`, caption below `credit points`. `role="meter"` like SeatMeter.

---

## 6. Page wireframes

Legend: `▌` accent bar · `▓░` meter fill · `[ ]` button · `→` link · `·` meta separator.
Wireframe glyphs are placeholders, never literal output: `⌕` → lucide `Search`, `⚠` → `AlertTriangle`,
`✓` → `Check`, `⏻` → `LogOut`, `◫` → `CalendarX`, `⠿` → `GripVertical`.

### 6.1 Login (`/login`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  MONASH │ Course Registration                                                │
├──────────────────────────────────────────┬───────────────────────────────────┤
│  hero-navy + grain (60%)                 │  canvas (40%), centred card        │
│                                          │                                   │
│  ▌ OVERLINE  SEMESTER 2 · 2026           │   ┌───────────────────────────┐   │
│  ──── gold rule 56px                     │   │  Welcome back             │   │
│                                          │   │  Sign in to register.     │   │
│  Course registration,                    │   │                           │   │
│  without the queue.                      │   │  Student ID               │   │
│                                          │   │  ┌─────────────────────┐  │   │
│  Choose your units, watch the seats      │   │  │ 32012345            │  │   │
│  and lock in your timetable in minutes.  │   │  └─────────────────────┘  │   │
│                                          │   │  Password          [show] │   │
│  ▪ 16 courses · Semester 2 2026          │   │  ┌─────────────────────┐  │   │
│  ▪ Registration open until 20 Dec        │   │  │ ••••••••••          │  │   │
│                                          │   │  └─────────────────────┘  │   │
│  ┌ glass card ─────────────────────────┐ │   │                           │   │
│  │ "Registration took 40 seconds."     │ │   │  [      Sign in        ]  │   │
│  │  — Aisha R., Bachelor of CS         │ │   │                           │   │
│  └─────────────────────────────────────┘ │   │  Demo: 32012345 /         │   │
│                                          │   │  Password123!             │   │
│                                          │   └───────────────────────────┘   │
│                                          │   Monash University · Phase 1     │
└──────────────────────────────────────────┴───────────────────────────────────┘
```

States:

```
ERROR (401)                          SUBMITTING
┌───────────────────────────┐        ┌───────────────────────────┐
│ ⚠ We couldn't sign you in.│        │  Student ID               │
│   Check your student ID   │        │  [ 32012345        ] (dim)│
│   and password.           │        │  Password                 │
│  Student ID               │        │  [ ••••••••        ] (dim)│
│  [ 32012345        ]      │        │  [ ⟳ Signing in…      ]   │
│  Password                 │        └───────────────────────────┘
│  [ ••••••••        ]      │
│  [      Sign in        ]  │        EXPIRED (?reason=expired)
└───────────────────────────┘        ┌───────────────────────────┐
                                     │ i Your session expired.   │
MOBILE (≤ 640)                       │   Please sign in again.   │
┌──────────────────────┐             └───────────────────────────┘
│ MONASH               │
├──────────────────────┤
│ hero-navy, 180px     │
│ Course registration, │
│ without the queue.   │
├──────────────────────┤
│ ┌──────────────────┐ │
│ │  Welcome back    │ │
│ │  [ fields ]      │ │
│ │  [   Sign in   ] │ │
│ └──────────────────┘ │
└──────────────────────┘
```

### 6.2 Dashboard (`/`)

```
┌─ HEADER ─────────────────────────────────────────────────────────────────────┐
│ MONASH │ Course Registration     Catalogue   My schedule    AR Aisha [out]   │
├──────────────────────────────────────────────────────────────────────────────┤
│ ▌hero-navy, 320px, gold rule, grain                                          │
│                                                                              │
│   OVERLINE  SEMESTER 2 · 2026                    ┌─────────────┐             │
│   Good morning, Aisha.                           │   ╭─────╮   │             │
│   You're registered in 2 courses and 18 of 24    │   │18/24│   │ credit ring │
│   credit points.                                 │   ╰─────╯   │             │
│                                                  └─────────────┘             │
│   [ Browse catalogue ]   [ View my schedule ]                                │
├──────────────────────────────────────────────────────────────────────────────┤
│ ▌ ● Registration open · closes 20 Dec 2026, 11:59 pm          in 42 days     │
├──────────────────────────────────────────────────────────────────────────────┤
│  NEXT CLASSES                                              [ Full timetable ]│
│  ┌───────────────────────────┐ ┌───────────────────────────┐                 │
│  │ MON  10:00–12:00          │ │ TUE  10:00–12:00          │                 │
│  │ FIT1045                   │ │ FIT2004                   │                 │
│  │ Introduction to Prog.     │ │ Algorithms and Data Str.  │                 │
│  │ Clayton, B25, Rm 125      │ │ Clayton, B25, Rm 125      │                 │
│  └───────────────────────────┘ └───────────────────────────┘                 │
├──────────────────────────────────────────────────────────────────────────────┤
│  FEATURED THIS SEMESTER                                  [ View all 16 → ]   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                          │
│  │ CourseCard   │ │ CourseCard   │ │ CourseCard   │                          │
│  └──────────────┘ └──────────────┘ └──────────────┘                          │
├─ FOOTER ─────────────────────────────────────────────────────────────────────┤
│ MONASH │ 16 courses · 2026-S2 │ Privacy · Accessibility · Contact            │
└──────────────────────────────────────────────────────────────────────────────┘
```

States: **loading** — hero renders immediately (no skeleton), credit ring shows `--/24`, two card
skeletons; **error** — hero + banner render, `NEXT CLASSES` panel shows inline "Couldn't load your
schedule. [Retry]"; **empty (0 registrations)** — hero copy "You're not registered in anything yet.",
CTA `Browse catalogue`, next-classes panel replaced by EmptyState; **closed** — banner variant + both
CTAs remain (browsing is always allowed).

### 6.3 Course Catalog / Search (`/catalog`)

```
┌─ HEADER ─────────────────────────────────────────────────────────────────────┐
├──────────────────────────────────────────────────────────────────────────────┤
│  Catalogue                                            Semester 2 2026 ▾      │
│  ──── gold rule                                                              │
│  Find your next unit.                                                        │
├──────────────────────────────────────────────────────────────────────────────┤
│  ┌ Search courses ───────────────────────────────┐  ┌ Semester ─────────┐    │
│  │ ⌕  Try FIT2004 or Algorithms             ✕   │  │ 2026-S2        ▾ │    │
│  └──────────────────────────────────────────────┘  └───────────────────┘    │
│  Faculty:  [All] [IT] [Business] [Engineering] [Law] [Science]              │
│  14 courses found                                     Sort: Code A→Z (fixed) │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌ CourseCard ──────────────┐ ┌ CourseCard ──────────────┐ ┌ CourseCard ────┐│
│ │ FIT1045          [ 6 CP ]│ │ FIT1051          [ 6 CP ]│ │ FIT1055    [3CP]││
│ │ Introduction to Prog.    │ │ Programming Foundations  │ │ Industry Exp.  ││
│ │ IT · Dr Alan Ng          │ │ IT · Dr Mei Lin          │ │ IT · Dr S. Rao ││
│ │ MON 10:00–12:00          │ │ WED 09:00–11:00          │ │ FRI 13:00–15:00││
│ │ SEATS ▓▓▓▓▓▓▓▓▓▓ 119/120 │ │ SEATS ▓▓▓▓▓▓▓▓░░ 74/100  │ │ SEATS ▓▓▓ 30/30││
│ │ [ Register ]  Details →  │ │ [ Register ]  Details →  │ │ [ Register ]   ││
│ └──────────────────────────┘ └──────────────────────────┘ └────────────────┘│
├──────────────────────────────────────────────────────────────────────────────┤
│                                                            16 of 16 shown   │
└──────────────────────────────────────────────────────────────────────────────┘
```

(No pagination in Phase 1 — the footer line is a count, not a pager.)

States:

```
LOADING (first load)                 EMPTY
┌────────┐┌────────┐┌────────┐        ┌──────────────────────────────┐
│ ▒▒▒▒▒▒ ││ ▒▒▒▒▒▒ ││ ▒▒▒▒▒▒ │        │            ⌕                 │
│ ▒▒▒▒▒▒ ││ ▒▒▒▒▒▒ ││ ▒▒▒▒▒▒ │        │   No courses match "algo"    │
│ ▒▒▒▒▒  ││ ▒▒▒▒▒  ││ ▒▒▒▒▒  │        │ Try a different code or      │
└────────┘└────────┘└────────┘        │ clear the search.            │
                                      │      [ Clear search ]        │
LOADING (refine)                      └──────────────────────────────┘
grid at opacity .6, aria-busy=true, previous results kept
                                      ERROR
                                      ┌──────────────────────────────┐
FULL COURSE CARD                      │ ⚠ We couldn't load courses.  │
┌──────────────────────────────┐      │   [ Retry ]                  │
│ FIT3182            [ FULL ]  │      └──────────────────────────────┘
│ Big Data Management          │
│ SEATS ░░░░░░░░░░ 0 of 40     │      CLOSED WINDOW
│ [  Full  ]      Details →    │      banner: ▌ Registration closed 20 Dec
└──────────────────────────────┘      cards keep [ Register ] disabled
```

### 6.4 Course Detail (`/courses/:id`)

```
┌─ HEADER ─────────────────────────────────────────────────────────────────────┐
├──────────────────────────────────────────────────────────────────────────────┤
│  ← Back to catalogue                                                         │
│  OVERLINE  FIT2004 · SEMESTER 2 2026 · INFORMATION TECHNOLOGY                │
│  ──── gold rule                                                              │
│  Algorithms and Data Structures                                              │
│  Dr Wei Chen · 6 credit points                                               │
├───────────────────────────────────────────────┬──────────────────────────────┤
│  ABOUT THIS UNIT                              │  ┌ sticky panel ────────────┐│
│  Algorithm design paradigms, complexity       │  │ 62 of 80 seats available ││
│  analysis and core data structures.           │  │ ▓▓▓▓▓▓▓▓▓░░░░░░░░░░      ││
│                                               │  │ ─────────────────────    ││
│  CLASS TIMES                                  │  │ [    Register          ] ││
│  ┌─────────────────────────────────────────┐  │  │ Adding this brings you   ││
│  │ TUE  10:00–12:00  Clayton, B25, Rm 125  │  │  │ to 12 of 24 credits.     ││
│  │ THU  14:00–15:00  Clayton, B25, Rm 130  │  │  └──────────────────────────┘│
│  └─────────────────────────────────────────┘  │                              │
│                                               │  AT A GLANCE                 │
│  ASSESSMENT & PREREQUISITES                   │  Code        FIT2004         │
│  None for Phase 1.                            │  Credit      6 CP            │
│                                               │  Faculty     IT              │
│                                               │  Capacity    80              │
└───────────────────────────────────────────────┴──────────────────────────────┘
```

States: loading (title skeleton + panel skeleton), 404 (`404 / We couldn't find that course. [Back to
catalogue]`), registered (panel shows `Registered` badge + `View in schedule` + `Drop` ghost danger),
full (`Full` disabled + "Join the catalogue next semester" note — no waitlist), closed (disabled with
reason), error (panel-level retry).

### 6.5 My Schedule (`/schedule`)

```
┌─ HEADER ─────────────────────────────────────────────────────────────────────┐
├──────────────────────────────────────────────────────────────────────────────┤
│  My schedule                                    [ Export CSV ]  [ Print ]    │
│  ──── gold rule                                                              │
│  Semester 2 2026 · 12 of 24 credit points          ╭─────╮                   │
│                                                    │12/24│                   │
│                                                    ╰─────╯                   │
├──────────────────────────────────────────┬───────────────────────────────────┤
│  TIMETABLE                               │  YOUR COURSES (2)                 │
│       MON    TUE    WED    THU    FRI    │  ┌───────────────────────────────┐│
│ 08   ░░░░░  ░░░░░  ░░░░░  ░░░░░  ░░░░░   │  │ FIT1045             [ 6 CP ]  ││
│ 09   ░░░░░  ░░░░░  ░░░░░  ░░░░░  ░░░░░   │  │ Introduction to Programming   ││
│ 10   █████  █████  ░░░░░  ░░░░░  ░░░░░   │  │ MON 10:00–12:00 · WED 14:00   ││
│ 11   █████  █████  ░░░░░  ░░░░░  ░░░░░   │  │ SEATS ▓▓▓▓▓▓▓▓▓ 119/120       ││
│ 12   ░░░░░  ░░░░░  ░░░░░  ░░░░░  ░░░░░   │  │              [ Drop ]         ││
│ 13   ░░░░░  ░░░░░  ░░░░░  ░░░░░  ░░░░░   │  └───────────────────────────────┘│
│ 14   ░░░░░  ░░░░░  █████  ░░░░░  ░░░░░   │  ┌───────────────────────────────┐│
│ 15   ░░░░░  ░░░░░  █████  ░░░░░  ░░░░░   │  │ FIT2004             [ 6 CP ]  ││
│ 16   ░░░░░  ░░░░░  ░░░░░  ░░░░░  ░░░░░   │  │ Algorithms and Data Structures││
│                                          │  │ TUE 10:00–12:00               ││
│  ███ = course block (navy, white text)   │  │ SEATS ▓▓▓▓▓▓▓▓▓ 62/80         ││
│  ░░░ = empty slot (mist, hairline)       │  │              [ Drop ]         ││
│                                          │  └───────────────────────────────┘│
└──────────────────────────────────────────┴───────────────────────────────────┘
```

Course block: navy bg, white text, radius 8, padding 8, code `overline`, time `body-sm`, gold 2 px left
edge; hover raises to `navy-700`; click → course detail. Two overlapping blocks are offset by 8 px with a
`warning-50` outline and a `Clash` note (visual only, no blocking).

States:

```
EMPTY                                ERROR
┌──────────────────────────────┐     ┌──────────────────────────────┐
│           ╭────╮             │     │ ⚠ We couldn't load your      │
│           │ ◫  │             │     │   schedule.                  │
│           ╰────╯             │     │   [ Retry ]                  │
│  You're not registered in    │     └──────────────────────────────┘
│  anything yet.               │
│  Browse the catalogue to     │     MOBILE (≤ 768)
│  find your first course.     │     ┌──────────────────────┐
│   [ Browse catalogue ]       │     │ My schedule          │
└──────────────────────────────┘     │ 12/24 cp             │
                                     │ [Timetable][Courses] │
                                     │ ┌──────────────────┐ │
                                     │ │  MON 10:00       │ │
                                     │ │  FIT1045         │ │
                                     │ └──────────────────┘ │
                                     └──────────────────────┘
```

### 6.6 Global states

```
SUCCESS (register)                      REGISTRATION CLOSED
┌───────────────────────────────┐       ▌ Registration closed 20 Dec 2026
│ ▌✓ Registered for FIT2004  ✕ │       Register buttons disabled, label
│    Seat 62 of 80              │       "Registration closed", tooltip on
└───────────────────────────────┘       hover/focus. Reads still work.

404                                    500 / OFFLINE
┌──────────────────────────────┐       ┌──────────────────────────────┐
│  ██ 404                      │       │  ⚠ Something went wrong.     │
│  This page doesn't exist.    │       │  Try again in a moment.      │
│  [ Back to catalogue ]       │       │  [ Retry ]                   │
└──────────────────────────────┘       └──────────────────────────────┘
```

---

## 7. Accessibility

| Area | Requirement |
| --- | --- |
| Contrast | Body text ≥ 4.5:1; large text ≥ 3:1; UI borders/focus ≥ 3:1. §2.3 table is the source of truth. |
| Focus | Always visible. `:focus-visible` ring 2 px `blue-600` + 2 px offset; on navy surfaces 2 px `gold`. Never `outline: none` without a replacement. |
| Keyboard | Full flow without a mouse: Tab order = visual order; `Enter` activates buttons and submits; `Space` toggles checkbox; `Esc` closes dialogs/menus; arrow keys move within radio groups and the week grid. |
| Skip link | First focusable element: `Skip to main content` → `#main`, visible only on focus. |
| Landmarks | One `<header>`, one `<main id="main">`, one `<footer>`, `<nav aria-label="Primary">`. |
| Headings | Exactly one `<h1>` per page; no skipped levels. |
| Forms | Every input has a `<label for>`; errors via `aria-describedby` + `aria-invalid`; error summary `role="alert"` at the top of the form on submit. |
| Live regions | Result count `aria-live="polite"`; toasts `role="status"`/`role="alert"`; pending buttons `aria-busy`. |
| Images/icons | Decorative icons `aria-hidden="true"`; icon-only buttons need `aria-label`; no text baked into images. |
| Motion | Honour `prefers-reduced-motion`; no content that only appears on hover. |
| Zoom | Usable at 200 % zoom and 320 px reflow (WCAG 1.4.10). |
| Target size | ≥ 44 × 44 px interactive targets on touch (WCAG 2.5.8 AAA-ish, adopted). |
| Dialog | `role="dialog" aria-modal="true"`, labelled by title, focus trapped, `Esc` closes, focus restored. |
| Announcements | Seat changes announced as "62 of 80 seats available" not "62". |

Testing: axe DevTools → 0 critical/serious; manual keyboard pass per page; VoiceOver/NVDA smoke on Login +
Catalog + Schedule.

---

## 8. Print stylesheet (FR5)

```css
@media print {
  @page { size: A4 portrait; margin: 14mm; }

  header, footer, nav, .no-print, .toast, .status-banner, button { display: none !important; }

  html, body { background: #fff !important; color: #000 !important; font-size: 11pt; }
  .print-only { display: block !important; }

  /* course blocks become bordered table rows */
  .timetable-grid { display: table; width: 100%; border-collapse: collapse; }
  .timetable-cell { border: 1px solid #999; }
  .course-block {
    background: #fff !important; color: #000 !important;
    border-left: 3px solid #000 !important; box-shadow: none !important;
  }

  a[href]::after { content: ''; }           /* no URL spam */
  .page-break { break-before: page; }
  .course-table { width: 100%; border-collapse: collapse; }
  .course-table th, .course-table td { border-bottom: 1px solid #ccc; padding: 4px 6px; text-align: left; }
}
```

Print header (`.print-only`, hidden on screen):

```
Monash University · Course timetable
Aisha Rahman (32012345) · Semester 2 2026 · 12 of 24 credit points
Printed 8 November 2026
─────────────────────────────────────────────────────────────
```

Print footer: page number + `Generated from the Monash Course Registration System`. No background
colours, no shadows, no toasts, no buttons. The timetable must fit one A4 page for ≤ 6 courses; beyond
that, the course table continues on a second page.

---

## 9. Copy deck (microcopy, use verbatim)

| Context | Text |
| --- | --- |
| Login title | `Welcome back` |
| Login subtitle | `Sign in to register for Semester 2 2026.` |
| Login button | `Sign in` |
| Login error | `We couldn't sign you in. Check your student ID and password.` |
| Expired | `Your session expired. Please sign in again.` |
| Dashboard greeting | `Good morning, {firstName}.` / `Good afternoon,` / `Good evening,` by server time |
| Empty catalogue | `No courses match "{q}".` |
| Search hint | `Matches course code or name` |
| Register button | `Register` |
| Register confirm | `Confirm register` |
| Register success | `Registered for {code}` |
| Full | `Full` / `No seats available` |
| Full on click | `That course just filled up.` |
| Credit limit | `That would take you to {n} credits. Your limit is 24.` |
| Already | `You're already registered in this course.` |
| Drop confirm | `Drop {code}?` |
| Drop success | `Dropped {code}` |
| Closed | `Registration closed {date}.` |
| Opens | `Registration opens {date}.` |
| Empty schedule | `You're not registered in anything yet.` |
| Export | `Export CSV` · `Print` |
| Generic error | `Something went wrong. Try again in a moment.` |

Tone: sentence case, no exclamation marks, no "Oops", no emoji, no contractions in errors.

---

## 10. Implementation checklist for coder-frontend

- [ ] Tailwind theme extended with the tokens in §2 (`colors`, `fontFamily`, `borderRadius`, `boxShadow`,
      `transitionTimingFunction`, `fontSize`) — no arbitrary hex in components.
- [ ] Fonts loaded with `display=swap`, `preconnect`, weights `Fraunces 600,700` + `Inter 400,500,600`.
- [ ] `focus-visible` styles global; no `outline-none` anywhere.
- [ ] All 6 pages + NotFound built; every state in §6 implemented (loading/empty/error/success/full/closed).
- [ ] `prefers-reduced-motion` block shipped.
- [ ] Print stylesheet verified with Chrome "Save as PDF" at A4.
- [ ] 360 px, 768 px, 1024 px, 1440 px screenshot set captured for the reviewer.
- [ ] axe DevTools: 0 critical/serious on all pages.

---

*Prev: [08 Non-functional](./08-non-functional.md) · Next: [10 Test strategy](./10-test-strategy.md)*
