# MyTasks — public pages

The homepage and privacy policy for **MyTasks.AI**, served by GitHub Pages at
`https://alexverlaty.github.io/MyTasks/`.

This repository holds **only the site**. The bot's own repository stays private and attaches
this one as a submodule at `site/`, because that repository tracks `config/roster.yaml` —
the Telegram user IDs, usernames and display names of three real people. Opening it up to
enable Pages would put them on the public internet permanently, where forks and caches
outlive any deletion.

## Why it exists

Google will not verify an OAuth application that asks for *sensitive* scopes without two
things, and these two files are them:

- **A homepage** describing what the application does, linking to the privacy policy, on a
  domain the developer controls and has verified in Search Console. Not a sign-in page.
- **A privacy policy** on the same origin, reachable from the homepage, matching the URL on
  the consent screen, and describing how the application obtains, uses, stores and shares
  Google user data.

Until both exist, everyone who links an account walks through
«Google hasn't verified this app» → «Advanced» → «Go to … (unsafe)».

## Layout

```
index.html     homepage: what the bot is, the five scopes and why each one
privacy.html   the policy, including the Limited Use disclosure
.nojekyll      serve the files as they are; there is no Jekyll build
```

Two files, no build step, no dependencies. A static site that needs a toolchain is a site
that stops working the month nobody is looking at it.

## Publishing

1. Push this repository to `main`.
2. Settings → Pages → Source: `main`, folder `/ (root)`.
3. Verify `https://alexverlaty.github.io/MyTasks/` in Google Search Console as a
   **URL-prefix property**, using the HTML-file or meta-tag method — both work on Pages.
   Google checks that the developer controls the site, and the verification submission is
   rejected without it.

There is no `CNAME` and no custom domain: the site is served from the `github.io` origin on
purpose. That origin is verifiable in Search Console, which is what Google's requirement
actually asks for — but a domain of one's own is the stronger form of the same proof, and if
this submission is ever refused on that ground, a custom domain is the first thing to try.

## Keeping it true

Both pages make claims about what the bot does and what it asks Google for. Those claims are
checkable against the source, and a page describing a version of the bot that no longer
exists is worse than no page — it is the failure the bot's own `CLAUDE.md` rule 7 exists to
prevent, one layer out.

So: **a scope added or removed in the bot changes `index.html` and `privacy.html` in the same
change.** The five named here were read out of `infrastructure/*/oauth.py` on 14 August 2026:
`calendar.events`, `calendar.calendars.readonly`, `calendar.calendarlist.readonly`, `tasks`,
`drive.file`. A sixth scope with no line on this page would be a scope the consent screen
asks for and the policy does not explain — which is exactly what review looks for.
