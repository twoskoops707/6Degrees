#!/usr/bin/env python3
"""Emulate 6Degrees OSINT searches and emit app-style dossier reports."""

import hashlib
import json
import re
import socket
import ssl
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from html import unescape
from xml.etree import ElementTree as ET

UA = "6Degrees/1.0 (Android OSINT; contact@6degrees.app)"
TIMEOUT = 25


def fetch(url, headers=None, accept_json=False):
    h = {"User-Agent": UA, "Accept": "application/json" if accept_json else "*/*"}
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, headers=h)
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT, context=ctx) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except Exception as e:
        return None, str(e)


def fetch_json(url, headers=None):
    status, body = fetch(url, headers=headers, accept_json=True)
    if status and 200 <= status < 300:
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return None
    return None


def md5_hex(s):
    return hashlib.md5(s.strip().lower().encode()).hexdigest()


def resolve_ip(domain):
    try:
        return socket.gethostbyname(domain)
    except Exception:
        return None


def ddg_search(query, max_results=8):
    encoded = urllib.parse.quote(query)
    status, body = fetch(
        f"https://html.duckduckgo.com/html/?q={encoded}",
        headers={"Referer": "https://duckduckgo.com/"},
    )
    if not status or status != 200:
        return []
    results = []
    for m in re.finditer(
        r'class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>.*?class="result__snippet"[^>]*>(.*?)</',
        body,
        re.DOTALL,
    ):
        url, title, snippet = m.group(1), unescape(re.sub("<[^>]+>", "", m.group(2))), unescape(
            re.sub("<[^>]+>", "", m.group(3))
        )
        if "uddg=" in url:
            qs = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
            url = qs.get("uddg", [url])[0]
        results.append({"title": title.strip(), "url": url.strip(), "snippet": snippet.strip()})
        if len(results) >= max_results:
            break
    return results


PLATFORMS = [
    ("GitHub", "https://github.com/{u}", []),
    ("Reddit", "https://www.reddit.com/user/{u}", []),
    ("Twitter/X", "https://x.com/{u}", ["This account doesn't exist", "Account suspended"]),
    ("Instagram", "https://www.instagram.com/{u}/", ["Sorry, this page isn't available", "Page Not Found"]),
    ("TikTok", "https://www.tiktok.com/@{u}", ["Couldn't find this account"]),
    ("YouTube", "https://www.youtube.com/@{u}", ["This page isn't available"]),
    ("Twitch", "https://www.twitch.tv/{u}", ["Sorry. Unless you've got a time machine"]),
    ("Pinterest", "https://www.pinterest.com/{u}/", []),
    ("LinkedIn", "https://www.linkedin.com/in/{u}", ["Page not found", "This profile is not available"]),
    ("Steam", "https://steamcommunity.com/id/{u}", ["The specified profile could not be found"]),
    ("Medium", "https://medium.com/@{u}", ["404", "Out of nothing, something"]),
    ("Dev.to", "https://dev.to/{u}", []),
    ("GitLab", "https://gitlab.com/{u}", []),
    ("Keybase", "https://keybase.io/{u}", []),
    ("HackerNews", "https://news.ycombinator.com/user?id={u}", []),
    ("Chess.com", "https://www.chess.com/member/{u}", []),
    ("Lichess", "https://lichess.org/@/{u}", ["404"]),
    ("Kaggle", "https://www.kaggle.com/{u}", ["404"]),
    ("LeetCode", "https://leetcode.com/{u}/", ["User not found"]),
    ("CodePen", "https://codepen.io/{u}/", []),
    ("Linktree", "https://linktr.ee/{u}", ["Page not found"]),
    ("Gravatar", "https://en.gravatar.com/{u}.json", ['"entry"']),
    ("Telegram", "https://t.me/{u}", ["you can contact"]),
    ("Substack", "https://{u}.substack.com/", ["404"]),
    ("Duolingo", "https://www.duolingo.com/profile/{u}", ["User not found"]),
    ("SoundCloud", "https://soundcloud.com/{u}", ["We can't find that user"]),
    ("Spotify", "https://open.spotify.com/user/{u}", ["Page not found"]),
    ("Patreon", "https://www.patreon.com/{u}", ["Couldn't find that page"]),
    ("Venmo", "https://venmo.com/{u}", ["Page Not Found"]),
    ("Cash App", "https://cash.app/{u}", ["not found"]),
    ("Behance", "https://www.behance.net/{u}", ["User Not Found"]),
    ("Dribbble", "https://dribbble.com/{u}", ["doesn't exist"]),
    ("Replit", "https://replit.com/@{u}", ["Page not found"]),
    ("Letterboxd", "https://letterboxd.com/{u}/", []),
    ("Last.fm", "https://www.last.fm/user/{u}", []),
    ("ArtStation", "https://www.artstation.com/{u}", ["Page not found"]),
    ("Unsplash", "https://unsplash.com/@{u}", ["Page not found"]),
    ("Mastodon", "https://mastodon.social/@{u}", []),
    ("Bluesky", "https://bsky.app/profile/{u}.bsky.social", ["Profile not found"]),
    ("Threads", "https://www.threads.net/@{u}", ["Sorry, this page isn't available"]),
    ("Ko-fi", "https://ko-fi.com/{u}", ["Page not found"]),
    ("Wattpad", "https://www.wattpad.com/user/{u}", ["User not found"]),
    ("Etsy", "https://www.etsy.com/people/{u}", ["Page not found"]),
    ("GoodReads", "https://www.goodreads.com/{u}", ["Page not found"]),
    ("Exercism", "https://exercism.org/profiles/{u}", ["Profile not found"]),
    ("Angel.co", "https://angel.co/u/{u}", ["Page not found"]),
    ("VK", "https://vk.com/{u}", ["error404"]),
    ("ProductHunt", "https://www.producthunt.com/@{u}", ["Page not found"]),
    ("Flickr", "https://www.flickr.com/people/{u}/", []),
    ("Tumblr", "https://{u}.tumblr.com/", ["There's nothing here."]),
    ("About.me", "https://about.me/{u}", []),
    ("Codecademy", "https://www.codecademy.com/profiles/{u}", []),
    ("Codeforces", "https://codeforces.com/profile/{u}", ["User not found"]),
    ("Bandcamp", "https://bandcamp.com/{u}", ["not found"]),
    ("Mixcloud", "https://www.mixcloud.com/{u}/", []),
]


