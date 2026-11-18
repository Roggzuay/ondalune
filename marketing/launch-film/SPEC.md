# Ondalune launch film: spec

GOAL:    the viewer leaves knowing Ondalune is a free, open-source player for their own music that looks and feels premium, and where to get it.
OUTPUT:  HyperFrames, 1920 x 1080, 60 fps (render with `--fps 60`).
TIME:    58.2 s. Music: Aquatone, "Mountains" (CC BY 4.0) from 0 s, 83.35 BPM, beat 0.720 s, bar 2.879 s.
         Scenes start on bars: 0, 6.20, 14.84, 26.36, 37.87, 49.39. Each scene overlaps the next by 0.6 s.

LOOK
- Ground #07070A, ink #F4F2EE, muted ink 62 %. No fixed accent: the light in each scene comes from the
  artwork on screen, as it does in the app (player colours #9FA7FF, #FF8E7E, #87F96E, #E889FF).
- Display: Unbounded 600, -2 % tracking, 104-130 px for heroes, 72-84 px for titles. Body: Figtree 500, 34-40 px.
- Medium: the real app (1.8.0) captured from the Creative Commons demo server, in a 422 x 911 phone (radius 58,
  11 px black bezel, 0 40 120 px shadow at 60 %). Never tilted, never redrawn.
- Light: one radial glow per scene (40-55 % opacity), 5 % grain, 30 % vignette. Artwork backdrops are
  pre-blurred files (`assets/covers-blur`), never CSS blur on media.

MOTION
- Arrivals expo.out / power4.out 0.8-1.1 s; camera moves power2.inOut 0.8-1.2 s; exits power3.in, 40 % shorter.
- Springs: damping 0.78-0.85 on position and scale only (closed-form `spring(z)` ease), never on opacity or colour.
- Titles: per word, blur 6-8 px to 0 with opacity, 0.13-0.3 s, 90 ms apart, no translate. A landed title is still.
- Screen changes happen on beats; layout changes on bars.
- Transitions, one kind per seam: zoom through the moon (1-2), zoom through the phone (2-3), matched pan (3-4),
  recede into depth (4-5), fade to black (5-6).

SHOTS
1. Intro 0-6.2: dark; light blooms; the real mark assembles (moon, waves, bars); "Ondalune" letter by letter;
   "Your music. Your universe."; the camera pushes through the moon.
2. Library 6.2-14.84: phone emerges, glides right among real covers (parallax); Home, Library, Artists,
   Favourites, Album slide in on beats while "Your [word]" rolls with them.
3. Player 14.84-26.36: blurred artwork fills the frame; four songs, the player and the frame recolour with each;
   punch-in on synced lyrics; speed & pitch and EQ sheets as a pair.
4. Make it yours 26.36-37.87: dark to light circular flood with the screen; triptych of styles and themes;
   music sources; light back to dark into the Home layout editor.
5. Philosophy 37.87-49.39: a wall of real album covers; "Your library." "Your server." "Your experience."
   "Open source. No ads. No trackers."
6. Finale 49.39-58.2: "Music without boundaries."; the mark and wordmark lock up; "Open Source. Built for your
   music."; github.com/Roggzuay/ondalune; credits. Held 3 s.

BANNED: invented UI or features, tilted 3D phones, purple gradient voids, neon glows, floating/pulsing loops,
fade-and-slide-up on every title, feature cards in threes, logo docked in a corner.

QA: frames at every seam (6.2, 14.84, 26.36, 37.87, 49.39) and mid-move; every title held long enough to read;
no text under 28 px except the credit line; nothing outside the 90 % action-safe box except intended bleed.
