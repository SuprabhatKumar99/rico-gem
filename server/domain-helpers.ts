import type { ArticleRecord, ArticleVersionRecord, RichTextBlock, RichTextContent } from "./store.ts";

export interface SecurityFinding {
  severity: "HIGH" | "MEDIUM" | "LOW";
  detail: string;
}

export function scanSensitiveContent(content: string): SecurityFinding[] {
  const text = String(content || "");
  if (!text.trim()) return [];
  const findings: SecurityFinding[] = [];

  if (/-----BEGIN [A-Z ]*PRIVATE KEY-----/.test(text)) {
    findings.push({ severity: "HIGH", detail: "Private cryptographic key header detected." });
  }
  if (/\bAKIA[0-9A-Z]{16}\b/.test(text)) {
    findings.push({ severity: "HIGH", detail: "Potential AWS access key ID pattern detected." });
  }
  if (/\b(?:sk_live|rk_live|ghp_|glpat-)[A-Za-z0-9_-]{12,}\b/.test(text)) {
    findings.push({ severity: "HIGH", detail: "Potential live API secret or access token detected." });
  }
  if (/\b\d{3}-\d{2}-\d{4}\b/.test(text)) {
    findings.push({ severity: "HIGH", detail: "Potential US Social Security Number pattern (###-##-####) detected." });
  }
  if (/\b(?:\d[ -]*?){13,16}\b/.test(text) && /\b(?:4\d{3}|5[1-5]\d{2}|3[47]\d{2}|6011)[ -]?\d{4}/.test(text)) {
    findings.push({ severity: "HIGH", detail: "Potential payment card number sequence detected." });
  }
  if (/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/.test(text)) {
    findings.push({ severity: "MEDIUM", detail: "Email address detected; verify it is an approved public contact." });
  }
  if (/(?:password|passwd|secret|api[_-]?key)\s*[:=]\s*["']?[^\s"']{6,}/i.test(text)) {
    findings.push({ severity: "HIGH", detail: "Hardcoded credential assignment detected." });
  }

  return findings;
}

export interface BlockDiffEntry {
  blockId: string;
  change: "ADDED" | "REMOVED" | "MODIFIED";
  before: RichTextBlock | null;
  after: RichTextBlock | null;
}

export function diffArticleBlocks(fromBlocks: RichTextBlock[], toBlocks: RichTextBlock[]): BlockDiffEntry[] {
  const fromMap = new Map<string, RichTextBlock>();
  const toMap = new Map<string, RichTextBlock>();

  fromBlocks.forEach((b, idx) => fromMap.set(b.id || `block-${idx}`, b));
  toBlocks.forEach((b, idx) => toMap.set(b.id || `block-${idx}`, b));

  const diffs: BlockDiffEntry[] = [];
  const seen = new Set<string>();

  for (const [id, after] of toMap.entries()) {
    seen.add(id);
    const before = fromMap.get(id) ?? null;
    if (!before) {
      diffs.push({ blockId: id, change: "ADDED", before: null, after });
    } else if (JSON.stringify(before) !== JSON.stringify(after)) {
      diffs.push({ blockId: id, change: "MODIFIED", before, after });
    }
  }

  for (const [id, before] of fromMap.entries()) {
    if (!seen.has(id)) {
      diffs.push({ blockId: id, change: "REMOVED", before, after: null });
    }
  }

  return diffs;
}

export function evaluateArticleQuality(article: ArticleRecord, version: ArticleVersionRecord | null) {
  const warnings: string[] = [];
  let score = 100;
  const blocks = version?.content?.blocks ?? [];

  if (!article.title || article.title.trim().length < 6) {
    warnings.push("Title is very short; use a descriptive customer-facing title.");
    score -= 15;
  }
  if (!article.categoryId) {
    warnings.push("Article is not assigned to a category.");
    score -= 10;
  }
  if (blocks.length === 0) {
    warnings.push("Article version has no content blocks.");
    score -= 40;
  } else {
    const hasHeading = blocks.some((b) => b.type === "heading");
    if (!hasHeading) {
      warnings.push("Add at least one heading block to improve scannability and table of contents.");
      score -= 10;
    }
    const textLen = blocks
      .map((b) => (b.text || "") + (b.code || "") + (Array.isArray(b.items) ? b.items.join(" ") : ""))
      .join(" ")
      .trim().length;
    if (textLen < 60) {
      warnings.push("Body content is under 60 characters; expand with actionable steps.");
      score -= 15;
    }
    const secFindings = scanSensitiveContent(blocks.map((b) => b.text || b.code || "").join("\n"));
    for (const f of secFindings) {
      if (f.severity === "HIGH") {
        warnings.push(`Sensitive content check: ${f.detail}`);
        score -= 20;
      }
    }
  }
  if (!article.tags.length) {
    warnings.push("Add tags to improve search discoverability.");
    score -= 5;
  }

  score = Math.max(0, Math.min(100, score));
  return {
    articleId: article.id,
    versionId: version?.id ?? null,
    score,
    readyForVerification: score >= 60 && blocks.length > 0,
    warnings
  };
}

function collectNodeText(node: Record<string, unknown>): string {
  if (typeof node.text === "string") return node.text;
  if (Array.isArray(node.content)) {
    return node.content
      .filter((child): child is Record<string, unknown> => Boolean(child) && typeof child === "object")
      .map((child) => collectNodeText(child))
      .join("");
  }
  return "";
}

export function tiptapToBlocks(doc: unknown): RichTextContent {
  if (!doc || typeof doc !== "object") return { blocks: [] };
  const rawNodes = (doc as { content?: unknown }).content;
  if (!Array.isArray(rawNodes)) return { blocks: [] };

  const blocks: RichTextBlock[] = [];
  rawNodes.forEach((raw, idx) => {
    if (!raw || typeof raw !== "object") return;
    const node = raw as Record<string, unknown>;
    const attrs = (node.attrs && typeof node.attrs === "object" ? node.attrs : {}) as Record<string, unknown>;
    const id = typeof attrs.blockId === "string" && attrs.blockId ? attrs.blockId : `b-${idx + 1}`;
    const type = String(node.type || "");

    if (type === "heading") {
      blocks.push({
        id,
        type: "heading",
        level: Number(attrs.level || 2),
        text: collectNodeText(node)
      });
    } else if (type === "codeBlock") {
      blocks.push({ id, type: "code", text: collectNodeText(node) });
    } else if (type === "bulletList" || type === "orderedList") {
      const listItems = Array.isArray(node.content)
        ? node.content
            .filter((li): li is Record<string, unknown> => Boolean(li) && typeof li === "object")
            .map((li) => collectNodeText(li))
            .filter(Boolean)
        : [];
      blocks.push({ id, type: "list", items: listItems });
    } else if (type === "image") {
      blocks.push({
        id,
        type: "image",
        url: typeof attrs.src === "string" ? attrs.src : "",
        alt: typeof attrs.alt === "string" ? attrs.alt : ""
      });
    } else {
      const text = collectNodeText(node);
      if (text.trim()) {
        blocks.push({ id, type: "paragraph", text });
      }
    }
  });

  return { blocks };
}

export function transformWriting(operation: string, text: string, language: string, guidance: string): string {
  const clean = text.trim();
  if (!clean) return "Provide source text to process.";
  const op = (operation || "IMPROVE").toUpperCase();
  const note = guidance.trim() ? ` Guidance applied: ${guidance.trim()}.` : "";

  switch (op) {
    case "SUMMARIZE": {
      const sentences = clean.split(/(?<=[.!?])\s+/).filter(Boolean);
      const summary = sentences.slice(0, 2).join(" ");
      return `Summary (${language || "en"}):\n• ${summary}${sentences.length > 2 ? `\n• Covers ${sentences.length} key statements distilled for quick scanning.` : ""}${note}`;
    }
    case "SIMPLIFY": {
      const lines = clean.split(/\r?\n/).filter(Boolean).map((l) => l.replace(/\s+/g, " ").trim());
      return `Simplified version (${language || "en"}):\n${lines.map((l, i) => `${i + 1}. ${l}`).join("\n")}${note}`;
    }
    case "TONE":
    case "PROFESSIONAL_TONE":
      return `Clear & Professional (${language || "en"}):\n${clean}\n\nNext steps: Confirm the configuration above and contact support if issues persist.${note}`;
    case "TRANSLATE": {
      const lang = (language || "fr").toLowerCase();
      const prefixes: Record<string, string> = {
        fr: "[Version française]",
        es: "[Versión en español]",
        de: "[Deutsche Fassung]",
        hi: "[हिंदी अनुवाद]",
        ja: "[日本語訳]"
      };
      return `${prefixes[lang] || `[Translated to ${lang.toUpperCase()}]`}\n\n${clean}${note}`;
    }
    default:
      return `${clean}\n\nKey takeaway: Follow each step in sequence and verify results in your workspace.${note}`;
  }
}

export function buildTranslationDraft(article: ArticleRecord, blocks: RichTextBlock[], targetLanguage: string) {
  const lang = targetLanguage.trim().toLowerCase() || "fr";
  const labelMap: Record<string, string> = {
    fr: "FR",
    es: "ES",
    de: "DE",
    hi: "HI",
    ja: "JA",
    pt: "PT"
  };
  const tag = labelMap[lang] || lang.toUpperCase();
  const translatedTitle = `[${tag}] ${article.title}`;
  const translatedText = blocks
    .map((b) => {
      if (b.type === "heading") return `## [${tag}] ${b.text || ""}`;
      if (b.type === "list") return (b.items || []).map((i) => `- ${i}`).join("\n");
      if (b.type === "code") return `\`\`\`\n${b.text || b.code || ""}\n\`\`\``;
      return b.text || "";
    })
    .filter(Boolean)
    .join("\n\n");

  return {
    translatedTitle,
    translatedContent: translatedText || `[${tag}] ${article.title}`
  };
}

export function calculateReadingStats(blocks: RichTextBlock[]): { wordCount: number; readingMinutes: number } {
  const words = blocks
    .map((b) => {
      if (b.type === "list") return (b.items || []).join(" ");
      return b.text || b.code || "";
    })
    .join(" ")
    .trim()
    .split(/\s+/)
    .filter(Boolean).length;

  return {
    wordCount: words,
    readingMinutes: Math.max(1, Math.ceil(words / 200))
  };
}

export function slugifyTitle(title: string): string {
  const base = title
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 64);
  return base || "untitled-article";
}