def check_username_platform(name, url_template, absent_markers, username):
    url = url_template.replace("{u}", urllib.parse.quote(username))
    status, body = fetch(url)
    if status is None:
        return None
    if status == 404:
        return None
    for marker in absent_markers:
        if marker.lower() in body.lower():
            return None
    if name == "Gravatar":
        data = fetch_json(url)
        if data and data.get("entry"):
            return url.replace(".json", "")
        return None
    if 200 <= (status or 0) < 400:
        return url
    return None


def scan_username(username):
    found = []
    with ThreadPoolExecutor(max_workers=12) as ex:
        futs = {
            ex.submit(check_username_platform, n, t, m, username): n
            for n, t, m in PLATFORMS
        }
        for fut in as_completed(futs):
            url = fut.result()
            if url:
                found.append((futs[fut], url))
    return sorted(found, key=lambda x: x[0])


def emailrep(email):
    return fetch_json(f"https://emailrep.io/{urllib.parse.quote(email)}")


def leakcheck(email):
    return fetch_json(f"https://leakcheck.io/api/public?check={urllib.parse.quote(email)}")


def kickbox(email):
    return fetch_json(f"https://open.kickbox.com/v1/disposable/{urllib.parse.quote(email)}")


def gravatar(email):
    h = md5_hex(email)
    return fetch_json(f"https://www.gravatar.com/{h}.json")


def hackertarget_email(domain):
    status, body = fetch(f"https://api.hackertarget.com/findemail/?q={urllib.parse.quote(domain)}")
    if status == 200 and body and "error" not in body.lower():
        return [l.strip() for l in body.splitlines() if "@" in l]
    return []


def hackertarget_host(domain):
    status, body = fetch(f"https://api.hackertarget.com/hostsearch/?q={urllib.parse.quote(domain)}")
    if status == 200 and body and "error" not in body.lower():
        return [l.strip() for l in body.splitlines() if l.strip()]
    return []


def hackertarget_dns(domain):
    status, body = fetch(f"https://api.hackertarget.com/dnslookup/?q={urllib.parse.quote(domain)}")
    if status == 200 and body and "error" not in body.lower():
        return body.strip()
    return ""


