#!/usr/bin/env python3
"""Generate light and dark SVG star-history charts for this repository."""

from __future__ import annotations

import argparse
import html
import json
import math
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


GRAPHQL_URL = "https://api.github.com/graphql"
MAX_PAGES = 1_000


@dataclass(frozen=True)
class Theme:
    background: str
    border: str
    grid: str
    primary: str
    secondary: str
    faint: str
    line: str
    fill: str


LIGHT = Theme(
    background="#ffffff",
    border="#d0d7de",
    grid="#eaeef2",
    primary="#1f2328",
    secondary="#59636e",
    faint="#818b98",
    line="#c69000",
    fill="#ffd33d",
)

DARK = Theme(
    background="#0d1117",
    border="#30363d",
    grid="#21262d",
    primary="#f0f6fc",
    secondary="#9198a1",
    faint="#656c76",
    line="#f2cc60",
    fill="#d29922",
)


def graphql_request(token: str, query: str, variables: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps({"query": query, "variables": variables}).encode("utf-8")
    request = urllib.request.Request(
        GRAPHQL_URL,
        data=body,
        method="POST",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": "lle-star-history-action",
            "X-GitHub-Api-Version": "2026-03-10",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read(500).decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub GraphQL returned HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"GitHub GraphQL request failed: {exc.reason}") from exc

    errors = payload.get("errors")
    if errors:
        messages = "; ".join(str(item.get("message", item)) for item in errors)
        raise RuntimeError(f"GitHub GraphQL error: {messages}")
    return payload


def fetch_star_timeline(repository: str, token: str) -> tuple[datetime, list[datetime]]:
    try:
        owner, name = repository.split("/", 1)
    except ValueError as exc:
        raise ValueError("repository must use the owner/name format") from exc
    if not owner or not name:
        raise ValueError("repository must use the owner/name format")

    query = """
    query StarTimeline($owner: String!, $name: String!, $cursor: String) {
      repository(owner: $owner, name: $name) {
        createdAt
        stargazers(
          first: 100
          after: $cursor
          orderBy: {field: STARRED_AT, direction: ASC}
        ) {
          totalCount
          pageInfo { hasNextPage endCursor }
          edges { starredAt }
        }
      }
    }
    """

    cursor: str | None = None
    created_at: datetime | None = None
    stars: list[datetime] = []
    expected_total: int | None = None

    for page in range(1, MAX_PAGES + 1):
        payload = graphql_request(
            token,
            query,
            {"owner": owner, "name": name, "cursor": cursor},
        )
        repository_data = payload.get("data", {}).get("repository")
        if repository_data is None:
            raise RuntimeError(f"repository not found or inaccessible: {repository}")

        if created_at is None:
            created_at = parse_github_time(repository_data["createdAt"])
        stargazers = repository_data["stargazers"]
        expected_total = int(stargazers["totalCount"])
        stars.extend(parse_github_time(edge["starredAt"]) for edge in stargazers["edges"])

        page_info = stargazers["pageInfo"]
        if not page_info["hasNextPage"]:
            break
        cursor = page_info.get("endCursor")
        if not cursor:
            raise RuntimeError(f"page {page} reports more results without an end cursor")
    else:
        raise RuntimeError(f"star timeline exceeded the {MAX_PAGES}-page safety limit")

    if created_at is None or expected_total is None:
        raise RuntimeError("GitHub returned an incomplete repository response")

    stars = sorted(set(stars))
    if len(stars) != expected_total:
        raise RuntimeError(
            f"inconsistent star timeline: received {len(stars)} dates, "
            f"but GitHub reports {expected_total} current stars"
        )
    return created_at, stars


def parse_github_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def nice_step(raw: float) -> int:
    if raw <= 1:
        return 1
    magnitude = 10 ** math.floor(math.log10(raw))
    normalized = raw / magnitude
    for candidate in (1, 2, 5, 10):
        if normalized <= candidate:
            return int(candidate * magnitude)
    return int(10 * magnitude)


def format_count(value: int) -> str:
    return f"{value:,}"


def render_svg(
    repository: str,
    created_at: datetime,
    stars: list[datetime],
    theme: Theme,
) -> str:
    width, height = 900, 460
    left, right, top, bottom = 76, 864, 112, 374
    total = len(stars)
    now = datetime.now(timezone.utc)
    start = min(created_at, stars[0] if stars else created_at)
    end = max(now, stars[-1] if stars else now)
    span = max((end - start).total_seconds(), 1.0)

    step = nice_step(max(total, 1) / 4)
    y_top = max(step, math.ceil(max(total, 1) / step) * step)

    def x_of(moment: datetime) -> float:
        return left + (right - left) * (moment - start).total_seconds() / span

    def y_of(value: int) -> float:
        return bottom - (bottom - top) * value / y_top

    escaped_repo = html.escape(repository)
    date_range = f"{start:%b %Y} – {end:%b %Y}"
    updated = f"Updated {now:%d %b %Y}"

    path_parts = [f"M {x_of(start):.1f} {y_of(0):.1f}"]
    for index, starred_at in enumerate(stars, start=1):
        x = x_of(starred_at)
        path_parts.append(f"H {x:.1f} V {y_of(index):.1f}")
    path_parts.append(f"H {x_of(end):.1f}")
    line_path = " ".join(path_parts)
    area_path = f"{line_path} V {bottom} H {x_of(start):.1f} Z"

    y_grid: list[str] = []
    for value in range(0, y_top + step, step):
        if value > y_top:
            break
        y = y_of(value)
        grid_color = theme.border if value == 0 else theme.grid
        y_grid.append(
            f'<line x1="{left}" y1="{y:.1f}" x2="{right}" y2="{y:.1f}" '
            f'stroke="{grid_color}" stroke-width="1"/>'
        )
        y_grid.append(
            f'<text x="{left - 14}" y="{y + 4:.1f}" text-anchor="end" '
            f'class="tick">{value}</text>'
        )

    x_ticks: list[str] = []
    for index in range(6):
        fraction = index / 5
        moment = start + (end - start) * fraction
        x = left + (right - left) * fraction
        x_ticks.append(
            f'<text x="{x:.1f}" y="402" text-anchor="middle" class="tick">'
            f'{moment:%d %b}</text>'
        )

    endpoint_x = x_of(end)
    endpoint_y = y_of(total)
    grid = "".join(y_grid)
    ticks = "".join(x_ticks)

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img">
  <title>{escaped_repo} star history — {format_count(total)} stars</title>
  <desc>Cumulative timeline of current GitHub stargazers from {date_range}.</desc>
  <defs>
    <linearGradient id="star-fill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="{theme.fill}" stop-opacity="0.30"/>
      <stop offset="100%" stop-color="{theme.fill}" stop-opacity="0.02"/>
    </linearGradient>
    <filter id="glow" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="3" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <style>
    text {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; }}
    .tick {{ fill: {theme.secondary}; font-size: 12px; }}
  </style>
  <rect x="0.5" y="0.5" width="899" height="459" rx="14" fill="{theme.background}" stroke="{theme.border}"/>
  <polygon points="42,29 46,39 57,39 48,46 51,57 42,50 33,57 36,46 27,39 38,39"
           fill="{theme.fill}"/>
  <text x="70" y="48" font-size="22" font-weight="700" fill="{theme.primary}">Stargazer</text>
  <text x="70" y="72" font-size="13" fill="{theme.secondary}">{date_range}</text>
  <text x="{right}" y="48" text-anchor="end" font-size="31" font-weight="750" fill="{theme.primary}">{format_count(total)}</text>
  <text x="{right}" y="70" text-anchor="end" font-size="12" fill="{theme.secondary}">GitHub stars</text>
  {grid}
  {ticks}
  <path d="{area_path}" fill="url(#star-fill)"/>
  <path d="{line_path}" fill="none" stroke="{theme.line}" stroke-width="3"
        stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="{endpoint_x:.1f}" cy="{endpoint_y:.1f}" r="5" fill="{theme.line}"
          stroke="{theme.background}" stroke-width="2" filter="url(#glow)"/>
  <text x="{left}" y="438" font-size="11" fill="{theme.faint}">{updated}</text>
  <text x="{right}" y="438" text-anchor="end" font-size="11" fill="{theme.faint}">Generated in GitHub Actions</text>
</svg>
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="GitHub repository in owner/name form (defaults to GITHUB_REPOSITORY)",
    )
    parser.add_argument("--light", type=Path, required=True, help="light SVG output path")
    parser.add_argument("--dark", type=Path, required=True, help="dark SVG output path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    token = os.environ.get("GITHUB_TOKEN")
    if not args.repository:
        print("error: --repository or GITHUB_REPOSITORY is required", file=sys.stderr)
        return 2
    if not token:
        print("error: GITHUB_TOKEN is required", file=sys.stderr)
        return 2

    try:
        created_at, stars = fetch_star_timeline(args.repository, token)
        if not stars:
            raise RuntimeError("GitHub returned an empty star timeline")
        for output, theme in ((args.light, LIGHT), (args.dark, DARK)):
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(
                render_svg(args.repository, created_at, stars, theme),
                encoding="utf-8",
                newline="\n",
            )
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(
        f"Generated {args.light} and {args.dark} from {len(stars)} current stargazers."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
