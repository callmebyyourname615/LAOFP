from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "diagrams" / "switching-kafka-iso20022-flow.png"

WIDTH = 2400
HEIGHT = 2000

BG = "#F5F7FB"
TEXT = "#102033"
MUTED = "#5F6F82"
LANE_BORDER = "#C7D4E5"
ARROW = "#2F5D8A"
SOFT_LINE = "#8EA4BB"

LANE_COLORS = {
    "channel": "#EAF4FF",
    "core": "#EEF7EE",
    "kafka": "#FFF5E8",
    "external": "#F8EEFF",
}

BOX_COLORS = {
    "channel": "#D2E9FF",
    "core": "#DDF2DE",
    "kafka": "#FFE3BD",
    "external": "#E9D9FF",
    "store": "#E7EDF6",
    "note": "#DCE8F7",
}

FONT_REGULAR = "/System/Library/Fonts/Supplemental/Thonburi.ttc"
FONT_BOLD = "/System/Library/Fonts/ThonburiUI.ttc"


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_width: int) -> str:
    wrapped_lines: list[str] = []

    for paragraph in text.split("\n"):
        words = paragraph.split()
        if not words:
            wrapped_lines.append("")
            continue

        current = words[0]
        for word in words[1:]:
            candidate = f"{current} {word}"
            left, top, right, bottom = draw.textbbox((0, 0), candidate, font=font)
            if right - left <= max_width:
                current = candidate
            else:
                wrapped_lines.append(current)
                current = word
        wrapped_lines.append(current)

    return "\n".join(wrapped_lines)


def draw_box(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    w: int,
    h: int,
    fill: str,
    title: str,
    body: str,
    title_size: int = 30,
    body_size: int = 24,
    radius: int = 26,
) -> None:
    shadow_offset = 10
    draw.rounded_rectangle(
        (x + shadow_offset, y + shadow_offset, x + w + shadow_offset, y + h + shadow_offset),
        radius=radius,
        fill="#D6DFEA",
    )
    draw.rounded_rectangle((x, y, x + w, y + h), radius=radius, fill=fill, outline="#A7B8CA", width=3)

    title_font = load_font(title_size, bold=True)
    body_font = load_font(body_size, bold=False)

    wrapped_title = wrap_text(draw, title, title_font, w - 40)
    wrapped_body = wrap_text(draw, body, body_font, w - 46)

    title_bbox = draw.multiline_textbbox((0, 0), wrapped_title, font=title_font, spacing=6, align="center")
    body_bbox = draw.multiline_textbbox((0, 0), wrapped_body, font=body_font, spacing=8, align="left")

    title_height = title_bbox[3] - title_bbox[1]
    body_height = body_bbox[3] - body_bbox[1]
    total_height = title_height + 16 + body_height
    start_y = y + (h - total_height) // 2

    draw.multiline_text(
        (x + w / 2, start_y),
        wrapped_title,
        font=title_font,
        fill=TEXT,
        spacing=6,
        align="center",
        anchor="ma",
    )
    draw.multiline_text(
        (x + 22, start_y + title_height + 16),
        wrapped_body,
        font=body_font,
        fill=TEXT,
        spacing=8,
        align="left",
    )


def draw_lane(draw: ImageDraw.ImageDraw, x1: int, y1: int, x2: int, y2: int, title: str, fill: str) -> None:
    draw.rounded_rectangle((x1, y1, x2, y2), radius=28, fill=fill, outline=LANE_BORDER, width=3)
    title_font = load_font(34, bold=True)
    draw.text((x1 + 28, y1 + 18), title, font=title_font, fill=TEXT)


def draw_poly_arrow(
    draw: ImageDraw.ImageDraw,
    points: list[tuple[int, int]],
    label: str | None = None,
    label_pos: tuple[int, int] | None = None,
    color: str = ARROW,
    width: int = 6,
) -> None:
    draw.line(points, fill=color, width=width, joint="curve")

    (x1, y1), (x2, y2) = points[-2], points[-1]
    angle = math.atan2(y2 - y1, x2 - x1)
    head_length = 18
    head_angle = math.pi / 8

    left = (
        x2 - head_length * math.cos(angle - head_angle),
        y2 - head_length * math.sin(angle - head_angle),
    )
    right = (
        x2 - head_length * math.cos(angle + head_angle),
        y2 - head_length * math.sin(angle + head_angle),
    )
    draw.polygon([(x2, y2), left, right], fill=color)

    if label and label_pos:
        font = load_font(22, bold=False)
        text = wrap_text(draw, label, font, 280)
        bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=4, align="center")
        padding = 10
        box = (
            label_pos[0] + bbox[0] - padding,
            label_pos[1] + bbox[1] - padding,
            label_pos[0] + bbox[2] + padding,
            label_pos[1] + bbox[3] + padding,
        )
        draw.rounded_rectangle(box, radius=14, fill="#FFFFFF", outline="#D0D9E4", width=2)
        draw.multiline_text(label_pos, text, font=font, fill=TEXT, spacing=4, align="center", anchor="mm")