export interface RankableKnowledgeRequest {
  status: string;
  priority: string;
  upvotes: number;
  createdAt: string;
}

export function rankKnowledgeRequests<T extends RankableKnowledgeRequest>(items: T[]): T[] {
  const statusWeight: Record<string, number> = {
    OPEN: 3,
    IN_PROGRESS: 2,
    DRAFT_CREATED: 1,
    COMPLETED: 0,
    DECLINED: -1
  };
  const priorityWeight: Record<string, number> = {
    URGENT: 4,
    HIGH: 3,
    MEDIUM: 2,
    LOW: 1
  };
  return [...items].sort((a, b) => {
    const sw = (statusWeight[b.status] ?? 0) - (statusWeight[a.status] ?? 0);
    if (sw !== 0) return sw;
    const pw = (priorityWeight[b.priority] ?? 0) - (priorityWeight[a.priority] ?? 0);
    if (pw !== 0) return pw;
    if (b.upvotes !== a.upvotes) return b.upvotes - a.upvotes;
    return b.createdAt.localeCompare(a.createdAt);
  });
}

export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

export function normalizeThemePreference(value: string | null | undefined): ThemePreference {
  const cleaned = String(value || "").trim().toLowerCase();
  if (cleaned === "dark" || cleaned === "light" || cleaned === "system") {
    return cleaned;
  }
  return "system";
}

export function resolveThemeMode(
  preference: string | null | undefined,
  systemPrefersDark: boolean
): ResolvedTheme {
  const normalized = normalizeThemePreference(preference);
  if (normalized === "dark") return "dark";
  if (normalized === "light") return "light";
  return systemPrefersDark ? "dark" : "light";
}

export function toggleThemeMode(currentResolved: ResolvedTheme): ResolvedTheme {
  return currentResolved === "dark" ? "light" : "dark";
}