def hackertarget_whois(domain):
    status, body = fetch(f"https://api.hackertarget.com/whois/?q={urllib.parse.quote(domain)}")
    if status == 200 and body and "error" not in body.lower():
        return body.strip()[:800]
    return ""


def crt_sh(domain):
    data = fetch_json(f"https://crt.sh/?q={urllib.parse.quote(domain)}&output=json")
    if not isinstance(data, list):
        return []
    subs = set()
    for entry in data[:50]:
        nv = entry.get("name_value", "")
        for part in nv.split("\n"):
            p = part.strip().lower()
            if p and domain in p:
                subs.add(p)
    return sorted(subs)[:30]


def wayback(domain):
    status, body = fetch(
        f"https://web.archive.org/cdx/search/cdx?url={urllib.parse.quote(domain)}/*&output=text&limit=20&fl=original,timestamp&collapse=urlkey"
    )
    if status != 200 or not body:
        return []
    lines = [l for l in body.splitlines() if l.strip()]
    return lines


def rdap(domain):
    return fetch_json(f"https://rdap.org/domain/{urllib.parse.quote(domain.lower())}")


def shodan_internetdb(ip):
    return fetch_json(f"https://internetdb.shodan.io/{ip}")


def ip_api(ip):
    return fetch_json(
        f"http://ip-api.com/json/{ip}?fields=status,country,regionName,city,zip,lat,lon,isp,org,as,query,proxy,hosting"
    )


def ipwho(ip):
    return fetch_json(f"https://ipwho.is/{ip}")


def ipinfo(ip):
    return fetch_json(f"https://ipinfo.io/{ip}/json")


def greynoise(ip):
    return fetch_json(f"https://api.greynoise.io/v3/community/{ip}")


def otx(domain):
    return fetch_json(f"https://otx.alienvault.com/api/v1/indicators/domain/{domain}/general")


def urlhaus(domain):
    return fetch_json(f"https://urlhaus-api.abuse.ch/v1/host/{domain}/")


def courtlistener(name):
    return fetch_json(
        f"https://www.courtlistener.com/api/rest/v4/search/?q={urllib.parse.quote(name)}&type=r&order_by=score+desc&page_size=5"
    )


def wikipedia(name):
    enc = urllib.parse.quote(name.replace(" ", "_"))
    return fetch_json(f"https://en.wikipedia.org/api/rest_v1/page/summary/{enc}")


def wikidata(name):
    return fetch_json(
        f"https://www.wikidata.org/w/api.php?action=wbsearchentities&search={urllib.parse.quote(name)}&language=en&format=json&limit=3"
    )


def genderize(first):
    return fetch_json(f"https://api.genderize.io/?name={urllib.parse.quote(first)}")


def agify(first):
    return fetch_json(f"https://api.agify.io/?name={urllib.parse.quote(first)}")


def zippopotam(city, state):
    st = state.upper()[:2]
    city_enc = urllib.parse.quote(city.lower())
    return fetch_json(f"http://api.zippopotam.us/us/{st.lower()}/{city_enc}")


def sec_edgar(name):
    return fetch_json(
        f"https://efts.sec.gov/LATEST/search-index?q=%22{urllib.parse.quote(name)}%22&from=0&size=10"
    )


def google_news(query):
    enc = urllib.parse.quote(query)
    status, body = fetch(f"https://news.google.com/rss/search?q={enc}&hl=en-US&gl=US&ceid=US:en")
    if status != 200:
        return []
    items = []
    try:
        root = ET.fromstring(body)
        for item in root.findall(".//item")[:10]:
            title = item.findtext("title", "")
            link = item.findtext("link", "")
            pub = item.findtext("pubDate", "")
            items.append({"title": title, "link": link, "date": pub})
    except ET.ParseError:
        pass
    return items


def github_user(username):
    return fetch_json(
        f"https://api.github.com/users/{urllib.parse.quote(username)}",
        headers={"Accept": "application/vnd.github+json"},
    )


def reddit_user(username):
    return fetch_json(f"https://www.reddit.com/user/{urllib.parse.quote(username)}/about.json")


