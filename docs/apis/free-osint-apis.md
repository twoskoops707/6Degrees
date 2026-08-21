# Free OSINT APIs — Catalog & Setup

Every source below is **free** (no-key, or a free API key). Sources marked *keyless* need nothing —
they just work. Sources marked *free key* need a free key you enter in **Settings → API Keys** (or
grab via the **Quick Apply / Auto-Apply All** signup wizard, which auto-fills your profile into the
signup form).

The machine-readable version of this list ships with the app at
`app/src/main/assets/free_apis.csv` — same columns, so you can diff what's bundled and add your own
rows (the app's **Import CSV** button in Settings loads keys from `api_name,api_key` rows).

---

## Email

| API | Key slot | Signup | Free tier | What you get |
|-----|----------|--------|-----------|--------------|
| EmailRep.io | `emailrep` | https://emailrep.io/ | 250/mo | Reputation, suspicious flag, breach exposure, linked profiles *(key now required)* |
| HaveIBeenPwned | `hibp` | https://haveibeenpwned.com/API/Key | free personal | Breach names + paste exposure |
| BreachDirectory | `breachdirectory` | https://breachdirectory.org/register | free tier | Breach names, **exposed passwords + hashes** |
| AbstractAPI Email | `abstractapi_email` | https://app.abstractapi.com/users/signup | 100/mo | Deliverability, disposable/free-mail flags, quality score |
| IntelX | `intelx` | https://intelx.io/?signup | free tier | Breach/credential records for the address |
| Gravatar | *(none)* | https://gravatar.com/ | free | Profile image + linked accounts *(keyless)* |
| Kickbox | *(none)* | https://open.kickbox.com/ | free | Disposable-address detection *(keyless)* |

## Phone

| API | Key slot | Signup | Free tier | What you get |
|-----|----------|--------|-----------|--------------|
| Numverify | `numverify` | https://numverify.com/product | 100/mo | Carrier + line type |
| Veriphone | `veriphone` | https://veriphone.io/signup | free tier | Carrier, line type, country, city |
| AbstractAPI Phone | `abstractapi_phone` | https://app.abstractapi.com/users/signup | 500/mo | Carrier, mobile/landline, location |

## IP / Domain

| API | Key slot | Signup | Free tier | What you get |
|-----|----------|--------|-----------|--------------|
| ip-api.com | *(none)* | https://ip-api.com/ | free HTTP | Geo, ISP, ASN *(keyless)* |
| ipwho.is | *(none)* | https://ipwho.is/ | free | Geo, org, timezone *(keyless)* |
| ipinfo.io | *(none)* | https://ipinfo.io/ | 50k/mo | Org, hostname, postal *(keyless)* |
| Shodan InternetDB | *(none)* | https://internetdb.shodan.io/ | free | Open ports + CVEs *(keyless)* |
| AlienVault OTX | *(none)* | https://otx.alienvault.com/ | free | Reputation + threat pulses *(keyless)* |
| Pulsedive | *(none)* | https://pulsedive.com/ | free tier | Indicator risk score *(keyless)* |
| Maltiverse | *(none)* | https://maltiverse.com/ | free tier | Classification + blacklist hits *(keyless)* |
| VirusTotal | `virustotal` | https://www.virustotal.com/gui/join-us | free key | File/URL/domain/IP analysis |
| AbuseIPDB | `abuseipdb` | https://www.abuseipdb.com/register | free key | Abuse reports + confidence score |
| URLScan.io | `urlscan` | https://urlscan.io/user/signup | free tier | Scan history + screenshots |
| GreyNoise | `greynoise` | https://www.greynoise.io/viz/sign-up | free community | Scanner / RIOT classification |
| ThreatFox | `threatfox` | https://threatfox.abuse.ch/ | free Auth-Key | Malware IOC search |
| LeakIX | `leakix` | https://leakix.net/register | free key | Open leaks + honeypots |
| SecurityTrails | `securitytrails` | https://securitytrails.com/app/account | free key | Subdomains + DNS history |
| CriminalIP | `criminalip` | https://www.criminalip.io/user/signup | free key | Threat score + intel |
| Netlas | `netlas` | https://app.netlas.io/registration/ | free key | Internet scan + DNS |
| HackerTarget | *(none)* | https://hackertarget.com/register/ | free tier | DNS, host search, findemail |
| crt.sh | *(none)* | https://crt.sh/ | free | Cert-transparency subdomains *(keyless, occasionally flaky)* |
| URLhaus | `urlhaus` | https://auth.abuse.ch/ | free Auth-Key | Malware URL hosting status |
| RDAP | *(none)* | https://rdap.org/ | free | Registrar/dates/nameservers *(keyless)* |
| Wayback CDX | *(none)* | https://web.archive.org/ | free | Archive history *(keyless)* |

## Person

| API | Key slot | Signup | Free tier | What you get |
|-----|----------|--------|-----------|--------------|
| CourtListener | *(none)* | https://www.courtlistener.com/accounts/register/ | free | Court records *(keyless)* |
| CMS NPI Registry | *(none)* | https://npiregistry.cms.hhs.gov/ | free | Healthcare provider records *(keyless)* |
| OpenFEC | `openfec` | https://api.open.fec.gov/developers/ | free | Campaign finance (defaults to DEMO_KEY) |
| FBI Wanted | *(none)* | https://api.fbi.gov/ | free | Wanted persons *(keyless)* |
| OpenSanctions | `opensanctions` | https://www.opensanctions.org/docs/api/ | free key | Sanctions / PEP screening |
| OpenCorporates | `opencorporates` | https://opencorporates.com/api_accounts/sign_up | free token | Officers + 190+ jurisdiction registry |
| Pipl | `pipl` | https://pipl.com/api/ | free trial | Full person profile |
| People Data Labs | `pdl` | https://www.peopledatalabs.com/signup | 100/mo | Identity graph |
| Name Demographics | *(none)* | https://genderize.io/ | free | Gender/age/nationality *(keyless)* |
| Wikipedia/Wikidata | *(none)* | https://www.wikidata.org/ | free | Encyclopedic *(keyless)* |
| Google News RSS | *(none)* | https://news.google.com/ | free | News mentions *(keyless)* |

## Company

| API | Key slot | Signup | Free tier | What you get |
|-----|----------|--------|-----------|--------------|
| GLEIF | *(none)* | https://api.gleif.org/ | free | LEI registry fuzzy search *(keyless)* |
| SEC EDGAR | *(none)* | https://www.sec.gov/edgar/ | free | Filings + Form-4 insiders *(keyless)* |
| Hunter.io | `hunter` | https://hunter.io/users/sign_up | 25/mo | Company email discovery |
| BuiltWith | `builtwith` | https://builtwith.com/ | free tier | Tech stack |
| OpenCorporates | `opencorporates` | (see Person) | free token | Registry + officers |

## Dark Web / Tor

| API | Key slot | Setup | Notes |
|-----|----------|-------|-------|
| Ahmia | *(none)* | https://ahmia.fi/ | Clearnet + `.onion` index; search is JS-gated, results come through the Tor path when Orbot is running |
| Tor (via Orbot) | *(none)* | Orbot app | Routes `.onion` searches; optional but recommended |

## AI / Dossier

| API | Key slot | Signup | Notes |
|-----|----------|--------|-------|
| OpenRouter | `openrouter` | https://openrouter.ai/keys | Free-tier models synthesize the final dossier |

---

## Adding your own APIs

1. **Keys:** Settings → API Keys → **Import CSV**. Format: `api_name,api_key` (one per line, `#` comments allowed).
2. **List:** add rows to `app/src/main/assets/free_apis.csv` (columns above) so the catalog stays up to date for everyone.
3. **Wiring:** each source needs a scraper in `OsintRepository` + a launch block in the matching search flow — follow the pattern of `scrapePulsedive` (keyless) or `scrapeBreachDirectory` (keyed).
