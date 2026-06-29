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
  **gear (⚙) button** — this **pins that channel to the mpv engine**. It lights up when active and is
  **remembered per channel**, so that one channel always uses mpv while everything else stays fast.
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
| **Gear (⚙)** | **Compatibility mode** — pin a live channel to mpv (see Live TV above). |
| **Aspect/Zoom** | Change aspect ratio / zoom (works in every render mode). |
| **PiP** | Picture‑in‑picture for live. |
| **Volume** | mpv VODs/channels can be **boosted to 150%** for quiet streams. |

---

## 🎨 Personalize (make it yours)

- **Settings → Customize**: **hide, rename and reorder** categories and channels, per profile (TiviMate‑style).
- **Settings → Theme / Accent colour / UI Zoom**: dark/AMOLED/light, a tint colour, and scale the whole UI.
- **Settings → Animations**: turn interface motion **off** for a snappier feel on lower‑end TV boxes.
- **Profiles** (Settings → Profiles): multiple viewers, a **Kids mode**, and **PIN locks**.

---

## ⚙️ Settings worth knowing

- 🚀 **Startup** — where each profile opens: **Home**, **Last channel** (auto‑plays the channel you last
  watched), or **Live · Favorites** (lands you right inside your favourites list).
- 🌈 **HDR** — use HDR output when the video and TV support it. Turn on for HDR/Dolby Vision content.
- 🧩 **Hardware decoder** (Video Player Settings) — hardware decoding is on for smooth 4K; switch to software
  only if a specific codec misbehaves.
- 🔊 **Surround sound** — ⚠️ **off by default, opt‑in.** Turn it on **only if you have a real 5.1/7.1
  receiver**. On TV speakers or a stereo soundbar it can make **audio lag behind video (lip‑sync drift)** —
  if you enable it and see drift, fix it live with the player's **Audio → A/V sync** nudge. Most people
  should leave this off.
- 🔄 **Check updates on startup** — get notified when a newer version is on GitHub Releases.
- 💾 **Backup & Restore** — export/restore your profiles & sources.
- 🧹 **Clear watch history** — wipe a profile's recently‑watched / continue rows.
- 📥 **Downloads** — download movies/episodes for offline play; pick the **Download folder** (app storage or
  external).

---

## 💡 Tips

- **Long‑press OK** is your friend — favourites, rename, hide, match EPG and catch‑up all live there.
- A channel buffering or showing artifacts on 4K? **Gear button → compatibility mode** usually fixes it.
- Audio out of sync on a VOD? **Audio → A/V sync** and nudge ± until lips match.
- **Guide looks blank when you first open it?** (especially with catch‑up channels) Try: **Settings → EPG** → tap Edit → delete your EPG source(s), then **add them again** and sync fresh. The v4.0.0 update changed how EPG loads, and old cached data needs to be cleared and reimported. Once done, the guide displays immediately.

---

*OwnTV is free, open‑source and ad‑free, forever. Found something confusing or missing from this guide?
Open an issue on GitHub.*