def shady_score_email(meta):
    score = 0
    if meta.get("emailrep_breach"):
        score += 25
    if meta.get("leakcheck_found", 0) > 0:
        score += 20
    if meta.get("emailrep_suspicious"):
        score += 15
    if meta.get("kickbox_disposable"):
        score += 10
    return min(score, 100)


def shady_score_person(meta):
    score = shady_score_email(meta) if meta.get("email") else 0
    if meta.get("court_count", 0) > 0:
        score += 20
    if meta.get("sites_found", 0) > 5:
        score += 5
    return min(score, 100)


def shady_score_domain(meta):
    score = 0
    if meta.get("urlhaus_status") and meta["urlhaus_status"] != "online":
        score += 30
    if meta.get("otx_pulse_count", 0) > 0:
        score += 15
    if meta.get("shodan_vulns"):
        score += 25
    return min(score, 100)


def shady_score_username(found_count):
    return min(found_count * 3, 40)


def sec_header(title):
    return f"\n### {title.upper()}\n"


def row(label, value):
    if not value:
        return ""
    return f"- **{label}:** {value}\n"


def report_header(title, subject, search_type, sources_count, shady):
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    return f"""# {title}

**Subject:** {subject}  
**Search type:** {search_type}  
**Generated:** {ts}  
**Sources checked:** {sources_count}  
**Risk assessment:** {shady}/100 — Informational only; not legal advice. Verify critical findings independently.

---
"""


