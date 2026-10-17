"""Build the v2.3 PDF directly from its editable Markdown source."""
from pathlib import Path
import re
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/GoServe-project-plan-v2.3.md'
OUTPUT = ROOT / 'output/pdf/GoServe_Project_Plan_v2.3.pdf'

def inline(text):
    return re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', escape(text))

def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name='PlanBody', fontName='Helvetica', fontSize=9.1, leading=13.2, spaceAfter=7))
    styles.add(ParagraphStyle(name='PlanH1', fontName='Helvetica-Bold', fontSize=22, leading=26, spaceAfter=13, textColor=colors.HexColor('#171717')))
    styles.add(ParagraphStyle(name='PlanH2', fontName='Helvetica-Bold', fontSize=13.8, leading=18, spaceBefore=17, spaceAfter=9, keepWithNext=True))
    styles.add(ParagraphStyle(name='PlanH3', fontName='Helvetica-Bold', fontSize=10.8, leading=15, spaceBefore=11, spaceAfter=7, keepWithNext=True))
    styles.add(ParagraphStyle(name='Cell', fontName='Helvetica', fontSize=8, leading=10.8, spaceAfter=0))
    styles.add(ParagraphStyle(name='HeadCell', parent=styles['Cell'], fontName='Helvetica-Bold', textColor=colors.white))
    flow = []
    lines = SOURCE.read_text(encoding='utf-8').splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                cells = [x.strip() for x in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c.replace(' ', '')) for c in cells):
                    rows.append(cells)
                i += 1
            n = len(rows[0])
            usable = 174 * mm
            if n == 2:
                widths = [usable * .29, usable * .71]
            elif n == 3:
                widths = [usable * .18, usable * .30, usable * .52]
            else:
                widths = [usable / n] * n
            data = [[Paragraph(inline(c), styles['HeadCell' if r == 0 else 'Cell']) for c in row] for r, row in enumerate(rows)]
            table = Table(data, colWidths=widths, repeatRows=1, hAlign='LEFT')
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#282828')),
                ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.HexColor('#f3f3f3'), colors.white]),
                ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                ('LEFTPADDING', (0, 0), (-1, -1), 7), ('RIGHTPADDING', (0, 0), (-1, -1), 7),
                ('TOPPADDING', (0, 0), (-1, -1), 5.5), ('BOTTOMPADDING', (0, 0), (-1, -1), 5.5),
                ('LINEBELOW', (0, 0), (-1, 0), .5, colors.HexColor('#555555')),
                ('LINEBELOW', (0, 1), (-1, -1), .25, colors.HexColor('#dedede')),
            ]))
            flow.extend([table, Spacer(1, 8)])
            continue
        if line.startswith('### '):
            flow.append(Paragraph(inline(line[4:]), styles['PlanH3']))
        elif line.startswith('## '):
            flow.append(Paragraph(inline(line[3:]), styles['PlanH2']))
        elif line.startswith('# '):
            flow.append(Paragraph(inline(line[2:]), styles['PlanH1']))
        else:
            paragraph = [line]
            while i + 1 < len(lines) and lines[i + 1].strip() and not lines[i + 1].startswith(('#', '|')) and not re.match(r'^\d+\. ', lines[i + 1]):
                i += 1
                paragraph.append(lines[i].strip())
            flow.append(Paragraph(inline(' '.join(paragraph)), styles['PlanBody']))
        i += 1

    def furniture(canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor('#cccccc'))
        canvas.line(18 * mm, 16 * mm, 192 * mm, 16 * mm)
        canvas.setFillColor(colors.HexColor('#555555'))
        canvas.setFont('Helvetica', 8)
        canvas.drawString(18 * mm, 11 * mm, 'GoServe  |  Updated program and implementation plan  |  v2.3')
        canvas.drawRightString(192 * mm, 11 * mm, str(doc.page))
        if doc.page > 1:
            canvas.setFont('Helvetica', 7.5)
            canvas.drawString(18 * mm, 286 * mm, 'TIP MANILA SANDBOX  /  16 OCTOBER 2026')
        canvas.restoreState()

    doc = SimpleDocTemplate(str(OUTPUT), pagesize=(210 * mm, 297 * mm), rightMargin=18 * mm, leftMargin=18 * mm, topMargin=18 * mm, bottomMargin=23 * mm, title='GoServe Updated Program and Implementation Plan Version 2.3', author='GoServe project', subject='Approved program requirements and gated implementation plan')
    doc.build(flow, onFirstPage=furniture, onLaterPages=furniture)
    print(OUTPUT)

if __name__ == '__main__':
    build()
