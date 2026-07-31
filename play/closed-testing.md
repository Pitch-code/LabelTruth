# Closed testing — the 12 testers / 14 days requirement

New **personal** Play developer accounts must run a closed test with at least
**12 testers opted in continuously for 14 days** before they can apply for
production access.

This is the longest item on the path to launch and **no amount of work shortens
it**. It is calendar time. Everything here exists to stop the clock from
restarting.

---

## The rules, precisely

| Rule | Detail |
|---|---|
| Minimum testers | **12** |
| Duration | **14 continuous days** |
| What counts | A unique Google account that opted in via your link **and installed the app** |
| What does not count | Invites sent, emails collected, people who said yes but never installed |
| If you drop below 12 | The count can reset. This is the main risk |

**Recruit 18–25, not 12.** Twelve is a floor, not a target. People change phones,
uninstall, or opt out without telling you. Slack is the only protection.

Google also looks at genuine engagement, not just headcount. Ask testers to
actually open the app a few times.

> **Never pay for testers.** Tester-farm services are detectable, and using one
> risks account termination — which also forfeits the $25.

---

## Setup: use a Google Group, not a pasted email list

Pasting individual addresses means editing the list in Play Console every time
someone joins or leaves. A Google Group means you manage members in one place and
Play just reads it.

1. Go to [groups.google.com](https://groups.google.com) and create a group,
   e.g. `labeltruth-testers`
2. Set **Who can join** to *Invited users only*
3. Add each tester's Gmail address as a member
4. In Play Console → **Testing → Closed testing → Create track**, choose
   **Google Groups** and paste the group address

---

## Message to send testers

Copy, paste, and send. Replace the link once Play gives you one.

```
Hi! I've built an Android app called LabelTruth and I need a few people to
help me test it before it can go on the Play Store.

It scans food barcodes and ingredient labels and tells you what each
ingredient actually is, using published sources from EFSA, WHO and the FDA.
No account, no ads, and nothing about you is collected.

What I need from you:
1. Send me the Gmail address on your Android phone
2. I'll send you a link — tap it and accept
3. Install the app from the Play Store link
4. Please leave it installed for at least 2 weeks, and open it a few times

Why 2 weeks: Google requires 12 testers to keep the app installed for 14 days
straight before a new developer is allowed to publish. If people uninstall
early, the countdown restarts for everyone.

Takes about 3 minutes. Genuinely helps a lot. Thank you!
```

### Follow-up once the test is live

```
The test link is ready:

[PASTE THE OPT-IN LINK]

1. Open the link on your Android phone
2. Tap "Become a tester"
3. Tap "Download it on Google Play" and install

Two things that matter:
- Use the SAME Gmail address you gave me, or it won't register
- Please don't uninstall or leave the test for 14 days

Reply "installed" when it's on your phone so I can keep count. Thanks!
```

---

## Tracking sheet

Keep this updated. Play Console does not clearly show you who has opted in, so
your own count is what you rely on.

| # | Name | Gmail | Added to group | Confirmed installed | Still installed at day 14 |
|---|------|-------|:--------------:|:-------------------:|:-------------------------:|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |
| 11 | | | | | |
| 12 | | | | | |
| 13 | | | | | |
| 14 | | | | | |
| 15 | | | | | |
| 16 | | | | | |
| 17 | | | | | |
| 18 | | | | | |

**Do not start counting the 14 days until at least 12 are in the
"Confirmed installed" column.**

---

## Where to find testers

- Family and friends with Android phones — start here, highest conversion
- Classmates or colleagues
- A WhatsApp group, explaining it is a favour and takes 3 minutes
- Fellow developers in Android communities, who understand the requirement and
  reciprocate. Offer to test theirs in return — that is legitimate, unlike paying

---

## Common ways this goes wrong

| Mistake | Consequence |
|---|---|
| Tester opts in with a different Google account than the one you listed | Does not count |
| Tester installs, then uninstalls after a few days | Count can reset |
| Only exactly 12 recruited | One dropout puts you below the minimum |
| Counting from the day you *sent* invites | The clock starts when they are opted in, not invited |
| Testers never actually open the app | Engagement is assessed, not just installs |
| Paying a tester service | Risks account termination |

---

## Timeline, realistically

| Stage | Time |
|---|---|
| Google verifies your identity | Hours to a few days |
| Verify phone number (blocked until identity is approved) | Minutes |
| Create the app, complete the listing, upload the AAB | A few hours |
| Recruit and confirm 12+ testers | **Start now, in parallel** |
| Closed testing runs | **14 days, immovable** |
| Apply for production access, Google reviews | Days |

Recruiting in parallel with verification is the only real lever you have. Start
collecting Gmail addresses today, before the app is even uploadable.
