# 6Degrees — Android OSINT Platform

A comprehensive Android application for gathering publicly available intelligence through legal OSINT techniques. Designed for security researchers, investigators, and journalists.

---

## Search Types

| Type | Description |
|------|-------------|
| **Person** | Name-based lookup: court records, arrest records, public profiles, property, social links |
| **Email** | Breach exposure, reputation, deliverability, linked profiles |
| **Phone** | Carrier/line type validation, reverse lookup links |
| **Username** | Existence check across 74+ platforms |
| **IP / Domain** | Geolocation, threat intel, DNS, WHOIS/RDAP, open ports, BGP |
| **Company** | Corporate registry, SEC filings, officers, domain discovery |
| **Image** | Reverse image search links (TinEye, Google Lens, Yandex, FaceCheck.id) |

---

## Data Sources

### Free — No API Key Required

**Email**
- EmailRep.io — reputation + breach flag
- Gravatar — profile image + linked accounts
- LeakCheck.io — public breach hits
- ThreatCrowd — linked domains
- HackerTarget — email-to-host correlation
- ProxyNova COMB — 3.2B leaked credential dataset
- EVA Email Validator — deliverability, MX, disposable/spam-trap detection

**Phone**
- TrueCaller / WhitePages / Spokeo — lookup links
- Numverify — carrier + line type *(requires free API key)*

**Username (74+ platforms)**
GitHub, Reddit, Twitter/X, Instagram, TikTok, YouTube, Twitch, Pinterest, LinkedIn, Steam,
Flickr, Tumblr, Medium, DeviantArt, SoundCloud, Spotify, GitLab, Keybase, Replit, HackerNews,
ProductHunt, Gravatar, About.me, Wattpad, Patreon, Venmo, Etsy, Behance, Dribbble, Last.fm,
Lichess, Chess.com, Codecademy, Duolingo, NameMC, VSCO, Snapchat, Xbox, PSN, Cashapp,
VK, Telegram, Mastodon, Bluesky, Threads, Substack, Ko-fi, Linktree, Letterboxd,
ArtStation, Unsplash, Mixcloud, Audiomack, Bandcamp, ReverbNation, Steemit, Odysee,
Rumble, Minds, Kaggle, Codeforces, LeetCode, CodePen, OnlyFans, Angel.co, GoodReads,
OkCupid, Xing, Exercism, and more

**IP / Domain**
- ip-api.com — geolocation + ISP + ASN
- ipwho.is — geolocation + org + timezone
- ipinfo.io — org + hostname + postal
- Shodan InternetDB — open ports + CVEs (no key)
- GreyNoise Community — internet scanner / RIOT classification
- Robtex — BGP + passive DNS
- AlienVault OTX — threat pulse count
- HackerTarget — DNS lookup, WHOIS, host search, reverse DNS
- crt.sh — SSL certificate transparency + subdomains
- Wayback Machine CDX — archive snapshot history
- ThreatCrowd — historic resolutions + hashes
- URLhaus — malware URL hosting status
- RDAP — modern WHOIS replacement (registrar, dates, nameservers)
- Maltiverse — threat classification + blacklist membership

**Person**
- CourtListener — federal + state court records
- JailBase — arrest records
- OpenCorporates Officers — officer history
- Wikipedia / WikiData — public encyclopedic records
- FEC Campaign Finance — political donation records
- SEC EDGAR — Form-4 insider filings
- Google News RSS — recent news mentions
- ThatsThem — age, city, phone, relatives (HTML scrape)
- USPhoneBook — address + phone (HTML scrape)
- ShadowDork Engine — 15 specialized Google/Bing dork queries
- Name Demographics — gender probability, average age, nationality (Genderize/Agify/Nationalize)

**Company**
- OpenCorporates — company registry (190+ jurisdictions)
- OpenCorporates Officers — associated executives
- WikiData — company knowledge graph
- SEC EDGAR — filing history
- Links: Crunchbase, LinkedIn, OpenBB, SEC full-text

### Paid / API Key Required

| Service | Use Case | Key Storage |
|---------|----------|-------------|
| HaveIBeenPwned | Email breach details | Settings |
| Pipl | Full person profile | Settings |
| Hunter.io | Company email discovery | Settings |
| Clearbit | Person/company enrichment | Settings |
| People Data Labs | Identity graph | Settings |
| Numverify | Phone validation | Settings |
| Google CSE | Custom search dorks | Settings |
| Bing Web Search | Person search dorks | Settings |
| Shodan | Full port/banner scan | Settings |
| VirusTotal | File/URL/domain analysis | Settings |
| AbuseIPDB | IP abuse reports | Settings |
| URLScan.io | Domain scan history | Settings |
| BuiltWith | Tech stack lookup | Settings |

---

## Installation

### From GitHub Releases
1. Go to [Releases](https://github.com/twoskoops707/6Degrees/releases)
2. Download the latest APK
3. Enable "Install from unknown sources" on your device
4. Install and launch

### Building from Source
1. Clone this repository
2. Open in Android Studio (Hedgehog or newer)
3. Build → Generate Signed APK or run on device

---

## API Key Setup

Open the app → **Settings** → enter keys for any paid services you have access to.

All keys are stored locally in Android EncryptedSharedPreferences and never transmitted to any server other than the respective API provider.

---

## Shady Score

Each search result is assigned a **Shady Score** — a composite trust/risk rating based on:
- Number of breach hits
- Threat intelligence matches
- Social footprint breadth
- Court/arrest record presence

Score range: 0 (clean) → 100 (high concern)

---

## Architecture

- **Language:** Kotlin
- **UI:** Material Design 3, dark terminal theme
- **Architecture:** MVVM + Repository pattern
- **Networking:** Retrofit 2 + OkHttp 4 + Moshi
- **Database:** Room (local report history)
- **Async:** Kotlin Coroutines + Flow
- **Navigation:** Navigation Component (single-activity)
- **Image:** Coil
- **Security:** AndroidX Security Crypto (EncryptedSharedPreferences)

---

## Legal & Ethics

This tool accesses **only publicly available information** through legal means:
- All sources are public APIs or publicly indexed web data
- No unauthorized access to private systems
- No storage of credentials or sensitive personal data beyond local report cache
- Respects robots.txt and API rate limits
- GDPR / CCPA compliant by design (no data transmitted to third-party servers beyond the OSINT APIs themselves)

**Use responsibly.** This tool is intended for authorized security research, journalism, and personal privacy auditing only.

---

## License

MIT License — see [LICENSE](LICENSE) for details.
