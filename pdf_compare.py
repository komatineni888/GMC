import fitz  # PyMuPDF
import difflib
import re
from PIL import Image, ImageDraw

def normalize_text(s: str) -> str:
    s = s.lower()
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"[^\w\s]", "", s)  # remove punctuation
    return s.strip()

def page_signature(doc: fitz.Document, i: int, maxlen: int = 2000) -> str:
    txt = doc[i].get_text("text") or ""
    txt = normalize_text(txt)
    return txt[:maxlen]

def extract_words(page: fitz.Page):
    """
    Returns: list of (word, (x0,y0,x1,y1)) in reading order.
    """
    words = page.get_text("words")  # x0,y0,x1,y1,"word",block,line,wordno
    # Sort in reading order (top-to-bottom, left-to-right)
    words.sort(key=lambda w: (round(w[1], 1), round(w[0], 1)))
    out = [(w[4], (w[0], w[1], w[2], w[3])) for w in words if w[4].strip()]
    return out

def diff_word_indices(a_words, b_words):
    """
    Returns indices in b_words that are inserted/replaced relative to a_words.
    """
    sm = difflib.SequenceMatcher(a=[w for w, _ in a_words], b=[w for w, _ in b_words])
    changed_b = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag in ("insert", "replace"):
            changed_b.extend(range(j1, j2))
    return changed_b

def merge_rects(rects, x_pad=1.5, y_pad=1.0, same_line_tol=3.0):
    """
    Merge nearby rects to reduce noisy highlighting.
    A simple heuristic: merge rects that overlap vertically (same line) and are close horizontally.
    """
    if not rects:
        return []
    rects = sorted(rects, key=lambda r: (r[1], r[0]))
    merged = []
    cur = list(rects[0])
    for r in rects[1:]:
        x0,y0,x1,y1 = r
        # same line?
        if abs(y0 - cur[1]) <= same_line_tol and x0 <= cur[2] + 5:
            cur[0] = min(cur[0], x0)
            cur[1] = min(cur[1], y0)
            cur[2] = max(cur[2], x1)
            cur[3] = max(cur[3], y1)
        else:
            merged.append(tuple(cur))
            cur = [x0,y0,x1,y1]
    merged.append(tuple(cur))
    # pad
    padded = []
    for x0,y0,x1,y1 in merged:
        padded.append((x0 - x_pad, y0 - y_pad, x1 + x_pad, y1 + y_pad))
    return padded

def render_page_with_highlights(page: fitz.Page, highlight_rects, zoom=2.0):
    """
    Renders a page to an image, draws filled yellow rectangles, returns PIL.Image.
    Rect coordinates are in PDF space; we scale them by zoom to match the pixmap.
    """
    mat = fitz.Matrix(zoom, zoom)
    pix = page.get_pixmap(matrix=mat, alpha=False)

    img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
    draw = ImageDraw.Draw(img, "RGBA")

    # Solid yellow (burned-in). You can change alpha to make it semi-transparent.
    fill = (255, 255, 0, 120)  # yellow with transparency
    outline = (255, 220, 0, 180)

    for (x0,y0,x1,y1) in highlight_rects:
        X0, Y0, X1, Y1 = (x0*zoom, y0*zoom, x1*zoom, y1*zoom)
        draw.rectangle([X0, Y0, X1, Y1], fill=fill, outline=outline)

    return img

def add_image_as_pdf_page(outdoc: fitz.Document, pil_img: Image.Image):
    """
    Inserts a PIL image as a full page in outdoc.
    """
    # Convert PIL image to PNG bytes
    import io
    buf = io.BytesIO()
    pil_img.save(buf, format="PNG")
    png_bytes = buf.getvalue()

    # Create a new page sized to the image (in points)
    # Assume 72 dpi base; since we rendered with zoom, dimensions are already pixels at higher res.
    # We can map pixels to points directly for a "what you see is what you get" review PDF.
    w, h = pil_img.size
    page = outdoc.new_page(width=w, height=h)
    rect = fitz.Rect(0, 0, w, h)
    page.insert_image(rect, stream=png_bytes)

def build_summary_page(outdoc: fitz.Document, lines):
    """
    Simple text summary page.
    """
    page = outdoc.new_page(width=612, height=792)  # Letter
    y = 50
    page.insert_text((50, y), "PDF Compare Summary", fontsize=18)
    y += 30
    for line in lines:
        page.insert_text((50, y), line, fontsize=11)
        y += 16
        if y > 740:
            page = outdoc.new_page(width=612, height=792)
            y = 50

