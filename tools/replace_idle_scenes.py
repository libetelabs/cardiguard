"""완성본에서 대기 화면 장면만 갈아 끼운다.

대기 화면(IdlePanel)을 고쳐 찍은 뒤, 완성본 전체를 다시 만들 수 없어서 쓴다.
완성본은 자막이 이미 박혀 있어 통째로 다시 자르면 자막이 두 겹이 된다.
대신 대기 화면이 나오는 세 토막만 새로 찍어 끼우고 나머지는 그대로 쓴다.

경계는 눈대중이 아니라 자막 띠(아래 140px)의 프레임 간 차이로 찾았다.
  프레임    0 ~  195   장면 1  대기            ← 갈아 끼움
  프레임  196 ~ 2488   장면 2~8 주행·사고·단계  ← 그대로
  프레임 2489 ~ 2672   장면 9  사고 뒤 대기     ← 갈아 끼움
  프레임 2673 ~ 2907   장면 10 주야간          ← 갈아 끼움
  프레임 2908 ~ 4410   덧붙인 장면 5개         ← 그대로
길이(4411프레임 / 147.03초)는 그대로 유지된다.

사용: REC=tools/out/idle-… python3 tools/replace_idle_scenes.py <기존완성본.mp4> <출력.mp4>
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

BASE, OUT = sys.argv[1], sys.argv[2]
REC = os.environ["REC"]
W, H, FPS = 1920, 1080, 30
FONT_B = os.path.expanduser("~/Library/Fonts/Pretendard-Bold.otf")
FONT_M = os.path.expanduser("~/Library/Fonts/Pretendard-Medium.otf")
tmp = tempfile.mkdtemp(prefix="cardiguard-idle-")


def font(size, bold=True):
    return ImageFont.truetype(FONT_B if bold else FONT_M, size)


def caption(name, title, voice=None):
    """자막 모양은 make_demo_video.py 와 한 글자도 다르면 안 된다. 이어 붙으면 티가 난다."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ft, fv = font(36), font(30, bold=False)
    lines = [(title, ft, (255, 255, 255))]
    if voice:
        lines.append((f"(음성) {voice}", fv, (255, 205, 198)))
    pad_x, pad_y, gap = 34, 20, 10
    bw = max(d.textlength(t, font=f) for t, f, _ in lines) + pad_x * 2
    heights = [f.size + 6 for _, f, _ in lines]
    bh = sum(heights) + gap * (len(lines) - 1) + pad_y * 2
    bx, by = (W - bw) / 2, H - 104 - bh
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=14, fill=(0, 0, 0, 196))
    y = by + pad_y
    for (t, f, color), hgt in zip(lines, heights):
        d.text(((W - d.textlength(t, font=f)) / 2, y), t, font=f, fill=color)
        y += hgt + gap
    p = os.path.join(tmp, name + ".png")
    im.save(p)
    return p


ENC = ["-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-an"]


def run(args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def fresh(idx, src, start, frames, title, voice=None):
    """새로 찍은 녹화에서 start 초부터 frames 장을 떼어 자막을 얹는다."""
    out = os.path.join(tmp, f"p{idx}.mp4")
    png = caption(f"cap{idx}", title, voice)
    vf = (f"scale={W}:{H}:force_original_aspect_ratio=decrease,"
          f"pad={W}:{H}:(ow-iw)/2:(oh-ih)/2,fps={FPS}")
    run(["ffmpeg", "-v", "error", "-ss", str(start), "-i", src, "-i", png,
         "-filter_complex", f"[0:v]{vf}[v];[v][1:v]overlay=0:0[o]",
         "-map", "[o]", "-frames:v", str(frames), *ENC, out, "-y"])
    return out


def keep(idx, first, last):
    """완성본의 [first, last] 프레임을 프레임 단위로 그대로 떼어 온다."""
    out = os.path.join(tmp, f"p{idx}.mp4")
    run(["ffmpeg", "-v", "error", "-i", BASE,
         "-vf", f"select='between(n\\,{first}\\,{last})',setpts=N/FRAME_RATE/TB",
         "-vsync", "0", *ENC, out, "-y"])
    return out


parts = [
    fresh(0, f"{REC}/idle-empty.mp4", 3.0, 196,
          "대기 — 오늘 주행·급제동과 경호 상태를 먼저 보여 준다"),
    keep(1, 196, 2488),
    fresh(2, f"{REC}/idle-record.mp4", 3.0, 184,
          "방금 생긴 기록이 대기 화면에 남는다"),
    fresh(3, f"{REC}/idle-night.mp4", 2.2, 235,
          "주야간은 앱이 정하지 않는다. 차가 정한다"),
    keep(4, 2908, 4410),
]

lst = os.path.join(tmp, "list.txt")
with open(lst, "w") as f:
    for p in parts:
        f.write(f"file '{os.path.abspath(p)}'\n")
run(["ffmpeg", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", OUT, "-y"])
print(OUT)
