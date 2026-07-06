# OwnTV — User Guide & Hidden Features

A quick tour of everything OwnTV can do. Most of these are **TV‑remote (D‑pad) shortcuts** that aren't
obvious at first glance — once you know them, the app is a lot faster to live in.

> **v4.0.0+ UI Update**: The app now features a completely redesigned shell with a **fixed sidebar** nav,
> a **top bar** with live clock, weather, search, and playlist name, and **rounded panels** for crisp content.
> Navigation is faster and more stable — panels don't jump around anymore.

> Navigation basics: **D‑pad** to move, **OK/Center** to select, **Back** to go up a level. The left
> column is the **navigation panel** (Search · Home · Live TV · Movies · Series · Downloads · Guide ·
> Settings). Press **Left** from a content list to jump back to it.

---

## ⚡ Adding a playlist — priority sync

- When adding an **Xtream** playlist you can pick what imports **first** (e.g. **Live TV only**).
  You get into the app as soon as that part is ready — **movies and series keep importing in the
  background**, even if you leave the screen or the device goes to sleep.
- **Re-syncs are incremental**: refreshing a playlist only writes what actually changed on the
  provider, so re-syncing big playlists is much faster.
- **M3U playlists can carry movies and series too** — tag entries in the playlist and OwnTV sorts
  them into the right tab:
  - `type="vod"`, `type="movie"` or `tvg-type="movie"` → the **Movies** grid.
  - `type="series"` or `tvg-type="series"` → the **Series** tab. Per-episode lines like
    `Show Name S01E05` (or `1x05`) are grouped into shows with seasons and episodes automatically.
  - Untagged entries stay in **Live TV**.

---

## 🗂️ Multiple playlists — switch & set a default

Have more than one playlist (e.g. a main one and a backup)? OwnTV can show them **all merged together**,
or **narrow the whole app to just one**.

- **Quick switcher (top bar):** when you have 2+ playlists, the **playlist chip in the top‑right** becomes a
  button with a **▾**. Open it to pick **All playlists** or a single playlist. Your choice applies **everywhere
  at once** — Live TV, Movies, Series, TV Guide, Search, and the Home rails (Continue Watching / Favourites) —
  and **sticks after a restart**. No need to go into Settings to switch.
- **Set a default playlist (Settings → Sources):** open **Add / Edit** on a playlist and turn on
  **“Default playlist.”** That playlist becomes the one shown across the app. The Sources list shows a
  **DEFAULT** badge on it — it's a status marker, not a button.
- **Show everything again:** pick **All playlists** from the top‑bar switcher, or edit the default playlist and
  turn **“Default playlist” off**. With no default set, **every playlist is shown** (the merged view).
- **What the filter affects:** categories, channels, movies, series, the guide, search results, and the
  **Favourites** and **History** rails inside each section all respect the selected playlist. Nothing is deleted
  or re‑imported — it's only a view filter, so switching back to **All** brings everything right back.
- Your selected default is included in **Backup & Restore** (Sources section).

---

## 🏠 Home — Continue Watching

- The **Home** tab opens to a row of what you were watching — partly‑watched **movies, episodes and recent
  live channels**, newest first.
- The selected card is shown **large with its poster**; focus it and it starts a **muted video preview**.
  Press **OK** to **resume right where you left off**.
- Press **Right** to move along the row — the next card expands and previews.
- Below is a **Favourite Channels** rail for one‑click live access.

---

## 📺 Live TV