def compare_pdfs(pdf_a_path, pdf_b_path, out_path, zoom=2.0):
    doc_a = fitz.open(pdf_a_path)
    doc_b = fitz.open(pdf_b_path)
    out = fitz.open()

    sig_a = [page_signature(doc_a, i) for i in range(doc_a.page_count)]
    sig_b = [page_signature(doc_b, i) for i in range(doc_b.page_count)]

    sm_pages = difflib.SequenceMatcher(a=sig_a, b=sig_b)
    ops = sm_pages.get_opcodes()

    summary_lines = []
    # We’ll also build a mapping list for transparency
    summary_lines.append(f"A pages: {doc_a.page_count}, B pages: {doc_b.page_count}")
    summary_lines.append("")

    # Summarize ops
    for tag, i1, i2, j1, j2 in ops:
        if tag == "equal":
            summary_lines.append(f"Unchanged block: A {i1+1}-{i2} ↔ B {j1+1}-{j2}")
        elif tag == "replace":
            summary_lines.append(f"Replaced block:  A {i1+1}-{i2} ↔ B {j1+1}-{j2}")
        elif tag == "insert":
            summary_lines.append(f"Inserted in B:   B {j1+1}-{j2}")
        elif tag == "delete":
            summary_lines.append(f"Deleted from A:  A {i1+1}-{i2}")

    build_summary_page(out, summary_lines)

    # Generate review pages
    for tag, i1, i2, j1, j2 in ops:
        if tag == "equal":
            # Pair pages 1:1 within the block
            for k in range(i2 - i1):
                pa = doc_a[i1 + k]
                pb = doc_b[j1 + k]
                # You can skip highlighting for equal; but many teams still do a quick diff.
                # Here: do a quick diff anyway (in case signatures collide).
                a_words = extract_words(pa)
                b_words = extract_words(pb)
                changed_idx = diff_word_indices(a_words, b_words)
                rects = [b_words[idx][1] for idx in changed_idx]
                rects = merge_rects(rects)
                img = render_page_with_highlights(pb, rects, zoom=zoom)
                add_image_as_pdf_page(out, img)

        elif tag == "replace":
            # Compare pages in order; if lengths differ, treat extras as inserted/deleted inside replace block
            len_a = i2 - i1
            len_b = j2 - j1
            common = min(len_a, len_b)

            for k in range(common):
                pa = doc_a[i1 + k]
                pb = doc_b[j1 + k]
                a_words = extract_words(pa)
                b_words = extract_words(pb)
                changed_idx = diff_word_indices(a_words, b_words)
                rects = [b_words[idx][1] for idx in changed_idx]
                rects = merge_rects(rects)
                img = render_page_with_highlights(pb, rects, zoom=zoom)
                add_image_as_pdf_page(out, img)

            # Extra B pages = inserted
            for k in range(common, len_b):
                pb = doc_b[j1 + k]
                img = render_page_with_highlights(pb, [], zoom=zoom)
                # Add a simple “Inserted” banner by drawing on the image
                draw = ImageDraw.Draw(img, "RGBA")
                draw.rectangle([0, 0, img.size[0], 50], fill=(255, 255, 0, 180))
                draw.text((10, 15), f"INSERTED PAGE (B page {j1 + k + 1})", fill=(0, 0, 0, 255))
                add_image_as_pdf_page(out, img)

            # Extra A pages = deleted (placeholder)
            for k in range(common, len_a):
                # Create a placeholder PDF page
                page = out.new_page(width=612, height=792)
                page.insert_text((50, 100), f"DELETED PAGE (A page {i1 + k + 1})", fontsize=20)
                page.insert_text((50, 140), "This page existed in A but not in B.", fontsize=12)

        elif tag == "insert":
            # Inserted pages in B: include them (no highlights)
            for k in range(j1, j2):
                pb = doc_b[k]
                img = render_page_with_highlights(pb, [], zoom=zoom)
                draw = ImageDraw.Draw(img, "RGBA")
                draw.rectangle([0, 0, img.size[0], 50], fill=(255, 255, 0, 180))
                draw.text((10, 15), f"INSERTED PAGE (B page {k+1})", fill=(0, 0, 0, 255))
                add_image_as_pdf_page(out, img)

        elif tag == "delete":
            # Deleted pages from A: add placeholders
            for k in range(i1, i2):
                page = out.new_page(width=612, height=792)
                page.insert_text((50, 100), f"DELETED PAGE (A page {k+1})", fontsize=20)
                page.insert_text((50, 140), "This page existed in A but not in B.", fontsize=12)

    out.save(out_path)
    out.close()
    doc_a.close()
    doc_b.close()

if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser(description="Compare two PDFs and produce a highlighted review PDF.")
    p.add_argument("pdf_a", help="Original PDF (A)")
    p.add_argument("pdf_b", help="Updated PDF (B)")
    p.add_argument("--out", default="review.pdf", help="Output review PDF path")
    p.add_argument("--zoom", type=float, default=2.0, help="Render zoom (higher = sharper output)")
    args = p.parse_args()

    compare_pdfs(args.pdf_a, args.pdf_b, args.out, zoom=args.zoom)
    print(f"Saved: {args.out}")
