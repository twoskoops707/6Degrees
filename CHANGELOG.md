# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- ProxyNova COMB breach search — free keyless check against 3.2B leaked credentials
- EVA Email Validator — deliverability, MX record, disposable address, spam trap detection
- ipwho.is — additional IP geolocation source (org, city, region, timezone)
- ipinfo.io — IP org/ASN + hostname + postal code
- Maltiverse — IP threat classification + blacklist membership (free tier)
- RDAP domain lookup via rdap.org — modern WHOIS replacement (registrar, dates, nameservers)
- Name Demographics — gender probability, average age, nationality inference (Genderize/Agify/Nationalize)
- Expanded username platform coverage from 40 to 74 platforms: VK, Telegram, Mastodon, Bluesky, Threads, Substack, Ko-fi, Linktree, Letterboxd, ArtStation, Unsplash, Mixcloud, Audiomack, Bandcamp, ReverbNation, Steemit, Odysee, Rumble, Minds, Kaggle, Codeforces, LeetCode, CodePen, OnlyFans, Angel.co, GoodReads, OkCupid, Xing, Exercism

## [1.0.0] — 2025

### Added
- 7 search types: person, email, phone, username, IP/domain, company, image
- Live search progress UI with per-source status (checking / found / not found / failed)
- Reports tab with full search history stored in Room database
- API key management with EncryptedSharedPreferences storage and monthly usage tracking
- Shady Score — composite risk/trust rating per search result
- ShadowDork Engine — 15 specialized Google/Bing dork query generators for person searches
- ThatsThem + USPhoneBook HTML scraping for person age, address, phone, relative data
- Google CSE + Bing Web Search integration (requires API keys) for targeted dork execution
- Dark terminal-style UI theme
- Camera + gallery image selection for reverse image search
- Clickable source URLs in result reports
- Report export and delete history functionality

**Email sources:** EmailRep.io, HaveIBeenPwned (key), Gravatar, LeakCheck.io, ThreatCrowd, HackerTarget

**Phone sources:** Numverify (key), TrueCaller / WhitePages / Spokeo links

**Username sources:** 40 platform HTTP HEAD checks + GitHub API, Keybase API, HackerNews API, Dev.to API

**IP/Domain sources:** ip-api.com, HackerTarget (DNS/WHOIS/host search), crt.sh, Wayback CDX, AlienVault OTX, Shodan InternetDB, GreyNoise Community, Robtex, AbuseIPDB (key), URLhaus, ThreatCrowd

**Person sources:** Pipl (key), CourtListener, OpenCorporates Officers, JailBase, Wikipedia, WikiData, FEC Campaign Finance, SEC EDGAR, Google News RSS, ThatsThem, USPhoneBook, ShadowDork Engine, Google CSE (key), Bing Search (key)

**Company sources:** OpenCorporates, OpenCorporates Officers, Hunter.io (key), WikiData, SEC EDGAR

**Image sources:** TinEye, Google Lens, Yandex Images, FaceCheck.id, Bing Visual Search (links)

### Initial Structure
- Networking: Retrofit 2 + OkHttp 4 + Moshi + Scalars converters
- Database: Room with OsintReport, Person DAOs
- Navigation Component single-activity architecture
- GitHub Actions CI/CD pipeline