def run_person_search():
    name = "Michael R Cline"
    city = "Arcata"
    state = "CA"
    email = "mcline@dlm-cpa.com"
    meta = {"name": name, "city": city, "state": state, "email": email}
    sources = []

    first = name.split()[0]
    gender = genderize(first)
    agify_data = agify(first)
    zip_data = zippopotam(city, state)
    wiki = wikipedia(name)
    wdata = wikidata(name)
    court = courtlistener(name)
    sec = sec_edgar(name)
    news = google_news(f'"{name}" Arcata CA')
    email_meta = emailrep(email)
    leak = leakcheck(email)
    grav = gravatar(email)

    dork_queries = [
        ("General", f'"{name}" "Arcata" "CA"'),
        ("LinkedIn", f'"{name}" "Arcata" site:linkedin.com'),
        ("People Finders", f'"{name}" "Arcata" site:fastpeoplesearch.com OR site:truepeoplesearch.com OR site:thatsthem.com'),
        ("News", f'"{name}" Arcata CA news'),
        ("Employment", f'"{name}" CPA accountant "Arcata" OR "Humboldt"'),
        ("Email CrossRef", f'"{email}"'),
    ]
    ddg_results = {}
    for label, q in dork_queries:
        ddg_results[label] = ddg_search(q)
        sources.append(f"DDG:{label}")

    for s in ["Wikipedia", "Wikidata", "CourtListener", "SEC EDGAR", "Google News", "EmailRep", "LeakCheck", "Gravatar", "Zippopotam", "Name Demographics"]:
        sources.append(s)

    # Build sections
    subject_file = []
    subject_file.append(sec_header("IDENTITY"))
    if agify_data and agify_data.get("age"):
        subject_file.append(row("Age (estimate)", f"~{agify_data['age']} (Agify.io, {int(agify_data.get('count', 0)):,} samples)"))
    if gender and gender.get("gender"):
        prob = int((gender.get("probability") or 0) * 100)
        subject_file.append(row("Gender (estimate)", f"{gender['gender'].title()} ({prob}% probability)"))
    subject_file.append(row("Search Location", f"{city}, {state}"))
    if zip_data and zip_data.get("places"):
        place = zip_data["places"][0]
        zips = ", ".join(place.get("post code", "").split()[:3]) if isinstance(place.get("post code"), str) else str(place.get("post code", ""))
        subject_file.append(row("ZIP codes (Arcata)", zips))
        subject_file.append(row("Coordinates", f"{place.get('latitude')}, {place.get('longitude')}"))

    wiki_extract = wiki.get("extract") if wiki and wiki.get("type") != "disambiguation" else None
    if wiki_extract:
        subject_file.append(sec_header("WIKIPEDIA"))
        subject_file.append(row("Page", wiki.get("title", "")))
        subject_file.append(row("Info", wiki_extract[:400]))

    snippets = []
    for label, results in ddg_results.items():
        for r in results[:2]:
            snippets.append(f"[{label}] {r['title']}: {r['snippet'][:160]}")
    if snippets:
        subject_file.append(sec_header("WEB INTELLIGENCE"))
        for s in snippets[:12]:
            subject_file.append(row("Result", s))

    if not any("Age" in l or "Location" in l for l in subject_file):
        subject_file.append(row("Status", "Limited identity data from free public sources — people-finder sites may require browser verification"))

    contacts = []
    contacts.append(row("Email", email))
    contacts.append(sec_header(f"EMAIL ADDRESSES (1)"))
    contacts.append(row("Email", email))
    email_ddg = ddg_results.get("Email CrossRef", [])
    if email_ddg:
        contacts.append(sec_header("EMAIL PATTERNS (AUTO-DORK)"))
        for r in email_ddg[:6]:
            contacts.append(row("Intel", f"{r['title']} — {r['snippet'][:120]}"))

    digital = []
    if email_ddg:
        digital.append(sec_header("GOOGLE INTELLIGENCE"))
        digital.append(row("Auto-dork hits", f"{sum(len(v) for v in ddg_results.values())} parsed"))
        for label, results in ddg_results.items():
            for r in results[:3]:
                digital.append(row(label, f"{r['title']}: {r['snippet'][:100]}"))
    linked = [r for r in ddg_results.get("LinkedIn", []) if "linkedin.com" in r["url"]]
    if linked:
        digital.append(sec_header("SOCIAL DISCOVERY"))
        for r in linked[:5]:
            digital.append(row("LinkedIn", r["url"]))

    legal = []
    court_count = court.get("count", 0) if court else 0
    meta["court_count"] = court_count
    if court_count > 0:
        legal.append(sec_header("CRIMINAL & COURT RECORDS"))
        legal.append(row("CourtListener", f"{court_count} case(s)"))
        for hit in (court.get("results") or [])[:5]:
            legal.append(row("Case", hit.get("caseName", hit.get("case_name", "Unknown"))))
    else:
        legal.append(sec_header("LEGAL & COURT RECORDS"))
        legal.append(row("Status", "No court records found in CourtListener federal index"))
    legal.append(row("> Search Court Records", f"https://www.courtlistener.com/?q={urllib.parse.quote(name)}&type=r"))

    intel = []
    if news:
        intel.append(sec_header("GOOGLE NEWS"))
        for n in news[:8]:
            intel.append(row("Article", f"{n['title']} ({n.get('date', '')})"))
            if n.get("link"):
                intel.append(row("Link", n["link"]))

    if wdata and wdata.get("search"):
        intel.append(sec_header("NEWS & PUBLIC RECORDS"))
        for ent in wdata["search"][:3]:
            intel.append(row("WikiData", ent.get("description", ent.get("label", ""))))

    sec_hits = sec.get("hits", {}).get("total", 0) if sec else 0
    if sec_hits:
        intel.append(sec_header("SEC / FINANCIAL FILINGS"))
        intel.append(row("SEC EDGAR hits", str(sec_hits)))
        for h in (sec.get("hits", {}).get("hits") or [])[:5]:
            src = h.get("_source", {})
            intel.append(row("Filing", src.get("display_names", src.get("file_date", "Unknown"))))

    for label, results in ddg_results.items():
        if label in ("Employment", "People Finders") and results:
            intel.append(sec_header("PEOPLE-SEARCH SITE SNIPPETS (AUTO-DORK)"))
            for r in results[:6]:
                intel.append(row("Profile Data", f"{r['title']}: {r['snippet'][:140]}"))

    if email_meta:
        meta["emailrep_breach"] = email_meta.get("details", {}).get("data_breach") or email_meta.get("breach")
        meta["emailrep_suspicious"] = email_meta.get("suspicious")
        if email_meta.get("references"):
            intel.append(row("EmailRep references", str(email_meta.get("references"))))

    shady = shady_score_person({**meta, "emailrep_breach": meta.get("emailrep_breach"), "leakcheck_found": (leak or {}).get("found", 0)})

    body = report_header("BACKGROUND REPORT", name, "Person", len(sources), shady)
    body += "\n## Subject file\n" + "".join(subject_file)
    body += "\n## Contact matrix\n" + "".join(contacts)
    body += "\n## Digital trace\n" + ("".join(digital) if digital else row("Status", "No digital footprint confirmed from dork results"))
    body += "\n## Legal & exposure\n" + "".join(legal)
    body += "\n## Field intel\n" + ("".join(intel) if intel else row("Status", "No additional intelligence found for this subject"))
    return body, len(sources), shady


