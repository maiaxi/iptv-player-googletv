"""A fake Xtream Codes panel for testing the TV app on the emulator (http://10.0.2.2:8766)."""
import base64
import gzip
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, unquote, urlparse

USER, PASS = "demo", "p@ss word"
HLS = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
MP4S = [
    "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4",
    "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
]

SERIES_INFO = {
    "info": {
        "name": "Test Show", "plot": "Two seasons of short test clips, served by a fake Xtream panel.",
        "genre": "Drama, Testing", "releaseDate": "2024-03-01", "cover": "",
        "backdrop_path": [],
    },
    # Keyed by season, with numbers as strings and out of order, like real panels.
    "episodes": {
        "2": [
            {"id": "201", "episode_num": "2", "title": "Second of S2", "container_extension": "mp4", "season": "2",
             "info": {"plot": "Season two, episode two.", "duration": "00:00:10"}},
            {"id": "200", "episode_num": 1, "title": "First of S2", "container_extension": "mp4", "season": 2,
             "info": {"plot": "Season two, episode one.", "duration": "00:00:10"}},
        ],
        "1": [
            {"id": "101", "episode_num": "1", "title": "Pilot", "container_extension": "mp4", "season": "1",
             "info": {"plot": "The first episode.", "duration": "00:00:10"}},
            {"id": "102", "episode_num": "2", "title": "The Return", "container_extension": "mp4", "season": "1",
             "info": {"plot": "The second episode, which plays next on its own.", "duration": "00:00:10"}},
            {"id": "103", "episode_num": "3", "title": "Finale", "container_extension": "mp4", "season": "1",
             "info": {"duration": "00:00:10"}},
        ],
    },
}


def slots(count=8, minutes=30):
    """Programme slots starting at the last half hour, as (start, stop) epoch seconds."""
    first = int(time.time()) // (minutes * 60) * (minutes * 60) - minutes * 60
    return [(first + i * minutes * 60, first + (i + 1) * minutes * 60) for i in range(count)]


def xmltv():
    fmt = lambda t: time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t))
    programmes = "".join(
        f'<programme start="{fmt(a)}" stop="{fmt(b)}" channel="bipbop.test">'
        f"<title>Bipbop Show {i}</title><desc>Slot {i} &amp; more</desc></programme>"
        for i, (a, b) in enumerate(slots())
    )
    return f'<?xml version="1.0"?><tv><channel id="bipbop.test"/>{programmes}</tv>'


def short_epg():
    b64 = lambda s: base64.b64encode(s.encode()).decode()
    return {"epg_listings": [
        {"title": b64(f"Short EPG Programa {i} ção"), "description": b64("Base64 description"),
         "start_timestamp": str(a), "stop_timestamp": str(b)}
        for i, (a, b) in enumerate(slots(4))
    ]}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        if url.path == "/xmltv.php":
            return self.send(gzip.compress(xmltv().encode()), "application/gzip")
        if url.path == "/player_api.php":
            if q.get("username") != USER or q.get("password") != PASS:
                return self.json({"user_info": {"auth": 0}})
            return self.json(self.api(q.get("action")))
        parts = [unquote(p) for p in url.path.strip("/").split("/")]
        if len(parts) == 4 and parts[1] == USER and parts[2] == PASS:
            kind, name = parts[0], parts[3]
            if kind == "live":
                return self.redirect(HLS)
            stream_id = int(name.split(".")[0])
            return self.redirect(MP4S[stream_id % len(MP4S)])
        self.send_error(404)

    def api(self, action):
        if action is None:
            return {"user_info": {"auth": 1, "status": "Active", "exp_date": "1893456000",
                                  "max_connections": "1", "allowed_output_formats": ["m3u8", "ts"]}}
        return {
            "get_live_categories": [{"category_id": 1, "category_name": "News"}],
            # The first channel is in the XMLTV guide; the second only has a short EPG. Both are
            # `.ts` addresses (the account allows ts) that redirect to HLS, as some panels do.
            "get_live_streams": [{"stream_id": "1", "name": "Bipbop Live", "category_id": "1",
                                  "epg_channel_id": "BIPBOP.test"},
                                 {"stream_id": 2, "name": "Short EPG Channel", "category_id": "1",
                                  "epg_channel_id": None}],
            "get_short_epg": short_epg(),
            "get_vod_categories": [{"category_id": "5", "category_name": "Shorts"}],
            "get_vod_streams": [{"stream_id": 50, "name": "Sintel (short)", "category_id": 5,
                                 "container_extension": "mp4", "rating": "7.9"}],
            "get_series_categories": [{"category_id": "9", "category_name": "Drama"}],
            "get_series": [{"series_id": "7", "name": "Test Show", "category_id": "9", "rating": 8.24,
                            "cover": ""}],
            "get_series_info": SERIES_INFO,
        }.get(action, [])

    def json(self, body):
        self.send(json.dumps(body).encode(), "application/json")

    def send(self, data, content_type):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def redirect(self, location):
        self.send_response(302)
        self.send_header("Location", location)
        self.end_headers()

    def log_message(self, fmt, *args):
        print(f"{self.headers.get('User-Agent')} {self.path}", flush=True)


HTTPServer(("0.0.0.0", 8766), Handler).serve_forever()
