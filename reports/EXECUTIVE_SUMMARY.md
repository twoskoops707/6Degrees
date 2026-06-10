# 6Degrees OSINT Investigation — Executive Summary

**Generated:** 2026-06-10 (UTC)  
**Method:** Headless emulation of 6Degrees `OsintRepository` search pipeline (same APIs, dorks, and username platform checks as the Android app)

---

## Search Targets

| Type | Query |
|------|-------|
| Person | `name=Michael r Cline\|city=Arcata\|state=CA\|email=mcline@dlm-cpa.com` |
| Email | `mcline@dlm-cpa.com` |
| Domain | `dlm-cpa.com` |
| Username | `jsmiller31` |

**Report files:** `person_michael_r_cline.md`, `email_mcline_dlm_cpa.md`, `domain_dlm_cpa.md`, `username_jsmiller31.md`, `combined_osint_report.json`

---

## 1. Person — Michael R. Cline (Arcata, CA)

### High-confidence findings

| Field | Value |
|-------|-------|
| **Name** | Michael R. Cline |
| **Profession** | Certified Public Accountant (CPA) |
| **Business** | Michael R. Cline, CPA, Inc. (President) |
| **Firm affiliation** | David L. Moonie & Co., LLP — 900 G St, Ste 103, Arcata, CA 95521 |
| **Location** | Arcata, Humboldt County, California |
| **Approx. age** | ~57–58 (per MyLife / Whitepages listings) |
| **Email (provided)** | mcline@dlm-cpa.com |
| **Phone (public listings)** | (707) 822-xxxx (Yellow Pages); 707-442-1737 (Whitepages — may be related Michael R. Cline Jr.) |

### Supporting sources

- **DDG Employment:** Corporation Wiki lists Michael R. Cline as President of *Michael R. Cline, CPA, Inc.* in Arcata, CA
- **Manta / MapQuest / Yellow Pages:** Business listings for *Michael R Cline CPA* at Arcata, CA 95521
- **Whitepages:** Multiple Michael R. Cline records nationally; Arcata-area phone association found
- **CourtListener:** 13 federal court records matching "Michael R. Cline" — mostly bankruptcy filings (OH, NC, VA) and unrelated criminal cases; **not verified as the Arcata subject** without case review

### Assessment

Strong public-record match for a **CPA practicing in Arcata, CA**, tied to the **dlm-cpa.com** domain (David L. Moonie & Co.). The provided email domain aligns with the firm. Multiple同名 individuals exist nationwide; geo-filtering to Arcata, CA increases confidence.

---

## 2. Email — mcline@dlm-cpa.com

| Check | Result |
|-------|--------|
| **Disposable (Kickbox)** | No — legitimate business email provider |
| **Gravatar** | No public profile |
| **EmailRep** | API unavailable without key (same as app without API key configured) |
| **Breach exposure (LeakCheck)** | **Found in 2 breaches:** AT&T (Jan 2021), MGMResorts.com (Jul 2019) |
| **Breach fields exposed** | name, phone, address, city, state, zip, password (per LeakCheck metadata) |

### Assessment

Email appears to be a **valid, non-disposable business address** on a long-registered domain. It has **appeared in at least two major data breaches** — credentials and PII may be compromised. Recommend password rotation and breach monitoring.

---

## 3. Domain — dlm-cpa.com

| Attribute | Value |
|-----------|-------|
| **Organization** | David L. Moonie & Co., LLP (CPA firm) |
| **Registered** | 1999-10-20 |
| **Expires** | 2026-10-20 |
| **Registrar** | GoDaddy.com, LLC |
| **Nameservers** | NS53/NS54.DOMAINCONTROL.COM |
| **Primary host** | 185.230.63.107 — **Wix.com** (Ashburn, VA) |
| **Subdomains** | `www.dlm-cpa.com` (34.149.87.45), `secure.dlm-cpa.com` (173.219.117.167), `vpn.dlm-cpa.com` (208.180.37.141) |
| **SSL** | Let's Encrypt cert active (renewed 2026-06-04) |
| **Archive history** | Wayback Machine snapshots from 2001–2013 |

### Assessment

Established CPA firm domain (25+ years). Site hosted on **Wix**. VPN portal subdomain suggests remote-work infrastructure for the firm.

---

## 4. Username — jsmiller31

**Platforms checked:** 17 (mirrors app's `UsernamePlatformRegistry` subset)

| Platform | Status |
|----------|--------|
| **Twitter/X** | Profile exists — https://x.com/jsmiller31 |
| **YouTube** | Profile exists — https://www.youtube.com/@jsmiller31 |
| **Twitch** | Profile exists — https://www.twitch.tv/jsmiller31 |
| **Pinterest** | Profile exists — https://www.pinterest.com/jsmiller31/ |
| GitHub | Not found |
| Reddit | Blocked (403) |
| Instagram, LinkedIn, Steam, etc. | Not found / no profile |

### Cross-reference to Michael R. Cline

No direct link was found between **jsmiller31** and **Michael R. Cline** or **mcline@dlm-cpa.com** in the searches run. The username may belong to a different individual (possibly the search operator). Further manual review of the social profiles would be needed to establish identity.

---

## Source Coverage vs. Full App

This emulation ran the **free/no-key sources** the app uses by default. Not executed (require Android device, API keys, or Termux):

- Have I Been Pwned (HIBP API key)
- Hunter.io verify / domain search (API key)
- Pipl, People Data Labs, Clearbit (API keys)
- Holehe, sherlock, maigret, theHarvester, nmap (Termux on-device)
- Full 74-platform username scan (17 checked here; remainder available in app)
- Two-phase deep investigation (Round 2 scrapers after subject lock)

To run the **complete** in-app experience, build and install the APK on an emulator or device:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Confidence Summary

| Search | Confidence | Notes |
|--------|------------|-------|
| Person (Arcata CPA) | **High** | Multiple independent business/people-finder listings agree |
| Email validity | **High** | Non-disposable, firm-domain aligned |
| Email breach status | **High** | LeakCheck confirmed 2 breaches |
| Domain intel | **High** | RDAP, DNS, hosting data consistent |
| Username jsmiller31 | **Medium** | Profiles exist; identity unverified |
| Username ↔ Person link | **Low / None found** | No cross-reference in automated searches |