def run_email_search():
    email = "mcline@dlm-cpa.com"
    domain = email.split("@")[1]
    sources = []
    rep = emailrep(email)
    sources.append("EmailRep")
    leak = leakcheck(email)
    sources.append("LeakCheck")
    kb = kickbox(email)
    sources.append("Kickbox")
    grav = gravatar(email)
    sources.append("Gravatar")
    ht_hosts = hackertarget_email(domain)
    sources.append("HackerTarget Email")

    meta = {}
    subject = []
    subject.append(sec_header("REPUTATION"))
    if rep:
        meta["emailrep_breach"] = rep.get("details", {}).get("data_breach") or rep.get("breach")
        meta["emailrep_suspicious"] = rep.get("suspicious")
        subject.append(row("Reputation", str(rep.get("reputation", "unknown")).title()))
        if rep.get("suspicious"):
            subject.append(row("! Suspicious", "Flagged by EmailRep threat database"))
        if meta["emailrep_breach"]:
            subject.append(row("! Breach Exposure", "Involved in known data breach"))
        subject.append(row("DB References", str(rep.get("references", 0))))
        profiles = rep.get("details", {}).get("profiles") or []
        if profiles:
            subject.append(row("Seen On", ", ".join(profiles) if isinstance(profiles, list) else str(profiles)))
    if kb:
        disp = kb.get("disposable", False)
        meta["kickbox_disposable"] = disp
        subject.append(row("Disposable (Kickbox)", "Yes — temporary provider" if disp else "No"))
    shady = shady_score_email(meta)

    breaches = []
    leak_found = 0
    if leak and leak.get("success"):
        leak_found = leak.get("found", 0)
        meta["leakcheck_found"] = leak_found
        if leak_found > 0:
            breaches.append(sec_header(f"LEAKCHECK — {leak_found} SOURCE(S)"))
            sources_list = leak.get("sources") or []
            if sources_list:
                names = [s.get("name", str(s)) if isinstance(s, dict) else str(s) for s in sources_list]
                breaches.append(row("Leak Sources", ", ".join(names[:15])))
            for src in (leak.get("sources") or [])[:10]:
                if isinstance(src, dict):
                    breaches.append(row("Breach", f"{src.get('name', '')} — {src.get('date', '')}"))
    if meta.get("emailrep_breach") and not breaches:
        breaches.append(sec_header("EMAILREP — BREACH CONFIRMED"))
        breaches.append(row("Status", "Address found in known data breaches"))
        breaches.append(row("Note", "Add HIBP key in Settings → API Keys for full breach names and exposed fields"))
    if not breaches:
        breaches.append(row("Status", "No detailed breach names without HIBP API key; EmailRep flags breach exposure"))

    identity = []
    if grav and grav.get("entry"):
        entry = grav["entry"][0]
        identity.append(sec_header("GRAVATAR PROFILE"))
        identity.append(row("Name", entry.get("displayName", "")))
        identity.append(row("Location", entry.get("currentLocation", "")))
        identity.append(row("Bio", entry.get("aboutMe", "")))
        accounts = entry.get("accounts", [])
        if accounts:
            identity.append(row("Linked Accounts", ", ".join(a.get("shortname", "") for a in accounts)))
    if ht_hosts:
        identity.append(sec_header("THREAT INTEL"))
        identity.append(row("Associated Hosts", ", ".join(ht_hosts[:10])))
    if not identity:
        identity.append(row("Status", "No identity data linked to this email"))

    body = report_header("INTELLIGENCE REPORT", email, "Email", len(sources), shady)
    body += "\n## Subject file\n" + "".join(subject)
    body += "\n## Breach exposure\n" + "".join(breaches)
    body += "\n## Identity links\n" + "".join(identity)
    return body, len(sources), shady


