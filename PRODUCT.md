# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Android TV publisher developers evaluating or implementing Infillion interactive ads in an existing video and ad flow.

## Product Purpose

Provide one clear Android TV entry point with complete, copyable examples for plain client-side ad insertion, Google IMA client-side ad insertion, and Google IMA server-side ad insertion.

## Positioning

Each delivery example is deliberately self-contained. A developer can study or copy one complete integration without tracing shared player or ad abstractions through the other examples.

## Operating Context

Developers run the app on an Android TV emulator or physical TV, choose an integration from a D-pad launcher, and observe content playback, a short midroll, TrueX or IDVx rendering, fallback ads, and return to content.

## Capabilities and Constraints

- One Android TV application, initially containing three and eventually no more than about six examples.
- A Compose for TV application shell opens independent View/XML player activities.
- Media3 provides native playback UI.
- The manual example simulates an ad request; IMA examples perform real requests.
- TrueX may award `AD_FREE_POD`; IDVx always continues the ad pod.
- Only the launcher, entry model, branding, and visual assets are shared.
- This is reference code, not a production application or general-purpose ad framework.

## Brand Commitments

Use the supplied Infillion logo and Be Vietnam Pro font. The TV experience uses Charcoal and Fog Gray with the Bloom purple, pink, and orange gradient for focus and atmospheric artwork. Shapes may adapt the brand's rounded and petal-inspired forms to TV.

## Evidence on Hand

- Existing TrueX CTV, IMA CSAI, IMA SSAI, and mobile reference repositories.
- Hosted sample content, ad tags, linear ads, and Google DAI stream identifiers.
- Local Infillion design-system fonts, logos, color tokens, and previews.

## Product Principles

- Make one complete integration understandable in isolation.
- Prefer explicit player and ad-event handling over architectural reuse.
- Make every ad-state transition visible during evaluation.
- Preserve familiar Android TV focus, Back, and native player behavior.
- Document what to copy and what a production publisher must replace.

## Accessibility & Inclusion

Support D-pad-only use, deterministic focus, visible focus contrast, ten-foot-readable text, and meaningful status messages.