- **Categories** are in the second column. Long category names **wrap to two lines** so they're never cut off.
- **Live preview**: focus a channel and its video plays in the preview pane (with the **real stream
  resolution**, e.g. `1080p`/`4K`, so a mislabelled "4K" channel can't fool you). Toggle this in
  **Settings → Live preview**; sound for the preview is **Settings → Preview audio**.
- ⭐ **Add to Favourites (and more)**: **long‑press OK** on a channel to open the quick menu — **Favourite,
  Rename, Hide, Match EPG, Catch‑up**. (Closing it returns you to the same channel.)
- 🔄 **Move channels** (reorder within folders/Favorites): **long‑press OK** on a channel and choose **Move** —
  a full‑screen reorder overlay opens with the full list. Use **D‑pad Up/Down** to move the item, **OK** to save,
  **Back** to cancel. Your reorder is saved across playlist re‑syncs and included in backups.
- **Open a channel full‑screen**: press **OK**.

### Inside the full‑screen live player
- **Left key → channel list**: with the on‑screen controls hidden, press **Left** to pop up a **channel
  list overlay** — scroll and **OK** to switch channels without leaving full‑screen.
- **CH+ / CH−** (or Up/Down on the channel‑list overlay) zap through the current category.
- 🔧 **Compatibility mode (two playback engines)**: live channels play on the fast **ExoPlayer** engine by
  default. If a channel shows **UHD artifacts**, won't open, or stutters, bring up the controls and press the
  **engine toggle (the ⇄ MPV/EXO pill)** — this **pins that channel to the mpv engine**. The pill always shows
  the engine that's **actually playing** (teal while on mpv, whether you pinned it or OwnTV auto‑switched), and
  **one tap always flips** the engine — a small "Switched to MPV/ExoPlayer" note confirms it. It's **remembered
  per channel**, so that one channel always uses mpv while everything else stays fast.
- 🔇 **Audio with no picture**: if a channel ever plays sound but shows a black screen, OwnTV now detects this
  automatically and switches engines for you (briefly shows a loading spinner). If neither engine can render
  video for that stream, you'll see a clear on‑screen message instead of a silent black screen.
- ⏪ **Catch‑up / rewind live**: on a channel that supports catch‑up (look for the marker, or use the
  long‑press **Catch‑up** menu), you can **rewind into the provider's archive** and play back from the past,
  then return to live.

---

## 🗓️ TV Guide (EPG)

- Open **Guide**. It loads instantly and opens scrolled to **now**.
- **Sort** the guide: A–Z · Provider · Live TV order · **Catch‑up** (archive‑capable channels first).
- ▶️ **Play catch‑up from the guide**: move **Right** into the timeline to a **past programme**, press
  **OK** to open its details, then choose **"Watch from start"** to replay it from the archive. Scroll
  **Left/Right** along the timeline to pick the programme you want.
- **EPG is opt‑in**: add guide feeds in **Settings → EPG Sources**. After importing a playlist you'll be
  offered a one‑tap **sync now** (with a live programme count), or you can sync later from Settings.
- **Auto‑match EPG**: the guide can smart‑match your channels to guide data; you can also fix one channel
  manually via the long‑press **Match EPG** menu.
- 🔄 **Auto refresh (per source)**: each **playlist** (Settings → Manage sources) and each **EPG feed**
  (Settings → EPG sources) has an **Auto refresh** dropdown — **Off** (default), **Refresh at startup**,
  or an interval (playlists 6–48h, EPG 1–48h). Intervals refresh only when the source is actually stale,
  checked on app start and when you return to the app. Everything stays **Off** until you turn it on.

---

## 🎬 Movies & 📺 Series

- **Grid / List toggle**: switch the poster wall to a compact **List** view (top‑right button) to scan many
  titles at once.
- **Detail pane**: focus a title to see its **poster, rating, plot** and **Play/Resume · Favourite ·
  Download** buttons.
- **Resume**: partly‑watched titles offer **Resume** (vs. Play). Choose how this behaves in
  **Settings → Resume** — **Ask**, **Auto** (silently continues), or **Never**.
- ⏭️ **Auto‑play next episode**: when an episode ends, the next one starts automatically — and it rolls into
  the **next season** when the current one finishes. Toggle in **Settings → Auto‑play next episode**.
- Series **open on your last‑watched episode**.
- 🔄 **Move movies/series** (reorder within categories/Favorites): **long‑press OK** on any title and choose **Move** —
  a full‑screen reorder overlay opens. Use **D‑pad Up/Down** to move, **OK** to save, **Back** to cancel.
- 📥 **Download via long‑press**: **long‑press OK** on a movie or episode and choose **Download** to queue it
  immediately (Movies) or queue all cached episodes (Series). No need to open the detail pane.
- 🔧 **Two playback engines with automatic fallback**: movies/episodes play on **mpv** by default (or
  **ExoPlayer** if you switched the **Movies & Series player** setting). If the chosen engine can't play an
  item, the **other engine is tried automatically** before any error. You can also switch the **current**
  movie/episode manually: bring up the controls and press the **engine toggle (the ⇄ MPV/EXO pill)** — it
  flips between mpv and ExoPlayer at the same position (the pill shows the active engine; teal while on
  ExoPlayer, and a small "Switched to MPV/ExoPlayer" note confirms it). Handy when one engine doesn't show a
  subtitle or audio track you know exists — flip and check. Like Live TV's compatibility mode, the choice is
  **remembered for that movie/episode** — it opens on that engine from then on, while other items keep
  following the setting.
- 🏷️ **Which engine is playing?** The player top‑left mini chips now start with **MPV** or **EXO** (on Live
  TV too), so you always know the active engine at a glance.

---

## 🎬 TMDB metadata (posters, plots, cast, trailers)

- **Settings → Metadata (TMDB):** pick a **Metadata source** — *Provider only* (no TMDB), *Provider + TMDB*
  (default; your playlist's info wins, TMDB fills the blanks and adds cast/genres/backdrops), or *TMDB only*
  (prefer TMDB). Turn on **Advanced options** to use your own TMDB API key or a self-hosted server; otherwise
  the built-in shared server is used with no setup. A "Test lookup" button verifies it works.
- 💡 **Recommended: use your own TMDB API key** (free for personal / non-commercial use) or a self-hosted
  server. TMDB keys are typically issued instantly — no waiting period or manual approval — and your own
  key means you're never affected by shared-server rate limits. Create one at
  [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api), paste it into **Settings →
  Metadata → TMDB API key (v3)**, and hit **Test lookup**.
- 🌐 **Self-host your own metadata server (free):** the exact Cloudflare Worker OwnTV's shared server runs
  is in the repo at [`worker/`](../worker/) — [`worker/README.md`](../worker/README.md) has the full
  step-by-step (deploy with `wrangler`, set your TMDB key as a secret via
  [`worker/wrangler.toml`](../worker/wrangler.toml) + `wrangler secret put TMDB_KEY`, then paste your
  `https://….workers.dev` URL into **Settings → Metadata → Custom metadata server URL**). Your key stays
  on your Cloudflare account, and responses are edge-cached for 30 days.
- **Movies/Series details:** focus a title to see enriched info in the side pane. **Long-press** a poster for
  Favorite, Download and **TMDB Details** (a scrollable window with the backdrop, full plot, cast and genres;
  press **Back** to close). **Single-press** plays.
- **Series & episodes:** open a series to see the episode list with a detail pane on the right — focus an
  episode to see its TMDB still, plot and rating. Episode rows: **single-press plays**, **long-press** for
  Download / TMDB Details.
- **Sorting:** the sort chip cycles **Provider → A–Z → Rating**. Rating shows the highest-rated titles first.
- **Refetch TMDB details:** long-press a movie, series, or episode → **Refetch TMDB details** forces a fresh
  TMDB search — it clears a wrong/stale match (or a 7-day "no match" cache) and re-searches at once, so you
  don't have to wait for the cache to expire. Use it when the art/plot is missing or looks wrong.
- **Set TMDB name:** long-press a movie or series → **Set TMDB name** opens a dialog
  pre-filled with the cleaned title; type the exact TMDB title (and an optional year to disambiguate) and
  Save forces a fresh TMDB search under that name. Clear removes the override and re-searches with the
  cleaned provider title. The escape hatch when matching still gets a title wrong (or it's stuck in the
  7-day "no match" cache).
- 🎞️ **Trailers:** long-press a movie or series → **Play Trailer** (shown only when TMDB has one). The trailer
  plays in a floating window: **Back or Exit** closes it, **◀/▶** seeks ±10 seconds. If the built-in player
  can't run on your box, OwnTV opens the trailer in the YouTube app instead.
- **Attribution:** OwnTV uses the TMDB API but is not endorsed or certified by TMDB.

---

## 🕐 History

- Browse **recently watched movies, series and channels**.
- ✂️ **Remove single item**: **long‑press OK** on any history item and choose **Remove from History** to
  delete just that entry (keeps the rest).
- 🧹 **Clear entire history** (by type): Settings → Content → **Clear watch history** — wipe all recently‑watched
  items, or just **Live TV, Movies or Series**. Playlists, Favorites and Downloads are untouched.

---

## 🔎 Search

- The **Search** tab searches **Live, Movies and Series together**, with a detailed result view.
- You can **favourite a channel straight from search** via **long‑press**.

---

## 🎛️ Player controls (reference)

Bring up the controls in any full‑screen player (press OK / a direction). The bottom bar has:

| Button | What it does |
|---|---|
| **Subtitles** | Pick a subtitle track (incl. **image subtitles**) and set **subtitle delay**. |
| **Audio** | Pick an audio track, and **A/V sync** (audio delay, **±50 ms** steps) — use this if surround makes lips drift. |
| **Info** (ⓘ) | Toggle the **stream info overlay**: codec · resolution · fps · HDR · bitrate · decoder · audio · buffer. |
| **Speed** | Playback speed (VOD). |
| **MPV/EXO (⇄)** | Live: **compatibility mode** — pin the channel to mpv. Movies/Series: **switch this item between mpv and ExoPlayer** (shows the active engine; teal on the non‑default one). Flipping it briefly confirms "Switched to MPV/ExoPlayer" at the bottom. |
| **Aspect/Zoom** | Change aspect ratio / zoom (works in every render mode). |
| **PiP** | Picture‑in‑picture for live. |
| **Volume** | mpv VODs/channels can be **boosted to 150%** for quiet streams. |

---

## 🎨 Personalize (make it yours)

- **Settings → Customize Category**: **hide, rename and reorder** categories .
  - **Hide a range of categories fast**: focus a category's **Hide** button and **long‑press (select‑hold)** it to
    enter **span/range mode**. Then scroll **up or down** — every category between your starting point and the
    category you land on gets hidden together as a range. Handy for quickly hiding a big block of categories (or
    even scrolling all the way to hide most of the list) instead of hiding them one by one.
- **Settings → Theme / Accent colour / UI Zoom**: dark/AMOLED/light, a tint colour, and scale the whole UI.
  - ⚠️ Going **below 85% zoom** shows a warning first — lower zoom draws many more items at once, which can
    crash devices with limited memory (e.g. 2 GB TV sticks) with big playlists/EPG. Press **OK** to accept
    and continue, or **Back** to stay at 85%.
- **Settings → Animations**: turn interface motion **off** for a snappier feel on lower‑end TV boxes.
- **Profiles** (Settings → Profiles): multiple viewers, a **Kids mode**, and **PIN locks**.

---

## ⚙️ Settings worth knowing

- 🚀 **Startup** — where each profile opens: **Home**, **Last channel** (auto‑plays the channel you last
  watched), or **Live · Favorites** (lands you right inside your favourites list).
- 🌈 **HDR** — use HDR output when the video and TV support it. Turn on for HDR/Dolby Vision content.
- 🧩 **Hardware decoder** (Video Player Settings) — hardware decoding is on for smooth 4K; switch to software
  only if a specific codec misbehaves.
- 🎬 **Movies & Series player** (Video Player Settings) — which engine plays movies/episodes first:
  **mpv** (default — widest format support incl. DTS/TrueHD audio, plus the A/V sync fix) or
  **ExoPlayer** (try it **only if movies/episodes won't start** on your device — it can't decode
  DTS/TrueHD audio and has no A/V sync fix). Either way, if the chosen player fails, the other is
  tried automatically before an error is shown. The player's **info overlay** shows which engine is
  active.
- 🔊 **Surround sound** — ⚠️ **off by default, opt‑in.** Turn it on **only if you have a real 5.1/7.1
  receiver**. On TV speakers or a stereo soundbar it can make **audio lag behind video (lip‑sync drift)** —
  if you enable it and see drift, fix it live with the player's **Audio → A/V sync** nudge. Most people
  should leave this off.
- 🔄 **Check updates on startup** — get notified when a newer version is on GitHub Releases.
- 💾 **Backup & Restore** — export/restore your profiles, sources, customizations, favorites, history,
  resume positions and app settings. On export you can set a **backup password** to encrypt saved
  passwords (source & proxy); without one, passwords are left out of the file. Restoring an encrypted
  backup asks for that password — enter it to bring passwords back, or **Skip** to restore everything
  else and re‑enter passwords later. Backups also preserve your **per‑source Auto refresh** choices,
  your **default source**, and any **compatibility‑mode / per‑item engine pins** (Live and Movies/Series),
  so a restored setup behaves exactly like the original. Older backup files still restore fine — anything
  they don't contain just keeps its default.
- 🧹 **Clear watch history** — wipe a profile's recently‑watched / continue rows.
- 📥 **Downloads** — download movies/episodes for offline play; pick the **Download folder** (app storage or
  external).

---

## 🛠️ Building your own custom M3U playlist

Making your own `.m3u`/`.m3u8` by hand (or with a script)? OwnTV decides which tab each entry lands in
**purely from the `#EXTINF` line** — the tag you put on it, not the file it points to. Get the line right
and your content sorts itself into **Live TV**, **Movies** or **Series** automatically.

**The rule OwnTV uses (in order):**

1. If the entry is tagged **series** → it goes to the **Series** tab.
2. Otherwise, if it's tagged as a **movie/VOD** → it goes to the **Movies** grid.
3. Otherwise (no VOD tag at all) → it stays in **Live TV**.

The tag can be written as either `type="…"` **or** `tvg-type="…"` — both are accepted:

| You want it under… | Add this attribute to the `#EXTINF` line |
|---|---|
| **Live TV** | *(nothing — any untagged entry is treated as a live channel)* |
| **Movies** | `type="movie"` **or** `type="vod"` **or** `tvg-type="movie"` **or** `tvg-type="vod"` |
| **Series** | `type="series"` **or** `tvg-type="series"` |

### Anatomy of a line

Every item is **two lines**: an `#EXTINF` metadata line, then the stream URL on the next line.

```
#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Display Name
http://your-server/stream.ext
```

- **`group-title="…"`** — the **category name inside the tab** (e.g. a Live TV category, a Movies
  category, or a Series category). Entries with the same `group-title` are grouped together.
- **`tvg-logo="…"`** — poster/channel logo URL (optional).
- **`tvg-id="…"`** — for **Live TV**, this is the EPG channel id used to match guide data (optional).
- **Display Name** — the text after the final comma. This is the title shown in the app.

### Live TV example

```
#EXTM3U url-tvg="http://your-server/epg.xml"
#EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://logo/bbc1.png" group-title="UK Channels",BBC One
http://your-server/live/bbc1.ts
```

> `url-tvg="…"` on the `#EXTM3U` header line is picked up as the playlist's EPG source automatically if
> you haven't set one. Catch-up attributes (`catchup="default"`, `catchup-source="…"`, `catchup-days="7"`)
> are also read on live entries.

### Movies example

```
#EXTINF:-1 type="movie" tvg-logo="http://logo/inception.jpg" group-title="Action",Inception (2010)
http://your-server/movie/inception.mkv
```

### Series example — this is the important one

Tag each **episode line** with `type="series"`, and put the **season/episode marker in the Display Name**.
OwnTV reads the marker to group episodes into shows, seasons and episodes:

```
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S01E01
http://your-server/series/st-s01e01.mkv
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S01E02
http://your-server/series/st-s01e02.mkv
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S02E01
http://your-server/series/st-s02e01.mkv
```

- The text **before** the marker becomes the **show name** — so all three lines above merge into one show
  *Stranger Things* with a Season 1 (2 episodes) and a Season 2 (1 episode).
- The text **after** the marker becomes the **episode title** (optional), e.g.
  `…,Stranger Things S01E01 - The Vanishing`.
- **Supported markers** (case-insensitive):
  - `S01E05` — also written `s1e5`, `S01 E05`, `S01.E05`, `S01-E05`.
  - `1x05` — the "1x05" style.
- **Keep the show name identical** across its episodes (spelling/case aside — matching is
  case-insensitive) so they group into the same show.
- If an episode line has **no marker**, it's still added, but as a plain sequential episode under that
  name — so always include a marker when you can.

> **Tip:** the `group-title` on a series entry becomes its **category** in the Series tab, not the show
> name — the show name always comes from the Display Name before the marker.

---

## 💡 Tips

- **Long‑press OK** is your friend — favourites, rename, hide, match EPG and catch‑up all live there.
- A channel buffering or showing artifacts on 4K? **MPV/EXO toggle → compatibility mode** usually fixes it.
- Audio out of sync on a VOD? **Audio → A/V sync** and nudge ± until lips match.
- **Guide looks blank when you first open it?** (especially with catch‑up channels) Try: **Settings → EPG** → tap Edit → delete your EPG source(s), then **add them again** and sync fresh. The v4.0.0 update changed how EPG loads, and old cached data needs to be cleared and reimported. Once done, the guide displays immediately.

---
*OwnTV is free, open‑source and ad‑free, forever. Found something confusing or missing from this guide?
Open an issue on GitHub.*
