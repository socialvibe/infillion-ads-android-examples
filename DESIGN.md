# Design

## Visual thesis

The launcher behaves like a concise interactive integration guide presented through an Android TV Immersive List. It rejects a generic settings menu: focus changes the full scene and explains the selected delivery model before the developer opens it.

## Color

- `Charcoal #2D2D2D`: primary TV background.
- `Deep Charcoal #242222`: elevated card and player status surfaces.
- `Fog Gray #F6F6F6`: primary text and logo.
- `Muted Fog #E4E4E4`: explanatory text.
- `Bloom #BB6AEF -> #F948A1 -> #FC5D3D`: focus borders and static hero artwork.

Color stays restrained in player screens so the ad and playback state remain primary.

## Typography

Be Vietnam Pro is bundled in Regular, Medium, and Bold. Large titles are bold, explanations regular, and compact labels medium. TV text is sized for a ten-foot viewing distance rather than mirroring the web type scale.

## Shape and focus

Cards use the standard 20-dp dark-surface corner radius. Focus scales a card to 1.08 and adds a four-dp Bloom border. Unfocused cards retain a low-contrast neutral stroke. The asymmetric petal shape remains reserved for hero artwork and containers.

## Composition

The selected artwork fills the viewport behind horizontal and vertical cinematic scrims. The official light Infillion wordmark anchors the upper-left safe area. Selected-example information occupies the lower-left, followed by one horizontal row of 16:9 cards. Focus, not a separate button, opens an example.

## Player screens

Each player activity uses a native full-screen Media3 `PlayerView`, a renderer layer above it, and a compact status surface in the TV-safe area. Status copy identifies content, ad request, linear ad, interactive ad, recovery, or error.

## Motion

Focus scale and selected background changes are the only authored launcher motion. Player and renderer lifecycle transitions remain immediate and functional. System animation preferences must not block navigation or state comprehension.
