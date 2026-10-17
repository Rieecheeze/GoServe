# GoServe typography and brand guide

## Direction

Montserrat supplies the structural voice for headlines. Karla handles reading and interaction. The existing monochrome palette stays in place. This guide records the current Figma direction, which remains under user review. Android implementation follows UI finalization; this document does not claim that every native screen already matches it.

Montserrat uses weight 800 for headings. Karla uses weight 400 for body text and 600 for labels. The official variable font files are bundled, with Android font-family resources selecting these weights on API 26 and later. Rendering does not require a font download. Sources: [Montserrat family metadata](https://raw.githubusercontent.com/google/fonts/main/ofl/montserrat/METADATA.pb), [Karla family metadata](https://raw.githubusercontent.com/google/fonts/main/ofl/karla/METADATA.pb). Font licenses are included in `android/third-party`.

## Hierarchy

The nine existing GoServe Figma text styles use the following verified sizes and line heights. Android equivalents use `sp`; spacing and logo dimensions use `dp`.

| Style | Font / weight | Size / line height | Use |
| --- | --- | --- | --- |
| Hero | Montserrat ExtraBold 800 | 30 / 38 | Authentication title, Home headline, directory and office titles |
| Section | Montserrat ExtraBold 800 | 20 / 28 | Campus offices, section and request-card headings |
| Toolbar | Montserrat ExtraBold 800 | 20 / 28 | App bar titles and authentication loading title |
| Office | Montserrat ExtraBold 800 | 16 / 24 | Office and card names |
| Body | Karla Regular 400 | 16 / 24 | Paragraphs, explanatory copy and form input |
| Support | Karla Regular 400 | 14 / 20 | Subtitles, helper copy and short notices |
| Label | Karla SemiBold 600 | 14 / 20 | Buttons, filters, form, checkbox and radio labels |
| Caption | Karla SemiBold 600 | 12 / 16 | Status labels and metadata |
| Navigation | Karla SemiBold 600 | 12 / 16 | Retained style; navigation labels are hidden in the current icon-only bars |

Use sentence case and normal letter spacing. Keep headlines left aligned and allow them to wrap. The first letter of displayed status names is uppercase. Do not shrink text to force a fixed-height container or reserve an empty row for hidden navigation labels.

## Layout rhythm

- Use 20dp side margins in the main app, 24dp in authentication and a 600dp maximum content width on wider windows.
- Use 4–8dp between a heading and its supporting line, 16dp between related content blocks and 24–32dp between major sections.
- New state screens use a content-hugging header, a scrolling content area that fills the remaining space, and fixed bottom navigation above the system inset. Header growth must reduce the viewport rather than overlap it.
- Search controls are 48dp high. Filter pills are 32dp visually, centered within independent 48dp touch targets.
- “See all” is a text action with a 64 × 48dp touch target, without a large outlined pill.
- Single-line primary and secondary actions are at least 48dp high. They grow when labels wrap; their visual size stays consistent across screens.
- Use `sp` for text and line height. Support doubled text by wrapping and scrolling rather than clipping or ellipsizing necessary labels.
- At compact widths or larger text settings, office cards use one column. The page owns vertical scrolling and reserves space above bottom navigation.
- Check both themes at the 390-unit design width and at a 320-unit compact logical width, with larger text and long labels. Keep the distinction between Figma logical dimensions and Android device measurements in verification reports.

## Responsive proof status

The current review includes **14 editable Figma proof frames at 320-unit logical width**, covering normal 1× text and simulated 2× text. These frames retain editable layout and text rather than flattened screenshots. The geometry audit passed for all 14 at the recorded checkpoint, before the final minor copy changes. This is a bounded audit result, not a claim that later edits were included in that pass.

The simulated text setting scales the design's type and line heights to expose wrapping, clipping, overflow, navigation-target and vertical-layout defects. It does not reproduce Android's native font scaling, font metrics, accessibility behavior, keyboard insets or physical-device rendering. Native small-phone and system font-scaling verification remains required after implementation.

Android code is awaiting the user's UI finalization. These proof frames and the nine GoServe text styles document the implementation target; they do not establish that the current APK already matches the approved design.

## Logo placement

### Authentication and loading

Retain the existing sign-in logo above its title and the prominent, larger logo in the centered authentication loading group. Use the current Figma logo dimensions when implementing rather than introducing a new loading size. Keep the logo, title and progress/status message in one content-hugging group that can scroll on short windows or with large text.

Show this state only while authentication work is running. Return to the form on failure and show its actionable error. Avoid an artificial delay or a fabricated progress percentage. The supplied icon is decorative beside the readable GoServe name, avoiding duplicate screen-reader announcements.

### Main application header

Place a 40dp logo at the start of the Home masthead, 20dp from the safe content edge. The Home header uses the logo alone, with no adjacent GoServe text. Give the image a GoServe accessibility description. Keep the notification action at the opposite edge with a 48dp touch area. Start the header below the real system-bar inset. Secondary screens retain their back-navigation headers.

### Light and dark modes

Use the supplied dark square with white bridge on light surfaces and the supplied white square with dark bridge on dark surfaces. Preserve the 1:1 aspect ratio, 300-unit viewport, rounded corners and original stroke geometry. Native vectors stay sharp at both logo sizes; Android selects the theme-qualified drawable automatically.

Original SVG files are preserved in `android/brand`. Native vector resources are `drawable/goserve_logo.xml` and `drawable-night/goserve_logo.xml`. Keep the mark unrotated and untinted. No wordmark is rebuilt from the icon.

## Service navigation

The selected directory filter uses the primary filled color and contrasting text; unselected filters use the surface color and border. In dark mode this produces a white active tab and dark inactive tabs. Payments lists Cashiering only. Other office detail pages provide a route to Cashiering when payment is required; this route does not create a charge or confirm payment.

## Capsule navigation and actions

Student navigation has six destinations: Home, Requests, Consult, Inbox, AI Chat and More. AI Chat opens Seryo, GoServe's eGovAI chatbot. Use the custom capsule navigation rather than adding a sixth item to Material BottomNavigationView, which supports five.

Office staff navigation has five destinations: Home, Queue, Schedule, Inbox and More. Teller navigation has four: Search, Pending, History and More. These bars use the same capsule treatment, icon sizes, selected indicator and minimum targets as student navigation; each role exposes only its authorized destinations.

| Theme | Capsule | Unselected icons | Selected circle | Selected icon |
| --- | --- | --- | --- | --- |
| Light | `#222626` | `#F4F4F4` | `#F4F4F4` | `#222626` |
| Dark | `#F4F4F4` | `#222626` | `#222626` | `#F4F4F4` |

The bar is icons only and 56dp tall, with no reserved label row. Icons are 22dp and the selected circle is 36dp. Each complete destination has a 48dp minimum touch target. Keep icon and indicator centers aligned, with 12dp outside the capsule and 4dp internal inset. At a 320-unit logical width, those insets leave a 288-unit row for six 48-unit student targets. The light and dark themes use the inverse capsule, icon and indicator colors above. Validate actual bounds rather than reducing targets to make them fit.

In Android, accessibility names and long-press tooltips must identify every destination and announce the selected state. These accessibility requirements remain part of the implementation handoff.

Primary actions use the existing full-width pill treatment at 48dp minimum height with Karla SemiBold 14/20sp labels. Height grows with wrapped labels. Reserve real system insets below navigation; screen content ends above it and remains scrollable.

## Request progress and fee states

The office declares whether a request is free, requires payment, or is awaiting a fee decision. Free requests omit the payment phase. Pending office decisions cannot imply a charge or enable document release. Required payments go through Cashiering; payment confirmation remains a trusted server operation.

Completed phases use a filled green check plus the word Complete. The current phase has a distinct marker and Current label; future phases use an outline and Upcoming label. Color supplements the text and marker. Initial letters in displayed states are uppercase; wire values and filenames retain their original spelling. Request cards and metadata rows grow to fit text.

## Seryo setup state

Seryo has a direct AI Chat destination, a privacy panel and a planned attachment panel. Its eGovAI access code is stored on the server. Sending and file selection stay disabled until provider contracts and the user's tasks and procedures are configured. The UI explains this state without simulated answers, uploads or successful deletion claims. Chat drafts are not saved as history.
