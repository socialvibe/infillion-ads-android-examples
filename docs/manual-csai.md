# Plain / Manual CSAI

Use this example when the application or a publisher-owned ad layer decides when an ad break starts and which
ads it contains.

## Copy

Copy the complete `manualcsai` package, `activity_manual_csai.xml`, and `manual_ad_break.json`. The package
does not depend on either IMA example.

## Flow

1. Before content starts, the activity replaces `${user-id}` / `#{user-id}` in each interactive `vastUrl`
   with `ref-app-{uuid}` (new uuid per load), fetches the VAST, and reads the ad parameters JSON payload.
2. The activity starts Media3 content and keeps the native `PlayerView` controls available.
3. A one-shot gate detects the short reference midroll.
4. The activity stores the content position and plays the resolved pod.
5. Linear ads play through the same case-local ExoPlayer.
6. For TrueX or IDVx, the placeholder is positioned near its end and paused, the player is hidden, and
   `TruexAdRenderer.init` receives the extracted JSON in the overlay `FrameLayout`.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit skips the remaining pod; errors and
   unavailable ads continue the fallback pod.
8. Content is reloaded and restored to its saved position after the pod.

## VAST tag formats and ad parameters

Depending on publisher ad serving setup, Infillion tags deliver ad parameters in one of two ways:

1. **Companion tag**:
   - TrueX: `/:placement_hash/vast/companion?<params>`
   - IDVx: `/:placement_hash/vast/idvx/companion?<params>`
   - `adParameters` is encoded as a base64 JSON `data:` URL inside
     `<Companion apiFramework="truex"><StaticResource creativeType="application/json">`:

   ```xml
   <Creative id="super_tag">
     <CompanionAds required="all">
       <Companion id="super_tag" width="960" height="540" apiFramework="truex">
         <StaticResource creativeType="application/json">
           <![CDATA[data:application/json;base64,eyJ1c2VyX2lkIjoi...]]>
         </StaticResource>
       </Companion>
     </CompanionAds>
   </Creative>
   ```

2. **Generic tag**:
   - TrueX: `/:placement_hash/vast/generic?<params>`
   - IDVx: `/:placement_hash/vast/idvx/generic?<params>`
   - `adParameters` is delivered directly in the `<Linear><AdParameters>` node:

   ```xml
   <Creative id="placeholder_video">
     <Linear>
       <Duration>00:00:30</Duration>
       <AdParameters><![CDATA[{"user_id":"...","vast_config_url":"..."}]]></AdParameters>
       <MediaFiles>
         <MediaFile delivery="progressive" type="video/mp4" width="1280" height="720">
           <![CDATA[https://media.truex.com/m/video/truexloadingplaceholder-30s.mp4]]>
         </MediaFile>
       </MediaFiles>
     </Linear>
   </Creative>
   ```

The reference parser checks for a `truex` companion first, then falls back to `<AdParameters>`. While the
sample fixture `manual_ad_break.json` uses the `.../generic` endpoint, supporting both ensures compatibility
with any publisher ad-server setup.

## Renderer contract

The activity creates `TruexAdRenderer` and `TruexAdOptions` itself:

- `supportsUserCancelStream` is enabled for TrueX and IDVx. Back then fires `USER_CANCEL_STREAM`. If it is
  false, TrueX choice-card Back is `OPT_OUT`; IDVx Back does nothing.
- `appId` uses the application package name.
- `enableWebViewDebugging` is debug-only (`BuildConfig.DEBUG`).
- Advertising IDs are not set here. The ad-server `advertising-id` macro should already be in `AdParameters`;
  confirm that during integration certification.
- `pause()`, `resume()`, `stop()`, listener removal, and renderer disposal follow the activity lifecycle.

`USER_CANCEL_STREAM` closes the playback screen. `POPUP_WEBSITE` is mobile-only and is not used on CTV.

## Replace in production

- Replace the JSON fixture and sample VAST URLs with the publisher's VMAP/VAST or ad-server response.
- Read `adParameters` from the metadata the host ad framework actually delivers.
- Replace the sample content, placeholders, and fallback media.
- Confirm the ad-server `advertising-id` macro lands in `AdParameters` during integration certification.
- Integrate the one-shot gate with the production timeline and persisted playback state.
- Define production retry, telemetry, and renderer-error policy.
