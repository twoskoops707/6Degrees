#!/usr/bin/env python3
"""
Emulates 6Degrees OSINT search pipeline (OsintRepository) for headless runs.
Mirrors key free API sources used by the Android app.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime, timezone
from html import unescape
from typing import Any

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
TIMEOUT = 15


@dataclass
class SourceResult:
    name: str
    url: str
    status: str  # found | not_found | blocked | error
    detail: str = ""
    fields: dict[str, Any] = field(default_factory=dict)


def fetch(url: str, headers: dict | None = None, accept_json: bool = False) -> tuple[int, str]:
    h = {"User-Agent": UA, "Accept": "application/json" if accept_json else "*/*"}
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        return e.code, body
    except Exception as e:
        return 0, str(e)


def ddg_instant(query: str) -> SourceResult:
    url = f"https://api.duckduckgo.com/?q={urllib.parse.quote(query)}&format=json&no_html=1&skip_disambig=1"
    code, body = fetch(url)
    if code != 200 or not body.startswith("{"):
        return SourceResult("DuckDuckGo", url, "not_found")
    data = json.loads(body)
    abstract = data.get("AbstractText") or data.get("Definition") or ""
    snippets = []
    for topic in (data.get("RelatedTopics") or [])[:8]:
        if isinstance(topic, dict) and topic.get("Text"):
            snippets.append(topic["Text"][:200])
    detail = abstract or "\n".join(snippets[:3])
    if not detail.strip():
        return SourceResult("DuckDuckGo", url, "not_found")
    return SourceResult("DuckDuckGo", url, "found", detail[:800], {
        "abstract": abstract[:800],
        "snippets": snippets,
        "source": data.get("AbstractSource", ""),
        "source_url": data.get("AbstractURL", ""),
    })


def ddg_html_search(query: str) -> SourceResult:
    url = f"https://html.duckduckgo.com/html/?q={urllib.parse.quote(query)}"
    code, body = fetch(url)
    if code != 200:
        return SourceResult(f"DDG HTML", url, "error", f"HTTP {code}")
    results = []
    for m in re.finditer(r'class="result__a"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>', body):
        href, title = m.group(1), unescape(m.group(2).strip())
        results.append(f"{title} — {href}")
    for m in re.finditer(r'class="result__snippet"[^>]*>([^<]+)</', body):
        results.append(unescape(m.group(1).strip())[:200])
    if not results:
        return SourceResult("DDG HTML", url, "not_found")
    return SourceResult("DDG HTML", url, "found", "\n".join(results[:12])[:1200])


def emailrep(email: str) -> SourceResult:
    url = f"https://emailrep.io/{urllib.parse.quote(email.lower())}"
    code, body = fetch(url, accept_json=True)
    if code != 200 or not body.startswith("{"):
        return SourceResult("EmailRep", url, "not_found")
    data = json.loads(body)
    details = data.get("details") or {}
    profiles = details.get("profiles") or []
    detail = f"Reputation: {data.get('reputation','?')} | Suspicious: {data.get('suspicious')} | References: {data.get('references',0)}"
    if profiles:
        detail += f" | Profiles: {', '.join(profiles)}"
    return SourceResult("EmailRep", url, "found", detail, data)


def leakcheck(email: str) -> SourceResult:
    url = f"https://leakcheck.io/api/public?check={urllib.parse.quote(email.lower())}"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("LeakCheck", url, "error", f"HTTP {code}")
    data = json.loads(body)
    if not data.get("found"):
        return SourceResult("LeakCheck", url, "not_found")
    sources = [s.get("name", "") for s in (data.get("sources") or [])]
    return SourceResult("LeakCheck", url, "found", f"{len(sources)} breach(es): {', '.join(sources)}", data)


def kickbox(email: str) -> SourceResult:
    url = f"https://open.kickbox.com/v1/disposable/{urllib.parse.quote(email.lower())}"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("Kickbox", url, "not_found")
    data = json.loads(body)
    disp = data.get("disposable", False)
    return SourceResult("Kickbox", url, "found", f"Disposable: {disp}", data)


def gravatar(email: str) -> SourceResult:
    h = hashlib.md5(email.strip().lower().encode()).hexdigest()
    url = f"https://www.gravatar.com/{h}.json"
    code, body = fetch(url)
    if code == 404 or not body.startswith("{"):
        return SourceResult("Gravatar", url, "not_found")
    data = json.loads(body)
    entry = (data.get("entry") or [{}])[0]
    name = (entry.get("name") or {}).get("formatted") or entry.get("displayName", "")
    accounts = entry.get("accounts") or []
    linked = ", ".join(f"{a.get('shortname','')} ({a.get('display','')})" for a in accounts)
    detail = f"Name: {name}" + (f" | Accounts: {linked}" if linked else "")
    return SourceResult("Gravatar", url, "found" if name or accounts else "not_found", detail, entry)


def hackertarget(query: str, mode: str = "host") -> SourceResult:
    if mode == "email":
        url = f"https://api.hackertarget.com/findemail/?q={urllib.parse.quote(query)}"
    else:
        url = f"https://api.hackertarget.com/hostsearch/?q={urllib.parse.quote(query)}"
    code, body = fetch(url)
    if code != 200 or not body or body.startswith("error") or len(body) < 10:
        return SourceResult("HackerTarget", url, "not_found", body[:200])
    lines = [l for l in body.strip().splitlines() if l.strip()][:20]
    return SourceResult("HackerTarget", url, "found", "\n".join(lines), {"lines": lines})


def crtsh(domain: str) -> SourceResult:
    url = f"https://crt.sh/?q={urllib.parse.quote(domain)}&output=json"
    code, body = fetch(url)
    if code != 200 or not body.strip().startswith("["):
        return SourceResult("crt.sh", url, "not_found")
    try:
        certs = json.loads(body)
    except json.JSONDecodeError:
        return SourceResult("crt.sh", url, "error", "Invalid JSON")
    subs = sorted({c.get("name_value", "") for c in certs if c.get("name_value")})
    subs_clean = sorted({s.strip() for entry in subs for s in entry.split("\n") if domain in s})[:30]
    return SourceResult("crt.sh", url, "found", f"{len(certs)} cert(s), {len(subs_clean)} subdomain(s)", {"subdomains": subs_clean})


def rdap(domain: str) -> SourceResult:
    url = f"https://rdap.org/domain/{domain}"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("RDAP", url, "not_found")
    data = json.loads(body)
    events = {e.get("eventAction"): e.get("eventDate", "")[:10] for e in (data.get("events") or [])}
    entities = []
    for ent in (data.get("entities") or [])[:5]:
        roles = ",".join(ent.get("roles") or [])
        vcard = ent.get("vcardArray", [None, []])
        fn = ""
        if len(vcard) > 1:
            for item in vcard[1]:
                if item[0] == "fn":
                    fn = item[3]
        entities.append(f"{roles}: {fn}")
    detail = f"Created: {events.get('registration','?')} | Expires: {events.get('expiration','?')} | Entities: {'; '.join(entities)}"
    return SourceResult("RDAP", url, "found", detail, data)


def wayback(domain: str) -> SourceResult:
    url = f"https://web.archive.org/cdx/search/cdx?url={urllib.parse.quote(domain)}/*&output=json&limit=10"
    code, body = fetch(url)
    if code != 200:
        return SourceResult("Wayback CDX", url, "not_found")
    try:
        rows = json.loads(body)
    except json.JSONDecodeError:
        return SourceResult("Wayback CDX", url, "not_found")
    if len(rows) < 2:
        return SourceResult("Wayback CDX", url, "not_found")
    snapshots = [f"{r[1]} — https://web.archive.org/web/{r[1]}/{r[2]}" for r in rows[1:11] if len(r) >= 3]
    return SourceResult("Wayback CDX", url, "found", f"{len(rows)-1} snapshot(s)\n" + "\n".join(snapshots))


def courtlistener(name: str) -> SourceResult:
    q = urllib.parse.quote(f'"{name}"')
    url = f"https://www.courtlistener.com/api/rest/v4/search/?q={q}&type=r&order_by=score+desc&page_size=5"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("CourtListener", url, "blocked" if code == 403 else "error", f"HTTP {code}")
    data = json.loads(body)
    count = data.get("count", 0)
    if count == 0:
        return SourceResult("CourtListener", url, "not_found")
    cases = []
    for r in (data.get("results") or [])[:5]:
        cases.append(f"{r.get('caseName','')} ({r.get('court_citation_string','')}) {r.get('dateFiled','')}")
    return SourceResult("CourtListener", url, "found", f"{count} case(s)\n" + "\n".join(cases), {"count": count})


def opensanctions(name: str) -> SourceResult:
    url = f"https://api.opensanctions.org/search/default?q={urllib.parse.quote(name)}&limit=5"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("OpenSanctions", url, "not_found" if code == 404 else "error", f"HTTP {code}")
    data = json.loads(body)
    results = data.get("results") or []
    if not results:
        return SourceResult("OpenSanctions", url, "not_found")
    hits = [f"{r.get('caption','')} ({r.get('schema','')})" for r in results[:5]]
    return SourceResult("OpenSanctions", url, "found", "\n".join(hits), data)


def wikipedia(name: str) -> SourceResult:
    url = f"https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch={urllib.parse.quote(name)}&format=json&srlimit=3"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("Wikipedia", url, "not_found")
    data = json.loads(body)
    hits = (data.get("query") or {}).get("search") or []
    if not hits:
        return SourceResult("Wikipedia", url, "not_found")
    detail = "\n".join(f"{h['title']}: {re.sub('<[^>]+>', '', h.get('snippet',''))}" for h in hits)
    return SourceResult("Wikipedia", url, "found", detail)


def google_news(query: str) -> SourceResult:
    url = f"https://news.google.com/rss/search?q={urllib.parse.quote(query)}&hl=en-US&gl=US&ceid=US:en"
    code, body = fetch(url)
    if code != 200:
        return SourceResult("Google News", url, "not_found")
    titles = re.findall(r"<title><!\[CDATA\[(.*?)\]\]></title>", body)
    titles = [unescape(t) for t in titles if t != "Google News"][1:8]
    if not titles:
        return SourceResult("Google News", url, "not_found")
    return SourceResult("Google News", url, "found", "\n".join(titles))


def ip_api(host: str) -> SourceResult:
    url = f"http://ip-api.com/json/{urllib.parse.quote(host)}?fields=status,message,country,regionName,city,isp,org,as,lat,lon,query"
    code, body = fetch(url, accept_json=True)
    if code != 200:
        return SourceResult("ip-api", url, "not_found")
    data = json.loads(body)
    if data.get("status") != "success":
        return SourceResult("ip-api", url, "not_found", data.get("message", ""))
    detail = f"{data.get('query')} — {data.get('city')}, {data.get('regionName')}, {data.get('country')} | ISP: {data.get('isp')} | ASN: {data.get('as')}"
    return SourceResult("ip-api", url, "found", detail, data)


def check_username_platform(platform: str, username: str, url_template: str, check_url: str | None, absent_markers: list[str]) -> SourceResult | None:
    profile_url = url_template.replace("{u}", username)
    check = (check_url or url_template).replace("{u}", username)
    code, body = fetch(check)
    if code == 404:
        return None
    if code == 0:
        return None
    for marker in absent_markers:
        if marker.lower() in body.lower():
            return None
    if platform == "Gravatar" and '"entry"' not in body:
        return None
    if code in (200, 301, 302) and len(body) > 100:
        return SourceResult(f"Username/{platform}", profile_url, "found", f"Profile exists (HTTP {code})")
    return None


USERNAME_PLATFORMS = [
    ("GitHub", "https://github.com/{u}", None, []),
    ("Reddit", "https://www.reddit.com/user/{u}", None, []),
    ("Twitter/X", "https://x.com/{u}", None, []),
    ("Instagram", "https://www.instagram.com/{u}/", None, ["Sorry, this page isn't available", "Page Not Found"]),
    ("TikTok", "https://www.tiktok.com/@{u}", None, ["Couldn't find this account"]),
    ("YouTube", "https://www.youtube.com/@{u}", None, ["This page isn't available"]),
    ("Twitch", "https://www.twitch.tv/{u}", None, ["Sorry. Unless you've got a time machine"]),
    ("LinkedIn", "https://www.linkedin.com/in/{u}", None, ["Page not found"]),
    ("Steam", "https://steamcommunity.com/id/{u}", None, ["The specified profile could not be found"]),
    ("Medium", "https://medium.com/@{u}", None, ["404", "Out of nothing, something"]),
    ("Dev.to", "https://dev.to/{u}", None, []),
    ("Keybase", "https://keybase.io/{u}", None, []),
    ("Pinterest", "https://www.pinterest.com/{u}/", None, []),
    ("SoundCloud", "https://soundcloud.com/{u}", None, ["We can't find that user"]),
    ("Linktree", "https://linktr.ee/{u}", None, ["Page not found"]),
    ("Chess.com", "https://www.chess.com/member/{u}", None, []),
    ("Gravatar", "https://en.gravatar.com/{u}", "https://en.gravatar.com/{u}.json", []),
]


def username_scan(username: str) -> list[SourceResult]:
    found = []
    with ThreadPoolExecutor(max_workers=8) as ex:
        futs = {
            ex.submit(check_username_platform, pname, username, tpl, chk, absent): pname
            for pname, tpl, chk, absent in USERNAME_PLATFORMS
        }
        for fut in as_completed(futs):
            try:
                r = fut.result()
                if r:
                    found.append(r)
            except Exception:
                pass
    return sorted(found, key=lambda x: x.name)


def enrich_github(username: str) -> SourceResult | None:
    url = f"https://api.github.com/users/{urllib.parse.quote(username)}"
    code, body = fetch(url, accept_json=True)
    if code == 404:
        return None
    if code != 200:
        return None
    data = json.loads(body)
    detail = f"{data.get('name','')} | {data.get('location','')} | {data.get('bio','')} | repos:{data.get('public_repos',0)} followers:{data.get('followers',0)}"
    return SourceResult("GitHub API", data.get("html_url", url), "found", detail, data)


def build_person_ddg_queries(name: str, city: str, state: str, email: str) -> list[tuple[str, str]]:
    loc = " ".join(x for x in [city, state] if x)
    loc_suffix = f" {loc}" if loc else ""
    return [
        ("General", f'"{name}"{loc_suffix}'),
        ("LinkedIn", f'site:linkedin.com "{name}"{loc_suffix}'),
        ("Employment", f'"{name}" employer company job{loc_suffix}'),
        ("FPS", f'site:fastpeoplesearch.com "{name}"{loc_suffix}'),
        ("Whitepages", f'site:whitepages.com "{name}"{loc_suffix}'),
        ("EmailCrossRef", f'"{email}" "{name}"' if email else None),
        ("DLM CPA", f'"{name}" dlm-cpa.com' if email else None),
    ]


def run_person_search(name: str, city: str, state: str, email: str) -> dict:
    loc = ", ".join(x for x in [city, state] if x)
    query = f"name={name}|city={city}|state={state}|email={email}"
    results: list[SourceResult] = []
    results.append(ddg_instant(f"{name} {loc}".strip()))
    for label, q in build_person_ddg_queries(name, city, state, email):
        if q:
            r = ddg_html_search(q)
            r.name = f"DDG:{label}"
            results.append(r)
            time.sleep(0.3)
    for fn in [lambda: wikipedia(name), lambda: google_news(f"{name} {city}"), lambda: courtlistener(name), lambda: opensanctions(name)]:
        try:
            results.append(fn())
        except Exception as e:
            results.append(SourceResult(fn.__name__ if hasattr(fn, '__name__') else "source", "", "error", str(e)))
    if email:
        results.append(ddg_html_search(f'"{email}" "{name}"'))
    return {"search_type": "person", "query": query, "subject": {"name": name, "city": city, "state": state, "email": email}, "results": results}


def run_email_search(email: str) -> dict:
    query = f"email={email}"
    results = [
        emailrep(email),
        leakcheck(email),
        kickbox(email),
        gravatar(email),
        hackertarget(email, "email"),
        ddg_html_search(f'"{email}"'),
        ddg_html_search(f'site:linkedin.com "{email}"'),
    ]
    return {"search_type": "email", "query": query, "subject": {"email": email}, "results": results}


def run_domain_search(domain: str) -> dict:
    query = f"domain={domain}"
    results = [
        hackertarget(domain, "host"),
        crtsh(domain),
        rdap(domain),
        wayback(domain),
        ip_api(domain),
        google_news(domain),
        ddg_html_search(f'site:{domain}'),
    ]
    return {"search_type": "domain", "query": query, "subject": {"domain": domain}, "results": results}


def run_username_search(username: str) -> dict:
    query = f"username={username}"
    hits = username_scan(username)
    results: list[SourceResult] = list(hits)
    gh = enrich_github(username)
    if gh:
        results.append(gh)
    results.append(ddg_html_search(f'"{username}"'))
    results.append(ddg_html_search(f'site:github.com "{username}"'))
    return {"search_type": "username", "query": query, "subject": {"username": username}, "results": results, "platforms_checked": len(USERNAME_PLATFORMS)}


def serialize_report(report: dict) -> dict:
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "app": "6Degrees OSINT Emulator v1.0",
        **report,
        "results": [
            {
                "source": r.name,
                "url": r.url,
                "status": r.status,
                "detail": r.detail[:2000] if r.detail else "",
                "fields": {k: v for k, v in (r.fields or {}).items() if k not in ("vcardArray",)} if isinstance(r.fields, dict) else r.fields,
            }
            for r in report["results"]
        ],
        "summary": {
            "total_sources": len(report["results"]),
            "found": sum(1 for r in report["results"] if r.status == "found"),
            "not_found": sum(1 for r in report["results"] if r.status == "not_found"),
            "errors": sum(1 for r in report["results"] if r.status in ("error", "blocked")),
        },
    }


def format_markdown(report: dict) -> str:
    s = report["summary"]
    lines = [
        f"# 6Degrees OSINT Report — {report['search_type'].upper()}",
        "",
        f"**Generated:** {report['generated_at']}",
        f"**Query:** `{report['query']}`",
        "",
        "## Subject",
        "",
    ]
    for k, v in report.get("subject", {}).items():
        lines.append(f"- **{k.title()}:** {v}")
    lines += [
        "",
        f"## Summary ({s['found']}/{s['total_sources']} sources returned data)",
        "",
    ]
    for r in report["results"]:
        if r["status"] != "found":
            continue
        lines.append(f"### {r['source']}")
        if r.get("url"):
            lines.append(f"- URL: {r['url']}")
        lines.append(f"- {r['detail'][:1500]}")
        lines.append("")
    lines.append("## All Source Status")
    lines.append("")
    lines.append("| Source | Status |")
    lines.append("|--------|--------|")
    for r in report["results"]:
        lines.append(f"| {r['source']} | {r['status']} |")
    return "\n".join(lines)


def main():
    out_dir = "/workspace/reports"
    import os
    os.makedirs(out_dir, exist_ok=True)

    person = run_person_search("Michael r Cline", "Arcata", "CA", "mcline@dlm-cpa.com")
    email = run_email_search("mcline@dlm-cpa.com")
    domain = run_domain_search("dlm-cpa.com")
    username = run_username_search("jsmiller31")

    reports = {
        "person_michael_r_cline": serialize_report(person),
        "email_mcline_dlm_cpa": serialize_report(email),
        "domain_dlm_cpa": serialize_report(domain),
        "username_jsmiller31": serialize_report(username),
    }

    for key, data in reports.items():
        json_path = f"{out_dir}/{key}.json"
        md_path = f"{out_dir}/{key}.md"
        with open(json_path, "w") as f:
            json.dump(data, f, indent=2, default=str)
        with open(md_path, "w") as f:
            f.write(format_markdown(data))
        print(f"Wrote {json_path} and {md_path}")

    combined = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "searches": reports,
    }
    with open(f"{out_dir}/combined_osint_report.json", "w") as f:
        json.dump(combined, f, indent=2, default=str)
    print(f"Wrote {out_dir}/combined_osint_report.json")


if __name__ == "__main__":
    main()