def run_domain_search():
    domain = "dlm-cpa.com"
    sources = []
    ip = resolve_ip(domain)
    rd = rdap(domain)
    sources.append("RDAP")
    dns = hackertarget_dns(domain)
    sources.append("HackerTarget DNS")
    whois = hackertarget_whois(domain)
    sources.append("HackerTarget WHOIS")
    hosts = hackertarget_host(domain)
    sources.append("HackerTarget Host")
    subs = crt_sh(domain)
    sources.append("crt.sh")
    wb = wayback(domain)
    sources.append("Wayback CDX")
    otx_data = otx(domain)
    sources.append("AlienVault OTX")
    uh = urlhaus(domain)
    sources.append("URLhaus")

    shodan = ip_api_data = ipwho_data = ipinfo_data = gn = None
    if ip:
        shodan = shodan_internetdb(ip)
        sources.extend(["Shodan InternetDB", "ip-api", "ipwho.is", "ipinfo.io", "GreyNoise"])
        ip_api_data = ip_api(ip)
        ipwho_data = ipwho(ip)
        ipinfo_data = ipinfo(ip)
        gn = greynoise(ip)

    network = []
    network.append(sec_header("GEOLOCATION"))
    if ip_api_data and ip_api_data.get("status") == "success":
        network.append(row("City", ip_api_data.get("city", "")))
        network.append(row("Country", ip_api_data.get("country", "")))
        network.append(row("Region", ip_api_data.get("regionName", "")))
    if ipwho_data and ipwho_data.get("success"):
        network.append(row("Timezone", ipwho_data.get("timezone", {}).get("id", "") if isinstance(ipwho_data.get("timezone"), dict) else ipwho_data.get("timezone", "")))
    network.append(sec_header("NETWORK"))
    network.append(row("Resolved IP", ip or "Unable to resolve"))
    if ip_api_data:
        network.append(row("ISP", ip_api_data.get("isp", "")))
        network.append(row("Org", ip_api_data.get("org", "")))
        network.append(row("ASN", ip_api_data.get("as", "")))
    if ipinfo_data:
        network.append(row("Hostname", ipinfo_data.get("hostname", "")))
        network.append(row("Postal", ipinfo_data.get("postal", "")))

    threats = []
    meta = {}
    if shodan:
        ports = shodan.get("ports", [])
        vulns = shodan.get("vulns", [])
        if ports or vulns:
            threats.append(sec_header("SHODAN EXPOSURE"))
            threats.append(row("Open Ports", ", ".join(str(p) for p in ports)))
            if vulns:
                meta["shodan_vulns"] = vulns
                threats.append(row("! CVEs", ", ".join(vulns)))
    if gn and gn.get("classification"):
        threats.append(sec_header("GREYNOISE"))
        threats.append(row("Classification", gn.get("classification", "").title()))
        if gn.get("name"):
            threats.append(row("Actor", gn["name"]))
    pulse = (otx_data or {}).get("pulse_info", {}).get("count", 0)
    meta["otx_pulse_count"] = pulse
    if pulse:
        threats.append(row("! OTX Pulses", str(pulse)))
    if uh:
        meta["urlhaus_status"] = uh.get("urlhaus_status", "")
        threats.append(sec_header("URLHAUS"))
        threats.append(row("Status", uh.get("urlhaus_status", "unknown")))
        threats.append(row("URLs on Record", str(uh.get("urls", []).__len__()) if uh.get("urls") else "0"))
    if not threats:
        threats.append(row("Status", "No significant threat intel flags from free sources"))

    registry = []
    registry.append(sec_header("WHOIS / REGISTRATION"))
    if rd:
        for ev in rd.get("events", []):
            action = ev.get("eventAction", "")
            date = ev.get("eventDate", "")[:10]
            if action == "registration":
                registry.append(row("Registered", date))
            elif action == "expiration":
                registry.append(row("Expires", date))
        for ent in rd.get("entities", []):
            roles = ent.get("roles", [])
            vcard = ent.get("vcardArray", [[], []])
            if "registrar" in roles:
                for item in vcard[1] if len(vcard) > 1 else []:
                    if item[0] == "fn":
                        registry.append(row("Registrar", item[3]))
        ns = [n.get("ldhName", "") for n in rd.get("nameservers", [])]
        if ns:
            registry.append(row("Nameservers", ", ".join(ns)))
    if whois:
        registry.append(row("WHOIS", whois[:500]))
    registry.append(sec_header("DNS & CERTIFICATES"))
    if subs:
        registry.append(row("SSL Subdomains", ", ".join(subs[:15])))
        registry.append(row("SSL Certs Found", str(len(subs))))
    if dns:
        registry.append(row("DNS Records", dns[:400]))
    if hosts:
        registry.append(sec_header("HOST SEARCH"))
        for h in hosts[:10]:
            registry.append(row("Host", h))
    registry.append(sec_header("ARCHIVE"))
    if wb:
        registry.append(row("Wayback Snapshots", str(len(wb))))
        if wb:
            ts = wb[0].split()[-1] if wb[0] else ""
            registry.append(row("Sample snapshot", ts))

    shady = shady_score_domain(meta)
    body = report_header("INTELLIGENCE REPORT", domain, "Domain / IP", len(sources), shady)
    body += "\n## Network map\n" + "".join(network)
    body += "\n## Threat intel\n" + "".join(threats)
    body += "\n## Domain registry\n" + "".join(registry)
    return body, len(sources), shady


