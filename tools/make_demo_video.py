"""시연 영상 만들기: 에뮬레이터 녹화 하나를 장면별로 잘라 자막을 얹고 mp4 로 붙인다.

사용: python3 tools/make_demo_video.py <녹화.mp4> <출력.mp4>

에뮬레이터 소리는 adb screenrecord 로 녹음되지 않는다. 앱이 말하는 문장은
「(음성)」 자막으로 넣는다. 이 맥의 ffmpeg 에 drawtext 가 없어 자막은 Pillow 로
투명 PNG 를 그려 overlay 로 얹는다.
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1], sys.argv[2]
W, H, FPS = 1920, 1080, 30
FONT_B = os.path.expanduser("~/Library/Fonts/Pretendard-Bold.otf")
FONT_M = os.path.expanduser("~/Library/Fonts/Pretendard-Medium.otf")
tmp = tempfile.mkdtemp(prefix="cardiguard-video-")


def font(size, bold=True):
    return ImageFont.truetype(FONT_B if bold else FONT_M, size)


def caption(name, title, voice=None):
    """자막은 Pleos 하단 바를 피해 그 위에 띠를 깔고 얹는다. 겹치면 둘 다 안 읽힌다."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ft, fv = font(36), font(30, bold=False)
    lines = [(title, ft, (255, 255, 255))]
    if voice:
        lines.append((f"(음성) {voice}", fv, (255, 205, 198)))
    pad_x, pad_y, gap = 34, 20, 10
    widths = [d.textlength(t, font=f) for t, f, _ in lines]
    heights = [f.size + 6 for _, f, _ in lines]
    bw = max(widths) + pad_x * 2
    bh = sum(heights) + gap * (len(lines) - 1) + pad_y * 2
    bx, by = (W - bw) / 2, H - 104 - bh      # Pleos 하단 바에 걸쳐 앱 화면을 덜 가린다
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=14, fill=(0, 0, 0, 196))
    y = by + pad_y
    for (t, f, color), hgt in zip(lines, heights):
        d.text(((W - d.textlength(t, font=f)) / 2, y), t, font=f, fill=color)
        y += hgt + gap
    p = os.path.join(tmp, name + ".png")
    im.save(p)
    return p


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def clip(idx, start, end, title, voice=None, speed=1.0):
    """[start, end) 구간을 잘라 1920x1080 으로 맞추고 자막을 얹는다."""
    out = os.path.join(tmp, f"c{idx:02d}.mp4")
    png = caption(f"cap{idx:02d}", title, voice)
    vf = f"scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2"
    if speed != 1.0:
        vf += f",setpts=PTS/{speed}"
    run(["ffmpeg", "-v", "error", "-ss", str(start), "-to", str(end), "-i", SRC,
         "-i", png, "-filter_complex", f"[0:v]{vf}[v];[v][1:v]overlay=0:0,fps={FPS}[o]",
         "-map", "[o]", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
         "-pix_fmt", "yuv420p", "-an", out, "-y"])
    return out


SCENES = [
    # (시작, 끝, 자막, 음성자막, 배속)   ※ 시각은 녹화본(raw3) 기준, 프리롤 +3s 반영
    (6, 12.5, "대기 — 오늘 주행과 급제동을 먼저 보여 준다", None, 1.0),
    (13, 21, "기어 D — 주행 감시 시작", None, 1.0),
    (22, 34, "급제동 감지", "시각과 위치를 기록했습니다. 따라가지 마세요.", 1.0),
    (36, 39, "운전자는 내비를 본다. 카디가드는 뒤에 있다", None, 1.0),
    (39, 50, "에어백 전개 — 화면이 뒤에 있어도 감지한다",
     "비상등을 켜고 안전한 곳에 세우세요. 다친 사람이 있으면 119가 먼저예요.", 1.0),
    (50, 56, "앱으로 돌아오면 사고가 이미 올라와 있다", None, 1.0),
    (57, 105, "정차 후 안내 — 한 화면에 한 단계. 그림 띠가 처리한 것을 남긴다", None, 1.7),
    (108, 116, "안내 완료 — 무엇을 처리했는지 남는다", None, 1.0),
    (117, 122, "방금 생긴 기록이 대기 화면에 남는다", None, 1.0),
    (123, 132, "주야간은 앱이 정하지 않는다. 차가 정한다", None, 1.0),
]

parts = [clip(i, *s) for i, s in enumerate(SCENES)]
lst = os.path.join(tmp, "list.txt")
with open(lst, "w") as f:
    for p in parts:
        f.write(f"file '{p}'\n")
run(["ffmpeg", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst,
     "-c", "copy", OUT, "-y"])
print(OUT)
