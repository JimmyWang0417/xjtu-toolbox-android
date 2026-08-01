# CMPS Migration Ledger

## Implemented In This Scaffold

- Android host app with MIUIX Compose tree.
- iOS SwiftUI host bridge to shared `ComposeUIViewController`.
- Shared navigation stack and four root tabs: home, schedule, tools, profile.
- MIUIX home design with non-duplicated service icons and grouped service catalog.
- Account/session state model with multi-account isolation shape.
- Empty-room page with stable building order and MIUI-style selected whole-block highlight.
- Campus-card page with balance and transaction model.
- Score page with weighted GPA calculation boundary.
- LMS, library, venue, notification, and AI assistant pages with repository-backed data.
- Cross-platform campus data models.
- School calendar API and XJTU class-time table.
- Lightweight cross-platform notification crawler with source/category/date/tag parsing.
- Campus-card balance, transaction presentation, monthly insight, category analysis, and payment-code API boundary.
- LMS course/activity/upload/submission/live replay models and class replay page structure.
- Library seat result, booking action, recommendation, and venue booking/captcha/slot models.
- Undergraduate/postgraduate attendance summary, course stats, card-flow records, judge dashboard, GSTE questionnaire, and yellow-page search models/pages.
- GMIS schedule/score, transcript workflow, school-course query, and Jiaocai textbook search models/pages.
- Mobile Jiaoda, YWTB user/week info, WebVPN conversion boundary, and settings-state models/pages.
- Fitness score/year, coupon filter/page/detail/type, and Agent tool/widget dashboard models/pages.
- Class-replay download task/progress, internal browser state, and account-scoped cache-entry models/pages.
- `CampusLocalStore` backed by multiplatform-settings for account-scoped cache, stale fallback, and setting flags.
- Jiaoxiaozhi model/session/message/conversation dashboard and screen.
- Auth bridge state machine contract for CAS, captcha, MFA, account choice, refresh, and logout.
- WebVPN endpoint rewriting boundary.
- OkHttp/Darwin Ktor client factory.

## Next Direct Ports From Android/CMP

- `auth`: port CAS form parsing, execution token, captcha, MFA, and service-ticket exchange.
- `jwapp`: port schedule, score, GPA detail, course query, and teaching evaluation APIs.
- `emptyroom`: port CDN source, direct JW source, section filter, and innovation-harbor building aliases.
- `card`: connect CampusCardLogin to real balance/transactions and wire coupon deduction state.
- `library`: port seat map, recommendation scoring, booking, swap, cancel, and check-in.
- `lms`: port course list, assignments, quiz status, live streams, and replay links.
- `venue`: port favorites, slot query, booking, and conflict warnings.
- `notification`: port heavy anti-bot challenge handling, search, and local read state.
- `agent`: port tool-call bridge, campus context injection, and account-scoped chat history.

## UI Fidelity Targets

- Selected state uses a lit whole block, not a small checkmark.
- Home keeps scene-oriented cards while service list remains visually explicit.
- Empty-room building multi-select never reorders items after selection.
- Icons stay unique at the service level unless two entries intentionally share one semantic family.
- MIUIX components remain the default surface instead of local lookalikes.