def run_username_search():
    username = "jsmiller31"
    sources = ["Username Scan", "GitHub", "Reddit"]
    found = scan_username(username)
    gh = github_user(username)
    rd = reddit_user(username)

    handles = []
    handles.append(sec_header(f"{len(found)} PROFILES FOUND ON {len(PLATFORMS)} PLATFORMS"))
    desc = {
        "GitHub": "Code hosting & developer collaboration",
        "Reddit": "Social news aggregation & discussion",
        "Twitter/X": "Microblogging & social network",
        "Instagram": "Photo & video sharing",
        "LinkedIn": "Professional networking",
    }
    for name, url in found:
        d = desc.get(name, "Social / web profile")
        handles.append(row(f"{name} | {d}", url))

    profiles = []
    if gh and gh.get("login"):
        profiles.append(sec_header("GITHUB PROFILE"))
        profiles.append(row("Name", gh.get("name", "")))
        profiles.append(row("Bio", gh.get("bio", "")))
        profiles.append(row("Location", gh.get("location", "")))
        profiles.append(row("Company", gh.get("company", "")))
        profiles.append(row("Public repos", str(gh.get("public_repos", 0))))
        profiles.append(row("Followers", str(gh.get("followers", 0))))
        if gh.get("email"):
            profiles.append(row("Email", gh["email"]))
        profiles.append(row("URL", gh.get("html_url", f"https://github.com/{username}")))
    if rd and rd.get("data"):
        d = rd["data"]
        profiles.append(sec_header("REDDIT PROFILE"))
        profiles.append(row("Name", d.get("name", "")))
        profiles.append(row("Link karma", str(d.get("link_karma", 0))))
        profiles.append(row("Comment karma", str(d.get("comment_karma", 0))))
        profiles.append(row("Created", datetime.fromtimestamp(d.get("created_utc", 0), tz=timezone.utc).strftime("%Y-%m-%d") if d.get("created_utc") else ""))
        profiles.append(row("URL", f"https://www.reddit.com/user/{username}"))

    shady = shady_score_username(len(found))
    body = report_header("INTELLIGENCE REPORT", f"@{username}", "Username", len(sources) + len(PLATFORMS), shady)
    body += "\n## Handle scan\n" + ("".join(handles) if found else row("Status", f"No profiles found on {len(PLATFORMS)} platforms checked"))
    body += "\n## Profile index\n" + ("".join(profiles) if profiles else row("Status", "No enriched profile metadata available"))
    return body, len(sources) + len(PLATFORMS), shady


def main():
    reports = [
        ("person_michael_r_cline", run_person_search),
        ("email_mcline_dlm_cpa", run_email_search),
        ("domain_dlm_cpa", run_domain_search),
        ("username_jsmiller31", run_username_search),
    ]
    summary = []
    for filename, fn in reports:
        print(f"Running {filename}...")
        text, sources, shady = fn()
        path = f"/workspace/reports/{filename}.md"
        with open(path, "w") as f:
            f.write(text)
        summary.append({"file": path, "sources": sources, "shady": shady})
        print(f"  -> {path} ({sources} sources, risk {shady}/100)")

    with open("/workspace/reports/SUMMARY.json", "w") as f:
        json.dump(summary, f, indent=2)
    print("Done.")


if __name__ == "__main__":
    main()