def draw_soft_connector(draw: ImageDraw.ImageDraw, start: tuple[int, int], end: tuple[int, int], label: str) -> None:
    draw.line([start, end], fill=SOFT_LINE, width=4)
    font = load_font(20)
    bbox = draw.textbbox((0, 0), label, font=font)
    label_x = (start[0] + end[0]) / 2
    label_y = (start[1] + end[1]) / 2 - 16
    draw.rounded_rectangle(
        (
            label_x + bbox[0] - 10,
            label_y + bbox[1] - 6,
            label_x + bbox[2] + 10,
            label_y + bbox[3] + 6,
        ),
        radius=12,
        fill="#FFFFFF",
        outline="#D0D9E4",
        width=1,
    )
    draw.text((label_x, label_y), label, font=font, fill=MUTED, anchor="mm")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate the switching + Kafka + ISO 20022 flowchart.")
    parser.add_argument(
        "--output",
        type=Path,
        default=OUTPUT,
        help="Output PNG path",
    )
    args = parser.parse_args()

    output_path = args.output.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    image = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(image)

    title_font = load_font(52, bold=True)
    subtitle_font = load_font(24)
    badge_font = load_font(24, bold=True)
    note_title_font = load_font(28, bold=True)
    note_body_font = load_font(24)

    draw.text((60, 44), "Switching, Kafka, and ISO 20022 End-to-End Flow", font=title_font, fill=TEXT)
    subtitle = (
        "Detailed PoC architecture for transfer intake, outbox publishing, Kafka event transport, "
        "ISO 20022 mapping, external dispatch, and asynchronous status updates"
    )
    draw.text((60, 112), wrap_text(draw, subtitle, subtitle_font, 2100), font=subtitle_font, fill=MUTED)

    badge_text = "Suggested first scope: REST intake + pacs.008 outbound + pacs.002 status inbound"
    badge_box = (60, 155, 920, 202)
    draw.rounded_rectangle(badge_box, radius=18, fill="#DCEBFF", outline="#AFC8EA", width=2)
    draw.text((84, 178), badge_text, font=badge_font, fill="#234C77")

    lane_top = 230
    lane_bottom = 1410

    draw_lane(draw, 40, lane_top, 530, lane_bottom, "Channel / Upstream", LANE_COLORS["channel"])
    draw_lane(draw, 570, lane_top, 1130, lane_bottom, "Switching Core", LANE_COLORS["core"])
    draw_lane(draw, 1170, lane_top, 1730, lane_bottom, "Kafka / Integration Layer", LANE_COLORS["kafka"])
    draw_lane(draw, 1770, lane_top, 2360, lane_bottom, "External Participant", LANE_COLORS["external"])

    boxes = {
        "a1": (90, 300, 390, 180, BOX_COLORS["channel"], "1. Channel App / API Client",
               "Submit transfer request.\nPOST /api/v1/transfers\nHeaders: X-Channel-Id, Idempotency-Key"),
        "a2": (90, 1015, 390, 190, BOX_COLORS["channel"], "10. Inquiry API / Notification",
               "Channel polls by transferRef or receives webhook/event to display latest status."),
        "b1": (620, 285, 460, 200, BOX_COLORS["core"], "2. Transfer API",
               "Validate payload, check participant availability, find routing rule, reject malformed or blocked requests."),
        "b2": (620, 555, 460, 220, BOX_COLORS["core"], "3. Transaction Save",
               "Persist transfer = RECEIVED, status history, idempotency record, outbox = NEW, and audit log in one DB transaction."),
        "b3": (620, 845, 460, 180, BOX_COLORS["core"], "4. Outbox Publisher",
               "Read NEW outbox rows, build internal event, publish to Kafka only after DB commit."),
        "b4": (620, 1095, 460, 220, BOX_COLORS["core"], "9. Status Update Service",
               "Normalize callback or pacs.002 result, update transfer to PROCESSING, PENDING, SUCCESS, or FAILED, then save history and audit."),
        "c1": (1220, 430, 460, 150, BOX_COLORS["kafka"], "5. Kafka Topic: switch.transfer.created",
               "Key by transferRef or UETR for partition ordering and easier replay."),
        "c2": (1220, 660, 460, 230, BOX_COLORS["kafka"], "6. ISO 20022 Mapper + Dispatch Consumer",
               "Consume created event, map internal transfer to pacs.008, add BizMsgIdr, MsgId, UETR, and apply route-specific adapter."),
        "c3": (1220, 990, 460, 190, BOX_COLORS["kafka"], "8. Status Ingress / Result Normalizer",
               "Receive async callback, poll result, or pacs.002 from external side and convert it into a normalized status event."),
        "c4": (1220, 1240, 460, 150, BOX_COLORS["kafka"], "11. Kafka Topic: switch.transfer.status.updated",
               "Fan out status events to inquiry, notification, and reconciliation consumers."),
        "d1": (1820, 790, 460, 240, BOX_COLORS["external"], "7. External Bank / Switch",
               "Accept pacs.008 over REST, MQ, or another bank connector.\nReturn immediate ACK, async pacs.002, timeout, or rejection."),
        "s1": (520, 1480, 500, 120, BOX_COLORS["store"], "Operational DB",
               "transfers, routing_rules, idempotency_records, status_history, audit_logs"),
        "s2": (1040, 1480, 500, 120, BOX_COLORS["store"], "Raw ISO Message Store",
               "raw pacs.008, pacs.002, business headers, correlation IDs, connector payloads"),
        "s3": (1560, 1480, 500, 120, BOX_COLORS["store"], "Retry / DLQ / Reconciliation",
               "failed publishes, replay queue, duplicate repair, manual operations review"),
    }

    for item in boxes.values():
        draw_box(draw, *item)

    status_y = 235
    chip_font = load_font(22, bold=True)
    chips = [
        ("RECEIVED", "#E4F2FF", "#4C87C6"),
        ("PROCESSING", "#FFF2D9", "#C47B17"),
        ("PENDING", "#FCE8B7", "#AE7C00"),
        ("SUCCESS", "#DCF5DD", "#2F7C35"),
        ("FAILED", "#F9D8D8", "#A53838"),
    ]
    chip_x = 1320
    for label, fill, edge in chips:
        left = chip_x
        top = status_y
        right = chip_x + 170
        bottom = status_y + 42
        draw.rounded_rectangle((left, top, right, bottom), radius=18, fill=fill, outline=edge, width=2)
        draw.text(((left + right) / 2, (top + bottom) / 2), label, font=chip_font, fill=edge, anchor="mm")
        chip_x += 182

    draw_poly_arrow(draw, [(480, 390), (620, 390)], "submit transfer", (550, 348))
    draw_poly_arrow(draw, [(850, 485), (850, 555)], "validated request", (940, 520))
    draw_poly_arrow(draw, [(850, 775), (850, 845)], "commit complete", (955, 810))
    draw_poly_arrow(draw, [(1080, 935), (1220, 505)], "publish created event", (1138, 688))
    draw_poly_arrow(draw, [(1450, 580), (1450, 660)], "Kafka delivers to consumer", (1572, 616))
    draw_poly_arrow(draw, [(1680, 775), (1820, 910)], "pacs.008 / connector request", (1710, 720))
    draw_poly_arrow(draw, [(1820, 950), (1680, 1085)], "ACK / pacs.002 / timeout / reject", (1782, 1065))
    draw_poly_arrow(draw, [(1220, 1085), (1080, 1205)], "normalized result", (1152, 1166))
    draw_poly_arrow(draw, [(1080, 1205), (1220, 1315)], "publish status event", (1166, 1260))
    draw_poly_arrow(draw, [(1220, 1315), (480, 1110)], "channel reads latest status or receives notification", (830, 1365))

    draw_soft_connector(draw, (850, 775), (770, 1480), "write")
    draw_soft_connector(draw, (850, 1315), (850, 1480), "update")
    draw_soft_connector(draw, (1450, 890), (1290, 1480), "store raw XML")
    draw_soft_connector(draw, (1450, 1180), (1360, 1480), "store response")
    draw_soft_connector(draw, (1450, 580), (1810, 1480), "retry/DLQ")
    draw_soft_connector(draw, (1450, 1390), (1810, 1480), "ops replay")
    draw_soft_connector(draw, (285, 1205), (610, 1480), "query by ref")

    note_x = 60
    note_y = 1650
    note_w = 2280
    note_h = 250
    draw.rounded_rectangle((note_x, note_y, note_x + note_w, note_y + note_h), radius=28, fill=BOX_COLORS["note"], outline="#AEC4E1", width=3)
    draw.text((note_x + 28, note_y + 24), "Design Rules For This PoC", font=note_title_font, fill=TEXT)

    note_lines = (
        "1. Keep the database as the system of record; Kafka is the asynchronous transport layer.\n"
        "2. Publish from the outbox only after the transfer transaction commits successfully.\n"
        "3. Use transferRef, UETR, and messageId for ordering, correlation, deduplication, and replay safety.\n"
        "4. Start small: implement pacs.008 outbound and pacs.002 inbound first; add returns, cancellations, limits, and reconciliation next."
    )
    wrapped_note = wrap_text(draw, note_lines, note_body_font, note_w - 60)
    draw.multiline_text((note_x + 28, note_y + 72), wrapped_note, font=note_body_font, fill=TEXT, spacing=10)

    footer_font = load_font(20)
    footer = f"Generated from {ROOT / 'scripts' / 'generate_switching_kafka_flowchart.py'}"
    draw.text((60, 1955), footer, font=footer_font, fill=MUTED)

    image.save(output_path)
    print(f"Saved {output_path}")


if __name__ == "__main__":
    main()
